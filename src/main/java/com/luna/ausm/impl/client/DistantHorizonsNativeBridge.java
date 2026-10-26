package com.luna.ausm.impl.client;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.NativeDistantHorizonsCompositor;
import com.luna.ausm.impl.pipeline.PipelineContext;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.config.EDhApiDepthDirection;
import com.seibel.distanthorizons.api.enums.config.EDhApiDepthRange;
import com.seibel.distanthorizons.api.enums.config.EDhApiRenderingEngine;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderProxy;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeApplyShaderRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import java.util.HashSet;
import java.util.Set;

/** Borrows native textures for the current frame; never overrides DH's shaders or framebuffer. */
public final class DistantHorizonsNativeBridge {
    private static boolean initialized;
    private static IDhApiConfigValue<Boolean> overriddenCloudSetting;
    private static Boolean previousCloudOverride;
    private static IDhApiConfigValue<Float> overriddenRadiusSetting;
    private static Float previousRadiusOverride;
    private static boolean loggedShaderedRenderCallback;
    private static final Set<String> LOGGED_CAPTURE_SKIPS = new HashSet<>();

    private DistantHorizonsNativeBridge() {
    }

    public static synchronized void initialize() {
        if (initialized || DhApi.getApiMajorVersion() != 7 || DhApi.getApiMinorVersion() < 2) {
            return;
        }
        DhApi.events.bind(DhApiBeforeRenderEvent.class, new DhApiBeforeRenderEvent() {
            @Override
            public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
                PipelineContext context = PipelineContext.getInstance();
                updateRenderingSettings(context);
                if (!loggedShaderedRenderCallback && context.isPipelineActive()) {
                    loggedShaderedRenderCallback = true;
                    MainMod.LOGGER.info("[AUSMDistantHorizons] Render callback reached: pass={} capturable={} phase={}",
                            event.value == null ? "null" : event.value.renderPass,
                            context.canCaptureDistantHorizonsFrame(), context.getPhase());
                }
                DistantHorizonsShadedBridge.beforeRender(event);
                if (context.isPipelineActive() && !context.canCaptureDistantHorizonsFrame()) {
                    event.cancelEvent();
                }
            }
        });
        DhApi.events.bind(DhApiBeforeApplyShaderRenderEvent.class, new DhApiBeforeApplyShaderRenderEvent() {
            @Override
            public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
                capture(event);
            }
        });
        initialized = true;
        MainMod.LOGGER.info("[AUSMDistantHorizons] Native texture bridge initialized (API {}.{}).",
                DhApi.getApiMajorVersion(), DhApi.getApiMinorVersion());
    }

    private static void updateRenderingSettings(PipelineContext context) {
        DistantHorizonsFogSettings.update(context.isPipelineActive());
        if (context.isPipelineActive()) {
            if (overriddenRadiusSetting == null && DhApi.Delayed.configs != null) {
                IDhApiConfigValue<Float> setting = DhApi.Delayed.configs.graphics().overdrawPreventionRadius();
                Float previous = setting.getApiValue();
                // Depth masks the overlap. Native distance dithering otherwise
                // leaves holes at the edge of the shaderpack's terrain view.
                if (setting.setValue(0.0F, "AUSM terrain depth mask")) {
                    overriddenRadiusSetting = setting;
                    previousRadiusOverride = previous;
                }
            }
        } else {
            releaseRadiusSetting();
        }
        if (context.isPipelineActive() && !context.shouldRenderClouds()) {
            if (overriddenCloudSetting == null && DhApi.Delayed.configs != null) {
                IDhApiConfigValue<Boolean> setting = DhApi.Delayed.configs.graphics()
                        .genericRendering().cloudRenderingEnabled();
                Boolean previous = setting.getApiValue();
                if (setting.setValue(false, "AUSM shader cloud setting")) {
                    overriddenCloudSetting = setting;
                    previousCloudOverride = previous;
                }
            }
        } else {
            releaseCloudSetting();
        }
    }

    public static void releaseRenderingSettings() {
        DistantHorizonsShadedBridge.release();
        DistantHorizonsFogSettings.release();
        releaseRadiusSetting();
        releaseCloudSetting();
    }

    private static void releaseCloudSetting() {
        if (overriddenCloudSetting != null) {
            if (Boolean.FALSE.equals(overriddenCloudSetting.getApiValue())) {
                overriddenCloudSetting.setValue(previousCloudOverride, "AUSM shader cloud setting");
            }
            overriddenCloudSetting = null;
            previousCloudOverride = null;
        }
    }

    private static void releaseRadiusSetting() {
        if (overriddenRadiusSetting != null) {
            if (Float.valueOf(0.0F).equals(overriddenRadiusSetting.getApiValue())) {
                overriddenRadiusSetting.setValue(previousRadiusOverride, "AUSM terrain depth mask");
            }
            overriddenRadiusSetting = null;
            previousRadiusOverride = null;
        }
    }

    private static void capture(DhApiCancelableEventParam<DhApiRenderParam> event) {
        if (event == null || event.value == null || event.isEventCanceled()
                || !PipelineContext.getInstance().canCaptureDistantHorizonsFrame()) {
            return;
        }
        // Suppress DH's native apply only after AUSM has a replacement for
        // this frame. Canceling before validating the borrowed textures can
        // leave a black LOD frame when DH reallocates its targets.
        if (DistantHorizonsShadedBridge.capture()) {
            event.cancelEvent();
            return;
        }
        IDhApiRenderProxy proxy = DhApi.Delayed.renderProxy;
        if (proxy == null || proxy.getRenderingEngine() != EDhApiRenderingEngine.OPEN_GL
                || !proxy.isNativeRenderer() || proxy.getDepthRange() != EDhApiDepthRange.NEG_ONE_TO_POS_ONE) {
            logCaptureSkip("native OpenGL render proxy unavailable");
            return;
        }
        DhApiResult<Integer> color = proxy.getDhColorTextureGlId();
        DhApiResult<Integer> depth = proxy.getDhDepthTextureGlId();
        if (!color.success || !depth.success || color.payload == null || depth.payload == null
                || color.payload <= 0 || depth.payload <= 0) {
            logCaptureSkip("native color/depth textures unavailable");
            return;
        }
        float[] inverse = inverseColumnMajor(event.value.dhProjectionMatrix);
        if (inverse == null) {
            logCaptureSkip("DH projection matrix is not invertible");
            return;
        }
        if (NativeDistantHorizonsCompositor.capture(color.payload, depth.payload,
                inverse, columnMajor(event.value.mcProjectionMatrix),
                proxy.getDepthDirection() == EDhApiDepthDirection.REVERSE_Z ? 0.0F : 1.0F)) {
            event.cancelEvent();
        } else {
            logCaptureSkip("native texture capture rejected");
        }
    }

    private static void logCaptureSkip(String reason) {
        if (LOGGED_CAPTURE_SKIPS.add(reason)) {
            MainMod.LOGGER.warn("[AUSMDistantHorizons] Keeping DH's native output because AUSM could not replace this frame: {}.", reason);
        }
    }

    static float[] inverseColumnMajor(DhApiMat4f matrix) {
        // API 7.2 canInvert() writes the adjugate into its receiver, and invert()
        // scales by det instead of 1/det. Never call either on shared event data.
        DhApiMat4f inverse = matrix.copy();
        float determinant = inverse.adjudicateAndDet();
        if (!Float.isFinite(determinant) || Math.abs(determinant) < 1.0E-6F) {
            return null;
        }
        inverse.multiply(1.0F / determinant);
        return columnMajor(inverse);
    }

    static float[] columnMajor(DhApiMat4f m) {
        return new float[]{m.m00, m.m10, m.m20, m.m30, m.m01, m.m11, m.m21, m.m31,
                m.m02, m.m12, m.m22, m.m32, m.m03, m.m13, m.m23, m.m33};
    }
}
