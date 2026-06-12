package com.l.ausm.impl.pipeline.compat;

import com.l.ausm.api.pipeline.fbo.Attachment;
import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.fbo.DeferredFramebuffer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * AUSM-owned renderer for Lumenized's BLOOM block layer.
 *
 * Lumenized injects after vanilla translucent terrain and then runs another
 * terrain/FBO path. In AUSM that duplicate pass leaks render state into portals,
 * so this class consumes the same BLOOM layer and composites it in isolation.
 */
public final class LumenizedBloomTarget {
    private static final String BLOOM_EFFECT_UTIL = "gregtech.client.utils.BloomEffectUtil";
    private static final String EFFECT_RENDER_CONTEXT = "gregtech.client.utils.EffectRenderContext";
    private static final String LUMENIZED_CONFIG = "github.kasuminova.lumenized.common.config.LumenizedConfig";
    private static final String BLOOM_EFFECT = "gregtech.client.shader.postprocessing.BloomEffect";
    private static final String SHADERS = "gregtech.client.shader.Shaders";

    private final IntBuffer viewportBuffer = BufferUtils.createIntBuffer(16);
    private final FloatBuffer clearColorBuffer = BufferUtils.createFloatBuffer(4);

    private Framebuffer bloomTarget;
    private Framebuffer pipelineDestinationView;
    private int width = -1;
    private int height = -1;
    private Reflection reflection;
    private boolean resolveAttempted;
    private boolean loggedStandalonePath;
    private boolean loggedReflectionFailure;
    private boolean loggedCustomTicketFailure;

    public int renderIntoPipeline(RenderGlobal renderGlobal, double partialTicks, int pass, Entity entity,
                                  DeferredFramebuffer destination) {
        if (destination == null) {
            return 0;
        }

        int targetWidth = destination.getAttachmentWidth(Attachment.COLOR);
        int targetHeight = destination.getAttachmentHeight(Attachment.COLOR);
        if (!prepareTarget(targetWidth, targetHeight)) {
            return 0;
        }

        captureColorClear();
        clearTargetColor();
        destination.blitDepthTo(bloomTarget.framebufferObject, targetWidth, targetHeight);

        RenderState state = captureState();
        int rendered;
        try {
            bindBloomTarget();
            rendered = renderBloomGeometry(renderGlobal, partialTicks, pass, entity);
        } finally {
            state.restore();
        }

        if (rendered > 0) {
            Framebuffer destinationView = updatePipelineDestinationView(destination);
            destination.bindAsExternalTarget(Attachment.COLOR, true);
            compositeBloom(destinationView, () -> destination.bindAsExternalTarget(Attachment.COLOR, true));
        }
        return rendered;
    }

    public int renderIntoMinecraft(RenderGlobal renderGlobal, double partialTicks, int pass, Entity entity,
                                   Framebuffer destination) {
        if (destination == null) {
            return 0;
        }

        int targetWidth = destination.framebufferWidth;
        int targetHeight = destination.framebufferHeight;
        if (!prepareTarget(targetWidth, targetHeight)) {
            return 0;
        }

        captureColorClear();
        clearTargetColor();
        blitMinecraftDepth(destination, targetWidth, targetHeight);

        RenderState state = captureState();
        int rendered;
        try {
            bindBloomTarget();
            rendered = renderBloomGeometry(renderGlobal, partialTicks, pass, entity);
        } finally {
            state.restore();
        }

        if (rendered > 0) {
            destination.bindFramebuffer(false);
            compositeBloom(destination, () -> destination.bindFramebuffer(false));
        }
        return rendered;
    }

    public void delete() {
        if (bloomTarget != null) {
            bloomTarget.deleteFramebuffer();
            bloomTarget = null;
        }
        clearDetachedView(pipelineDestinationView);
        pipelineDestinationView = null;
        width = -1;
        height = -1;
    }

    private boolean prepareTarget(int targetWidth, int targetHeight) {
        if (targetWidth <= 0 || targetHeight <= 0) {
            return false;
        }

        if (bloomTarget == null) {
            bloomTarget = new Framebuffer(targetWidth, targetHeight, true);
            bloomTarget.setFramebufferColor(0.0F, 0.0F, 0.0F, 0.0F);
            width = targetWidth;
            height = targetHeight;
        } else if (targetWidth != width || targetHeight != height) {
            bloomTarget.createBindFramebuffer(targetWidth, targetHeight);
            bloomTarget.setFramebufferColor(0.0F, 0.0F, 0.0F, 0.0F);
            width = targetWidth;
            height = targetHeight;
        }

        bloomTarget.setFramebufferFilter(GL11.GL_LINEAR);
        if (!loggedStandalonePath) {
            loggedStandalonePath = true;
            MainMod.LOGGER.info("[LumenizedBloom] Using AUSM standalone BLOOM layer renderer size={}x{}", width, height);
        }
        return true;
    }

    private int renderBloomGeometry(RenderGlobal renderGlobal, double partialTicks, int pass, Entity entity) {
        if (renderGlobal == null || entity == null) {
            return 0;
        }

        BlockRenderLayer bloomLayer = bloomLayer();
        if (bloomLayer == null) {
            return 0;
        }

        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(false);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        int rendered = renderGlobal.renderBlockLayer(bloomLayer, partialTicks, pass, entity);
        rendered += renderCustomBloomTickets(entity, (float) partialTicks);
        return rendered;
    }

    private void bindBloomTarget() {
        bloomTarget.bindFramebuffer(false);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glViewport(0, 0, width, height);
    }

    private void compositeBloom(Framebuffer destination, Runnable bindDestination) {
        if (destination == null) {
            return;
        }

        additiveComposite(destination, bindDestination);
        if (!isPostProcessEnabled()) {
            return;
        }

        Reflection resolved = reflection();
        if (resolved == null || !resolved.postProcessAvailable()) {
            return;
        }

        RenderState state = captureState();
        try {
            resolved.copyConfigToBloomEffect();
            resolved.renderBloomEffect(bloomTarget, destination);
            bindDestination.run();
            resolved.copyCurrentTextureTo(destination);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            logReflectionFailure("Failed to run Lumenized bloom postprocess; using raw BLOOM composite", e);
        } finally {
            state.restore();
        }
    }

    private void additiveComposite(Framebuffer destination, Runnable bindDestination) {
        RenderState state = captureState();
        try {
            bindDestination.run();
            GL11.glDrawBuffer(destination.framebufferObject == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadBuffer(destination.framebufferObject == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);

            OpenGlHelper.glUseProgram(0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GlStateManager.bindTexture(bloomTarget.framebufferTexture);
            GlStateManager.enableTexture2D();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.colorMask(true, true, true, true);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_ONE, GL11.GL_ONE);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

            drawFullscreenQuad();
        } finally {
            state.restore();
        }
    }

    private void blitMinecraftDepth(Framebuffer source, int targetWidth, int targetHeight) {
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.framebufferObject);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, bloomTarget.framebufferObject);
            GL30.glBlitFramebuffer(
                    0,
                    0,
                    source.framebufferWidth,
                    source.framebufferHeight,
                    0,
                    0,
                    targetWidth,
                    targetHeight,
                    GL11.GL_DEPTH_BUFFER_BIT,
                    GL11.GL_NEAREST
            );
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
        }
    }

    private Framebuffer updatePipelineDestinationView(DeferredFramebuffer destination) {
        if (pipelineDestinationView == null) {
            pipelineDestinationView = detachedFramebufferView();
        }

        pipelineDestinationView.framebufferTextureWidth = destination.getAttachmentWidth(Attachment.COLOR);
        pipelineDestinationView.framebufferTextureHeight = destination.getAttachmentHeight(Attachment.COLOR);
        pipelineDestinationView.framebufferWidth = destination.getAttachmentWidth(Attachment.COLOR);
        pipelineDestinationView.framebufferHeight = destination.getAttachmentHeight(Attachment.COLOR);
        pipelineDestinationView.framebufferObject = destination.getFramebufferId();
        pipelineDestinationView.framebufferTexture = destination.getTexture(Attachment.COLOR);
        pipelineDestinationView.depthBuffer = 0;
        pipelineDestinationView.framebufferFilter = GL11.GL_LINEAR;
        return pipelineDestinationView;
    }

    private static Framebuffer detachedFramebufferView() {
        Framebuffer view = new Framebuffer(1, 1, false);
        view.deleteFramebuffer();
        clearDetachedView(view);
        return view;
    }

    private static void clearDetachedView(Framebuffer view) {
        if (view == null) {
            return;
        }
        view.framebufferObject = -1;
        view.framebufferTexture = -1;
        view.depthBuffer = -1;
    }

    private BlockRenderLayer bloomLayer() {
        Reflection resolved = reflection();
        if (resolved == null || resolved.getBloomLayer == null) {
            return null;
        }
        try {
            Object layer = resolved.getBloomLayer.invoke(null);
            return layer instanceof BlockRenderLayer ? (BlockRenderLayer) layer : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            logReflectionFailure("Failed to resolve Lumenized BLOOM layer", e);
            return null;
        }
    }

    private boolean isPostProcessEnabled() {
        Reflection resolved = reflection();
        if (resolved == null || resolved.emissiveTexturesBloom == null || resolved.bloomStyle == null) {
            return false;
        }
        try {
            int style = resolved.bloomStyle.getInt(null);
            return resolved.emissiveTexturesBloom.getBoolean(null)
                    && style >= 0
                    && style <= 2
                    && OpenGlHelper.shadersSupported;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            logReflectionFailure("Failed to read Lumenized bloom config", e);
            return false;
        }
    }

    private int renderCustomBloomTickets(Entity entity, float partialTicks) {
        Reflection resolved = reflection();
        if (resolved == null || !resolved.customTicketsAvailable()) {
            return 0;
        }

        try {
            resolved.preDraw.invoke(null);
            Object context = resolved.effectContextGetInstance.invoke(null);
            resolved.effectContextUpdate.invoke(context, entity, partialTicks);
            Object mapObject = resolved.bloomRenders.get(null);
            if (!(mapObject instanceof Map<?, ?> bloomRenders) || bloomRenders.isEmpty()) {
                return 0;
            }

            BufferBuilder buffer = Tessellator.getInstance().getBuffer();
            int rendered = 0;
            Collection<?> ticketLists = bloomRenders.values();
            for (Object ticketList : ticketLists) {
                if (ticketList instanceof List<?>) {
                    resolved.draw.invoke(null, buffer, context, ticketList);
                    rendered++;
                }
            }
            return rendered;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            if (!loggedCustomTicketFailure) {
                loggedCustomTicketFailure = true;
                MainMod.LOGGER.warn("[LumenizedBloom] Failed to render custom bloom tickets", e);
            }
            return 0;
        } finally {
            try {
                resolved.postDraw.invoke(null);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            }
        }
    }

    private Reflection reflection() {
        if (resolveAttempted) {
            return reflection;
        }

        resolveAttempted = true;
        try {
            ClassLoader loader = LumenizedBloomTarget.class.getClassLoader();
            Class<?> bloomUtil = Class.forName(BLOOM_EFFECT_UTIL, false, loader);
            Class<?> context = Class.forName(EFFECT_RENDER_CONTEXT, false, loader);
            Class<?> config = Class.forName(LUMENIZED_CONFIG, false, loader);
            Class<?> effect = Class.forName(BLOOM_EFFECT, false, loader);
            Class<?> shaders = Class.forName(SHADERS, false, loader);
            reflection = Reflection.resolve(bloomUtil, context, config, effect, shaders);
            return reflection;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            logReflectionFailure("Lumenized standalone bloom reflection unavailable", e);
            return null;
        }
    }

    private void logReflectionFailure(String message, Throwable throwable) {
        if (!loggedReflectionFailure) {
            loggedReflectionFailure = true;
            MainMod.LOGGER.warn("[LumenizedBloom] {}", message, throwable);
        }
    }

    private void captureColorClear() {
        clearColorBuffer.clear();
        GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, clearColorBuffer);
    }

    private void clearTargetColor() {
        bloomTarget.bindFramebuffer(false);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        float previousRed = clearColorBuffer.get(0);
        float previousGreen = clearColorBuffer.get(1);
        float previousBlue = clearColorBuffer.get(2);
        float previousAlpha = clearColorBuffer.get(3);
        GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glClearColor(previousRed, previousGreen, previousBlue, previousAlpha);
    }

    private static void drawFullscreenQuad() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, 0.0F);
        GL11.glVertex2f(-1.0F, -1.0F);
        GL11.glTexCoord2f(1.0F, 0.0F);
        GL11.glVertex2f(1.0F, -1.0F);
        GL11.glTexCoord2f(1.0F, 1.0F);
        GL11.glVertex2f(1.0F, 1.0F);
        GL11.glTexCoord2f(0.0F, 1.0F);
        GL11.glVertex2f(-1.0F, 1.0F);
        GL11.glEnd();

        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

    private RenderState captureState() {
        return new RenderState();
    }

    private final class RenderState {
        private final int readFramebuffer;
        private final int drawFramebuffer;
        private final int activeTexture;
        private final int texture;
        private final int program;
        private final boolean blend;
        private final boolean depthTest;
        private final boolean alphaTest;
        private final boolean cull;
        private final boolean depthMask;
        private final int depthFunc;
        private final int blendSrcRgb;
        private final int blendDstRgb;
        private final int blendSrcAlpha;
        private final int blendDstAlpha;
        private final int viewportX;
        private final int viewportY;
        private final int viewportWidth;
        private final int viewportHeight;

        private RenderState() {
            readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            blend = GL11.glIsEnabled(GL11.GL_BLEND);
            depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
            cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
            blendSrcRgb = GL11.glGetInteger(GL14Accessor.GL_BLEND_SRC_RGB);
            blendDstRgb = GL11.glGetInteger(GL14Accessor.GL_BLEND_DST_RGB);
            blendSrcAlpha = GL11.glGetInteger(GL14Accessor.GL_BLEND_SRC_ALPHA);
            blendDstAlpha = GL11.glGetInteger(GL14Accessor.GL_BLEND_DST_ALPHA);
            viewportBuffer.clear();
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer);
            viewportX = viewportBuffer.get(0);
            viewportY = viewportBuffer.get(1);
            viewportWidth = viewportBuffer.get(2);
            viewportHeight = viewportBuffer.get(3);
        }

        private void restore() {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL11.glViewport(viewportX, viewportY, viewportWidth, viewportHeight);
            OpenGlHelper.glUseProgram(program);
            GL13.glActiveTexture(activeTexture);
            GlStateManager.bindTexture(texture);
            GlStateManager.depthMask(depthMask);
            GL11.glDepthFunc(depthFunc);
            GlStateManager.tryBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
            if (blend) {
                GlStateManager.enableBlend();
            } else {
                GlStateManager.disableBlend();
            }
            if (depthTest) {
                GlStateManager.enableDepth();
            } else {
                GlStateManager.disableDepth();
            }
            if (alphaTest) {
                GlStateManager.enableAlpha();
            } else {
                GlStateManager.disableAlpha();
            }
            if (cull) {
                GlStateManager.enableCull();
            } else {
                GlStateManager.disableCull();
            }
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.colorMask(true, true, true, true);
        }
    }

    private static final class Reflection {
        private final Method getBloomLayer;
        private final Method preDraw;
        private final Method draw;
        private final Method postDraw;
        private final Method effectContextGetInstance;
        private final Method effectContextUpdate;
        private final Field bloomRenders;
        private final Field emissiveTexturesBloom;
        private final Field bloomStyle;
        private final Field highBrightnessThreshold;
        private final Field lowBrightnessThreshold;
        private final Field baseBrightness;
        private final Field strength;
        private final Field step;
        private final Field effectStrength;
        private final Field effectHighBrightnessThreshold;
        private final Field effectLowBrightnessThreshold;
        private final Field effectBaseBrightness;
        private final Field effectStep;
        private final Method renderLog;
        private final Method renderUnity;
        private final Method renderUnreal;
        private final Method renderFullImageInFbo;
        private final Field imageFragmentShader;

        private Reflection(Method getBloomLayer, Method preDraw, Method draw, Method postDraw,
                           Method effectContextGetInstance, Method effectContextUpdate, Field bloomRenders,
                           Field emissiveTexturesBloom, Field bloomStyle, Field highBrightnessThreshold,
                           Field lowBrightnessThreshold, Field baseBrightness, Field strength, Field step,
                           Field effectStrength, Field effectHighBrightnessThreshold,
                           Field effectLowBrightnessThreshold, Field effectBaseBrightness, Field effectStep,
                           Method renderLog, Method renderUnity, Method renderUnreal,
                           Method renderFullImageInFbo, Field imageFragmentShader) {
            this.getBloomLayer = getBloomLayer;
            this.preDraw = preDraw;
            this.draw = draw;
            this.postDraw = postDraw;
            this.effectContextGetInstance = effectContextGetInstance;
            this.effectContextUpdate = effectContextUpdate;
            this.bloomRenders = bloomRenders;
            this.emissiveTexturesBloom = emissiveTexturesBloom;
            this.bloomStyle = bloomStyle;
            this.highBrightnessThreshold = highBrightnessThreshold;
            this.lowBrightnessThreshold = lowBrightnessThreshold;
            this.baseBrightness = baseBrightness;
            this.strength = strength;
            this.step = step;
            this.effectStrength = effectStrength;
            this.effectHighBrightnessThreshold = effectHighBrightnessThreshold;
            this.effectLowBrightnessThreshold = effectLowBrightnessThreshold;
            this.effectBaseBrightness = effectBaseBrightness;
            this.effectStep = effectStep;
            this.renderLog = renderLog;
            this.renderUnity = renderUnity;
            this.renderUnreal = renderUnreal;
            this.renderFullImageInFbo = renderFullImageInFbo;
            this.imageFragmentShader = imageFragmentShader;
        }

        private static Reflection resolve(Class<?> bloomUtil, Class<?> context, Class<?> config,
                                          Class<?> effect, Class<?> shaders) throws ReflectiveOperationException {
            Method getBloomLayer = bloomUtil.getMethod("getBloomLayer");
            Method preDraw = accessible(bloomUtil.getDeclaredMethod("preDraw"));
            Method postDraw = accessible(bloomUtil.getDeclaredMethod("postDraw"));
            Method draw = null;
            Field bloomRenders = null;
            try {
                draw = accessible(bloomUtil.getDeclaredMethod("draw", BufferBuilder.class, context, List.class));
                bloomRenders = accessible(bloomUtil.getDeclaredField("BLOOM_RENDERS"));
            } catch (ReflectiveOperationException ignored) {
                // Custom tickets are optional; block-layer bloom still works without them.
            }

            Method effectContextGetInstance = context.getMethod("getInstance");
            Method effectContextUpdate = context.getMethod("update", Entity.class, float.class);

            Field emissiveTexturesBloom = config.getField("emissiveTexturesBloom");
            Field bloomStyle = config.getField("bloomStyle");
            Field highBrightnessThreshold = config.getField("highBrightnessThreshold");
            Field lowBrightnessThreshold = config.getField("lowBrightnessThreshold");
            Field baseBrightness = config.getField("baseBrightness");
            Field strength = config.getField("strength");
            Field step = config.getField("step");

            Field effectStrength = effect.getField("strength");
            Field effectHighBrightnessThreshold = effect.getField("highBrightnessThreshold");
            Field effectLowBrightnessThreshold = effect.getField("lowBrightnessThreshold");
            Field effectBaseBrightness = effect.getField("baseBrightness");
            Field effectStep = effect.getField("step");
            Method renderLog = effect.getMethod("renderLOG", Framebuffer.class, Framebuffer.class);
            Method renderUnity = effect.getMethod("renderUnity", Framebuffer.class, Framebuffer.class);
            Method renderUnreal = effect.getMethod("renderUnreal", Framebuffer.class, Framebuffer.class);

            Method renderFullImageInFbo = null;
            for (Method method : shaders.getMethods()) {
                if ("renderFullImageInFBO".equals(method.getName()) && method.getParameterTypes().length == 3) {
                    renderFullImageInFbo = method;
                    break;
                }
            }
            if (renderFullImageInFbo == null) {
                throw new NoSuchMethodException("Shaders.renderFullImageInFBO");
            }
            Field imageFragmentShader = shaders.getField("IMAGE_F");

            return new Reflection(
                    getBloomLayer,
                    preDraw,
                    draw,
                    postDraw,
                    effectContextGetInstance,
                    effectContextUpdate,
                    bloomRenders,
                    emissiveTexturesBloom,
                    bloomStyle,
                    highBrightnessThreshold,
                    lowBrightnessThreshold,
                    baseBrightness,
                    strength,
                    step,
                    effectStrength,
                    effectHighBrightnessThreshold,
                    effectLowBrightnessThreshold,
                    effectBaseBrightness,
                    effectStep,
                    renderLog,
                    renderUnity,
                    renderUnreal,
                    renderFullImageInFbo,
                    imageFragmentShader
            );
        }

        private static Method accessible(Method method) {
            method.setAccessible(true);
            return method;
        }

        private static Field accessible(Field field) {
            field.setAccessible(true);
            return field;
        }

        private boolean customTicketsAvailable() {
            return preDraw != null
                    && draw != null
                    && postDraw != null
                    && effectContextGetInstance != null
                    && effectContextUpdate != null
                    && bloomRenders != null;
        }

        private boolean postProcessAvailable() {
            return renderLog != null
                    && renderUnity != null
                    && renderUnreal != null
                    && renderFullImageInFbo != null
                    && imageFragmentShader != null;
        }

        private void copyConfigToBloomEffect() throws ReflectiveOperationException {
            effectStrength.setFloat(null, (float) strength.getDouble(null));
            effectBaseBrightness.setFloat(null, (float) baseBrightness.getDouble(null));
            effectHighBrightnessThreshold.setFloat(null, (float) highBrightnessThreshold.getDouble(null));
            effectLowBrightnessThreshold.setFloat(null, (float) lowBrightnessThreshold.getDouble(null));
            effectStep.setFloat(null, (float) step.getDouble(null));
        }

        private void renderBloomEffect(Framebuffer bloomFramebuffer, Framebuffer destination)
                throws ReflectiveOperationException {
            int style = bloomStyle.getInt(null);
            switch (style) {
                case 0 -> renderLog.invoke(null, bloomFramebuffer, destination);
                case 1 -> renderUnity.invoke(null, bloomFramebuffer, destination);
                case 2 -> renderUnreal.invoke(null, bloomFramebuffer, destination);
                default -> {
                }
            }
        }

        private void copyCurrentTextureTo(Framebuffer destination) throws ReflectiveOperationException {
            renderFullImageInFbo.invoke(null, destination, imageFragmentShader.get(null), null);
        }
    }

    private static final class GL14Accessor {
        private static final int GL_BLEND_DST_RGB = 0x80C8;
        private static final int GL_BLEND_SRC_RGB = 0x80C9;
        private static final int GL_BLEND_DST_ALPHA = 0x80CA;
        private static final int GL_BLEND_SRC_ALPHA = 0x80CB;
    }
}
