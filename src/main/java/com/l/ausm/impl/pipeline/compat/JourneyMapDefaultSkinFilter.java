package com.l.ausm.impl.pipeline.compat;

public final class JourneyMapDefaultSkinFilter {
    private JourneyMapDefaultSkinFilter() {
    }

    public static boolean isDefaultPlayerSkin(String location) {
        return "minecraft:textures/entity/alex.png".equals(location)
                || "minecraft:textures/entity/steve.png".equals(location);
    }
}
