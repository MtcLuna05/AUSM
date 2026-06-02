package com.l.ausm.api.shader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loader-neutral option description for GUI/API consumers.
 */
public final class ShaderOptionInfo {
    private final String name;
    private final String defaultValue;
    private final String value;
    private final List<String> choices;
    private final boolean slider;
    private final boolean toggle;

    public ShaderOptionInfo(String name, String defaultValue, String value, List<String> choices, boolean slider, boolean toggle) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.value = value;
        this.choices = Collections.unmodifiableList(new ArrayList<>(choices));
        this.slider = slider;
        this.toggle = toggle;
    }

    public String getName() {
        return name;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public String getValue() {
        return value;
    }

    public List<String> getChoices() {
        return choices;
    }

    public boolean isSlider() {
        return slider;
    }

    public boolean isToggle() {
        return toggle;
    }
}
