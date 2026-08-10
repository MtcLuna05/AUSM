package com.l.ausm.impl.pipeline;

import com.l.ausm.api.pipeline.pack.ShaderCustomTextureBinding;
import com.l.ausm.api.pipeline.pack.ShaderRawTextureDirective;
import com.l.ausm.api.pipeline.pack.ShaderTextureDirectives;
import com.l.ausm.api.pipeline.shader.ProgramArrayId;
import com.l.ausm.api.pipeline.shader.RenderPass;
import com.l.ausm.api.pipeline.shader.ShaderProgramArrayKey;
import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.pack.ShaderPack;
import com.l.ausm.impl.pipeline.render.ShaderTextureLoader;
import com.l.ausm.impl.pipeline.render.TextureBinder;
import com.l.ausm.impl.pipeline.shader.FullscreenArrayProgram;
import com.l.ausm.impl.pipeline.shader.ShaderBindingLayout;
import com.l.ausm.impl.pipeline.shader.ShaderProgram;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

final class PipelineCustomTextures {
    private final Map<RenderPass, List<LoadedCustomTexture>> customTextures = new EnumMap<>(RenderPass.class);
    private final Map<ShaderProgramArrayKey, List<LoadedCustomTexture>> customArrayTextures = new HashMap<>();

    void load(ShaderPack pack, ShaderTextureDirectives directives, Map<ProgramArrayId, List<FullscreenArrayProgram>> fullscreenArrayPrograms) {
        Map<String, Integer> loadedByPath = new HashMap<>();
        Map<ShaderRawTextureDirective, ShaderTextureLoader.RawTexture> loadedRawTextures = new HashMap<>();
        Map<String, Integer> customUnitsBySampler = new HashMap<>();
        Set<String> failedTexturePaths = new HashSet<>();
        int[] nextCustomUnit = {ShaderBindingLayout.CUSTOM_TEXTURE_BASE_UNIT};
        Minecraft mc = MinecraftReflectionCompat.minecraft();

        for (RenderPass pass : RenderPass.values()) {
            List<LoadedCustomTexture> textures = loadTextureList(
                    pack,
                    mc,
                    directives.rawTexturesFor(pass.programId()),
                    directives.texturesFor(pass.programId()),
                    loadedByPath,
                    loadedRawTextures,
                    customUnitsBySampler,
                    failedTexturePaths,
                    nextCustomUnit,
                    "pass " + pass.getProgramName()
            );
            if (!textures.isEmpty()) {
                customTextures.put(pass, List.copyOf(textures));
            }
        }

        LinkedHashSet<ShaderProgramArrayKey> arrayTextureKeys = new LinkedHashSet<>();
        arrayTextureKeys.addAll(directives.programArrayRawTextures().keySet());
        arrayTextureKeys.addAll(directives.programArrayTextures().keySet());
        for (Map.Entry<ProgramArrayId, List<FullscreenArrayProgram>> entry : fullscreenArrayPrograms.entrySet()) {
            for (FullscreenArrayProgram program : entry.getValue()) {
                arrayTextureKeys.add(new ShaderProgramArrayKey(entry.getKey(), program.index()));
            }
        }
        for (ShaderProgramArrayKey key : arrayTextureKeys) {
            List<LoadedCustomTexture> textures = loadTextureList(
                    pack,
                    mc,
                    directives.rawTexturesFor(key.arrayId(), key.index()),
                    directives.texturesFor(key.arrayId(), key.index()),
                    loadedByPath,
                    loadedRawTextures,
                    customUnitsBySampler,
                    failedTexturePaths,
                    nextCustomUnit,
                    "program array " + key.arrayId().sourcePrefix() + (key.index() == 0 ? "" : key.index())
            );
            if (!textures.isEmpty()) {
                customArrayTextures.put(key, List.copyOf(textures));
            }
        }
    }

    private List<LoadedCustomTexture> loadTextureList(
            ShaderPack pack,
            Minecraft mc,
            List<ShaderRawTextureDirective> rawDirectives,
            List<ShaderCustomTextureBinding> bindings,
            Map<String, Integer> loadedByPath,
            Map<ShaderRawTextureDirective, ShaderTextureLoader.RawTexture> loadedRawTextures,
            Map<String, Integer> customUnitsBySampler,
            Set<String> failedTexturePaths,
            int[] nextCustomUnit,
            String owner
    ) {
        List<LoadedCustomTexture> textures = new ArrayList<>();
        for (ShaderRawTextureDirective directive : rawDirectives) {
            int textureUnit = customUnitsBySampler.computeIfAbsent(directive.samplerName(), ignored -> nextCustomUnit[0]++);
            try {
                ShaderTextureLoader.RawTexture rawTexture = loadedRawTextures.computeIfAbsent(directive, raw -> {
                    try {
                        return ShaderTextureLoader.loadRawTexture(pack, raw);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
                textures.add(new LoadedCustomTexture(
                        directive.samplerName(),
                        directive.replacementSamplerName(),
                        directive.resourcePath(),
                        textureUnit,
                        rawTexture.textureId(),
                        rawTexture.textureTarget(),
                        true
                ));
            } catch (UncheckedIOException e) {
                MainMod.LOGGER.warn("[ShaderTextures] Failed to load raw {}", directive.resourcePath(), e.getCause());
            }
        }
        for (ShaderCustomTextureBinding binding : bindings) {
            int textureUnit = TextureBinder.textureUnitForSampler(binding.samplerName());
            if (textureUnit < 0) {
                textureUnit = customUnitsBySampler.computeIfAbsent(binding.samplerName(), ignored -> nextCustomUnit[0]++);
            }

            int atlasTexture = minecraftBlockAtlasTexture(mc, binding.resourcePath());
            if (atlasTexture > 0) {
                textures.add(new LoadedCustomTexture(binding.samplerName(), binding.samplerName(), binding.resourcePath(), textureUnit, atlasTexture, GL11.GL_TEXTURE_2D, false));
                MainMod.LOGGER.debug(
                        "[ShaderTextures] Prepared Minecraft block atlas for sampler '{}' on unit {} in {} as texture {}",
                        binding.samplerName(),
                        textureUnit,
                        owner,
                        atlasTexture
                );
                continue;
            }

            try {
                String textureCacheKey = binding.resourcePath() + "|" + binding.blur() + "|" + binding.clamp();
                int textureId = loadedByPath.computeIfAbsent(textureCacheKey, ignored -> {
                    try {
                        return ShaderTextureLoader.loadTexture(pack, binding.resourcePath(), binding.blur(), binding.clamp());
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
                textures.add(new LoadedCustomTexture(binding.samplerName(), binding.samplerName(), binding.resourcePath(), textureUnit, textureId, GL11.GL_TEXTURE_2D, true));
                MainMod.LOGGER.debug(
                        "[ShaderTextures] Prepared {} for sampler '{}' on unit {} in {} as texture {}",
                        binding.resourcePath(),
                        binding.samplerName(),
                        textureUnit,
                        owner,
                        textureId
                );
            } catch (UncheckedIOException e) {
                if (failedTexturePaths.add(binding.resourcePath())) {
                    MainMod.LOGGER.warn("[ShaderTextures] Failed to load {}", binding.resourcePath(), e.getCause());
                }
            }
        }
        return textures;
    }

    private int minecraftBlockAtlasTexture(Minecraft mc, String resourcePath) {
        TextureManager textureManager = MinecraftReflectionCompat.textureManager(mc);
        if (textureManager == null || !isMinecraftBlockAtlasPath(resourcePath)) {
            return -1;
        }

        ITextureObject texture = MinecraftReflectionCompat.texture(textureManager, MinecraftReflectionCompat.blocksTexture());
        if (texture == null) {
            MinecraftReflectionCompat.bindTexture(textureManager, MinecraftReflectionCompat.blocksTexture());
            texture = MinecraftReflectionCompat.texture(textureManager, MinecraftReflectionCompat.blocksTexture());
        }
        return texture != null ? MinecraftReflectionCompat.glTextureId(texture) : -1;
    }

    private static boolean isMinecraftBlockAtlasPath(String resourcePath) {
        return "minecraft:textures/atlas/blocks.png".equals(resourcePath)
                || "shaders/minecraft:textures/atlas/blocks.png".equals(resourcePath);
    }

    void bind(RenderPass pass, ShaderProgram program) {
        bind(customTextures.get(pass), program, pass == RenderPass.GBUFFERS_SKYBASIC);
    }

    void bind(ProgramArrayId arrayId, int index, ShaderProgram program) {
        bind(customArrayTextures.get(new ShaderProgramArrayKey(arrayId, index)), program, false);
    }

    private void bind(List<LoadedCustomTexture> textures, ShaderProgram program, boolean ownedSkyPass) {
        if (textures == null || textures.isEmpty()) {
            return;
        }

        for (LoadedCustomTexture texture : textures) {
            TextureBinder.bindTexture(texture.textureTarget(), texture.textureUnit(), texture.textureId());
            int location = program.getUniformLocation(texture.samplerName());
            if (location != -1) {
                MinecraftReflectionCompat.glUniform1i(location, texture.textureUnit());
            }
            if (!texture.replacementSamplerName().equals(texture.samplerName())) {
                int replacementLocation = program.getUniformLocation(texture.replacementSamplerName());
                if (replacementLocation != -1) {
                    MinecraftReflectionCompat.glUniform1i(replacementLocation, texture.textureUnit());
                }
            }
        }
        TextureBinder.restoreDefaultTextureUnit();
    }

    void delete() {
        Stream.concat(customTextures.values().stream(), customArrayTextures.values().stream())
                .flatMap(List::stream)
                .filter(LoadedCustomTexture::deleteOnCleanup)
                .mapToInt(LoadedCustomTexture::textureId)
                .distinct()
                .forEach(GL11::glDeleteTextures);
        customTextures.clear();
        customArrayTextures.clear();
    }

    private record LoadedCustomTexture(
            String samplerName,
            String replacementSamplerName,
            String resourcePath,
            int textureUnit,
            int textureId,
            int textureTarget,
            boolean deleteOnCleanup
    ) {
    }
}
