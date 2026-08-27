package com.luna.ausm.impl;

import java.util.Map;

final class OptionalMixinTargets {
    private static final Map<String, OptionalMixinTarget> TARGETS = Map.ofEntries(
            target("NothiriumRenderChunkTaskCompileMixin", "meldexun/nothirium/mc/renderer/chunk/RenderChunkTaskCompile.class", true),
            target("NothiriumRenderChunkTaskSortTranslucentMixin", "meldexun/nothirium/mc/renderer/chunk/RenderChunkTaskSortTranslucent.class", true),
            target("NothiriumShadowChunkAccessMixin", "meldexun/nothirium/renderer/chunk/AbstractRenderChunk.class", true),
            target("NothiriumChunkRendererDynamicVboMixin", "meldexun/nothirium/mc/renderer/chunk/ChunkRendererDynamicVbo.class", true),
            target("NothiriumChunkRendererGL15Mixin", "meldexun/nothirium/mc/renderer/chunk/ChunkRendererGL15.class", true),
            target("NothiriumChunkRendererGL20Mixin", "meldexun/nothirium/mc/renderer/chunk/ChunkRendererGL20.class", true),
            target("NothiriumChunkRendererGL42Mixin", "meldexun/nothirium/mc/renderer/chunk/ChunkRendererGL42.class", true),
            target("NothiriumChunkRendererGL43Mixin", "meldexun/nothirium/mc/renderer/chunk/ChunkRendererGL43.class", true),
            target("NothiriumFogUtilMixin", "meldexun/nothirium/mc/util/FogUtil.class", true),
            target("NothiriumMinecraftChunkRendererMixin", "meldexun/nothirium/mc/renderer/chunk/MinecraftChunkRenderer.class", true),
            target("NothiriumSectionRenderCacheDynamicLightMixin", "meldexun/nothirium/mc/renderer/chunk/SectionRenderCache.class", true),
            target("EntityCullingQueryLatencyMixin", "meldexun/entityculling/util/culling/CullingInstance.class", true),
            target("EfficientEntitiesModelRendererCompatMixin", "com/michaelsebero/efficiententities/mixin/MixinModelRenderer.class", true),
            target("JourneyMapTextureImplMixin", "journeymap/client/render/texture/TextureImpl.class", true),
            target("CustomMainMenuGuiCustomMixin", "lumien/custommainmenu/gui/GuiCustom.class", false),
            target("RenderLibBetweenlandsEntityRendererMixin", "meldexun/renderlib/renderer/entity/EntityRenderer.class", true),
            target("RenderLibTileEntityRendererMixin", "meldexun/renderlib/renderer/tileentity/TileEntityRenderer.class", true),
            target("ThaumcraftFixClientEventHandlerMixin", "thecodex6824/thaumcraftfix/client/ClientEventHandler.class", false),
            target("ProjectRedRenderHaloMixin", "mrtjp/projectred/core/RenderHalo$.class", false),
            target("ProjectRedLampRendererMixin", "mrtjp/projectred/illumination/LampRenderer$.class", false),
            target("AstralSorcerySkyboxMixin", "hellfirepvp/astralsorcery/client/sky/RenderAstralSkybox.class", true),
            target("AstralSorceryRenderSkyboxMixin", "hellfirepvp/astralsorcery/client/sky/RenderSkybox.class", true),
            target("AstralSorceryConstellationRendererMixin", "hellfirepvp/astralsorcery/client/util/RenderConstellation.class", false),
            target("AstralSorceryEffectHandlerMixin", "hellfirepvp/astralsorcery/client/effect/EffectHandler.class", false),
            target("ActuallyAdditionsAssetUtilMixin", "de/ellpeck/actuallyadditions/mod/util/AssetUtil.class", false),
            target("AppliedEnergisticsCableBusBakedModelMixin", "appeng/client/render/cablebus/CableBusBakedModel.class", false),
            target("AppliedEnergisticsFacadeBuilderMixin", "appeng/client/render/cablebus/FacadeBuilder.class", false),
            target("BetterPortalsPortalRendererMixin", "de/johni0702/minecraft/betterportals/client/render/PortalRenderer.class", false),
            target("BetterPortalsClientWorldsManagerMixin", "de/johni0702/minecraft/view/impl/client/ClientWorldsManagerImpl.class", false),
            target("BetterPortalsCreateWorldHandlerMixin", "de/johni0702/minecraft/view/impl/net/CreateWorld$Handler.class", true),
            target("BetterPortalsServerWorldsManagerMixin", "de/johni0702/minecraft/view/impl/server/ServerWorldsManagerImpl.class", false),
            target("AbyssalCraftPortalLayerMixin", "com/shinoow/abyssalcraft/common/blocks/BlockAbyssPortal.class", false),
            target("AbyssalCraftShadowEntityRendererMixin", "com/shinoow/abyssalcraft/client/render/entity/RenderShadowMonster.class", false),
            target("AbyssalCraftNecroDataCapMessageMixin", "com/shinoow/abyssalcraft/common/network/client/NecroDataCapMessage.class", true),
            target("DimensionalDoorsEntranceRiftRendererMixin", "org/dimdev/dimdoors/client/TileEntityEntranceRiftRenderer.class", false),
            target("DimensionalDoorsFloatingRiftRendererMixin", "org/dimdev/dimdoors/client/TileEntityFloatingRiftRenderer.class", false),
            target("GpomBetterPortalsClientWorldCleanupMixin", "com/luna/gpom/compat/betterportals/BetterPortalsClientWorldCleanup.class", false),
            // Euphoria is not visible until Foundation finishes early mixin selection.
            target("EuphoriaPatcherEntreeMixin", "com/euphoriapatches/euphoria_patcher/EuphoriaPatcher.class", false),
            target("BetweenlandsMessageSyncChunkStorageMixin", "thebetweenlands/common/network/clientbound/MessageSyncChunkStorage.class", true),
            target("LumenizedDisableBloomMixin", "gregtech/client/utils/BloomEffectUtil.class", false),
            target("ScannableScannerRendererMixin", "li/cil/scannable/client/renderer/ScannerRenderer.class", false),
            target("ScannableOverlayRendererMixin", "li/cil/scannable/client/renderer/OverlayRenderer.class", false),
            target("ReachFixUtilMixin", "meldexun/reachfix/util/ReachFixUtil.class", true),
            target("OpenBlocksSkyBlockRendererMixin", "openblocks/client/renderer/SkyBlockRenderer.class", true),
            target("OpenBlocksSkyCaptureMixin", "openblocks/client/renderer/SkyBlockRenderer$SkyCapture.class", true),
            target("OpenBlocksSkyTextureRendererMixin", "openblocks/client/renderer/tileentity/TileEntitySkyRenderer.class", true),
            target("BewitchmentSyncExtendedPlayerHandlerMixin", "com/bewitchment/api/message/SyncExtendedPlayer$Handler.class", true));

    private OptionalMixinTargets() {
    }

    static OptionalMixinTarget find(String mixinClassName) {
        if (mixinClassName == null || mixinClassName.isEmpty()) {
            return null;
        }
        int packageSeparator = mixinClassName.lastIndexOf('.');
        String simpleName = mixinClassName.substring(packageSeparator + 1);
        return TARGETS.get(simpleName);
    }

    private static Map.Entry<String, OptionalMixinTarget> target(
            String mixinName, String resourcePath, boolean allowJarFallback) {
        return Map.entry(mixinName, new OptionalMixinTarget(resourcePath, allowJarFallback));
    }
}

record OptionalMixinTarget(String resourcePath, boolean allowJarFallback) {
}
