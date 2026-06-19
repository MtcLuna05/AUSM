package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.mixin.pipeline.RenderManagerAccessor;
import com.l.ausm.impl.pipeline.PipelineContext;
import meldexun.renderlib.renderer.entity.EntityRenderList;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderEntity;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.Minecraft;
import meldexun.renderlib.api.IEntityRendererCache;
import meldexun.renderlib.api.ILoadable;
import meldexun.renderlib.renderer.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraftforge.fml.client.registry.IRenderFactory;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

@Mixin(targets = "meldexun.renderlib.renderer.entity.EntityRenderer", remap = false)
public abstract class RenderLibBetweenlandsEntityRendererMixin {
    private static final int AUSM_MAX_SETUP_LOGS = 160;
    private static final int AUSM_MAX_MANUAL_RENDER_LOGS = 80;
    private static final int AUSM_MAX_RENDER_LOOKUP_LOGS = 80;
    private static int ausm$setupLogCount;
    private static int ausm$manualRenderLogCount;
    private static int ausm$renderLookupLogCount;
    private static int ausm$seenBetweenlands;
    private static int ausm$forcedBetweenlands;
    private static int ausm$queuedBetweenlands;
    private static int ausm$rejectedBetweenlands;
    private static String ausm$firstBetweenlands;
    private static String ausm$firstRejectedBetweenlands;
    private static String ausm$sampleBetweenlands;
    private static Field ausm$renderLibRendererField;
    private static Field ausm$renderLibRendererInitializedField;
    private static Field ausm$forgeRenderingRegistryInstanceField;
    private static Field ausm$forgeEntityRenderersField;
    private static boolean ausm$rendererCacheReflectionFailed;
    private static boolean ausm$forgeRendererFactoryReflectionFailed;

    @Shadow
    protected int renderedEntities;

    @Shadow
    protected int occludedEntities;

    @Shadow
    protected int totalEntities;

    @Shadow
    private boolean shouldRender(Entity entity, ICamera camera, double partialTicks, double cameraX, double cameraY, double cameraZ) {
        throw new AssertionError();
    }

    @Shadow
    protected abstract <T extends Entity> boolean isOcclusionCulled(T entity);

    @Inject(method = "setup", at = @At("HEAD"))
    private void ausm$beginBetweenlandsProbe(ICamera camera, float partialTicks, double cameraX, double cameraY, double cameraZ, CallbackInfo ci) {
        ausm$seenBetweenlands = 0;
        ausm$forcedBetweenlands = 0;
        ausm$queuedBetweenlands = 0;
        ausm$rejectedBetweenlands = 0;
        ausm$firstBetweenlands = null;
        ausm$firstRejectedBetweenlands = null;
        ausm$sampleBetweenlands = null;
    }

    @Inject(method = "setup", at = @At("RETURN"))
    private void ausm$endBetweenlandsProbe(ICamera camera, float partialTicks, double cameraX, double cameraY, double cameraZ, CallbackInfo ci) {
        if ((ausm$seenBetweenlands > 0 || ausm$queuedBetweenlands > 0) && ausm$setupLogCount++ < AUSM_MAX_SETUP_LOGS) {
            MainMod.LOGGER.info(
                    "[AUSMBetweenlandsEntity] renderlib-setup total={} rendered={} occluded={} blSeen={} blForced={} blQueued={} blRejected={} first={} firstRejected={} sample={}",
                    totalEntities,
                    renderedEntities,
                    occludedEntities,
                    ausm$seenBetweenlands,
                    ausm$forcedBetweenlands,
                    ausm$queuedBetweenlands,
                    ausm$rejectedBetweenlands,
                    ausm$firstBetweenlands,
                    ausm$firstRejectedBetweenlands,
                    ausm$sampleBetweenlands
            );
        }
    }

    @Redirect(
            method = "setup",
            at = @At(
                    value = "INVOKE",
                    target = "Lmeldexun/renderlib/renderer/entity/EntityRenderer;shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;DDDD)Z"
            )
    )
    private boolean ausm$forceBetweenlandsIntoRenderList(EntityRenderer renderer, Entity entity, ICamera camera,
                                                         double partialTicks, double cameraX, double cameraY, double cameraZ) {
        boolean shouldRender = shouldRender(entity, camera, partialTicks, cameraX, cameraY, cameraZ);
        if (!ausm$isBetweenlandsEntity(entity)) {
            return shouldRender;
        }
        ausm$seenBetweenlands++;
        if (ausm$firstBetweenlands == null) {
            ausm$firstBetweenlands = ausm$describe(entity);
        }
        ausm$sampleBetweenlands = ausm$appendSample(ausm$sampleBetweenlands, ausm$describe(entity), 5);
        ausm$ensureBetweenlandsRendererCache(entity);
        if (shouldRender || ausm$canForceBetweenlandsIntoRenderList(entity)) {
            if (!shouldRender) {
                ausm$forcedBetweenlands++;
            }
            return true;
        }
        ausm$rejectedBetweenlands++;
        if (ausm$firstRejectedBetweenlands == null) {
            ausm$firstRejectedBetweenlands = ausm$describe(entity) + " reason=" + ausm$forceRejectReason(entity);
        }
        return false;
    }

    @Redirect(
            method = "setup",
            at = @At(
                    value = "INVOKE",
                    target = "Lmeldexun/renderlib/renderer/entity/EntityRenderer;isOcclusionCulled(Lnet/minecraft/entity/Entity;)Z"
            )
    )
    private boolean ausm$disableBetweenlandsOcclusionCull(EntityRenderer renderer, Entity entity) {
        if (ausm$isBetweenlandsEntity(entity)) {
            return false;
        }
        return isOcclusionCulled(entity);
    }

    @Inject(method = "renderEntities(FLmeldexun/renderlib/renderer/entity/EntityRenderList;)V", at = @At("RETURN"))
    private void ausm$renderBetweenlandsFallback(float partialTicks, EntityRenderList renderList, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.getRenderManager() == null) {
            return;
        }
        List<Entity> queuedEntities = renderList.getEntities();
        int pass = net.minecraftforge.client.MinecraftForgeClient.getRenderPass();
        int loadedBetweenlands = 0;
        int skippedQueued = 0;
        int skippedPass = 0;
        int candidates = 0;
        int rendererNull = 0;
        int rendered = 0;
        String firstCandidate = null;
        String firstRendered = null;
        String firstRendererNull = null;
        String firstSkippedPass = null;
        String sampleCandidates = null;
        for (Entity entity : mc.world.loadedEntityList) {
            if (!ausm$isBetweenlandsEntity(entity)) {
                continue;
            }
            loadedBetweenlands++;
            if (queuedEntities.contains(entity)) {
                skippedQueued++;
                continue;
            }
            if (!entity.shouldRenderInPass(pass)) {
                skippedPass++;
                if (firstSkippedPass == null) {
                    firstSkippedPass = ausm$describe(entity);
                }
                continue;
            }
            candidates++;
            if (firstCandidate == null) {
                firstCandidate = ausm$describe(entity);
            }
            sampleCandidates = ausm$appendSample(sampleCandidates, ausm$describe(entity), 5);
            Render<Entity> renderer = ausm$registryRenderer(mc.getRenderManager(), entity);
            if (renderer == null) {
                rendererNull++;
                if (firstRendererNull == null) {
                    firstRendererNull = ausm$describe(entity);
                }
                continue;
            }
            PipelineContext context = PipelineContext.getInstance();
            boolean vanillaProgram = context.shouldRenderEntityWithVanillaProgram(entity);
            if (!context.shouldBypassWorldPassRendering()) {
                context.setCurrentEntity(entity);
                if (vanillaProgram) {
                    context.prepareExternalWorldOverlayRender();
                }
            }
            try {
                RenderManagerAccessor renderManagerAccessor = (RenderManagerAccessor) mc.getRenderManager();
                double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks - renderManagerAccessor.ausm$renderPosX();
                double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks - renderManagerAccessor.ausm$renderPosY();
                double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks - renderManagerAccessor.ausm$renderPosZ();
                renderer.doRender(entity, x, y, z, entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * partialTicks, partialTicks);
                rendered++;
                if (firstRendered == null) {
                    firstRendered = ausm$describe(entity) + " renderer=" + renderer.getClass().getName();
                }
            } finally {
                if (!context.shouldBypassWorldPassRendering()) {
                    if (vanillaProgram) {
                        context.restoreActiveWorldPassAfterExternalShader();
                    }
                    context.clearCurrentEntity();
                }
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
        if (loadedBetweenlands > 0 && ausm$manualRenderLogCount++ < AUSM_MAX_MANUAL_RENDER_LOGS) {
            MainMod.LOGGER.info(
                    "[AUSMBetweenlandsEntity] manual-render pass={} loaded={} queued={} candidates={} skippedQueued={} skippedPass={} rendererNull={} rendered={} firstCandidate={} firstSkippedPass={} firstRendererNull={} firstRendered={} sampleCandidates={}",
                    pass,
                    loadedBetweenlands,
                    queuedEntities.size(),
                    candidates,
                    skippedQueued,
                    skippedPass,
                    rendererNull,
                    rendered,
                    firstCandidate,
                    firstSkippedPass,
                    firstRendererNull,
                    firstRendered,
                    sampleCandidates
            );
        }
    }

    @Redirect(
            method = "setup",
            at = @At(
                    value = "INVOKE",
                    target = "Lmeldexun/renderlib/renderer/entity/EntityRenderList;addEntity(Lnet/minecraft/entity/Entity;)V"
            )
    )
    private void ausm$countBetweenlandsQueued(EntityRenderList renderList, Entity entity) {
        renderList.addEntity(entity);
        if (ausm$isBetweenlandsEntity(entity)) {
            ausm$queuedBetweenlands++;
        }
    }

    private static boolean ausm$isBetweenlandsEntity(Entity entity) {
        ResourceLocation key = entity != null ? EntityList.getKey(entity) : null;
        return key != null && "thebetweenlands".equals(key.getNamespace());
    }

    private static boolean ausm$canForceBetweenlandsIntoRenderList(Entity entity) {
        if (!(entity instanceof IEntityRendererCache)) {
            return false;
        }
        ausm$ensureBetweenlandsRendererCache(entity);
        if (!((IEntityRendererCache) entity).hasRenderer()) {
            return false;
        }
        if (entity instanceof ILoadable && !((ILoadable) entity).isChunkLoaded()) {
            return false;
        }
        if (!entity.shouldRenderInPass(0) && !entity.shouldRenderInPass(1)) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        return mc == null
                || mc.gameSettings.thirdPersonView != 0
                || entity != mc.getRenderViewEntity()
                || !(entity instanceof EntityLivingBase)
                || ((EntityLivingBase) entity).isPlayerSleeping();
    }

    private static String ausm$forceRejectReason(Entity entity) {
        if (!(entity instanceof IEntityRendererCache)) {
            return "no-renderer-cache";
        }
        ausm$ensureBetweenlandsRendererCache(entity);
        if (!((IEntityRendererCache) entity).hasRenderer()) {
            return "no-renderer";
        }
        if (entity instanceof ILoadable && !((ILoadable) entity).isChunkLoaded()) {
            return "chunk-unloaded";
        }
        if (!entity.shouldRenderInPass(0) && !entity.shouldRenderInPass(1)) {
            return "no-render-pass";
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null
                && mc.gameSettings.thirdPersonView == 0
                && entity == mc.getRenderViewEntity()
                && entity instanceof EntityLivingBase
                && !((EntityLivingBase) entity).isPlayerSleeping()) {
            return "first-person-view-entity";
        }
        return "unknown";
    }

    private static String ausm$describe(Entity entity) {
        ResourceLocation key = entity != null ? EntityList.getKey(entity) : null;
        String id = key != null ? key.toString() : "unregistered";
        String cls = entity != null ? entity.getClass().getName() : "null";
        if (entity == null) {
            return id + "/" + cls;
        }
        return id + "/" + cls + " @ "
                + Math.round(entity.posX * 10.0D) / 10.0D + ","
                + Math.round(entity.posY * 10.0D) / 10.0D + ","
                + Math.round(entity.posZ * 10.0D) / 10.0D
                + " pass0=" + entity.shouldRenderInPass(0)
                + " pass1=" + entity.shouldRenderInPass(1);
    }

    private static void ausm$ensureBetweenlandsRendererCache(Entity entity) {
        if (entity == null || !(entity instanceof IEntityRendererCache) || !ausm$isBetweenlandsEntity(entity)) {
            return;
        }
        if (((IEntityRendererCache) entity).hasRenderer()) {
            return;
        }
        Render<Entity> vanillaRenderer = ausm$vanillaRenderer(entity);
        if (vanillaRenderer == null) {
            return;
        }
        try {
            Field renderer = ausm$renderLibRendererField;
            Field initialized = ausm$renderLibRendererInitializedField;
            if (renderer == null || initialized == null) {
                renderer = ausm$findField(entity.getClass(), "renderer");
                initialized = ausm$findField(entity.getClass(), "rendererInitialized");
                renderer.setAccessible(true);
                initialized.setAccessible(true);
                ausm$renderLibRendererField = renderer;
                ausm$renderLibRendererInitializedField = initialized;
            }
            renderer.set(entity, vanillaRenderer);
            initialized.setBoolean(entity, true);
        } catch (ReflectiveOperationException | RuntimeException error) {
            if (!ausm$rendererCacheReflectionFailed) {
                ausm$rendererCacheReflectionFailed = true;
                MainMod.LOGGER.warn("[AUSMBetweenlandsEntity] Failed to populate RenderLib entity renderer cache", error);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Render<Entity> ausm$vanillaRenderer(Entity entity) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getRenderManager() == null) {
            return null;
        }
        Render<?> renderer = mc.getRenderManager().getEntityRenderObject(entity);
        if (renderer == null || renderer.getClass() == RenderEntity.class) {
            renderer = ausm$registryRenderer(mc.getRenderManager(), entity);
        }
        if (renderer == null || renderer.getClass() == RenderEntity.class) {
            return null;
        }
        return (Render<Entity>) renderer;
    }

    @SuppressWarnings("unchecked")
    private static Render<Entity> ausm$registryRenderer(RenderManager renderManager, Entity entity) {
        if (renderManager == null || entity == null) {
            return null;
        }
        try {
            Map<Class<? extends Entity>, Render<? extends Entity>> renderers = ((RenderManagerAccessor) renderManager).ausm$entityRenderMap();
            Class<?> type = entity.getClass();
            while (type != null && Entity.class.isAssignableFrom(type)) {
                Render<? extends Entity> renderer = renderers.get(type);
                if (renderer != null && renderer.getClass() != RenderEntity.class) {
                    ausm$logRendererLookup(entity, renderers.size(), type, renderer, null);
                    return (Render<Entity>) renderer;
                }
                type = type.getSuperclass();
            }
            Render<Entity> factoryRenderer = ausm$forgeFactoryRenderer(renderManager, entity, renderers);
            if (factoryRenderer != null) {
                return factoryRenderer;
            }
            ausm$logRendererLookup(entity, renderers.size(), null, null, null);
        } catch (RuntimeException error) {
            ausm$logRendererLookup(entity, -1, null, null, error);
            // Fall back to RenderManager's public lookup path.
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Render<Entity> ausm$forgeFactoryRenderer(RenderManager renderManager,
                                                            Entity entity,
                                                            Map<Class<? extends Entity>, Render<? extends Entity>> renderers) {
        try {
            Field instanceField = ausm$forgeRenderingRegistryInstanceField;
            Field entityRenderersField = ausm$forgeEntityRenderersField;
            if (instanceField == null || entityRenderersField == null) {
                instanceField = RenderingRegistry.class.getDeclaredField("INSTANCE");
                entityRenderersField = RenderingRegistry.class.getDeclaredField("entityRenderers");
                instanceField.setAccessible(true);
                entityRenderersField.setAccessible(true);
                ausm$forgeRenderingRegistryInstanceField = instanceField;
                ausm$forgeEntityRenderersField = entityRenderersField;
            }
            Object registry = instanceField.get(null);
            Map<Class<? extends Entity>, IRenderFactory<? extends Entity>> factories =
                    (Map<Class<? extends Entity>, IRenderFactory<? extends Entity>>) entityRenderersField.get(registry);
            Class<?> type = entity.getClass();
            while (type != null && Entity.class.isAssignableFrom(type)) {
                IRenderFactory factory = factories.get(type);
                if (factory != null) {
                    Render<?> renderer = factory.createRenderFor(renderManager);
                    if (renderer != null && renderer.getClass() != RenderEntity.class) {
                        renderers.put((Class<? extends Entity>) type, (Render<? extends Entity>) renderer);
                        ausm$logRendererLookup(entity, renderers.size(), type, renderer, null);
                        return (Render<Entity>) renderer;
                    }
                }
                type = type.getSuperclass();
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            if (!ausm$forgeRendererFactoryReflectionFailed) {
                ausm$forgeRendererFactoryReflectionFailed = true;
                MainMod.LOGGER.warn("[AUSMBetweenlandsEntity] Failed to create renderer from Forge RenderingRegistry factory", error);
            }
            ausm$logRendererLookup(entity, -1, null, null, error instanceof RuntimeException ? (RuntimeException) error : new RuntimeException(error));
        }
        return null;
    }

    private static String ausm$appendSample(String sample, String value, int maxEntries) {
        if (value == null || value.isEmpty()) {
            return sample;
        }
        if (sample == null || sample.isEmpty()) {
            return value;
        }
        int entries = 1;
        for (int i = 0; i < sample.length(); i++) {
            if (sample.charAt(i) == '|') {
                entries++;
            }
        }
        if (entries >= maxEntries) {
            return sample;
        }
        return sample + " | " + value;
    }

    private static void ausm$logRendererLookup(Entity entity, int mapSize, Class<?> matchedType, Render<?> renderer, RuntimeException error) {
        if (!ausm$isBetweenlandsEntity(entity) || ausm$renderLookupLogCount++ >= AUSM_MAX_RENDER_LOOKUP_LOGS) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMBetweenlandsEntity] renderer-lookup entity={} mapSize={} matchedType={} renderer={} error={}",
                ausm$describe(entity),
                mapSize,
                matchedType != null ? matchedType.getName() : null,
                renderer != null ? renderer.getClass().getName() : null,
                error != null ? error.toString() : null
        );
    }

    private static Field ausm$findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> cursor = type;
        while (cursor != null) {
            try {
                return cursor.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
