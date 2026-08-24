package com.luna.ausm.api.shader;

/**
 * Loader-neutral view of the currently selected shader pack.
 *
 * <p>This class intentionally stays Java 8 compatible so it can move into a
 * standalone API jar later without constraining the Cleanroom implementation.</p>
 */
public final class ShaderPackInfo {
    private final String name;
    private final boolean enabled;
    private final boolean available;

    public ShaderPackInfo(String name, boolean enabled, boolean available) {
        this.name = name;
        this.enabled = enabled;
        this.available = available;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAvailable() {
        return available;
    }
}
