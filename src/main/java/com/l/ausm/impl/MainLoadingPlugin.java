package com.l.ausm.impl;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.jetbrains.annotations.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

public class MainLoadingPlugin implements IFMLLoadingPlugin {
    private static final Logger LOGGER = LogManager.getLogger(Reference.MOD_NAME);
    private static final String[] EARLY_OPTIONAL_COMPAT_TARGETS = {
            "mrtjp/projectred/core/RenderHalo$.class",
            "mrtjp/projectred/illumination/LampRenderer$.class",
            "mrtjp/projectred/illumination/LightFactory$$anon$1.class",
            "mrtjp/projectred/illumination/ButtonItemRenderer$.class",
            "codechicken/lib/render/item/CCRenderItem.class"
    };
    private static final Set<String> EXPOSED_COMPAT_JARS = new HashSet<>();

    @Override
    public @Nullable String[] getASMTransformerClass() {
        return new String[]{
                "com.l.ausm.impl.core.ProjectRedScalaModuleTransformer",
                "com.l.ausm.impl.core.NothiriumBypassTransformer"
        };
    }

    @Override
    public @Nullable String getModContainerClass() {
        return null;
    }

    @Override
    public @Nullable String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> map) {
        exposeOptionalCompatJars(map);
    }

    private static void exposeOptionalCompatJars(Map<String, Object> data) {
        if (Launch.classLoader == null) {
            return;
        }

        File modsDirectory = resolveModsDirectory(data);
        if (!modsDirectory.isDirectory()) {
            LOGGER.warn("[MixinCompat] Could not expose optional compat jars: mods directory not found at {}", modsDirectory);
            return;
        }

        File[] files = modsDirectory.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".jar") || lower.endsWith(".zip");
        });
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (!containsOptionalCompatTarget(file)) {
                continue;
            }
            String path = file.getAbsolutePath();
            if (!EXPOSED_COMPAT_JARS.add(path)) {
                continue;
            }
            try {
                URL url = file.toURI().toURL();
                Launch.classLoader.addURL(url);
                LOGGER.info("[MixinCompat] Added optional compat jar to LaunchClassLoader early: {}", file.getName());
            } catch (Exception exception) {
                LOGGER.warn("[MixinCompat] Could not add optional compat jar to LaunchClassLoader: {}", file.getName(), exception);
            }
        }
    }

    private static File resolveModsDirectory(Map<String, Object> data) {
        Object mcLocation = data == null ? null : data.get("mcLocation");
        if (mcLocation instanceof File) {
            return new File((File) mcLocation, "mods");
        }
        return new File(System.getProperty("user.dir", "."), "mods");
    }

    private static boolean containsOptionalCompatTarget(File file) {
        try (JarFile jar = new JarFile(file)) {
            for (String target : EARLY_OPTIONAL_COMPAT_TARGETS) {
                if (jar.getEntry(target) != null) {
                    return true;
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Broken jars should not make optional compat loading fatal.
        }
        return false;
    }

    @Override
    public @Nullable String getAccessTransformerClass() {
        return null;
    }
}
