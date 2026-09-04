package com.luna.ausm.impl.client.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShaderMenuPresentationTest {
    @Test
    void optionsEntryUsesAStandalonePage() {
        ShaderMenuPresentation presentation = ShaderMenuPresentation.forParent(true);

        assertSame(ShaderMenuPresentation.OPTIONS_PAGE, presentation);
        assertTrue(presentation.hasFullPageBackground());
    }

    @Test
    void keybindKeepsTheInGameOverlay() {
        ShaderMenuPresentation presentation = ShaderMenuPresentation.forParent(false);

        assertSame(ShaderMenuPresentation.IN_GAME_OVERLAY, presentation);
        assertFalse(presentation.hasFullPageBackground());
    }
}
