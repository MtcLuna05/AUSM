package com.l.ausm.impl.client.dynamic;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.Reference;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.compat.ProjectRedHaloRenderer;
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

    private DynamicLightManager() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ProjectRedHaloRenderer.auditHeldItems(Minecraft.getMinecraft());
        if (++ticks % UPDATE_INTERVAL_TICKS != 0) {
            return;
        }
        update(Minecraft.getMinecraft());
    }

    public static boolean active() {
        return active;
    }

    public static int lightAt(BlockPos pos) {
        if (!active || pos == null) {
            return 0;
        }

        int brightest = 0;
        double blockX = pos.getX() + 0.5D;
        double blockY = pos.getY() + 0.5D;
        double blockZ = pos.getZ() + 0.5D;
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
        update(Minecraft.getMinecraft(), true);
    }

    public static int stackLight(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
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
        Item item = stack.getItem();
        if (!(item instanceof ItemBlock itemBlock)) {
            return 0;
        }

        Block block = itemBlock.getBlock();
        if (block == null) {
            return 0;
        }

        try {
            IBlockState state = block.getStateFromMeta(stack.getMetadata());
            return clampLight(block.getLightValue(state));
        } catch (RuntimeException ignored) {
            return clampLight(block.getLightValue(block.getDefaultState()));
        }
    }

    private static void update(Minecraft minecraft) {
        update(minecraft, false);
    }

    private static void update(Minecraft minecraft, boolean forceRebuild) {
        World world = minecraft != null ? minecraft.world : null;
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
        rebuildChangedRegions(world, previousSources, next, forceRebuild);
        previousSources = next;
        activeSources = List.copyOf(next.values());
        active = !activeSources.isEmpty();
    }

    private static boolean shouldRun(Minecraft minecraft) {
        if (minecraft == null || minecraft.world == null) {
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
        if (minecraft.player != null && !minecraft.player.isDead) {
            addHeldSource(sources, minecraft.player, "main", minecraft.player.getHeldItemMainhand());
            addHeldSource(sources, minecraft.player, "off", minecraft.player.getHeldItemOffhand());
        }
        for (Entity entity : minecraft.world.loadedEntityList) {
            if (entity == null || entity.isDead) {
                continue;
            }
            if (entity instanceof EntityLivingBase living) {
                addHeldSource(sources, living, "main", living.getHeldItemMainhand());
                addHeldSource(sources, living, "off", living.getHeldItemOffhand());
            } else if (entity instanceof EntityItem itemEntity) {
                int light = stackLight(itemEntity.getItem());
                if (light > 0) {
                    addSource(sources, entity.getEntityId() + ":item", entity.posX, entity.posY + 0.25D, entity.posZ, light);
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
        addSource(sources, entity.getEntityId() + ":" + slot, entity.posX, entity.posY + entity.getEyeHeight(), entity.posZ, light);
    }

    private static void addSource(Map<String, DynamicLightSource> sources, String key, double x, double y, double z, int light) {
        sources.put(key, new DynamicLightSource(key, x, y, z, clampLight(light)));
    }

    private static void clear(Minecraft minecraft, boolean forceRebuild) {
        if (active || !previousSources.isEmpty() || forceRebuild) {
            World world = minecraft != null ? minecraft.world : previousWorld;
            markSources(world, previousSources);
        }
        active = false;
        activeSources = List.of();
        previousSources = Map.of();
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
        world.markBlockRangeForRenderUpdate(
                center.getX() - radius,
                Math.max(0, center.getY() - radius),
                center.getZ() - radius,
                center.getX() + radius,
                Math.min(255, center.getY() + radius),
                center.getZ() + radius
        );
    }

    private static int clampLight(int light) {
        return Math.max(0, Math.min(15, light));
    }

}
