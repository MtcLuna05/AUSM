package com.luna.ausm.api.shader;

import java.util.List;
import java.util.Map;

/**
 * Public shader-pack control surface shared by future platform jars.
 */
public interface ShaderPackController {
    ShaderPackInfo getCurrentShaderPack();

    List<String> getAvailablePacks();

    boolean loadPack(String packName);

    void reloadPack();

    boolean areShadersEnabled();

    void setShadersEnabled(boolean enabled);

    Map<String, String> getOptionOverrides(String packName);

    void setShaderOption(String name, String value);

    void setShaderOptions(String packName, Map<String, String> values);

    void resetShaderOptions(String packName);
}
