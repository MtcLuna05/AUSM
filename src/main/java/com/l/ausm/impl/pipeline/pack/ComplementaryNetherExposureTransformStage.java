package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.shader.RenderPass;
import com.l.ausm.impl.MainMod;

public final class ComplementaryNetherExposureTransformStage implements ShaderTransformStage {
    private static final String EXPOSURE_LINE = "color = TM_EXPOSURE * color;";
    private static final String NETHER_BLOOM_STRENGTH_LINE = "bloomStrength = mix(bloomStrength * 0.7, bloomStrength * 1.8, netherBloom);";
    private static final String BLOOM_MIX_LINE = "color = mix(color, blur, bloomStrength);";
    private static final String NETHER_BLOOM_FOG_LINE = "float bloomFogMult = netherBloomAdd;";
    private static final String NETHER_EXPOSURE_LINE = """
            #ifdef NETHER
                color = (TM_EXPOSURE * 0.33333334) * color; // AUSM: avoid overexposed Nether tonemapping
            #else
                color = TM_EXPOSURE * color;
            #endif""";
    private static final String SCALED_BLOOM_MIX_LINE = """
            #ifdef NETHER
                color = mix(color, blur, bloomStrength * 0.33333334); // AUSM: keep Nether from blurring every surface
            #else
                color = mix(color, blur, bloomStrength);
            #endif""";

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (parameters.pass() != RenderPass.COMPOSITE5 || !parameters.fragmentShader()) {
            return source;
        }
        if (!source.contains("#define NETHER")) {
            return source;
        }

        String transformed = source;
        transformed = transformed.replace(EXPOSURE_LINE, NETHER_EXPOSURE_LINE);
        transformed = transformed.replace(NETHER_BLOOM_STRENGTH_LINE,
                "bloomStrength = mix(bloomStrength * 0.23333334, bloomStrength * 0.6, netherBloom); // AUSM: damp Nether bloom fog");
        transformed = transformed.replace(BLOOM_MIX_LINE, SCALED_BLOOM_MIX_LINE);
        transformed = transformed.replace(NETHER_BLOOM_FOG_LINE,
                "float bloomFogMult = netherBloomAdd * 0.33333334; // AUSM: damp Nether bloom fog");

        MainMod.LOGGER.debug("[ShaderTransform] Reduced Complementary Nether exposure in composite5");
        return transformed;
    }
}
