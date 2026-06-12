package com.l.ausm.impl.client.gui;

import com.l.ausm.impl.MainMod;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Mouse;

import java.util.List;

public class GuiSlotShaders {

    private final GuiShaders parent;
    private final Minecraft mc;
    private final List<String> shaderPacks;
    private final int panelLeft = 10;
    private final int panelRight;
    private final int top;
    private final int bottom;
    private final int slotHeight;
    private int selectedIndex = -1;
    private int scrollOffset;
    private long lastClickTime;
    private int lastClickIndex = -1;

    public GuiSlotShaders(GuiShaders parent, Minecraft mc, int panelRight, int top, int bottom, int slotHeight) {
        this.parent = parent;
        this.mc = mc;
        this.panelRight = panelRight;
        this.top = top;
        this.bottom = bottom;
        this.slotHeight = slotHeight;
        this.shaderPacks = MainMod.getShaderPackManager().getAvailablePacks();

        String currentPackName = MainMod.getShaderPackManager().getSelectedPackName();
        if (currentPackName == null || currentPackName.equals("(internal)")) {
            currentPackName = "OFF";
        }

        for (int i = 0; i < shaderPacks.size(); i++) {
            if (shaderPacks.get(i).equals(currentPackName)) {
                selectedIndex = i;
                break;
            }
        }
    }

    public void handleMouseInput() {
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scrollOffset -= Integer.compare(wheel, 0);
            clampScroll();
        }
    }

    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        int index = slotIndexAt(mouseX, mouseY);
        if (index < 0) {
            return;
        }

        if (index != selectedIndex) {
            selectedIndex = index;
            parent.onSelectionChanged();
        }

        if (mouseButton == 1) {
            parent.openSelectedPackOptions();
            return;
        }

        long now = Minecraft.getSystemTime();
        if (mouseButton == 0 && index == lastClickIndex && now - lastClickTime < 250L) {
            parent.applySelectedPack();
        }
        lastClickIndex = index;
        lastClickTime = now;
    }

    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawScreen(mouseX, mouseY, partialTicks, false);
    }

    public void drawScreen(int mouseX, int mouseY, float partialTicks, boolean keyboardFocused) {
        int first = scrollOffset;
        int visibleRows = Math.max(0, (bottom - top) / slotHeight);
        int last = Math.min(shaderPacks.size(), first + visibleRows);
        int hoveredIndex = slotIndexAt(mouseX, mouseY);

        for (int index = first; index < last; index++) {
            int y = top + (index - first) * slotHeight;
            boolean selected = index == selectedIndex;
            boolean hovered = hoveredIndex == index;

            if (selected) {
                drawRect(panelLeft + 14, y, panelRight - 14, y + slotHeight - 2, 0x88000000);
                drawRect(panelLeft + 14, y, panelRight - 14, y + 1, 0xFFBFC7D1);
                drawRect(panelLeft + 14, y + slotHeight - 3, panelRight - 14, y + slotHeight - 2, 0xFFBFC7D1);
                drawRect(panelLeft + 14, y, panelLeft + 15, y + slotHeight - 2, 0xFFBFC7D1);
                drawRect(panelRight - 15, y, panelRight - 14, y + slotHeight - 2, 0xFFBFC7D1);
                if (keyboardFocused) {
                    drawRect(panelLeft + 16, y + 2, panelRight - 16, y + 3, 0xFFFFD27D);
                    drawRect(panelLeft + 16, y + slotHeight - 5, panelRight - 16, y + slotHeight - 4, 0xFFFFD27D);
                    drawRect(panelLeft + 16, y + 2, panelLeft + 17, y + slotHeight - 4, 0xFFFFD27D);
                    drawRect(panelRight - 17, y + 2, panelRight - 16, y + slotHeight - 4, 0xFFFFD27D);
                }
            } else if (hovered) {
                drawRect(panelLeft + 14, y, panelRight - 14, y + slotHeight - 2, 0x551D2C3B);
            }

            int color = selected ? 0xFFFFFF : 0xC8CED6;
            mc.fontRenderer.drawString(shaderPacks.get(index), panelLeft + 28, y + 4, color);
        }

        drawScrollbar(visibleRows);
    }

    public String getSelectedPackName() {
        if (selectedIndex >= 0 && selectedIndex < shaderPacks.size()) {
            return shaderPacks.get(selectedIndex);
        }
        return null;
    }

    public String getPackNameAt(int mouseX, int mouseY) {
        int index = slotIndexAt(mouseX, mouseY);
        return index >= 0 ? shaderPacks.get(index) : null;
    }

    public boolean selectPack(String packName) {
        int index = shaderPacks.indexOf(packName);
        if (index < 0) {
            return false;
        }
        return selectIndex(index);
    }

    public boolean moveSelection(int delta) {
        if (shaderPacks.isEmpty()) {
            return false;
        }

        int next = selectedIndex < 0 ? 0 : Math.max(0, Math.min(shaderPacks.size() - 1, selectedIndex + delta));
        return selectIndex(next);
    }

    public int getPackCount() {
        return shaderPacks.size();
    }

    private boolean selectIndex(int index) {
        if (index < 0 || index >= shaderPacks.size()) {
            return false;
        }
        if (index == selectedIndex) {
            ensureVisible(index);
            return true;
        }

        selectedIndex = index;
        ensureVisible(index);
        parent.onSelectionChanged();
        return true;
    }

    private void ensureVisible(int index) {
        int visibleRows = Math.max(1, (bottom - top) / slotHeight);
        if (index < scrollOffset) {
            scrollOffset = index;
        } else if (index >= scrollOffset + visibleRows) {
            scrollOffset = index - visibleRows + 1;
        }
        clampScroll();
    }

    private int slotIndexAt(int mouseX, int mouseY) {
        if (mouseX < panelLeft || mouseX >= panelRight || mouseY < top || mouseY >= bottom) {
            return -1;
        }

        int index = scrollOffset + (mouseY - top) / slotHeight;
        return index >= 0 && index < shaderPacks.size() ? index : -1;
    }

    private void clampScroll() {
        int visibleRows = Math.max(1, (bottom - top) / slotHeight);
        int maxScroll = Math.max(0, shaderPacks.size() - visibleRows);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
    }

    private void drawScrollbar(int visibleRows) {
        if (shaderPacks.size() <= visibleRows || visibleRows <= 0) {
            return;
        }

        int trackX = panelRight - 10;
        int trackTop = top;
        int trackBottom = bottom - 2;
        int trackHeight = trackBottom - trackTop;
        int thumbHeight = Math.max(16, trackHeight * visibleRows / shaderPacks.size());
        int maxScroll = shaderPacks.size() - visibleRows;
        int thumbY = trackTop + (trackHeight - thumbHeight) * scrollOffset / maxScroll;

        drawRect(trackX, trackTop, trackX + 3, trackBottom, 0x6605080C);
        drawRect(trackX, thumbY, trackX + 3, thumbY + thumbHeight, 0xFF5D7894);
    }

    private void drawRect(int left, int top, int right, int bottom, int color) {
        net.minecraft.client.gui.Gui.drawRect(left, top, right, bottom, color);
    }
}
