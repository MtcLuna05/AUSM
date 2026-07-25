package com.l.ausm.impl.client.dynamic;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.Reference;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.compat.ProjectRedHaloRenderer;
import com.l.ausm.impl.pipeline.vertex.BlockRenderContext;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Mod.EventBusSubscriber(value = Side.CLIENT, modid = Reference.MODID)
public final class DynamicLightManager {
    private static final int UPDATE_INTERVAL_TICKS = 2;
    private static final int REBUILD_PADDING = 2;
    private static volatile boolean active;
    private static volatile List<DynamicLightSource> activeSources = List.of();
    private static Map<String, DynamicLightSource> previousSources = Map.of();
    private static World previousWorld;
    private static int ticks;
    private static int lastLoggedSourceCount = -1;
    private static final AtomicInteger lightApplicationProbeLogs = new AtomicInteger();
    private DynamicLightManager() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ProjectRedHaloRenderer.auditHeldItems(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft());
        if (++ticks % UPDATE_INTERVAL_TICKS != 0) {
            return;
        }
        update(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft());
    }

    public static boolean active() {
        return active;
    }

    public static int activeSourceCount() {
        return activeSources.size();
    }

    public static String firstSourceSummary() {
        if (activeSources.isEmpty()) {
            return "none";
        }
        DynamicLightSource first = activeSources.get(0);
        return first.key()
                + "@"
                + (int) Math.floor(first.x())
                + ","
                + (int) Math.floor(first.y())
                + ","
                + (int) Math.floor(first.z())
                + "/"
                + first.light();
    }

    public static int lightAt(BlockPos pos) {
        if (!active || pos == null) {
            return 0;
        }

        int brightest = 0;
        double blockX = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos) + 0.5D;
        double blockY = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos) + 0.5D;
        double blockZ = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos) + 0.5D;
        for (DynamicLightSource source : activeSources) {
            int light = source.lightAt(blockX, blockY, blockZ);
            if (light > brightest) {
                brightest = light;
                if (brightest >= 15) {
                    return 15;
                }
            }
        }
        return brightest;
    }

    public static boolean shouldApplyToBlockRenderLightQuery(BlockPos pos) {
        return active
                && pos != null;
    }

    public static int applyPackedLight(BlockPos pos, int packedLight) {
        if (!active || pos == null) {
            return packedLight;
        }

        int dynamicLight = lightAt(pos);
        int blockLight = packedLight >> 4 & 15;
        if (dynamicLight <= blockLight) {
            return packedLight;
        }
        int adjusted = (packedLight & 0xFFFF0000) | (dynamicLight << 4);
        if (lightApplicationProbeLogs.getAndIncrement() < 16) {
            MainMod.LOGGER.info("[DynamicLights] applied pos={} packed=0x{} dynamic={} result=0x{} context={}",
                    pos,
                    Integer.toHexString(packedLight),
                    dynamicLight,
                    Integer.toHexString(adjusted),
                    BlockRenderContext.hasWorldBlockContext());
        }
        return adjusted;
    }

    public static void refreshAfterConfigChange() {
        update(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft(), true);
    }

    public static int stackLight(ItemStack stack) {
        if (com.l.ausm.impl.util.MinecraftReflectionCompat.itemStackIsEmpty(stack)) {
            return 0;
        }

        DynamicLightConfig config = MainMod.getDynamicLightConfig();
        int configured = config != null ? config.configuredLight(stack) : 0;
        return Math.max(configured, blockItemLight(stack));
    }

    private static int blockItemLight(ItemStack stack) {
        int light = rawBlockItemLight(stack);
        if (light <= 0) {
            return 0;
        }

        DynamicLightConfig config = MainMod.getDynamicLightConfig();
        double multiplier = config != null ? config.lightMultiplier() : 0.5D;
        return clampLight((int) Math.ceil(light * multiplier));
    }

    private static int rawBlockItemLight(ItemStack stack) {
        Item item = com.l.ausm.impl.util.MinecraftReflectionCompat.itemStackItem(stack);
        if (!(item instanceof ItemBlock itemBlock)) {
            return 0;
        }

        Block block = com.l.ausm.impl.util.MinecraftReflectionCompat.call((itemBlock), net.minecraft.block.Block.class, null, new String[] {"func_179223_d", "getBlock"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS);
        if (block == null) {
            return 0;
        }

        try {
            IBlockState state = com.l.ausm.impl.util.MinecraftReflectionCompat.blockStateFromMeta(block, com.l.ausm.impl.util.MinecraftReflectionCompat.itemStackMetadata(stack));
            return clampLight(com.l.ausm.impl.util.MinecraftReflectionCompat.callInt((block), new String[] {"getLightValue", "func_149750_m"},
                new Class<?>[] {net.minecraft.block.state.IBlockState.class}, 0, (state)));
        } catch (RuntimeException ignored) {
            return clampLight(com.l.ausm.impl.util.MinecraftReflectionCompat.callInt((block), new String[] {"getLightValue", "func_149750_m"},
                new Class<?>[] {net.minecraft.block.state.IBlockState.class}, 0, (com.l.ausm.impl.util.MinecraftReflectionCompat.blockDefaultState(block))));
        }
    }

    private static void update(Minecraft minecraft) {
        update(minecraft, false);
    }

    private static void update(Minecraft minecraft, boolean forceRebuild) {
        World world = minecraft != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(minecraft) : null;
        if (world != previousWorld) {
            activeSources = List.of();
            previousSources = Map.of();
            previousWorld = world;
        }

        if (!shouldRun(minecraft)) {
            clear(minecraft, forceRebuild);
            return;
        }

        Map<String, DynamicLightSource> next = collectSources(minecraft);
        logSourceChanges(next);
        activeSources = List.copyOf(next.values());
        active = !activeSources.isEmpty();
        rebuildChangedRegions(world, previousSources, next, forceRebuild);
        previousSources = next;
    }

    private static boolean shouldRun(Minecraft minecraft) {
        if (minecraft == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(minecraft) == null) {
            return false;
        }
        DynamicLightConfig config = MainMod.getDynamicLightConfig();
        if (config == null || !config.available() || !config.enabled()) {
            return false;
        }
        if (MainMod.getShaderPackManager() != null && MainMod.getShaderPackManager().areShadersEnabled()) {
            return false;
        }
        return !PipelineContext.getInstance().isActive();
    }

    private static Map<String, DynamicLightSource> collectSources(Minecraft minecraft) {
        Map<String, DynamicLightSource> sources = new LinkedHashMap<>();
        EntityLivingBase player = com.l.ausm.impl.util.MinecraftReflectionCompat.player(minecraft);
        if (player != null && !com.l.ausm.impl.util.MinecraftReflectionCompat.entityIsDead(player)) {
            addHeldSource(sources, player, "main", com.l.ausm.impl.util.MinecraftReflectionCompat.heldItemMainhand(player));
            addHeldSource(sources, player, "off", com.l.ausm.impl.util.MinecraftReflectionCompat.heldItemOffhand(player));
        }
        for (Entity entity : com.l.ausm.impl.util.MinecraftReflectionCompat.loadedEntityList(com.l.ausm.impl.util.MinecraftReflectionCompat.world(minecraft))) {
            if (entity == null || com.l.ausm.impl.util.MinecraftReflectionCompat.entityIsDead(entity)) {
                continue;
            }
            if (entity instanceof EntityLivingBase living) {
                addHeldSource(sources, living, "main", com.l.ausm.impl.util.MinecraftReflectionCompat.heldItemMainhand(living));
                addHeldSource(sources, living, "off", com.l.ausm.impl.util.MinecraftReflectionCompat.heldItemOffhand(living));
            } else if (entity instanceof EntityItem itemEntity) {
                int light = stackLight(com.l.ausm.impl.util.MinecraftReflectionCompat.call((itemEntity), net.minecraft.item.ItemStack.class, null, new String[] {"func_92059_d", "getItem"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS));
                if (light > 0) {
                    addSource(sources,
                            com.l.ausm.impl.util.MinecraftReflectionCompat.entityId(entity) + ":item",
                            com.l.ausm.impl.util.MinecraftReflectionCompat.posX(entity),
                            com.l.ausm.impl.util.MinecraftReflectionCompat.posY(entity) + 0.25D,
                            com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(entity),
                            light);
                }
            }
        }
        return sources;
    }

    private static void logSourceChanges(Map<String, DynamicLightSource> sources) {
        int sourceCount = sources.size();
        if (sourceCount == lastLoggedSourceCount) {
            return;
        }

        lastLoggedSourceCount = sourceCount;
        if (sourceCount == 0) {
            MainMod.LOGGER.info("[DynamicLights] activeSources=0");
            return;
        }

        DynamicLightSource first = sources.values().iterator().next();
        MainMod.LOGGER.info("[DynamicLights] activeSources={} first={} light={} pos={},{},{}",
                sourceCount,
                first.key(),
                first.light(),
                (int) Math.floor(first.x()),
                (int) Math.floor(first.y()),
                (int) Math.floor(first.z()));
    }

    private static void addHeldSource(Map<String, DynamicLightSource> sources, EntityLivingBase entity, String slot, ItemStack stack) {
        int light = stackLight(stack);
        if (light <= 0) {
            return;
        }
        addSource(sources,
                com.l.ausm.impl.util.MinecraftReflectionCompat.entityId(entity) + ":" + slot,
                com.l.ausm.impl.util.MinecraftReflectionCompat.posX(entity),
                com.l.ausm.impl.util.MinecraftReflectionCompat.posY(entity) + com.l.ausm.impl.util.MinecraftReflectionCompat.eyeHeight(entity),
                com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(entity),
                light);
    }

    private static void addSource(Map<String, DynamicLightSource> sources, String key, double x, double y, double z, int light) {
        sources.put(key, new DynamicLightSource(key, x, y, z, clampLight(light)));
    }

    private static void clear(Minecraft minecraft, boolean forceRebuild) {
        Map<String, DynamicLightSource> previous = previousSources;
        if (active || !previousSources.isEmpty() || forceRebuild) {
            World world = minecraft != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(minecraft) : previousWorld;
            active = false;
            activeSources = List.of();
            previousSources = Map.of();
            markSources(world, previous);
        } else {
            active = false;
            activeSources = List.of();
            previousSources = Map.of();
        }
        if (lastLoggedSourceCount != 0) {
            lastLoggedSourceCount = 0;
            MainMod.LOGGER.info("[DynamicLights] activeSources=0");
        }
    }

    private static void rebuildChangedRegions(World world, Map<String, DynamicLightSource> previous, Map<String, DynamicLightSource> next, boolean force) {
        if (world == null) {
            return;
        }
        for (Map.Entry<String, DynamicLightSource> entry : previous.entrySet()) {
            DynamicLightSource nextSource = next.get(entry.getKey());
            if (force || nextSource == null || !entry.getValue().sameRenderRegion(nextSource)) {
                markSource(world, entry.getValue());
            }
        }
        for (Map.Entry<String, DynamicLightSource> entry : next.entrySet()) {
            DynamicLightSource previousSource = previous.get(entry.getKey());
            if (force || previousSource == null || !entry.getValue().sameRenderRegion(previousSource)) {
                markSource(world, entry.getValue());
            }
        }
    }

    private static void markSources(World world, Map<String, DynamicLightSource> sources) {
        if (world == null) {
            return;
        }
        for (DynamicLightSource source : sources.values()) {
            markSource(world, source);
        }
    }

    private static void markSource(World world, DynamicLightSource source) {
        int radius = source.light() + REBUILD_PADDING;
        BlockPos center = source.blockPos();
        com.l.ausm.impl.util.MinecraftReflectionCompat.worldMarkBlockRangeForRenderUpdate(world,
                com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(center) - radius,
                Math.max(0, com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(center) - radius),
                com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(center) - radius,
                com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(center) + radius,
                Math.min(255, com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(center) + radius),
                com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(center) + radius
        );
    }

    private static int clampLight(int light) {
        return Math.max(0, Math.min(15, light));
    }

}
