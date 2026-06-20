package com.l.ausm.impl;

import net.minecraft.launchwrapper.Launch;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

public class MainMixinConfigPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String s) {

    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".NothiriumRenderChunkTaskCompileMixin")) {
            return optionalTargetPresent(mixinClassName, "meldexun/nothirium/mc/renderer/chunk/RenderChunkTaskCompile.class", true);
        }
        if (mixinClassName.endsWith(".NothiriumRenderChunkTaskSortTranslucentMixin")) {
            return optionalTargetPresent(mixinClassName, "meldexun/nothirium/mc/renderer/chunk/RenderChunkTaskSortTranslucent.class", true);
        }
        if (mixinClassName.endsWith(".NothiriumChunkRendererDynamicVboMixin")) {
            return optionalTargetPresent(mixinClassName, "meldexun/nothirium/mc/renderer/chunk/ChunkRendererDynamicVbo.class", true);
        }
        if (mixinClassName.endsWith(".NothiriumChunkRendererGL15Mixin")) {
            return optionalTargetPresent(mixinClassName, "meldexun/nothirium/mc/renderer/chunk/ChunkRendererGL15.class", true);
        }
        if (mixinClassName.endsWith(".NothiriumChunkRendererGL20Mixin")) {
            return optionalTargetPresent(mixinClassName, "meldexun/nothirium/mc/renderer/chunk/ChunkRendererGL20.class", true);
        }
        if (mixinClassName.endsWith(".NothiriumChunkRendererGL42Mixin")) {
            return optionalTargetPresent(mixinClassName, "meldexun/nothirium/mc/renderer/chunk/ChunkRendererGL42.class", true);
        }
        if (mixinClassName.endsWith(".NothiriumChunkRendererGL43Mixin")) {
            return optionalTargetPresent(mixinClassName, "meldexun/nothirium/mc/renderer/chunk/ChunkRendererGL43.class", true);
        }
        if (mixinClassName.endsWith(".NothiriumMinecraftChunkRendererMixin")) {
            return optionalTargetPresent(mixinClassName, "meldexun/nothirium/mc/renderer/chunk/MinecraftChunkRenderer.class", true);
        }
        if (mixinClassName.endsWith(".NothiriumSectionRenderCacheDynamicLightMixin")) {
            return optionalTargetPresent(mixinClassName, "meldexun/nothirium/mc/renderer/chunk/SectionRenderCache.class", true);
        }
        if (mixinClassName.endsWith(".CustomMainMenuGuiCustomMixin")) {
            return optionalTargetPresent(mixinClassName, "lumien/custommainmenu/gui/GuiCustom.class", false);
        }
        if (mixinClassName.endsWith(".RenderLibBetweenlandsEntityRendererMixin")) {
            return optionalTargetPresent(mixinClassName, "meldexun/renderlib/renderer/entity/EntityRenderer.class", true);
        }
        if (mixinClassName.endsWith(".ThaumcraftFixClientEventHandlerMixin")) {
            return optionalTargetPresent(mixinClassName, "thecodex6824/thaumcraftfix/client/ClientEventHandler.class", false);
        }
        if (mixinClassName.endsWith(".ProjectRedRenderHaloMixin")) {
            return optionalTargetPresent(mixinClassName, "mrtjp/projectred/core/RenderHalo$.class", false);
        }
        if (mixinClassName.endsWith(".ProjectRedLampRendererMixin")) {
            return optionalTargetPresent(mixinClassName, "mrtjp/projectred/illumination/LampRenderer$.class", false);
        }
        if (mixinClassName.endsWith(".ProjectRedLightFactoryItemRendererMixin")) {
            return optionalTargetPresent(mixinClassName, "mrtjp/projectred/illumination/LightFactory$$anon$1.class", false);
        }
        if (mixinClassName.endsWith(".ProjectRedButtonItemRendererMixin")) {
            return optionalTargetPresent(mixinClassName, "mrtjp/projectred/illumination/ButtonItemRenderer$.class", false);
        }
        if (mixinClassName.endsWith(".AstralSorcerySkyboxMixin")) {
            return optionalTargetPresent(mixinClassName, "hellfirepvp/astralsorcery/client/sky/RenderAstralSkybox.class", false);
        }
        if (mixinClassName.endsWith(".AstralSorceryConstellationRendererMixin")) {
            return optionalTargetPresent(mixinClassName, "hellfirepvp/astralsorcery/client/util/RenderConstellation.class", false);
        }
        if (mixinClassName.endsWith(".AstralSorceryEffectHandlerMixin")) {
            return optionalTargetPresent(mixinClassName, "hellfirepvp/astralsorcery/client/effect/EffectHandler.class", false);
        }
        if (mixinClassName.endsWith(".CodeChickenRenderItemMixin")) {
            return optionalTargetPresent(mixinClassName, "codechicken/lib/render/item/CCRenderItem.class", false);
        }
        if (mixinClassName.endsWith(".BetterPortalsPortalRendererMixin")) {
            return optionalTargetPresent(mixinClassName, "de/johni0702/minecraft/betterportals/client/render/PortalRenderer.class", false);
        }
        if (mixinClassName.endsWith(".BetterPortalsClientWorldsManagerMixin")) {
            return optionalTargetPresent(mixinClassName, "de/johni0702/minecraft/view/impl/client/ClientWorldsManagerImpl.class", false);
        }
        if (mixinClassName.endsWith(".BetterPortalsServerWorldsManagerMixin")) {
            return optionalTargetPresent(mixinClassName, "de/johni0702/minecraft/view/impl/server/ServerWorldsManagerImpl.class", false);
        }
        if (mixinClassName.endsWith(".AbyssalCraftPortalLayerMixin")) {
            return optionalTargetPresent(mixinClassName, "com/shinoow/abyssalcraft/common/blocks/BlockAbyssPortal.class", false);
        }
        if (mixinClassName.endsWith(".AbyssalCraftShadowEntityRendererMixin")) {
            return optionalTargetPresent(mixinClassName, "com/shinoow/abyssalcraft/client/render/entity/RenderShadowMonster.class", false);
        }
        if (mixinClassName.endsWith(".DimensionalDoorsEntranceRiftRendererMixin")) {
            return optionalTargetPresent(mixinClassName, "org/dimdev/dimdoors/client/TileEntityEntranceRiftRenderer.class", false);
        }
        if (mixinClassName.endsWith(".DimensionalDoorsFloatingRiftRendererMixin")) {
            return optionalTargetPresent(mixinClassName, "org/dimdev/dimdoors/client/TileEntityFloatingRiftRenderer.class", false);
        }
        if (mixinClassName.endsWith(".GpomBetterPortalsClientWorldCleanupMixin")) {
            return optionalTargetPresent(mixinClassName, "com/l/gpom/compat/betterportals/BetterPortalsClientWorldCleanup.class", false);
        }
        if (mixinClassName.endsWith(".BetweenlandsMessageSyncChunkStorageMixin")) {
            return optionalTargetPresent(mixinClassName, "thebetweenlands/common/network/clientbound/MessageSyncChunkStorage.class", true);
        }
        if (mixinClassName.endsWith(".LumenizedDisableBloomMixin")) {
            return optionalTargetPresent(mixinClassName, "gregtech/client/utils/BloomEffectUtil.class", false);
        }
        if (mixinClassName.endsWith(".ScannableScannerRendererMixin")) {
            return optionalTargetPresent(mixinClassName, "li/cil/scannable/client/renderer/ScannerRenderer.class", false);
        }
        if (mixinClassName.endsWith(".ScannableOverlayRendererMixin")) {
            return optionalTargetPresent(mixinClassName, "li/cil/scannable/client/renderer/OverlayRenderer.class", false);
        }
        if (mixinClassName.endsWith(".BlockcrafteryBakedModelEditableMixin")) {
            return optionalTargetPresent(mixinClassName, "epicsquid/blockcraftery/model/BakedModelEditable.class", false);
        }
        if (mixinClassName.endsWith(".RandomThingsLuminousBlockMixin")) {
            return optionalTargetPresent(mixinClassName, "lumien/randomthings/block/BlockBlockLuminousBase.class", true);
        }
        if (mixinClassName.endsWith(".ArchitectureCraftRenderTargetWorldMixin")) {
            return optionalTargetPresent(mixinClassName, "com/elytradev/architecture/client/render/target/RenderTargetWorld.class", true);
        }
        if (mixinClassName.endsWith(".ArchitectureCraftCustomBlockDispatcherMixin")) {
            return optionalTargetPresent(mixinClassName, "com/elytradev/architecture/client/render/CustomBlockDispatcher.class", true);
        }
        if (mixinClassName.endsWith(".ArchitectureCraftRenderingManagerMixin")) {
            return optionalTargetPresent(mixinClassName, "com/elytradev/architecture/client/render/RenderingManager.class", true);
        }
        if (mixinClassName.contains(".hei.")) {
            return optionalTargetPresent(mixinClassName, "mezz/jei/JEIInternalPlugin.class", false);
        }
        return true;
    }

    private static boolean optionalTargetPresent(String mixinClassName, String resourcePath, boolean allowJarFallback) {
        String source = classResourceSource(resourcePath, allowJarFallback);
        boolean present = source != null;
        if (shouldLogOptionalTargetProbe(mixinClassName)) {
            MainMod.LOGGER.info("[AUSMMixinProbe] mixin={} target={} present={} source={} allowJarFallback={} userDir={}",
                    mixinClassName,
                    resourcePath,
                    present,
                    source,
                    allowJarFallback,
                    System.getProperty("user.dir", "."));
        }
        return present;
    }

    private static boolean classResourcePresent(String resourcePath, boolean allowJarFallback) {
        return classResourceSource(resourcePath, allowJarFallback) != null;
    }

    private static String classResourceSource(String resourcePath, boolean allowJarFallback) {
        if (resourcePath == null || resourcePath.isEmpty()) {
            return null;
        }

        if (resourcePresent(MainMixinConfigPlugin.class.getClassLoader(), resourcePath)) {
            return "plugin-classloader";
        }
        if (resourcePresent(Thread.currentThread().getContextClassLoader(), resourcePath)) {
            return "context-classloader";
        }
        if (resourcePresent(Launch.classLoader, resourcePath)) {
            return "launch-classloader";
        }
        if (ClassLoader.getSystemResource(resourcePath) != null) {
            return "system-classloader";
        }
        if (!allowJarFallback) {
            return null;
        }
        File jar = resourceJarInModsDirectory(resourcePath);
        return jar != null ? "mods-jar:" + jar.getName() : null;
    }

    private static boolean shouldLogOptionalTargetProbe(String mixinClassName) {
        return mixinClassName != null
                && (mixinClassName.contains("ArchitectureCraft")
                || mixinClassName.contains("Blockcraftery"));
    }

    private static boolean resourcePresent(ClassLoader loader, String resourcePath) {
        try {
            return loader != null && loader.getResource(resourcePath) != null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean resourcePresentInModsDirectory(String resourcePath) {
        return resourceJarInModsDirectory(resourcePath) != null;
    }

    private static File resourceJarInModsDirectory(String resourcePath) {
        File modsDirectory = new File(System.getProperty("user.dir", "."), "mods");
        if (!modsDirectory.isDirectory()) {
            return null;
        }

        File[] files = modsDirectory.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".jar") || lower.endsWith(".zip");
        });
        if (files == null) {
            return null;
        }

        for (File file : files) {
            try (JarFile jar = new JarFile(file)) {
                if (jar.getEntry(resourcePath) != null) {
                    return file;
                }
            } catch (IOException | RuntimeException ignored) {
                // Broken jars should not make optional compat mixins fatal.
            }
        }
        return null;
    }

    @Override
    public void acceptTargets(Set<String> set, Set<String> set1) {

    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {

    }

    @Override
    public void postApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {

    }
}
