package com.l.ausm.impl.client.gui;

import com.l.ausm.impl.MainMod;
import net.minecraft.client.gui.GuiScreen;

public class GuiShaderOptions extends GuiShaderOptionsTooltips {
    public GuiShaderOptions(GuiScreen parent) {
        this(parent, MainMod.getShaderPackManager().getCurrentPack().getName());
    }

    public GuiShaderOptions(GuiScreen parent, String packName) {
        this.parent = parent;
        this.packName = packName;
        this.savedValues.putAll(MainMod.getShaderPackManager().getOptionOverrides(packName));
        this.pendingValues.putAll(savedValues);
        this.properties = MainMod.getShaderPackManager().getShaderProperties(packName, pendingValues);
        syncProfileWithCurrentValuesIfNeeded(this.properties);
    }
}
