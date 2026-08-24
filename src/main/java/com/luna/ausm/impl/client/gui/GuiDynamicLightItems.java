package com.luna.ausm.impl.client.gui;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.client.dynamic.DynamicLightConfig;
import com.luna.ausm.impl.client.dynamic.DynamicLightManager;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Registry-validated editor for shaderless dynamic-light item overrides. */
public final class GuiDynamicLightItems extends MappingSafeGuiScreen {
    private static final int INVALID_METADATA = Integer.MIN_VALUE;
    private static final int WILDCARD_METADATA = Integer.MIN_VALUE + 1;
    private static final int ID_SAVE = 10;
    private static final int ID_REMOVE = 11;
    private static final int ID_COLOR = 12;
    private static final int ID_DONE = 200;

    private final GuiScreen parent;
    private GuiTextField itemIdField;
    private GuiTextField metadataField;
    private GuiAusmValueSlider lightSlider;
    private List<String> entries = List.of();
    private String selectedKey;
    private int scrollOffset;
    private int editingLight = 15;
    private int editingColor = 0xFFFFFF;
    private String status = "Enter a registry id, optionally set metadata, then save.";

    public GuiDynamicLightItems(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    protected boolean ausm$doesGuiPauseGame() {
        return false;
    }

    @Override
    protected void ausm$initGui() {
        buttonList.clear();
        refreshEntries();
        int rightLeft = editorLeft();
        int editorWidth = Math.max(150, width - rightLeft - 14);
        itemIdField = field(30, rightLeft, 70, editorWidth, 20, itemIdField, 96);
        metadataField = field(31, rightLeft, 112, editorWidth, 20, metadataField, 6);
        lightSlider = new GuiAusmValueSlider(32, rightLeft, 154, editorWidth, "Light level", 0.0D, 15.0D, 16,
                () -> editingLight, value -> editingLight = (int) Math.round(value), value -> Integer.toString((int) Math.round(value)));
        buttonList.add(lightSlider);
        int half = (editorWidth - 4) / 2;
        buttonList.add(new GuiFlatButton(ID_COLOR, rightLeft, 194, half, 20, "Color..."));
        buttonList.add(new GuiFlatButton(ID_SAVE, rightLeft + half + 4, 194, half, 20, "Save Item"));
        buttonList.add(new GuiFlatButton(ID_REMOVE, rightLeft, 220, editorWidth, 20, "Remove Selected"));
        buttonList.add(new GuiFlatButton(ID_DONE, width / 2 - 50, height - 28, 100, 20,
                MinecraftReflectionCompat.i18nFormat("gui.done")));
        syncFieldsFromSelection();
    }

    @Override
    protected void ausm$handleMouseInput() throws IOException {
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int mouseX = Mouse.getEventX() * width / MinecraftReflectionCompat.displayWidth(mc);
            int mouseY = height - Mouse.getEventY() * height / MinecraftReflectionCompat.displayHeight(mc) - 1;
            if (mouseX >= 10 && mouseX < editorLeft() - 8 && mouseY >= 54 && mouseY < height - 40) {
                scrollOffset = Math.clamp(scrollOffset - Integer.compare(wheel, 0), 0, maxScroll());
                return;
            }
        }
        super.ausm$handleMouseInput();
    }

    @Override
    protected void ausm$mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0 && selectRow(mouseX, mouseY)) {
            return;
        }
        MinecraftReflectionCompat.guiTextFieldMouseClicked(itemIdField, mouseX, mouseY, mouseButton);
        MinecraftReflectionCompat.guiTextFieldMouseClicked(metadataField, mouseX, mouseY, mouseButton);
        super.ausm$mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void ausm$keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            MinecraftReflectionCompat.displayGuiScreen(mc, parent);
            return;
        }
        if (MinecraftReflectionCompat.guiTextFieldFocused(itemIdField)
                && MinecraftReflectionCompat.guiTextFieldKeyTyped(itemIdField, typedChar, keyCode)) {
            return;
        }
        if (MinecraftReflectionCompat.guiTextFieldFocused(metadataField)
                && MinecraftReflectionCompat.guiTextFieldKeyTyped(metadataField, typedChar, keyCode)) {
            return;
        }
        super.ausm$keyTyped(typedChar, keyCode);
    }

    @Override
    protected void ausm$actionPerformed(GuiButton button) {
        int id = MinecraftReflectionCompat.guiButtonId(button);
        if (id == ID_DONE) {
            MinecraftReflectionCompat.displayGuiScreen(mc, parent);
            return;
        }
        if (id == ID_COLOR) {
            MinecraftReflectionCompat.displayGuiScreen(mc, new GuiColorPicker(this, editingColor, value -> editingColor = value));
            return;
        }
        if (id == ID_REMOVE) {
            if (selectedKey == null) {
                status = "Select a custom item to remove.";
                return;
            }
            DynamicLightConfig config = MainMod.getDynamicLightConfig();
            if (config != null) {
                config.removeCustomItem(selectedKey);
                DynamicLightManager.refreshAfterConfigChange();
            }
            selectedKey = null;
            status = "Custom item removed.";
            refreshEntries();
            return;
        }
        if (id == ID_SAVE) {
            saveEntry();
        }
    }

    @Override
    protected void ausm$drawScreen(int mouseX, int mouseY, float partialTicks) {
        ausm$drawDefaultBackground();
        int split = editorLeft() - 8;
        drawRect(8, 48, split, height - 40, 0x880E0E0E);
        drawRect(editorLeft() - 4, 48, width - 8, height - 40, 0x880E0E0E);
        drawCenteredString(fontRenderer, "Custom Item Lights", width / 2, 18, 0xFFFFFF);
        drawString(fontRenderer, "Configured items", 16, 54, 0xDCE5F0);
        drawString(fontRenderer, "Registry-backed editor", editorLeft(), 54, 0xDCE5F0);
        drawEntries(mouseX, mouseY);
        drawString(fontRenderer, "Item id", editorLeft(), 60, 0xA7B2BF);
        drawString(fontRenderer, "Metadata (* = all)", editorLeft(), 102, 0xA7B2BF);
        drawString(fontRenderer, "Color", editorLeft(), 182, 0xA7B2BF);
        int swatchX = width - 38;
        drawRect(swatchX, 180, swatchX + 20, 200, 0xFF000000 | editingColor);
        drawRect(swatchX - 1, 179, swatchX + 21, 180, 0xFFFFFFFF);
        drawRect(swatchX - 1, 200, swatchX + 21, 201, 0xFFFFFFFF);
        drawRect(swatchX - 1, 179, swatchX, 201, 0xFFFFFFFF);
        drawRect(swatchX + 20, 179, swatchX + 21, 201, 0xFFFFFFFF);
        drawString(fontRenderer, status, editorLeft(), 252, 0xA7B2BF);
        drawString(fontRenderer, "Color is saved for shader-capable paths; vanilla block light is monochrome.", editorLeft(), 268, 0x7F8A96);
        super.ausm$drawScreen(mouseX, mouseY, partialTicks);
        MinecraftReflectionCompat.drawGuiTextField(itemIdField);
        MinecraftReflectionCompat.drawGuiTextField(metadataField);
        drawPreviewIcon();
        if (lightSlider != null && lightSlider.dragging()) {
            drawHoveringText(List.of(Integer.toString((int) Math.round(lightSlider.value()))), mouseX + 12, mouseY - 12);
        }
    }

    private void saveEntry() {
        DynamicLightConfig config = MainMod.getDynamicLightConfig();
        if (config == null) {
            status = "Dynamic light config is unavailable.";
            return;
        }
        ResourceLocation id;
        try {
            id = new ResourceLocation(MinecraftReflectionCompat.guiTextFieldText(itemIdField).trim());
        } catch (RuntimeException e) {
            status = "Use a registry id such as minecraft:torch.";
            return;
        }
        Item item = Item.REGISTRY.getObject(id);
        if (item == null) {
            status = "That item id is not registered in this instance.";
            return;
        }
        Integer metadata = parseMetadata(MinecraftReflectionCompat.guiTextFieldText(metadataField));
        if (metadata == INVALID_METADATA) {
            status = "Metadata must be * or an integer from 0 to 32767.";
            return;
        }
        String key = DynamicLightConfig.itemKey(id, metadata == WILDCARD_METADATA ? null : metadata);
        config.upsertCustomItem(key, editingLight, editingColor);
        DynamicLightManager.refreshAfterConfigChange();
        selectedKey = key;
        status = "Saved " + key + " at light level " + editingLight + ".";
        refreshEntries();
    }

    private void refreshEntries() {
        DynamicLightConfig config = MainMod.getDynamicLightConfig();
        entries = config == null ? List.of() : config.customItemLights().keySet().stream().sorted(Comparator.naturalOrder()).toList();
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll());
    }

    private void syncFieldsFromSelection() {
        if (selectedKey == null) {
            if (itemIdField != null && MinecraftReflectionCompat.guiTextFieldText(itemIdField).isBlank()) {
                MinecraftReflectionCompat.setGuiTextFieldText(metadataField, "*");
            }
            return;
        }
        int separator = selectedKey.lastIndexOf('@');
        MinecraftReflectionCompat.setGuiTextFieldText(itemIdField, separator < 0 ? selectedKey : selectedKey.substring(0, separator));
        MinecraftReflectionCompat.setGuiTextFieldText(metadataField, separator < 0 ? "*" : selectedKey.substring(separator + 1));
        DynamicLightConfig config = MainMod.getDynamicLightConfig();
        if (config != null) {
            editingLight = config.customItemLights().getOrDefault(selectedKey, 15);
            editingColor = config.customItemColor(selectedKey);
        }
    }

    private boolean selectRow(int mouseX, int mouseY) {
        int row = (mouseY - 76) / 22;
        int index = scrollOffset + row;
        if (mouseX < 10 || mouseX >= editorLeft() - 8 || row < 0 || index < 0 || index >= entries.size()) {
            return false;
        }
        selectedKey = entries.get(index);
        syncFieldsFromSelection();
        status = "Editing " + selectedKey + ".";
        return true;
    }

    private void drawEntries(int mouseX, int mouseY) {
        DynamicLightConfig config = MainMod.getDynamicLightConfig();
        int visible = visibleRows();
        for (int row = 0; row < visible; row++) {
            int index = scrollOffset + row;
            if (index >= entries.size()) {
                break;
            }
            String key = entries.get(index);
            int y = 76 + row * 22;
            boolean selected = key.equals(selectedKey);
            boolean hovered = mouseX >= 10 && mouseX < editorLeft() - 8 && mouseY >= y && mouseY < y + 20;
            if (selected) {
                drawRect(12, y, editorLeft() - 12, y + 20, 0x99000000);
            }
            if (selected || hovered) {
                int color = selected ? 0xFFBFC7D1 : 0xFF71849A;
                drawRect(12, y, editorLeft() - 12, y + 1, color);
                drawRect(12, y + 19, editorLeft() - 12, y + 20, color);
            }
            int light = config == null ? 0 : config.customItemLights().getOrDefault(key, 0);
            drawString(fontRenderer, key, 18, y + 3, selected ? 0xFFFFFF : 0xC8CED6);
            drawString(fontRenderer, Integer.toString(light), editorLeft() - 28, y + 3, 0xFFE7C86E);
        }
    }

    private void drawPreviewIcon() {
        ItemStack stack = previewStack();
        if (stack == null || MinecraftReflectionCompat.itemStackIsEmpty(stack)) {
            return;
        }
        RenderItem renderer = MinecraftReflectionCompat.call(mc, RenderItem.class, null,
                new String[]{"func_175599_af", "getRenderItem"}, MinecraftReflectionCompat.NO_PARAMETERS);
        if (renderer != null) {
            MinecraftReflectionCompat.invoke(renderer, new String[]{"func_180450_b", "renderItemAndEffectIntoGUI"},
                    new Class<?>[]{ItemStack.class, int.class, int.class}, stack, width - 64, 60);
        }
    }

    private ItemStack previewStack() {
        try {
            Item item = Item.REGISTRY.getObject(new ResourceLocation(MinecraftReflectionCompat.guiTextFieldText(itemIdField).trim()));
            if (item == null) {
                return ItemStack.EMPTY;
            }
            Integer metadata = parseMetadata(MinecraftReflectionCompat.guiTextFieldText(metadataField));
            return new ItemStack(item, 1, metadata == null || metadata < 0 ? 0 : metadata);
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    private int editorLeft() {
        return Math.max(180, width / 2 + 8);
    }

    private int visibleRows() {
        return Math.max(1, (height - 122) / 22);
    }

    private int maxScroll() {
        return Math.max(0, entries.size() - visibleRows());
    }

    private GuiTextField field(int id, int x, int y, int fieldWidth, int fieldHeight, GuiTextField previous, int maxLength) {
        String previousText = previous == null ? "" : MinecraftReflectionCompat.guiTextFieldText(previous);
        GuiTextField field = new GuiTextField(id, fontRenderer, x, y, fieldWidth, fieldHeight);
        MinecraftReflectionCompat.setGuiTextFieldMaxLength(field, maxLength);
        MinecraftReflectionCompat.setGuiTextFieldBackground(field, true);
        MinecraftReflectionCompat.setGuiTextFieldText(field, previousText);
        return field;
    }

    private static Integer parseMetadata(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.equals("*")) {
            return WILDCARD_METADATA;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= 0 && parsed <= 32767 ? parsed : INVALID_METADATA;
        } catch (NumberFormatException e) {
            return INVALID_METADATA;
        }
    }
}
