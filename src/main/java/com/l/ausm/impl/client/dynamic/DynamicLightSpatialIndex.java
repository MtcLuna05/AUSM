package com.l.ausm.impl.client.dynamic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable section-local lookup for dynamic light sources.
 *
 * <p>Terrain compilation asks for block light many times per block. Walking
 * the complete source list for each query scales particularly badly in bases
 * with many carried or dropped lights. Each source is therefore inserted into
 * every 16-cubed section touched by its maximum radius, leaving the render hot
 * path with a single map lookup and only nearby candidates.</p>
 */
final class DynamicLightSpatialIndex {
    private static final int SECTION_SHIFT = 4;
    private static final long HORIZONTAL_MASK = 0x3FFFFFL;
    private static final long VERTICAL_MASK = 0xFFFL;

    private DynamicLightSpatialIndex() {
    }

    static Map<Long, List<DynamicLightSource>> build(Collection<DynamicLightSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<DynamicLightSource>> mutable = new HashMap<>();
        for (DynamicLightSource source : sources) {
            double radius = source.light() + 0.5D;
            int minSectionX = floorToSection(source.x() - radius);
            int maxSectionX = floorToSection(source.x() + radius);
            int minSectionY = floorToSection(source.y() - radius);
            int maxSectionY = floorToSection(source.y() + radius);
            int minSectionZ = floorToSection(source.z() - radius);
            int maxSectionZ = floorToSection(source.z() + radius);

            for (int sectionX = minSectionX; sectionX <= maxSectionX; sectionX++) {
                for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                    for (int sectionZ = minSectionZ; sectionZ <= maxSectionZ; sectionZ++) {
                        mutable.computeIfAbsent(sectionKey(sectionX, sectionY, sectionZ), ignored -> new ArrayList<>())
                                .add(source);
                    }
                }
            }
        }

        Map<Long, List<DynamicLightSource>> frozen = new HashMap<>(mutable.size());
        for (Map.Entry<Long, List<DynamicLightSource>> entry : mutable.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(frozen);
    }

    static int lightAt(Map<Long, List<DynamicLightSource>> sourcesBySection, int blockX, int blockY, int blockZ) {
        if (sourcesBySection == null || sourcesBySection.isEmpty()) {
            return 0;
        }

        List<DynamicLightSource> candidates = sourcesBySection.get(sectionKey(
                blockX >> SECTION_SHIFT,
                blockY >> SECTION_SHIFT,
                blockZ >> SECTION_SHIFT));
        if (candidates == null || candidates.isEmpty()) {
            return 0;
        }

        double centerX = blockX + 0.5D;
        double centerY = blockY + 0.5D;
        double centerZ = blockZ + 0.5D;
        int brightest = 0;
        for (DynamicLightSource source : candidates) {
            int light = source.lightAt(centerX, centerY, centerZ);
            if (light > brightest) {
                brightest = light;
                if (brightest >= 15) {
                    return 15;
                }
            }
        }
        return brightest;
    }

    static int candidateCount(Map<Long, List<DynamicLightSource>> sourcesBySection,
                              int blockX, int blockY, int blockZ) {
        List<DynamicLightSource> candidates = sourcesBySection.get(sectionKey(
                blockX >> SECTION_SHIFT,
                blockY >> SECTION_SHIFT,
                blockZ >> SECTION_SHIFT));
        return candidates != null ? candidates.size() : 0;
    }

    private static int floorToSection(double coordinate) {
        return (int) Math.floor(coordinate) >> SECTION_SHIFT;
    }

    private static long sectionKey(int sectionX, int sectionY, int sectionZ) {
        return ((long) sectionX & HORIZONTAL_MASK) << 34
                | ((long) sectionZ & HORIZONTAL_MASK) << 12
                | (long) sectionY & VERTICAL_MASK;
    }
}
