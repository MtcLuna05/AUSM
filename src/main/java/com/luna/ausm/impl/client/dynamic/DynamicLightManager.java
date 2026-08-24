package com.luna.ausm.impl.client.dynamic;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.Reference;
import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

@Mod.EventBusSubscriber(value = Side.CLIENT, modid = Reference.MODID)
public final class DynamicLightManager {
    private static final int UPDATE_INTERVAL_TICKS = 2;
    private static final int REBUILD_PADDING = 2;
    private static volatile boolean active;
    private static volatile List<DynamicLightSource> activeSources = List.of();
    private static volatile Map<Long, List<DynamicLightSource>> activeSourcesBySection = Map.of();
    private static Map<String, DynamicLightSource> previousSources = Map.of();
    private static World previousWorld;
    private static int ticks;
    private static int lastLoggedSourceCount = -1;

    private DynamicLightManager() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++ticks % UPDATE_INTERVAL_TICKS != 0) {
            return;
        }
        update(MinecraftReflectionCompat.minecraft());
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

        return DynamicLightSpatialIndex.lightAt(
                activeSourcesBySection,
                MinecraftReflectionCompat.blockPosX(pos),
                MinecraftReflectionCompat.blockPosY(pos),
                MinecraftReflectionCompat.blockPosZ(pos));
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
        return (packedLight & 0xFFFF0000) | (dynamicLight << 4);
    }

    public static void refreshAfterConfigChange() {
        update(MinecraftReflectionCompat.minecraft(), true);
    }

    public static int stackLight(ItemStack stack) {
        if (MinecraftReflectionCompat.itemStackIsEmpty(stack)) {
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
        Item item = MinecraftReflectionCompat.itemStackItem(stack);
        if (!(item instanceof ItemBlock itemBlock)) {
            return 0;
        }

        Block block = MinecraftReflectionCompat.call(itemBlock, Block.class, null, new String[]{"func_179223_d", "getBlock"}, MinecraftReflectionCompat.NO_PARAMETERS);
        if (block == null) {
            return 0;
        }

        try {
            IBlockState state = MinecraftReflectionCompat.blockStateFromMeta(block, MinecraftReflectionCompat.itemStackMetadata(stack));
            return clampLight(MinecraftReflectionCompat.callInt(block, new String[]{"getLightValue", "func_149750_m"},
                    new Class<?>[]{IBlockState.class}, 0, state));
        } catch (RuntimeException ignored) {
            return clampLight(MinecraftReflectionCompat.callInt(block, new String[]{"getLightValue", "func_149750_m"},
                    new Class<?>[]{IBlockState.class}, 0, MinecraftReflectionCompat.blockDefaultState(block)));
        }
    }

    private static void update(Minecraft minecraft) {
        update(minecraft, false);
    }

    private static void update(Minecraft minecraft, boolean forceRebuild) {
        World world = minecraft != null ? MinecraftReflectionCompat.world(minecraft) : null;
        if (world != previousWorld) {
            activeSources = List.of();
            activeSourcesBySection = Map.of();
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
        activeSourcesBySection = DynamicLightSpatialIndex.build(next.values());
        active = !activeSources.isEmpty();
        rebuildChangedRegions(world, previousSources, next, forceRebuild);
        previousSources = next;
    }

    private static boolean shouldRun(Minecraft minecraft) {
        if (minecraft == null || MinecraftReflectionCompat.world(minecraft) == null) {
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
        EntityLivingBase player = MinecraftReflectionCompat.player(minecraft);
        if (player != null && !MinecraftReflectionCompat.entityIsDead(player)) {
            addHeldSource(sources, player, "main", MinecraftReflectionCompat.heldItemMainhand(player));
            addHeldSource(sources, player, "off", MinecraftReflectionCompat.heldItemOffhand(player));
        }
        for (Entity entity : MinecraftReflectionCompat.loadedEntityList(MinecraftReflectionCompat.world(minecraft))) {
            if (entity == null || entity == player || MinecraftReflectionCompat.entityIsDead(entity)) {
                continue;
            }
            if (entity instanceof EntityLivingBase living) {
                addHeldSource(sources, living, "main", MinecraftReflectionCompat.heldItemMainhand(living));
                addHeldSource(sources, living, "off", MinecraftReflectionCompat.heldItemOffhand(living));
            } else if (entity instanceof EntityItem itemEntity) {
                int light = stackLight(MinecraftReflectionCompat.call(itemEntity, ItemStack.class, null, new String[]{"func_92059_d", "getItem"}, MinecraftReflectionCompat.NO_PARAMETERS));
                if (light > 0) {
                    addSource(sources,
                            MinecraftReflectionCompat.entityId(entity) + ":item",
                            MinecraftReflectionCompat.posX(entity),
                            MinecraftReflectionCompat.posY(entity) + 0.25D,
                            MinecraftReflectionCompat.posZ(entity),
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
                MinecraftReflectionCompat.entityId(entity) + ":" + slot,
                MinecraftReflectionCompat.posX(entity),
                MinecraftReflectionCompat.posY(entity) + MinecraftReflectionCompat.eyeHeight(entity),
                MinecraftReflectionCompat.posZ(entity),
                light);
    }

    private static void addSource(Map<String, DynamicLightSource> sources, String key, double x, double y, double z, int light) {
        sources.put(key, new DynamicLightSource(key, x, y, z, clampLight(light)));
    }

    private static void clear(Minecraft minecraft, boolean forceRebuild) {
        Map<String, DynamicLightSource> previous = previousSources;
        if (active || !previousSources.isEmpty() || forceRebuild) {
            World world = minecraft != null ? MinecraftReflectionCompat.world(minecraft) : previousWorld;
            active = false;
            activeSources = List.of();
            activeSourcesBySection = Map.of();
            previousSources = Map.of();
            markSources(world, previous);
        } else {
            active = false;
            activeSources = List.of();
            activeSourcesBySection = Map.of();
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
        Set<BlockPos> dirtySections = new LinkedHashSet<>();
        for (Map.Entry<String, DynamicLightSource> entry : previous.entrySet()) {
            DynamicLightSource nextSource = next.get(entry.getKey());
            if (force || nextSource == null || !entry.getValue().sameRenderRegion(nextSource)) {
                addSourceSections(dirtySections, entry.getValue());
            }
        }
        for (Map.Entry<String, DynamicLightSource> entry : next.entrySet()) {
            DynamicLightSource previousSource = previous.get(entry.getKey());
            if (force || previousSource == null || !entry.getValue().sameRenderRegion(previousSource)) {
                addSourceSections(dirtySections, entry.getValue());
            }
        }
        markSections(world, dirtySections);
    }

    private static void markSources(World world, Map<String, DynamicLightSource> sources) {
        if (world == null) {
            return;
        }
        Set<BlockPos> dirtySections = new LinkedHashSet<>();
        for (DynamicLightSource source : sources.values()) {
            addSourceSections(dirtySections, source);
        }
        markSections(world, dirtySections);
    }

    private static void addSourceSections(Set<BlockPos> sections, DynamicLightSource source) {
        int radius = source.light() + REBUILD_PADDING;
        BlockPos center = source.blockPos();
        int minSectionX = (MinecraftReflectionCompat.blockPosX(center) - radius) >> 4;
        int maxSectionX = (MinecraftReflectionCompat.blockPosX(center) + radius) >> 4;
        int minSectionY = Math.max(0, MinecraftReflectionCompat.blockPosY(center) - radius) >> 4;
        int maxSectionY = Math.min(255, MinecraftReflectionCompat.blockPosY(center) + radius) >> 4;
        int minSectionZ = (MinecraftReflectionCompat.blockPosZ(center) - radius) >> 4;
        int maxSectionZ = (MinecraftReflectionCompat.blockPosZ(center) + radius) >> 4;
        for (int sectionX = minSectionX; sectionX <= maxSectionX; sectionX++) {
            for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                for (int sectionZ = minSectionZ; sectionZ <= maxSectionZ; sectionZ++) {
                    sections.add(new BlockPos(sectionX, sectionY, sectionZ));
                }
            }
        }
    }

    private static void markSections(World world, Set<BlockPos> sections) {
        for (BlockPos section : sections) {
            int minX = MinecraftReflectionCompat.blockPosX(section) << 4;
            int minY = MinecraftReflectionCompat.blockPosY(section) << 4;
            int minZ = MinecraftReflectionCompat.blockPosZ(section) << 4;
            MinecraftReflectionCompat.worldMarkBlockRangeForRenderUpdate(
                    world, minX, minY, minZ, minX + 15, Math.min(255, minY + 15), minZ + 15);
        }
    }

    private static int clampLight(int light) {
        return Math.clamp(light, 0, 15);
    }

}
