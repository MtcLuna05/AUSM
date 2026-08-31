package com.luna.ausm.impl.client.gui;

import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

/**
 * Bridges Cleanroom's SRG GUI dispatch to AUSM-owned lifecycle hooks.
 */
abstract class MappingSafeGuiScreen extends GuiScreen {
    protected Minecraft mc;
    public int width;
    public int height;
    protected FontRenderer fontRenderer;
    protected List<GuiButton> buttonList = new ArrayList<>();

    protected boolean ausm$doesGuiPauseGame() {
        return false;
    }

    protected void ausm$initGui() {
    }

    protected void ausm$onGuiClosed() {
    }

    protected void ausm$updateScreen() {
    }

    protected void ausm$handleMouseInput() throws IOException {
    }

    protected void ausm$mouseReleased(int mouseX, int mouseY, int mouseButton) {
        for (GuiButton button : buttonList) {
            MinecraftReflectionCompat.invoke(
                    button,
                    new String[]{"func_146118_a", "mouseReleased"},
                    new Class<?>[]{int.class, int.class},
                    mouseX,
                    mouseY);
        }
    }

    protected void ausm$actionPerformed(GuiButton button) throws IOException {
    }

    protected void ausm$drawScreen(int mouseX, int mouseY, float partialTicks) {
        for (GuiButton button : buttonList) {
            // Exactly one draw route: runtime classes use the SRG name, development classes use the fallback.
            MinecraftReflectionCompat.invoke(
                    button,
                    new String[]{"func_191745_a", "drawButton"},
                    new Class<?>[]{Minecraft.class, int.class, int.class, float.class},
                    mc,
                    mouseX,
                    mouseY,
                    partialTicks);
        }
    }

    protected void ausm$mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton != 0) {
            return;
        }
        for (GuiButton button : buttonList) {
            Object pressed = MinecraftReflectionCompat.invoke(
                    button,
                    new String[]{"func_146116_c", "mousePressed"},
                    new Class<?>[]{Minecraft.class, int.class, int.class},
                    mc,
                    mouseX,
                    mouseY);
            if (Boolean.TRUE.equals(pressed)) {
                ausm$actionPerformed(button);
                return;
            }
        }
    }

    protected void ausm$keyTyped(char typedChar, int keyCode) throws IOException {
    }

    protected void ausm$drawDefaultBackground() {
        MinecraftReflectionCompat.invoke(
                this,
                new String[]{"func_146276_q_", "drawDefaultBackground"},
                MinecraftReflectionCompat.NO_PARAMETERS);
    }

    public static void drawRect(int left, int top, int right, int bottom, int color) {
        MinecraftReflectionCompat.guiDrawRect(left, top, right, bottom, color);
    }

    public void drawString(FontRenderer renderer, String text, int x, int y, int color) {
        MinecraftReflectionCompat.fontDrawString(renderer, text, x, y, color);
    }

    public void drawCenteredString(FontRenderer renderer, String text, int x, int y, int color) {
        int textWidth = MinecraftReflectionCompat.fontStringWidth(renderer, text);
        MinecraftReflectionCompat.fontDrawString(renderer, text, x - textWidth / 2, y, color);
    }

    public void drawHoveringText(List<String> lines, int x, int y) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        MinecraftReflectionCompat.guiScreenDrawHoveringText(this, lines, x, y);
    }

    public final boolean func_73868_f() {
        ausm$syncRuntimeScreenState();
        return ausm$doesGuiPauseGame();
    }

    public final void func_73866_w_() {
        ausm$syncRuntimeScreenState();
        ausm$initGui();
    }

    public final void func_146281_b() {
        ausm$syncRuntimeScreenState();
        ausm$onGuiClosed();
    }

    public final void func_73876_c() {
        ausm$syncRuntimeScreenState();
        ausm$updateScreen();
    }

    public final void func_146274_d() throws IOException {
        ausm$syncRuntimeScreenState();
        ausm$handleMouseInput();

        int mouseX = Mouse.getEventX() * this.width / MinecraftReflectionCompat.displayWidth(this.mc);
        int mouseY = this.height - Mouse.getEventY() * this.height
                / MinecraftReflectionCompat.displayHeight(this.mc) - 1;
        int mouseButton = Mouse.getEventButton();
        if (mouseButton != -1) {
            if (Mouse.getEventButtonState()) {
                ausm$mouseClicked(mouseX, mouseY, mouseButton);
            } else {
                ausm$mouseReleased(mouseX, mouseY, mouseButton);
            }
        }
    }

    protected final void func_146284_a(GuiButton button) throws IOException {
        ausm$syncRuntimeScreenState();
        ausm$actionPerformed(button);
    }

    public final void func_73863_a(int mouseX, int mouseY, float partialTicks) {
        ausm$syncRuntimeScreenState();
        ausm$drawScreen(mouseX, mouseY, partialTicks);
    }

    protected final void func_73864_a(int mouseX, int mouseY, int mouseButton) throws IOException {
        ausm$syncRuntimeScreenState();
        ausm$mouseClicked(mouseX, mouseY, mouseButton);
    }

    protected final void func_73869_a(char typedChar, int keyCode) throws IOException {
        ausm$syncRuntimeScreenState();
        ausm$keyTyped(typedChar, keyCode);
    }

    @SuppressWarnings("unchecked")
    private void ausm$syncRuntimeScreenState() {
        Minecraft runtimeMinecraft = MinecraftReflectionCompat.field(
                this, Minecraft.class, null, "field_146297_k");
        if (runtimeMinecraft != null) {
            mc = runtimeMinecraft;
        }
        width = MinecraftReflectionCompat.fieldInt(this, width, "field_146294_l");
        height = MinecraftReflectionCompat.fieldInt(this, height, "field_146295_m");
        FontRenderer runtimeFont = MinecraftReflectionCompat.field(
                this, FontRenderer.class, null, "field_146289_q");
        if (runtimeFont != null) {
            fontRenderer = runtimeFont;
        } else if (mc != null) {
            fontRenderer = MinecraftReflectionCompat.fontRenderer(mc);
        }
        List<GuiButton> runtimeButtons = MinecraftReflectionCompat.field(
                this, List.class, null, "field_146292_n");
        if (runtimeButtons != null) {
            buttonList = runtimeButtons;
        }
    }
}
