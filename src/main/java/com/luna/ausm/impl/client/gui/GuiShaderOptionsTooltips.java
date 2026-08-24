package com.luna.ausm.impl.client.gui;

import com.luna.ausm.api.pipeline.pack.ShaderScreenEntry;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.pack.ShaderProperties;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiButton;

abstract class GuiShaderOptionsTooltips extends GuiShaderOptionsProfiles {
    protected List<String> tooltipFor(GuiButton button) {
        String comment = null;
        if (button instanceof GuiShaderOptions.GuiShaderProfileDropdown) {
            comment = self().translationOrNull("profile.comment");
        } else if (button instanceof GuiShaderOptions.GuiShaderOptionSlider) {
            GuiShaderOptions.GuiShaderOptionSlider slider = (GuiShaderOptions.GuiShaderOptionSlider) button;
            comment = self().optionComment(slider.option.name());
        } else if (button instanceof GuiShaderOptions.GuiShaderOptionDropdown) {
            GuiShaderOptions.GuiShaderOptionDropdown dropdown = (GuiShaderOptions.GuiShaderOptionDropdown) button;
            comment = self().optionComment(dropdown.option.name());
        } else if (MinecraftReflectionCompat.guiButtonId(button) >= OPTION_BASE_ID) {
            int index = MinecraftReflectionCompat.guiButtonId(button) - OPTION_BASE_ID;
            if (index >= 0 && index < visibleEntries.size()) {
                ShaderScreenEntry entry = visibleEntries.get(index);
                if (entry.type() == ShaderScreenEntry.Type.OPTION) {
                    comment = self().optionComment(entry.name());
                } else if (entry.type() == ShaderScreenEntry.Type.SCREEN) {
                    comment = self().translationOrNull("screen." + entry.name() + ".comment");
                } else if (entry.type() == ShaderScreenEntry.Type.PROFILE) {
                    comment = self().translationOrNull("profile.comment");
                }
            }
        } else if (MinecraftReflectionCompat.guiButtonId(button) >= CATEGORY_BASE_ID
                && MinecraftReflectionCompat.guiButtonId(button) < OPTION_BASE_ID) {
            int index = MinecraftReflectionCompat.guiButtonId(button) - CATEGORY_BASE_ID;
            if (index >= 0 && index < sidebarItems.size()) {
                comment = self().translationOrNull("screen." + sidebarItems.get(index).screen() + ".comment");
            }
        }

        if (comment == null || comment.isBlank()) {
            return List.of();
        }
        return self().wrapTooltip(comment);
    }

    protected String optionComment(String optionName) {
        String comment = self().translationOrNull("option." + optionName + ".comment");
        if (comment != null) {
            return comment;
        }
        return self().translationOrNull("options." + optionName + ".comment");
    }

    protected String translationOrNull(String key) {
        return self().properties().translations().get(key);
    }

    protected List<String> wrapTooltip(String text) {
        List<String> lines = new ArrayList<>();
        for (String segment : text.split("\\\\n|\\n")) {
            String trimmed = segment.trim();
            if (!trimmed.isEmpty()) {
                lines.addAll(MinecraftReflectionCompat.fontListFormattedStringToWidth(fontRenderer, trimmed, 260));
            }
        }
        return lines;
    }

    protected ShaderProperties properties() {
        if (properties == null) {
            properties = MainMod.getShaderPackManager().getShaderProperties(packName, pendingValues);
        }
        return properties;
    }

    protected String displayPackName() {
        return packName == null || packName.equals("(internal)") ? "OFF" : packName;
    }

    protected void drawScrollbar(int left, int dropdownWidth, int top, int bottom, int totalRows, int visibleRows, int scrollOffset) {
        if (totalRows <= visibleRows) {
            return;
        }

        int trackLeft = left + dropdownWidth - 5;
        int trackTop = top + 2;
        int trackBottom = bottom - 2;
        int trackHeight = Math.max(1, trackBottom - trackTop);
        int thumbHeight = Math.max(8, trackHeight * visibleRows / totalRows);
        int maxScroll = Math.max(1, totalRows - visibleRows);
        int thumbTop = trackTop + (trackHeight - thumbHeight) * scrollOffset / maxScroll;
        drawRect(trackLeft, trackTop, trackLeft + 3, trackBottom, 0xAA06090D);
        drawRect(trackLeft, thumbTop, trackLeft + 3, thumbTop + thumbHeight, 0xFF6D849B);
    }
}
