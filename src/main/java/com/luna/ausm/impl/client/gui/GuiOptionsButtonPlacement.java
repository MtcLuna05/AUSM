package com.luna.ausm.impl.client.gui;

import java.util.List;

/**
 * Finds a non-overlapping slot for an extra control on the vanilla Options screen.
 */
public final class GuiOptionsButtonPlacement {
    public static final int BUTTON_WIDTH = 150;
    public static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;

    private GuiOptionsButtonPlacement() {
    }

    /**
     * Prefers the slot directly above Skin Customization, then the matching slot
     * in the other vanilla column. Empty is safer than covering another mod's UI.
     */
    public static Placement findAboveSkin(Placement skinButton, int screenWidth, List<Placement> occupiedButtons) {
        int y = skinButton.y() - BUTTON_HEIGHT - BUTTON_GAP;
        Placement aboveSkin = new Placement(skinButton.x(), y, BUTTON_WIDTH, BUTTON_HEIGHT);
        if (!overlapsAny(aboveSkin, occupiedButtons)) {
            return aboveSkin;
        }

        int otherColumnX = screenWidth - skinButton.x() - BUTTON_WIDTH;
        Placement otherColumn = new Placement(otherColumnX, y, BUTTON_WIDTH, BUTTON_HEIGHT);
        return overlapsAny(otherColumn, occupiedButtons) ? null : otherColumn;
    }

    private static boolean overlapsAny(Placement candidate, List<Placement> occupiedButtons) {
        return occupiedButtons.stream().anyMatch(candidate::overlaps);
    }

    public record Placement(int x, int y, int width, int height) {
        public boolean overlaps(Placement other) {
            return x < other.x + other.width
                    && x + width > other.x
                    && y < other.y + other.height
                    && y + height > other.y;
        }
    }
}
