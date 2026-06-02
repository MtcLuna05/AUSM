package com.l.ausm.impl.pipeline.shader;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

/**
 * Central place for Iris-style reserved sampler/image binding decisions.
 */
public final class ShaderBindingLayout {
    public static final int COLOR_TEXTURE_BASE_UNIT = 0;
    public static final int SHADOW_TEXTURE_BASE_UNIT = 4;
    public static final int HIGH_COLOR_TEXTURE_BASE_UNIT = 7;
    public static final int DEPTH_TEXTURE_BASE_UNIT = 11;
    public static final int NOISE_TEXTURE_UNIT = 14;
    public static final int CUSTOM_TEXTURE_BASE_UNIT = 20;
    public static final int CUSTOM_IMAGE_TEXTURE_BASE_UNIT = 28;

    private ShaderBindingLayout() {
    }
}
