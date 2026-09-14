package com.luna.ausm.impl.pipeline.compat;

import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.World;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.Minecraft;

/** World-based coverage requirements independent of VBO upload readiness. */
public final class NothiriumShadowCoverage {
    private NothiriumShadowCoverage() {}

    public static int requiredDraws(NothiriumShadowRenderer renderer, int maximum) {
        // Count world sections, not uploaded VBOs: a temporary upload gap must
        // not lower the completeness requirement for the replacement map.
        NothiriumShadowRenderer.Reflection reflection = NothiriumShadowRenderer.reflection();
        NothiriumShadowRenderer.ShadowSelection selection = renderer.shadowSelection;
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        World world = mc == null ? null : MinecraftReflectionCompat.world(mc);
        if (reflection == null || selection == null || world == null) {
            return maximum;
        }
        Object provider = MinecraftReflectionCompat.invoke(world,
                new String[]{"func_72863_F", "getChunkProvider"}, new Class<?>[0]);
        if (provider == null) {
            return maximum;
        }
        int sections = 0;
        try {
            for (Object selected : selection.chunks) {
                if (selected == null) continue;
                NothiriumShadowRenderer.ChunkOrigin origin = renderer.chunkOrigin(reflection, selected);
                Object loaded = MinecraftReflectionCompat.invoke(provider,
                        new String[]{"func_186026_b", "getLoadedChunk"},
                        new Class<?>[]{int.class, int.class}, origin.x >> 4, origin.z >> 4);
                // Unloaded chunks have no world terrain to draw; never load them here.
                if (loaded == null) continue;
                if (!(loaded instanceof Chunk)) return maximum;
                ExtendedBlockStorage[] storage =
                        MinecraftReflectionCompat.chunkBlockStorageArray((Chunk) loaded);
                int index = origin.y >> 4;
                if (storage == null || index < 0 || index >= storage.length) return maximum;
                if (storage[index] == null) continue;
                // Alfheim extends isEmpty() to include light-only sections.
                int blocks = MinecraftReflectionCompat.fieldInt(storage[index], -1,
                        "field_76682_b", "blockRefCount");
                if (blocks < 0) return maximum;
                if (blocks > 0 && ++sections >= maximum) {
                    return maximum;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            renderer.warnOnce(exception);
            return maximum;
        }
        return Math.max(1, sections);
    }

}
