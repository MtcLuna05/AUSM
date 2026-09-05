package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.pack.ShaderBlendMode;
import org.lwjgl.opengl.GL11;

final class PipelineRenderConstants {
    static final int SHADERLESS_MATERIAL_EMISSION = 15;
    static final int SHADERLESS_LIGHT_EMITTING_TEXTURE_EMISSION = 5;
    static final ShaderBlendMode OIT_COEFFICIENT_BLEND = new ShaderBlendMode(true, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
    static final ShaderBlendMode WATER_BLEND_MODE = new ShaderBlendMode(true, GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
    static final ShaderBlendMode BLOCK_ENTITY_TRANSLUCENT_BLEND = new ShaderBlendMode(true, GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
    static final float PORTAL_NETHER_FOG_DENSITY = 0.08f;
    static final float[] PORTAL_NETHER_FOG_COLOR = {0.20f, 0.03f, 0.03f};
    static final float NETHER_SHADER_FOG_COLOR_SCALE = 0.25f;
    static final float SHADER_OVERWORLD_FOG_START_RATIO = 0.85f;
    static final float TEMPORAL_HISTORY_CAMERA_DELTA_RESET = 0.85f;
    static final float TEMPORAL_HISTORY_VERTICAL_CAMERA_DELTA_RESET = 4.0f;
    static final float TEMPORAL_HISTORY_ACCUMULATED_YAW_RESET = 35.0f;
    static final float TEMPORAL_HISTORY_ACCUMULATED_PITCH_RESET = 25.0f;

    private PipelineRenderConstants() {
    }
}
