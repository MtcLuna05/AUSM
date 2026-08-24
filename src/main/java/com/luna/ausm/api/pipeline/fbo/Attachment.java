package com.luna.ausm.api.pipeline.fbo;

/**
 * Defines the logical attachments used in the deferred rendering pipeline.
 * These map to standard shaderpack naming conventions.
 */
public enum Attachment {
    COLOR(0, "gcolor"),
    DEPTH(1, "gdepth"),
    NORMAL(2, "gnormal"),
    COMPOSITE(3, "composite"),
    AUX1(4, "gaux1"),
    AUX2(5, "gaux2"),
    AUX3(6, "gaux3"),
    AUX4(7, "gaux4"),
    AUX5(8, "colortex8"),
    AUX6(9, "colortex9");

    private final int index;
    private final String legacyName;

    Attachment(int index, String legacyName) {
        this.index = index;
        this.legacyName = legacyName;
    }

    public int getIndex() {
        return index;
    }

    public String getLegacyName() {
        return legacyName;
    }

    public static Attachment fromColorIndex(int index) {
        for (Attachment attachment : values()) {
            if (attachment.getIndex() == index) {
                return attachment;
            }
        }
        return null;
    }

    public static Attachment fromName(String name) {
        if (name.startsWith("colortex")) {
            try {
                return fromColorIndex(Integer.parseInt(name.substring("colortex".length())));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        for (Attachment att : values()) {
            if (att.getLegacyName().equals(name)) {
                return att;
            }
        }
        return null;
    }
}
