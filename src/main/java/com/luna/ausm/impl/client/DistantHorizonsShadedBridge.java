package com.luna.ausm.impl.client;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.pipeline.ShadedDistantHorizonsRendering;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.config.EDhApiRenderingEngine;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRenderPass;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiFramebuffer;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiShaderProgram;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeDeferredRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderCleanupEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeTextureClearEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3f;
import java.nio.FloatBuffer;
import java.lang.reflect.Method;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/** Official API overrides registered only during supported shaderpack world rendering. */
final class DistantHorizonsShadedBridge {
    private static boolean initialized;
    private static boolean bound;
    private static boolean previousDefer;
    private static IDhApiConfigValue<Boolean> ambientOcclusion;
    private static Boolean previousAmbientOcclusion;
    private static final Shader SHADER = new Shader();
    private static final Framebuffer FRAMEBUFFER = new Framebuffer();
    private static Object renderApi;
    private static Method renderDeferred;

    private DistantHorizonsShadedBridge() { }

    static void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
        if (!initialized) initialize();
        boolean ready = !event.isEventCanceled() && DhApi.Delayed.renderProxy != null
                && DhApi.Delayed.renderProxy.isNativeRenderer()
                && DhApi.Delayed.renderProxy.getRenderingEngine() == EDhApiRenderingEngine.OPEN_GL
                && renderDeferred != null && ShadedDistantHorizonsRendering.prepare();
        if (!ready) {
            unbind();
            return;
        }
        if (!bound) {
            // API 7.2 replaces equal-priority overrides. Preserve other owners.
            if (DhApi.overrides.get(IDhApiShaderProgram.class, SHADER.getPriority()) != null
                    || DhApi.overrides.get(IDhApiFramebuffer.class, FRAMEBUFFER.getPriority()) != null) return;
            previousDefer = DhApi.Delayed.renderProxy.getDeferTransparentRendering();
            DhApi.Delayed.renderProxy.setDeferTransparentRendering(true);
            DhApi.overrides.bind(IDhApiShaderProgram.class, SHADER);
            DhApi.overrides.bind(IDhApiFramebuffer.class, FRAMEBUFFER);
            ambientOcclusion = DhApi.Delayed.configs.graphics().ambientOcclusion().enabled();
            previousAmbientOcclusion = ambientOcclusion.getApiValue();
            ambientOcclusion.setValue(false, "AUSM shaderpack ambient occlusion");
            bound = true;
            ShadedDistantHorizonsRendering.shaderpackOverrideActive = true;
            MainMod.LOGGER.info("[AUSMDistantHorizons] Shaderpack terrain/water overrides enabled.");
        }
        ShadedDistantHorizonsRendering.selectPass(false);
        // DH computes its pass before firing this event. Wait one frame after
        // enabling separate transparent rendering, rather than mixing shaders.
        if (event.value.renderPass == EDhApiRenderPass.OPAQUE_AND_TRANSPARENT) event.cancelEvent();
    }

    private static void initialize() {
        try {
            // DH 3.3.3's 1.12 hook invokes this only for an Iris accessor.
            // The public API controls deferral but has no render entrypoint.
            Class<?> clientApi = Class.forName("com.seibel.distanthorizons.core.api.internal.ClientApi");
            renderApi = clientApi.getField("INSTANCE").get(null);
            renderDeferred = clientApi.getMethod("renderDeferredLodsForShaders");
        } catch (ReflectiveOperationException failure) {
            MainMod.LOGGER.warn("[AUSMDistantHorizons] Shaderpack water entrypoint unavailable.", failure);
        }
        DhApi.events.bind(DhApiBeforeTextureClearEvent.class, new DhApiBeforeTextureClearEvent() {
            @Override public void beforeClear(DhApiCancelableEventParam<DhApiRenderParam> event) {
                if (!bound) return;
                event.cancelEvent();
                ShadedDistantHorizonsRendering.clearPass();
            }
        });
        DhApi.events.bind(DhApiBeforeDeferredRenderEvent.class, new DhApiBeforeDeferredRenderEvent() {
            @Override public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
                if (!bound) return;
                if (!PipelineContext.getInstance().canCaptureDistantHorizonsFrame()) {
                    event.cancelEvent();
                    return;
                }
                ShadedDistantHorizonsRendering.selectPass(true);
            }
        });
        DhApi.events.bind(DhApiBeforeRenderCleanupEvent.class, new DhApiBeforeRenderCleanupEvent() {
            @Override public void beforeCleanup(DhApiEventParam<DhApiRenderParam> event) {
                if (bound) ShadedDistantHorizonsRendering.captured();
            }
        });
        initialized = true;
    }

    public static void renderDeferred() {
        if (!bound || !PipelineContext.getInstance().canCaptureDistantHorizonsFrame()) return;
        try {
            renderDeferred.invoke(renderApi);
        } catch (ReflectiveOperationException failure) {
            unbind();
            MainMod.LOGGER.warn("[AUSMDistantHorizons] Shaderpack water rendering failed.", failure);
        } finally {
            PipelineContext.getInstance().restorePassAfterDistantHorizons();
        }
    }

    static boolean capture() {
        if (!bound) return false;
        ShadedDistantHorizonsRendering.captured();
        return true;
    }

    private static void unbind() {
        if (!bound) return;
        DhApi.overrides.unbind(IDhApiShaderProgram.class, SHADER);
        DhApi.overrides.unbind(IDhApiFramebuffer.class, FRAMEBUFFER);
        if (DhApi.Delayed.renderProxy.getDeferTransparentRendering()) {
            DhApi.Delayed.renderProxy.setDeferTransparentRendering(previousDefer);
        }
        if (ambientOcclusion != null && Boolean.FALSE.equals(ambientOcclusion.getApiValue())) {
            ambientOcclusion.setValue(previousAmbientOcclusion, "AUSM shaderpack ambient occlusion");
        }
        ambientOcclusion = null;
        bound = false;
        ShadedDistantHorizonsRendering.shaderpackOverrideActive = false;
    }

    static void release() {
        unbind();
        ShadedDistantHorizonsRendering.release();
    }

    static DhApiMat4f forwardProjection(DhApiRenderParam param) {
        DhApiMat4f projection = param.dhProjectionMatrix.copy();
        float near = param.nearClipPlane;
        float far = param.farClipPlane;
        if (!(near > 0) || !(far > near)) throw new IllegalArgumentException("Invalid DH clipping planes");
        // Packs reconstruct conventional OpenGL -1..1 forward depth. The
        // native renderer's reverse/zero-one coefficients never enter this FBO.
        projection.m22 = -(far + near) / (far - near);
        projection.m23 = -2 * far * near / (far - near);
        return projection;
    }

    private static void matrix(FloatBuffer buffer, DhApiMat4f matrix, boolean inverse) {
        float[] values = inverse ? DistantHorizonsNativeBridge.inverseColumnMajor(matrix)
                : DistantHorizonsNativeBridge.columnMajor(matrix);
        if (values == null) throw new IllegalArgumentException("Singular DH matrix");
        buffer.clear();
        buffer.put(values).flip();
    }

    private static final class Shader implements IDhApiShaderProgram {
        @Override public boolean overrideThisFrame() { return bound; }
        @Override public int getId() { return ShadedDistantHorizonsRendering.programId(); }
        @Override public void free() { release(); }
        @Override public void bind() { ShadedDistantHorizonsRendering.bind(); }
        @Override public void unbind() { GL20.glUseProgram(0); GL30.glBindVertexArray(0); }
        @Override public void fillUniformData(DhApiRenderParam param) {
            DhApiMat4f projection = forwardProjection(param);
            matrix(ShadedDistantHorizonsRendering.PROJECTION, projection, false);
            matrix(ShadedDistantHorizonsRendering.PROJECTION_INVERSE, projection, true);
            matrix(ShadedDistantHorizonsRendering.MODEL_VIEW, param.dhModelViewMatrix, false);
            matrix(ShadedDistantHorizonsRendering.MODEL_VIEW_INVERSE, param.dhModelViewMatrix, true);
            ShadedDistantHorizonsRendering.near = param.nearClipPlane;
            ShadedDistantHorizonsRendering.far = param.farClipPlane;
            ShadedDistantHorizonsRendering.renderDistance = DhApi.Delayed.configs.graphics().chunkRenderDistance().getValue() * 16;
            ShadedDistantHorizonsRendering.bind();
        }
        @Override public void setModelOffsetPos(DhApiVec3f offset) {
            ShadedDistantHorizonsRendering.modelOffset(offset.x, offset.y, offset.z);
        }
        @Override public void bindVertexBuffer(int vbo) { ShadedDistantHorizonsRendering.bindVertexBuffer(vbo); }
    }

    private static final class Framebuffer implements IDhApiFramebuffer {
        @Override public boolean overrideThisFrame() { return bound; }
        @Override public void bind() {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, getId());
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        }
        // Owned textures must survive DH resizing its separate native targets.
        @Override public void addDepthAttachment(int texture, boolean stencil) { }
        @Override public void addColorAttachment(int index, int texture) { }
        @Override public int getId() { return ShadedDistantHorizonsRendering.framebuffer(); }
        @Override public int getStatus() { bind(); return GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER); }
        @Override public void destroy() { release(); }
    }
}
