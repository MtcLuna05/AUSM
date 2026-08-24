package com.luna.ausm.impl.client.gui;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GuiOptionsButtonPlacementTest {
    private static final GuiOptionsButtonPlacement.Placement SKIN = new GuiOptionsButtonPlacement.Placement(165, 142, 150, 20);

    @Test
    void placesShaderOptionsDirectlyAboveSkinWhenTheSlotIsFree() {
        GuiOptionsButtonPlacement.Placement placement = GuiOptionsButtonPlacement.findAboveSkin(SKIN, 640, List.of(SKIN));

        assertEquals(new GuiOptionsButtonPlacement.Placement(165, 118, 150, 20), placement);
    }

    @Test
    void movesToTheOtherColumnWhenBetterFpsOccupiesThePreferredSlot() {
        GuiOptionsButtonPlacement.Placement betterFps = new GuiOptionsButtonPlacement.Placement(165, 118, 150, 20);

        GuiOptionsButtonPlacement.Placement placement = GuiOptionsButtonPlacement.findAboveSkin(SKIN, 640, List.of(SKIN, betterFps));

        assertEquals(new GuiOptionsButtonPlacement.Placement(325, 118, 150, 20), placement);
    }

    @Test
    void skipsTheShaderButtonRatherThanOverlappingExistingControls() {
        GuiOptionsButtonPlacement.Placement left = new GuiOptionsButtonPlacement.Placement(165, 118, 150, 20);
        GuiOptionsButtonPlacement.Placement right = new GuiOptionsButtonPlacement.Placement(325, 118, 150, 20);

        GuiOptionsButtonPlacement.Placement placement = GuiOptionsButtonPlacement.findAboveSkin(SKIN, 640, List.of(SKIN, left, right));

        assertNull(placement);
    }
}
