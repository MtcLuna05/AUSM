package com.luna.ausm.impl.pipeline.compat;

import com.luna.ausm.impl.pipeline.render.TextureBinder;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

abstract class BetterPortalsCompatBase {
    protected static final String MOD_ID = "betterportals";

    protected static final String VIEW_RENDER_PLAN_CLASS = "de.johni0702.minecraft.view.impl.client.render.ViewRenderPlan";

    protected static final String PORTAL_ENTITY_CLASS = "de.johni0702.minecraft.betterportals.common.entity.PortalEntity";

    protected static final String BETTER_PORTALS_MOD_CLASS = "de.johni0702.minecraft.betterportals.impl.BetterPortalsMod";

    protected static final String BETTER_PORTALS_CONFIG_CLASS = "de.johni0702.minecraft.betterportals.impl.BPConfig";

    protected static final String RENDER_PASS_EVENT_PREFIX = "de.johni0702.minecraft.view.client.render.RenderPassEvent$";

    protected static Boolean installed;

    protected static boolean viewPlanReflectionResolved;

    protected static boolean viewPlanReflectionFailed;

    protected static Field currentViewPlanField;

    protected static Field mainViewPlanField;

    protected static Method renderPassParentMethod;

    protected static boolean configReflectionResolved;

    protected static boolean configReflectionFailed;

    protected static Field seeThroughPortalsField;

    protected static boolean portalEntityClassResolved;

    protected static Class<?> portalEntityClass;

    protected static final ConcurrentMap<Class<?>, Boolean> PORTAL_BLOCK_CLASSES = new ConcurrentHashMap<>();

    protected static final Deque<RenderPassState> renderPassStack = new ArrayDeque<>();

    protected static final Deque<PortalRendererGlState> portalRendererStateStack = new ArrayDeque<>();

    protected static int renderPassDepth;

    protected static int nestedRenderPassDepth;

    protected static int quietDimensionReloadRequests;

    protected static int mainViewSwapHandlingDepth;

    protected static int mainViewSwapRecoveryFrames;

    protected static int mainViewSwapRecoveryDimensionId = Integer.MIN_VALUE;

    protected static String lastLoggedNestedRenderState;

    protected static WorldClient pendingParentRenderWorld;

    protected static boolean portalSurfaceCompositeLogged;

    protected static boolean portalRendererStateWarningLogged;

    protected static boolean renderStateDiagnosticWarningLogged;

    protected static boolean mainViewSwapRecoveryLogged;

    protected static final int CAPTURED_TEXTURE_UNITS = 32;

    protected static final int VANILLA_GL_STATE_TEXTURE_UNITS = 8;

    protected static int cachedCapturedTextureUnitCount = -1;

    protected static final int MAIN_VIEW_SWAP_RECOVERY_FRAMES = 40;

    protected static final int MAX_RENDER_STATE_DIAGNOSTIC_LOGS = 0;

    protected static final int MAX_TRANSITION_DIAGNOSTIC_LOGS = 0;

    protected static final int RENDER_STATE_DIAGNOSTIC_FRAMES_AFTER_NESTED = 180;

    protected static final boolean NESTED_SHADER_PIPELINE_ENABLED = false;

    protected static int renderStateDiagnosticLogs;

    protected static int renderStateDiagnosticFramesRemaining;

    protected static boolean nestedShaderPipelineDisabledLogged;

    protected static int transitionDiagnosticLogs;

    protected static final class RenderPassState {
        final boolean nested;
        final WorldClient world;
        final int dimensionId;
        final Framebuffer framebuffer;
        final PortalRendererGlState glState;

        RenderPassState(boolean nested, WorldClient world, int dimensionId, Framebuffer framebuffer, PortalRendererGlState glState) {
            this.nested = nested;
            this.world = world;
            this.dimensionId = dimensionId;
            this.framebuffer = framebuffer;
            this.glState = glState;
        }

        boolean nested() {
            return nested;
        }

        WorldClient world() {
            return world;
        }

        int dimensionId() {
            return dimensionId;
        }

        Framebuffer framebuffer() {
            return framebuffer;
        }

        PortalRendererGlState glState() {
            return glState;
        }
    }

    protected static final class PortalRendererGlState {
        final boolean fullCapture;
        final int program;
        final int activeTexture;
        final int[] texture2dBindings;
        final boolean[] texture2dEnabled;
        final int framebuffer;
        final int readFramebuffer;
        final int drawFramebuffer;
        final int drawBuffer;
        final int readBuffer;
        final int[] viewport;
        final int[] scissorBox;
        final boolean texture2d;
        final boolean depthTest;
        final boolean depthMask;
        final int depthFunc;
        final boolean alphaTest;
        final int alphaFunc;
        final float alphaRef;
        final boolean blend;
        final int blendSrcRgb;
        final int blendDstRgb;
        final int blendSrcAlpha;
        final int blendDstAlpha;
        final boolean cullFace;
        final int cullFaceMode;
        final boolean lighting;
        final boolean colorMaterial;
        final boolean stencilTest;
        final int stencilFunc;
        final int stencilRef;
        final int stencilValueMask;
        final int stencilWriteMask;
        final int stencilFail;
        final int stencilPassDepthFail;
        final int stencilPassDepthPass;
        final boolean scissorTest;
        final boolean[] clipPlanes;
        final boolean polygonOffsetFill;
        final float polygonOffsetFactor;
        final float polygonOffsetUnits;
        final boolean[] colorMask;
        final float[] color;
        final int matrixMode;
        final int shadeModel;

        PortalRendererGlState(boolean fullCapture) {
            this.fullCapture = fullCapture;
            program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            framebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            drawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
            readBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
            viewport = BetterPortalsCompat.glInt4(GL11.GL_VIEWPORT);
            scissorBox = BetterPortalsCompat.glInt4(GL11.GL_SCISSOR_BOX);
            depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            depthMask = BetterPortalsCompat.glBoolean(GL11.GL_DEPTH_WRITEMASK);
            depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
            alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
            alphaFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
            alphaRef = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
            blend = GL11.glIsEnabled(GL11.GL_BLEND);
            blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
            blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
            blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
            blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
            cullFace = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            cullFaceMode = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);
            scissorTest = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            if (fullCapture) {
                int textureUnits = capturedTextureUnitCount();
                texture2dBindings = new int[textureUnits];
                texture2dEnabled = new boolean[textureUnits];
                captureTextureBindings(activeTexture, texture2dBindings, texture2dEnabled);
                texture2d = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
                lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
                colorMaterial = GL11.glIsEnabled(GL11.GL_COLOR_MATERIAL);
                stencilTest = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
                stencilFunc = GL11.glGetInteger(GL11.GL_STENCIL_FUNC);
                stencilRef = GL11.glGetInteger(GL11.GL_STENCIL_REF);
                stencilValueMask = GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK);
                stencilWriteMask = GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK);
                stencilFail = GL11.glGetInteger(GL11.GL_STENCIL_FAIL);
                stencilPassDepthFail = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL);
                stencilPassDepthPass = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS);
                clipPlanes = new boolean[6];
                for (int i = 0; i < clipPlanes.length; i++) {
                    clipPlanes[i] = GL11.glIsEnabled(GL11.GL_CLIP_PLANE0 + i);
                }
                polygonOffsetFill = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
                polygonOffsetFactor = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR);
                polygonOffsetUnits = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS);
                colorMask = BetterPortalsCompat.glBoolean4(GL11.GL_COLOR_WRITEMASK);
                color = BetterPortalsCompat.glFloat4(GL11.GL_CURRENT_COLOR);
                matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
                shadeModel = GL11.glGetInteger(GL11.GL_SHADE_MODEL);
            } else {
                texture2dBindings = null;
                texture2dEnabled = null;
                texture2d = true;
                lighting = false;
                colorMaterial = false;
                stencilTest = false;
                stencilFunc = GL11.GL_ALWAYS;
                stencilRef = 0;
                stencilValueMask = 0xFF;
                stencilWriteMask = 0xFF;
                stencilFail = GL11.GL_KEEP;
                stencilPassDepthFail = GL11.GL_KEEP;
                stencilPassDepthPass = GL11.GL_KEEP;
                clipPlanes = null;
                polygonOffsetFill = false;
                polygonOffsetFactor = 0.0F;
                polygonOffsetUnits = 0.0F;
                colorMask = null;
                color = null;
                matrixMode = GL11.GL_MODELVIEW;
                shadeModel = GL11.GL_SMOOTH;
            }
        }

        static PortalRendererGlState capture() {
            return new PortalRendererGlState(true);
        }

        static PortalRendererGlState captureRenderPass() {
            return new PortalRendererGlState(false);
        }

        String summary() {
            return "program=" + program
                    + " activeTex=" + textureUnitIndex(activeTexture)
                    + " fbo=" + framebuffer
                    + " readFb=" + readFramebuffer
                    + " drawFb=" + drawFramebuffer
                    + " drawBuf=" + BetterPortalsCompat.hex(drawBuffer)
                    + " readBuf=" + BetterPortalsCompat.hex(readBuffer)
                    + " viewport=" + Arrays.toString(viewport)
                    + " scissor=" + Arrays.toString(scissorBox)
                    + " depth=" + depthTest + "/" + depthMask + "/" + BetterPortalsCompat.hex(depthFunc)
                    + " alpha=" + alphaTest + "/" + BetterPortalsCompat.hex(alphaFunc) + "/" + alphaRef
                    + " blend=" + blend + "/" + BetterPortalsCompat.hex(blendSrcRgb) + "," + BetterPortalsCompat.hex(blendDstRgb) + "," + BetterPortalsCompat.hex(blendSrcAlpha) + "," + BetterPortalsCompat.hex(blendDstAlpha)
                    + " cull=" + cullFace + "/" + BetterPortalsCompat.hex(cullFaceMode)
                    + " light=" + lighting
                    + " colorMat=" + colorMaterial
                    + " stencil=" + stencilTest + "/" + BetterPortalsCompat.hex(stencilFunc) + "/" + stencilRef + "/" + BetterPortalsCompat.hex(stencilValueMask) + "/" + BetterPortalsCompat.hex(stencilWriteMask)
                    + " scissorTest=" + scissorTest
                    + " clip=" + Arrays.toString(clipPlanes)
                    + " poly=" + polygonOffsetFill + "/" + polygonOffsetFactor + "," + polygonOffsetUnits
                    + " colorMask=" + Arrays.toString(colorMask)
                    + " color=" + Arrays.toString(color)
                    + " matrix=" + BetterPortalsCompat.hex(matrixMode)
                    + " shade=" + BetterPortalsCompat.hex(shadeModel)
                    + " tex=" + textureSummary();
        }

        String textureSummary() {
            int[] units = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15, 16, 20, 28, 29, 30, 31};
            StringBuilder builder = new StringBuilder();
            for (int unit : units) {
                if (unit >= texture2dBindings.length) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append(',');
                }
                builder.append(unit)
                        .append('=')
                        .append(texture2dBindings[unit])
                        .append(texture2dEnabled[unit] ? 'e' : 'd');
            }
            return builder.toString();
        }

        static int textureUnitIndex(int textureUnit) {
            return textureUnit >= GL13.GL_TEXTURE0 ? textureUnit - GL13.GL_TEXTURE0 : textureUnit;
        }

        void restore() {
            MinecraftReflectionCompat.glUseProgram(program);
            if (fullCapture) {
                restoreTextureBindings(activeTexture, texture2dBindings, texture2dEnabled);
            } else if (isVanillaGlStateTextureUnit(activeTexture)) {
                MinecraftReflectionCompat.glStateSetActiveTexture(activeTexture);
                GL13.glActiveTexture(activeTexture);
            } else {
                TextureBinder.restoreDefaultTextureUnit();
            }
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            if (readFramebuffer == drawFramebuffer && readFramebuffer == framebuffer) {
                MinecraftReflectionCompat.glBindFramebuffer(MinecraftReflectionCompat.glFramebuffer(), framebuffer);
            }
            GL11.glDrawBuffer(drawBuffer);
            GL11.glReadBuffer(readBuffer);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GL11.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
            BetterPortalsCompat.setCapability(GL11.GL_DEPTH_TEST, depthTest);
            GL11.glDepthMask(depthMask);
            GL11.glDepthFunc(depthFunc);
            BetterPortalsCompat.setCapability(GL11.GL_ALPHA_TEST, alphaTest);
            GL11.glAlphaFunc(alphaFunc, alphaRef);
            BetterPortalsCompat.setCapability(GL11.GL_BLEND, blend);
            MinecraftReflectionCompat.glStateTryBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
            BetterPortalsCompat.setCapability(GL11.GL_CULL_FACE, cullFace);
            GL11.glCullFace(cullFaceMode);
            BetterPortalsCompat.setCapability(GL11.GL_SCISSOR_TEST, scissorTest);
            if (fullCapture) {
                BetterPortalsCompat.setCapability(GL11.GL_TEXTURE_2D, texture2d);
                BetterPortalsCompat.setCapability(GL11.GL_LIGHTING, lighting);
                BetterPortalsCompat.setCapability(GL11.GL_COLOR_MATERIAL, colorMaterial);
                BetterPortalsCompat.setCapability(GL11.GL_STENCIL_TEST, stencilTest);
                GL11.glStencilMask(stencilWriteMask);
                GL11.glStencilFunc(stencilFunc, stencilRef, stencilValueMask);
                GL11.glStencilOp(stencilFail, stencilPassDepthFail, stencilPassDepthPass);
                for (int i = 0; i < clipPlanes.length; i++) {
                    BetterPortalsCompat.setCapability(GL11.GL_CLIP_PLANE0 + i, clipPlanes[i]);
                }
                BetterPortalsCompat.setCapability(GL11.GL_POLYGON_OFFSET_FILL, polygonOffsetFill);
                GL11.glPolygonOffset(polygonOffsetFactor, polygonOffsetUnits);
                GL11.glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
                GL11.glColor4f(color[0], color[1], color[2], color[3]);
                GL11.glShadeModel(shadeModel);
                GL11.glMatrixMode(matrixMode);
            }
        }

        static void captureTextureBindings(int previousActiveTexture, int[] bindings, boolean[] enabled) {
            try {
                for (int i = 0; i < bindings.length; i++) {
                    GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
                    bindings[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                    enabled[i] = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
                }
            } finally {
                GL13.glActiveTexture(previousActiveTexture);
            }
        }

        static int capturedTextureUnitCount() {
            if (cachedCapturedTextureUnitCount > 0) {
                return cachedCapturedTextureUnitCount;
            }
            int maxUnits = GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);
            if (maxUnits <= 0) {
                maxUnits = CAPTURED_TEXTURE_UNITS;
            }
            cachedCapturedTextureUnitCount = Math.min(CAPTURED_TEXTURE_UNITS, maxUnits);
            return cachedCapturedTextureUnitCount;
        }

        static void restoreTextureBindings(int activeTexture, int[] bindings, boolean[] enabled) {
            try {
                for (int i = 0; i < bindings.length; i++) {
                    int unit = GL13.GL_TEXTURE0 + i;
                    GL13.glActiveTexture(unit);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, bindings[i]);
                    BetterPortalsCompat.setCapability(GL11.GL_TEXTURE_2D, enabled[i]);
                }
            } finally {
                if (isVanillaGlStateTextureUnit(activeTexture)) {
                    MinecraftReflectionCompat.glStateSetActiveTexture(activeTexture);
                    GL13.glActiveTexture(activeTexture);
                } else {
                    TextureBinder.restoreDefaultTextureUnit();
                }
            }
        }

        static boolean isVanillaGlStateTextureUnit(int unit) {
            return unit >= GL13.GL_TEXTURE0 && unit < GL13.GL_TEXTURE0 + VANILLA_GL_STATE_TEXTURE_UNITS;
        }
    }

    protected final BetterPortalsCompat self() {
        return (BetterPortalsCompat) this;
    }
}
