package com.luna.ausm.impl.client.gui;

/**
 * Chooses whether the shader menu is a standalone settings page or the
 * in-game quick overlay opened by the keybind.
 */
enum ShaderMenuPresentation {
    IN_GAME_OVERLAY(false),
    OPTIONS_PAGE(true);

    private final boolean fullPageBackground;

    ShaderMenuPresentation(boolean fullPageBackground) {
        this.fullPageBackground = fullPageBackground;
    }

    static ShaderMenuPresentation forParent(boolean hasParent) {
        return hasParent ? OPTIONS_PAGE : IN_GAME_OVERLAY;
    }

    boolean hasFullPageBackground() {
        return fullPageBackground;
    }
}
