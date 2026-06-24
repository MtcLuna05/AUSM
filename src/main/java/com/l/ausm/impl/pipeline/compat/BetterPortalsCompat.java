package com.l.ausm.impl.pipeline.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.render.TextureBinder;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.Event;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

public final class BetterPortalsCompat {
    private static final String MOD_ID = "betterportals";
    private static final String VIEW_RENDER_PLAN_CLASS = "de.johni0702.minecraft.view.impl.client.render.ViewRenderPlan";
    private static final String PORTAL_ENTITY_CLASS = "de.johni0702.minecraft.betterportals.common.entity.PortalEntity";
    private static final String BETTER_PORTALS_MOD_CLASS = "de.johni0702.minecraft.betterportals.impl.BetterPortalsMod";
    private static final String BETTER_PORTALS_CONFIG_CLASS = "de.johni0702.minecraft.betterportals.impl.BPConfig";
    private static final String RENDER_PASS_EVENT_PREFIX = "de.johni0702.minecraft.view.client.render.RenderPassEvent$";

    private static Boolean installed;
    private static boolean viewPlanReflectionResolved;
    private static boolean viewPlanReflectionFailed;
    private static Field currentViewPlanField;
    private static Field mainViewPlanField;
    private static Method renderPassParentMethod;
    private static boolean configReflectionResolved;
    private static boolean configReflectionFailed;
    private static Field seeThroughPortalsField;

    private static boolean portalEntityClassResolved;
    private static Class<?> portalEntityClass;

    private static final Deque<RenderPassState> renderPassStack = new ArrayDeque<>();
    private static final Deque<PortalRendererGlState> portalRendererStateStack = new ArrayDeque<>();
    private static int renderPassDepth;
    private static int nestedRenderPassDepth;
    private static int quietDimensionReloadRequests;
    private static int mainViewSwapHandlingDepth;
    private static int mainViewSwapRecoveryFrames;
    private static int mainViewSwapRecoveryDimensionId = Integer.MIN_VALUE;
    private static String lastLoggedNestedRenderState;
    private static WorldClient pendingParentRenderWorld;
    private static boolean portalSurfaceCompositeLogged;
    private static boolean portalRendererStateWarningLogged;
    private static boolean renderStateDiagnosticWarningLogged;
    private static boolean mainViewSwapRecoveryLogged;
    private static final int CAPTURED_TEXTURE_UNITS = 32;
    private static final int VANILLA_GL_STATE_TEXTURE_UNITS = 8;
    private static int cachedCapturedTextureUnitCount = -1;
    private static final int MAIN_VIEW_SWAP_RECOVERY_FRAMES = 10;
    private static final int MAX_RENDER_STATE_DIAGNOSTIC_LOGS = 0;
    private static final int MAX_TRANSITION_DIAGNOSTIC_LOGS = 0;
    private static final int RENDER_STATE_DIAGNOSTIC_FRAMES_AFTER_NESTED = 180;
    private static final boolean NESTED_SHADER_PIPELINE_ENABLED = false;
    private static int renderStateDiagnosticLogs;
    private static int renderStateDiagnosticFramesRemaining;
    private static boolean nestedShaderPipelineDisabledLogged;
    private static int transitionDiagnosticLogs;

    private BetterPortalsCompat() {
    }

    public static boolean isInstalled() {
        if (installed == null) {
            installed = Loader.isModLoaded(MOD_ID) || classPresent(BETTER_PORTALS_MOD_CLASS);
        }
        return installed;
    }

    public static boolean isRenderingNestedView() {
        if (!isInstalled()) {
            return false;
        }

        if (nestedRenderPassDepth > 0) {
            return true;
        }

        if (!resolveViewPlanReflection()) {
            return false;
        }

        try {
            Object current = currentViewPlanField.get(null);
            if (current == null) {
                return false;
            }

            Object main = mainViewPlanField.get(null);
            if (main != null) {
                return current != main;
            }

            return renderPassParentMethod.invoke(current) != null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            logViewPlanReflectionFailure(e);
            return false;
        }
    }

    public static boolean isRenderingRenderPass() {
        return isInstalled() && renderPassDepth > 0;
    }

    public static boolean isPortalEntity(Entity entity) {
        if (entity == null || !isInstalled()) {
            return false;
        }

        Class<?> portalEntityType = portalEntityClass();
        if (portalEntityType != null && portalEntityType.isInstance(entity)) {
            return true;
        }

        String className = entity.getClass().getName();
        return className.startsWith("de.johni0702.minecraft.betterportals.")
                && className.contains("Portal")
                && className.endsWith("Entity");
    }

    public static boolean isSeeThroughPortalsEnabled() {
        if (!isInstalled() || !resolveConfigReflection()) {
            return false;
        }

        try {
            return seeThroughPortalsField.getBoolean(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            logConfigReflectionFailure(e);
            return false;
        }
    }

    public static boolean shouldRenderNestedViewWithShaders() {
        boolean configured = isInstalled()
                && isSeeThroughPortalsEnabled()
                && PipelineContext.getInstance().isActive()
                && MainMod.getClientSettingsConfig() != null
                && MainMod.getClientSettingsConfig().portalShadersEnabled();
        if (!configured) {
            return false;
        }
        if (!NESTED_SHADER_PIPELINE_ENABLED) {
            if (!nestedShaderPipelineDisabledLogged) {
                nestedShaderPipelineDisabledLogged = true;
                MainMod.LOGGER.info("[BetterPortalsCompat] AUSM nested portal shader pipeline is disabled; portal child views will render shaderless.");
            }
            return false;
        }
        return true;
    }

    public static boolean isNestedShaderPipelineAvailable() {
        return NESTED_SHADER_PIPELINE_ENABLED;
    }

    public static boolean shouldUseVanillaRenderGlobalForNestedView() {
        return isInstalled()
                && isSeeThroughPortalsEnabled()
                && isRenderingNestedView()
                && !shouldRenderNestedViewWithShaders();
    }

    public static boolean shouldUseAusmPortalShaderHandling() {
        return shouldRenderNestedViewWithShaders();
    }

    public static boolean shouldUseAusmPortalSurfaceReplacement() {
        return false;
    }

    private static boolean shouldProtectBetterPortalsRenderState() {
        return isInstalled() && isSeeThroughPortalsEnabled();
    }

    public static int currentShaderRenderPassDimensionId() {
        RenderPassState state = renderPassStack.peek();
        if (!shouldRenderNestedViewWithShaders()) {
            return Integer.MIN_VALUE;
        }
        if (state != null && state.nested()) {
            return state.dimensionId();
        }

        Object current = currentViewPlan();
        if (isNestedViewPlan(current)) {
            return renderPassWorldDimension(current);
        }
        return Integer.MIN_VALUE;
    }

    public static Framebuffer currentShaderRenderPassFramebuffer() {
        RenderPassState state = renderPassStack.peek();
        if (!shouldRenderNestedViewWithShaders()) {
            return null;
        }
        if (state != null && state.nested()) {
            return state.framebuffer();
        }

        Object current = currentViewPlan();
        return isNestedViewPlan(current) ? renderPassFramebuffer(current) : null;
    }

    public static boolean consumeQuietDimensionReloadLogRequest() {
        if (quietDimensionReloadRequests <= 0) {
            return false;
        }
        quietDimensionReloadRequests--;
        return true;
    }

    public static WorldClient consumePendingParentRenderWorld() {
        WorldClient world = pendingParentRenderWorld;
        pendingParentRenderWorld = null;
        return world;
    }

    public static WorldClient currentRenderPassWorld() {
        RenderPassState state = renderPassStack.peek();
        if (state != null) {
            return state.world();
        }

        Object current = currentViewPlan();
        return current != null ? renderPassWorld(current) : null;
    }

    public static Framebuffer currentRenderPassFramebuffer() {
        RenderPassState state = renderPassStack.peek();
        if (state != null) {
            return state.framebuffer();
        }

        Object current = currentViewPlan();
        return current != null ? renderPassFramebuffer(current) : null;
    }

    public static void startMainViewSwapRecovery(WorldClient world) {
        if (!isInstalled()) {
            return;
        }

        int dimensionId = world != null && world.provider != null
                ? world.provider.getDimension()
                : Integer.MIN_VALUE;
        mainViewSwapRecoveryFrames = Math.max(mainViewSwapRecoveryFrames, MAIN_VIEW_SWAP_RECOVERY_FRAMES);
        mainViewSwapRecoveryDimensionId = dimensionId;
        mainViewSwapRecoveryLogged = false;
    }

    public static void beginMainViewSwapHandling() {
        if (isInstalled()) {
            mainViewSwapHandlingDepth++;
        }
    }

    public static void endMainViewSwapHandling() {
        if (mainViewSwapHandlingDepth > 0) {
            mainViewSwapHandlingDepth--;
        }
    }

    public static boolean isMainViewSwapHandling() {
        return isInstalled() && mainViewSwapHandlingDepth > 0;
    }

    public static void clearMainViewSwapTransientState() {
        if (!isInstalled()) {
            return;
        }

        renderPassStack.clear();
        portalRendererStateStack.clear();
        renderPassDepth = 0;
        nestedRenderPassDepth = 0;
        quietDimensionReloadRequests = 0;
        pendingParentRenderWorld = null;
        lastLoggedNestedRenderState = null;
        mainViewSwapHandlingDepth = 0;
    }

    public static void cancelMainViewSwapRecovery() {
        if (!isInstalled()) {
            return;
        }

        mainViewSwapRecoveryFrames = 0;
        mainViewSwapRecoveryDimensionId = Integer.MIN_VALUE;
        mainViewSwapRecoveryLogged = false;
    }

    public static void tickMainViewSwapRecovery() {
        if (mainViewSwapRecoveryFrames > 0) {
            mainViewSwapRecoveryFrames--;
            if (mainViewSwapRecoveryFrames == 0) {
                mainViewSwapRecoveryDimensionId = Integer.MIN_VALUE;
                mainViewSwapRecoveryLogged = false;
            }
        }
    }

    public static boolean isMainViewSwapRecoveryActive() {
        return isInstalled() && mainViewSwapRecoveryFrames > 0;
    }

    public static int mainViewSwapRecoveryDimensionId() {
        return isMainViewSwapRecoveryActive() ? mainViewSwapRecoveryDimensionId : Integer.MIN_VALUE;
    }

    public static String describeTransitionState() {
        if (!isInstalled()) {
            return "installed=false";
        }

        Object current = currentViewPlan();
        Object main = mainViewPlan();
        return "passDepth=" + renderPassDepth
                + " nestedDepth=" + nestedRenderPassDepth
                + " stack=" + renderPassStack.size()
                + " rendererStack=" + portalRendererStateStack.size()
                + " handlingDepth=" + mainViewSwapHandlingDepth
                + " recoveryFrames=" + mainViewSwapRecoveryFrames
                + " recoveryDim=" + mainViewSwapRecoveryDimensionId
                + " quietReloads=" + quietDimensionReloadRequests
                + " pendingParent=" + dimensionId(pendingParentRenderWorld)
                + " current=" + describeViewPlan(current)
                + " main=" + describeViewPlan(main)
                + " currentIsMain=" + (current != null && current == main)
                + " currentNested=" + isRenderingNestedView()
                + " renderPass=" + isRenderingRenderPass()
                + " seeThrough=" + isSeeThroughPortalsEnabled();
    }

    public static void logTransitionDiagnostic(String stage, Object viewPlan) {
        if (!isInstalled() || transitionDiagnosticLogs >= MAX_TRANSITION_DIAGNOSTIC_LOGS) {
            return;
        }
        transitionDiagnosticLogs++;
        MainMod.LOGGER.info("[BetterPortalsTransition] call={} stage={} view={} state={} caller={}",
                transitionDiagnosticLogs,
                stage,
                describeViewPlan(viewPlan),
                describeTransitionState(),
                externalCaller());
    }

    public static void logMainViewSwapRecoveryIfNeeded(WorldClient world) {
        if (!isMainViewSwapRecoveryActive() || mainViewSwapRecoveryLogged) {
            return;
        }
        mainViewSwapRecoveryLogged = true;
        int dimensionId = world != null && world.provider != null
                ? world.provider.getDimension()
                : mainViewSwapRecoveryDimensionId;
        MainMod.LOGGER.info("[BetterPortalsCompat] Main view swap recovery active: world={} frames={}",
                dimensionId,
                mainViewSwapRecoveryFrames);
    }

    public static float portalOpacity(Object portal) {
        if (portal == null) {
            return 1.0F;
        }

        return portalOpacityForClassName(portal.getClass().getName());
    }

    public static boolean shouldSuppressOriginalPortalBlock(IBlockState state) {
        if (state == null || !shouldUseAusmPortalSurfaceReplacement()) {
            return false;
        }
        if (!isInstalled() || !isSeeThroughPortalsEnabled()) {
            return false;
        }

        Block block = state.getBlock();
        if (!isBetterPortalsPortalBlock(block)) {
            return false;
        }

        return true;
    }

    public static boolean isBetterPortalsPortalBlock(Block block) {
        if (block == null) {
            return false;
        }
        Class<?> type = block.getClass();
        String name = type.getName();
        if (name.startsWith("de.johni0702.minecraft.betterportals.")
                && name.contains("Portal")) {
            return true;
        }
        while (type != null) {
            for (Class<?> iface : type.getInterfaces()) {
                if ("de.johni0702.minecraft.betterportals.common.block.PortalBlock".equals(iface.getName())) {
                    return true;
                }
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static float portalOpacityForClassName(String portalClassName) {
        if (!resolveConfigReflection() || portalClassName == null) {
            return 1.0F;
        }

        Object config = portalConfig(portalClassName);
        if (config == null) {
            return 1.0F;
        }

        try {
            Field opacityField = config.getClass().getField("opacity");
            double opacity = opacityField.getDouble(config);
            if (!Double.isFinite(opacity)) {
                return 1.0F;
            }
            return (float) Math.max(0.0D, Math.min(1.0D, opacity));
        } catch (ReflectiveOperationException | RuntimeException e) {
            logConfigReflectionFailure(e);
            return 1.0F;
        }
    }

    public static boolean handleRenderPassEvent(Event event) {
        if (!isInstalled() || event == null) {
            return false;
        }

        String eventClassName = event.getClass().getName();
        if (!eventClassName.startsWith(RENDER_PASS_EVENT_PREFIX)) {
            return false;
        }

        if (eventClassName.endsWith("$Start")) {
            handleRenderPassStart(event);
            return false;
        } else if (eventClassName.endsWith("$End")) {
            return handleRenderPassEnd(event);
        }
        return false;
    }

    public static void pushPortalRendererState() {
        if (!shouldProtectBetterPortalsRenderState()) {
            return;
        }

        logRenderStateDiagnostic("portal-renderer:before-capture");
        try {
            portalRendererStateStack.push(PortalRendererGlState.capture());
            logRenderStateDiagnostic("portal-renderer:after-capture stack=" + portalRendererStateStack.size());
        } catch (RuntimeException e) {
            if (!portalRendererStateWarningLogged) {
                portalRendererStateWarningLogged = true;
                MainMod.LOGGER.warn("[BetterPortalsCompat] Failed to capture Better Portals renderer GL state", e);
            }
        }
    }

    public static void popPortalRendererState() {
        if (!isInstalled()) {
            return;
        }

        PortalRendererGlState state = portalRendererStateStack.poll();
        if (state == null) {
            return;
        }

        logRenderStateDiagnostic("portal-renderer:before-restore stack=" + portalRendererStateStack.size());
        try {
            state.restore();
            logRenderStateDiagnostic("portal-renderer:after-restore stack=" + portalRendererStateStack.size());
            restoreAfterPortalRender();
            logRenderStateDiagnostic("portal-renderer:after-normalize stack=" + portalRendererStateStack.size());
        } catch (RuntimeException e) {
            if (!portalRendererStateWarningLogged) {
                portalRendererStateWarningLogged = true;
                MainMod.LOGGER.warn("[BetterPortalsCompat] Failed to restore Better Portals renderer GL state", e);
            }
            restoreAfterPortalRender();
        }
    }

    public static void markPortalSurfaceCompositeHandled(Framebuffer framebuffer) {
        if (framebuffer == null || portalSurfaceCompositeLogged) {
            return;
        }
        portalSurfaceCompositeLogged = true;
        MainMod.LOGGER.info("[BetterPortalsCompat] AUSM portal surface composite: framebuffer={} size={}x{} seeThroughPortals={}",
                framebuffer.framebufferObject,
                framebuffer.framebufferWidth,
                framebuffer.framebufferHeight,
                isSeeThroughPortalsEnabled());
    }

    private static void restoreAfterPortalRender() {
        if (!isInstalled()) {
            return;
        }

        OpenGlHelper.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GlStateManager.enableDepth();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GlStateManager.enableAlpha();
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.disableLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GlStateManager.disableColorMaterial();
        GL11.glDisable(GL11.GL_COLOR_MATERIAL);
        GlStateManager.disableBlend();
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColorMask(true, true, true, true);
        GL11.glStencilMask(0xFF);
        GL11.glStencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        for (int i = 0; i < 6; i++) {
            GL11.glDisable(GL11.GL_CLIP_PLANE0 + i);
        }
        GL11.glPolygonOffset(0.0F, 0.0F);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
    }

    private static void handleRenderPassStart(Event event) {
        Object pass = renderPassFromEvent(event);
        logTransitionDiagnostic("render-pass-start:before", pass);
        boolean nested = renderPassParent(pass) != null;
        WorldClient world = renderPassWorld(pass);
        int dimensionId = renderPassWorldDimension(pass);
        Framebuffer framebuffer = renderPassFramebuffer(pass);
        PortalRendererGlState glState = null;
        if (nested) {
            renderStateDiagnosticFramesRemaining = Math.max(
                    renderStateDiagnosticFramesRemaining,
                    RENDER_STATE_DIAGNOSTIC_FRAMES_AFTER_NESTED
            );
            if (dimensionId != Integer.MIN_VALUE
                    && shouldRenderNestedViewWithShaders()
                    && MainMod.getShaderPackManager() != null) {
                MainMod.getShaderPackManager().scheduleBetterPortalsDimensionPrewarm(dimensionId);
            }
        }
        logRenderStateDiagnostic("render-pass-start:before nested=" + nested
                + " world=" + dimensionId
                + " fb=" + describeFramebuffer(framebuffer));
        if (shouldProtectBetterPortalsRenderState()) {
            try {
                glState = PortalRendererGlState.captureRenderPass();
            } catch (RuntimeException e) {
                if (!portalRendererStateWarningLogged) {
                    portalRendererStateWarningLogged = true;
                    MainMod.LOGGER.warn("[BetterPortalsCompat] Failed to capture Better Portals render-pass GL state", e);
                }
            }
        }

        renderPassStack.push(new RenderPassState(nested, world, dimensionId, framebuffer, glState));
        renderPassDepth++;
        if (nested) {
            nestedRenderPassDepth++;
            logNestedRenderPassState(pass);
        }
        logTransitionDiagnostic("render-pass-start:after", pass);
        logRenderStateDiagnostic("render-pass-start:after nested=" + nested
                + " world=" + dimensionId
                + " fb=" + describeFramebuffer(framebuffer));
    }

    private static boolean handleRenderPassEnd(Event event) {
        Object pass = renderPassFromEvent(event);
        logTransitionDiagnostic("render-pass-end:before", pass);
        RenderPassState state = renderPassStack.poll();
        boolean nested = state != null ? state.nested() : renderPassParent(pass) != null;

        logRenderStateDiagnostic("render-pass-end:before-restore nested=" + nested
                + " world=" + (state != null ? state.dimensionId() : renderPassWorldDimension(pass))
                + " fb=" + describeFramebuffer(state != null ? state.framebuffer() : renderPassFramebuffer(pass)));
        if (state != null && state.glState() != null) {
            try {
                state.glState().restore();
                logRenderStateDiagnostic("render-pass-end:after-gl-restore nested=" + nested);
            } catch (RuntimeException e) {
                if (!portalRendererStateWarningLogged) {
                    portalRendererStateWarningLogged = true;
                    MainMod.LOGGER.warn("[BetterPortalsCompat] Failed to restore Better Portals render-pass GL state", e);
                }
            }
        }

        boolean nestedRenderStackEmpty = false;
        if (nested) {
            if (nestedRenderPassDepth > 0) {
                nestedRenderPassDepth--;
            }
            nestedRenderStackEmpty = nestedRenderPassDepth <= 0;
        }
        if (nested && nestedRenderStackEmpty) {
            pendingParentRenderWorld = renderPassWorld(renderPassParent(pass));
            quietDimensionReloadRequests = Math.min(quietDimensionReloadRequests + 1, 4);
        }
        if (renderPassDepth > 0) {
            renderPassDepth--;
        } else {
            nestedRenderPassDepth = 0;
            quietDimensionReloadRequests = 0;
            nestedRenderStackEmpty = nested;
        }
        if (shouldProtectBetterPortalsRenderState()) {
            restoreAfterPortalRender();
            logRenderStateDiagnostic("render-pass-end:after-normalize nested=" + nested);
        }
        logTransitionDiagnostic("render-pass-end:after", pass);
        logRenderStateDiagnostic("render-pass-end:after-pop nested=" + nested);
        return nested && nestedRenderStackEmpty;
    }

    public static void logRenderStateDiagnostic(String label) {
        if (!isInstalled()
                || renderStateDiagnosticLogs >= MAX_RENDER_STATE_DIAGNOSTIC_LOGS
                || !shouldLogRenderStateDiagnostic(label)) {
            return;
        }

        if (renderStateDiagnosticFramesRemaining > 0 && label.startsWith("pipeline:world-pass-begin")) {
            renderStateDiagnosticFramesRemaining--;
        }
        renderStateDiagnosticLogs++;
        try {
            PortalRendererGlState glState = PortalRendererGlState.capture();
            boolean portalShaders = MainMod.getClientSettingsConfig() != null
                    && MainMod.getClientSettingsConfig().portalShadersEnabled();
            MainMod.LOGGER.info("[BetterPortalsDiag] {} bpDepth={} nestedDepth={} bpStack={} currentNested={} seeThrough={} portalShadersSetting={} ausmPortalHandling={} pipeline=[{}] gl=[{}]",
                    label,
                    renderPassDepth,
                    nestedRenderPassDepth,
                    renderPassStack.size(),
                    isRenderingNestedView(),
                    isSeeThroughPortalsEnabled(),
                    portalShaders,
                    shouldUseAusmPortalShaderHandling(),
                    PipelineContext.getInstance().describeBetterPortalsDiagnostics(),
                    glState.summary());
        } catch (RuntimeException e) {
            if (!renderStateDiagnosticWarningLogged) {
                renderStateDiagnosticWarningLogged = true;
                MainMod.LOGGER.warn("[BetterPortalsDiag] Failed to capture render-state diagnostic", e);
            }
        }
    }

    public static void resetRenderStateDiagnostics() {
        renderStateDiagnosticLogs = 0;
        renderStateDiagnosticFramesRemaining = 0;
        renderStateDiagnosticWarningLogged = false;
        transitionDiagnosticLogs = 0;
    }

    private static boolean shouldLogRenderStateDiagnostic(String label) {
        if (label.contains("nested=true")) {
            return true;
        }
        if (label.startsWith("portal-renderer")) {
            return PipelineContext.getInstance().isActive()
                    || renderStateDiagnosticFramesRemaining > 0
                    || renderPassDepth > 0 && nestedRenderPassDepth > 0
                    || isRenderingNestedView()
                    || mainViewSwapHandlingDepth > 0
                    || mainViewSwapRecoveryFrames > 0;
        }
        return renderStateDiagnosticFramesRemaining > 0
                || renderPassDepth > 0 && nestedRenderPassDepth > 0
                || isRenderingNestedView()
                || mainViewSwapHandlingDepth > 0
                || mainViewSwapRecoveryFrames > 0;
    }

    private static Object renderPassFromEvent(Event event) {
        try {
            Method method = event.getClass().getMethod("getRenderPass");
            return method.invoke(event);
        } catch (ReflectiveOperationException | RuntimeException e) {
            logViewPlanReflectionFailure(e);
            return null;
        }
    }

    private static Object renderPassParent(Object pass) {
        if (pass == null) {
            return null;
        }

        try {
            return pass.getClass().getMethod("getParent").invoke(pass);
        } catch (ReflectiveOperationException | RuntimeException e) {
            logViewPlanReflectionFailure(e);
            return null;
        }
    }

    private static Object currentViewPlan() {
        if (!isInstalled() || !resolveViewPlanReflection()) {
            return null;
        }

        try {
            return currentViewPlanField.get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            logViewPlanReflectionFailure(e);
            return null;
        }
    }

    private static Object mainViewPlan() {
        if (!isInstalled() || !resolveViewPlanReflection()) {
            return null;
        }

        try {
            return mainViewPlanField.get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            logViewPlanReflectionFailure(e);
            return null;
        }
    }

    private static String describeViewPlan(Object pass) {
        if (pass == null) {
            return "null";
        }

        return pass.getClass().getName()
                + "@"
                + Integer.toHexString(System.identityHashCode(pass))
                + "{world="
                + describeRenderPassWorld(pass)
                + ", fb="
                + describeRenderPassFramebuffer(pass)
                + ", parent="
                + describeParent(pass)
                + "}";
    }

    private static String describeParent(Object pass) {
        Object parent = renderPassParent(pass);
        if (parent == null) {
            return "null";
        }
        return parent.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(parent));
    }

    private static boolean isNestedViewPlan(Object pass) {
        if (pass == null) {
            return false;
        }

        try {
            Object main = mainViewPlanField.get(null);
            if (main != null) {
                return pass != main;
            }
            return renderPassParent(pass) != null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            logViewPlanReflectionFailure(e);
            return false;
        }
    }

    private static void logNestedRenderPassState(Object pass) {
        boolean seeThrough = isSeeThroughPortalsEnabled();
        boolean portalShaders = MainMod.getClientSettingsConfig() != null
                && MainMod.getClientSettingsConfig().portalShadersEnabled();
        String framebuffer = describeRenderPassFramebuffer(pass);
        String state = describeRenderPassWorld(pass) + ":" + seeThrough + ":" + portalShaders;
        if (state.equals(lastLoggedNestedRenderState)) {
            return;
        }

        lastLoggedNestedRenderState = state;
        MainMod.LOGGER.info("[BetterPortalsCompat] Nested portal view detected: world={} framebuffer={} seeThroughPortals={} portalShaders={}",
                describeRenderPassWorld(pass), framebuffer, seeThrough, portalShaders);
    }

    private static String describeRenderPassWorld(Object pass) {
        int dimensionId = renderPassWorldDimension(pass);
        if (dimensionId != Integer.MIN_VALUE) {
            return Integer.toString(dimensionId);
        }
        if (pass == null) {
            return "unknown";
        }

        try {
            Object world = pass.getClass().getMethod("getWorld").invoke(pass);
            return world != null ? world.getClass().getName() : "null";
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "unknown";
        }
    }

    private static int renderPassWorldDimension(Object pass) {
        WorldClient world = renderPassWorld(pass);
        return world != null && world.provider != null ? world.provider.getDimension() : Integer.MIN_VALUE;
    }

    private static WorldClient renderPassWorld(Object pass) {
        if (pass == null) {
            return null;
        }

        try {
            Object world = pass.getClass().getMethod("getWorld").invoke(pass);
            if (world instanceof WorldClient) {
                return (WorldClient) world;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
        return null;
    }

    private static Framebuffer renderPassFramebuffer(Object pass) {
        if (pass == null) {
            return null;
        }

        try {
            Object framebuffer = pass.getClass().getMethod("getFramebuffer").invoke(pass);
            if (framebuffer instanceof Framebuffer) {
                return (Framebuffer) framebuffer;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
        return null;
    }

    private static Object portalConfig(String portalClassName) {
        try {
            Class<?> configClass = Class.forName(BETTER_PORTALS_CONFIG_CLASS, false, BetterPortalsCompat.class.getClassLoader());
            String fieldName = portalConfigFieldName(portalClassName);
            if (fieldName == null) {
                return null;
            }
            return configClass.getField(fieldName).get(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            logConfigReflectionFailure(e);
            return null;
        }
    }

    private static String portalConfigFieldName(String portalClassName) {
        String name = portalClassName.toLowerCase();
        if (name.contains("aether")) {
            return "aetherPortals";
        }
        if (name.contains("nether")) {
            return "netherPortals";
        }
        if (name.equals("net.minecraft.block.blockportal") || name.endsWith(".blockportal")) {
            return "netherPortals";
        }
        if (name.contains("end")) {
            return "endPortals";
        }
        if (name.contains("twilight") || name.contains("tfportal")) {
            return "twilightForestPortals";
        }
        if (name.contains("mekanism")) {
            return "mekanismPortals";
        }
        if (name.contains("abyss") || name.contains("dreadlands") || name.contains("omothol")) {
            return "abyssalcraftPortals";
        }
        if (name.contains("travelhuts") || name.contains("travel_huts")) {
            return "travelHutsPortals";
        }
        return null;
    }

    private static String describeRenderPassFramebuffer(Object pass) {
        Framebuffer framebuffer = renderPassFramebuffer(pass);
        return describeFramebuffer(framebuffer);
    }

    private static String describeFramebuffer(Framebuffer framebuffer) {
        if (framebuffer == null) {
            return "null";
        }
        return framebuffer.framebufferObject
                + "("
                + framebuffer.framebufferWidth
                + "x"
                + framebuffer.framebufferHeight
                + ")";
    }

    private static int dimensionId(WorldClient world) {
        return world != null && world.provider != null ? world.provider.getDimension() : Integer.MIN_VALUE;
    }

    private static String externalCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement frame : stack) {
            String className = frame.getClassName();
            if (className.equals(Thread.class.getName()) || className.equals(BetterPortalsCompat.class.getName())) {
                continue;
            }
            return className + "#" + frame.getMethodName() + ":" + frame.getLineNumber();
        }
        return "unknown";
    }

    private static String hex(int value) {
        return "0x" + Integer.toHexString(value);
    }

    private static void setCapability(int capability, boolean enabled) {
        if (enabled) {
            GL11.glEnable(capability);
        } else {
            GL11.glDisable(capability);
        }
    }

    private static boolean glBoolean(int parameter) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(1);
        GL11.glGetBoolean(parameter, buffer);
        return buffer.get(0) != 0;
    }

    private static boolean[] glBoolean4(int parameter) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(4);
        GL11.glGetBoolean(parameter, buffer);
        return new boolean[]{
                buffer.get(0) != 0,
                buffer.get(1) != 0,
                buffer.get(2) != 0,
                buffer.get(3) != 0
        };
    }

    private static float[] glFloat4(int parameter) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(4);
        GL11.glGetFloat(parameter, buffer);
        return new float[]{buffer.get(0), buffer.get(1), buffer.get(2), buffer.get(3)};
    }

    private static int[] glInt4(int parameter) {
        IntBuffer buffer = BufferUtils.createIntBuffer(4);
        GL11.glGetInteger(parameter, buffer);
        return new int[]{buffer.get(0), buffer.get(1), buffer.get(2), buffer.get(3)};
    }

    private static final class RenderPassState {
        private final boolean nested;
        private final WorldClient world;
        private final int dimensionId;
        private final Framebuffer framebuffer;
        private final PortalRendererGlState glState;

        private RenderPassState(boolean nested, WorldClient world, int dimensionId, Framebuffer framebuffer, PortalRendererGlState glState) {
            this.nested = nested;
            this.world = world;
            this.dimensionId = dimensionId;
            this.framebuffer = framebuffer;
            this.glState = glState;
        }

        private boolean nested() {
            return nested;
        }

        private WorldClient world() {
            return world;
        }

        private int dimensionId() {
            return dimensionId;
        }

        private Framebuffer framebuffer() {
            return framebuffer;
        }

        private PortalRendererGlState glState() {
            return glState;
        }
    }

    private static final class PortalRendererGlState {
        private final boolean fullCapture;
        private final int program;
        private final int activeTexture;
        private final int[] texture2dBindings;
        private final boolean[] texture2dEnabled;
        private final int framebuffer;
        private final int readFramebuffer;
        private final int drawFramebuffer;
        private final int drawBuffer;
        private final int readBuffer;
        private final int[] viewport;
        private final int[] scissorBox;
        private final boolean texture2d;
        private final boolean depthTest;
        private final boolean depthMask;
        private final int depthFunc;
        private final boolean alphaTest;
        private final int alphaFunc;
        private final float alphaRef;
        private final boolean blend;
        private final int blendSrcRgb;
        private final int blendDstRgb;
        private final int blendSrcAlpha;
        private final int blendDstAlpha;
        private final boolean cullFace;
        private final int cullFaceMode;
        private final boolean lighting;
        private final boolean colorMaterial;
        private final boolean stencilTest;
        private final int stencilFunc;
        private final int stencilRef;
        private final int stencilValueMask;
        private final int stencilWriteMask;
        private final int stencilFail;
        private final int stencilPassDepthFail;
        private final int stencilPassDepthPass;
        private final boolean scissorTest;
        private final boolean[] clipPlanes;
        private final boolean polygonOffsetFill;
        private final float polygonOffsetFactor;
        private final float polygonOffsetUnits;
        private final boolean[] colorMask;
        private final float[] color;
        private final int matrixMode;
        private final int shadeModel;

        private PortalRendererGlState(boolean fullCapture) {
            this.fullCapture = fullCapture;
            program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            framebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            drawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
            readBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
            viewport = glInt4(GL11.GL_VIEWPORT);
            scissorBox = glInt4(GL11.GL_SCISSOR_BOX);
            depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            depthMask = glBoolean(GL11.GL_DEPTH_WRITEMASK);
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
                colorMask = glBoolean4(GL11.GL_COLOR_WRITEMASK);
                color = glFloat4(GL11.GL_CURRENT_COLOR);
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

        private static PortalRendererGlState capture() {
            return new PortalRendererGlState(true);
        }

        private static PortalRendererGlState captureRenderPass() {
            return new PortalRendererGlState(false);
        }

        private String summary() {
            return "program=" + program
                    + " activeTex=" + textureUnitIndex(activeTexture)
                    + " fbo=" + framebuffer
                    + " readFb=" + readFramebuffer
                    + " drawFb=" + drawFramebuffer
                    + " drawBuf=" + hex(drawBuffer)
                    + " readBuf=" + hex(readBuffer)
                    + " viewport=" + Arrays.toString(viewport)
                    + " scissor=" + Arrays.toString(scissorBox)
                    + " depth=" + depthTest + "/" + depthMask + "/" + hex(depthFunc)
                    + " alpha=" + alphaTest + "/" + hex(alphaFunc) + "/" + alphaRef
                    + " blend=" + blend + "/" + hex(blendSrcRgb) + "," + hex(blendDstRgb) + "," + hex(blendSrcAlpha) + "," + hex(blendDstAlpha)
                    + " cull=" + cullFace + "/" + hex(cullFaceMode)
                    + " light=" + lighting
                    + " colorMat=" + colorMaterial
                    + " stencil=" + stencilTest + "/" + hex(stencilFunc) + "/" + stencilRef + "/" + hex(stencilValueMask) + "/" + hex(stencilWriteMask)
                    + " scissorTest=" + scissorTest
                    + " clip=" + Arrays.toString(clipPlanes)
                    + " poly=" + polygonOffsetFill + "/" + polygonOffsetFactor + "," + polygonOffsetUnits
                    + " colorMask=" + Arrays.toString(colorMask)
                    + " color=" + Arrays.toString(color)
                    + " matrix=" + hex(matrixMode)
                    + " shade=" + hex(shadeModel)
                    + " tex=" + textureSummary();
        }

        private String textureSummary() {
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

        private static int textureUnitIndex(int textureUnit) {
            return textureUnit >= GL13.GL_TEXTURE0 ? textureUnit - GL13.GL_TEXTURE0 : textureUnit;
        }

        private void restore() {
            OpenGlHelper.glUseProgram(program);
            if (fullCapture) {
                restoreTextureBindings(activeTexture, texture2dBindings, texture2dEnabled);
            } else if (isVanillaGlStateTextureUnit(activeTexture)) {
                GlStateManager.setActiveTexture(activeTexture);
                GL13.glActiveTexture(activeTexture);
            } else {
                TextureBinder.restoreDefaultTextureUnit();
            }
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            if (readFramebuffer == drawFramebuffer && readFramebuffer == framebuffer) {
                OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
            }
            GL11.glDrawBuffer(drawBuffer);
            GL11.glReadBuffer(readBuffer);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GL11.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
            setCapability(GL11.GL_DEPTH_TEST, depthTest);
            GL11.glDepthMask(depthMask);
            GL11.glDepthFunc(depthFunc);
            setCapability(GL11.GL_ALPHA_TEST, alphaTest);
            GL11.glAlphaFunc(alphaFunc, alphaRef);
            setCapability(GL11.GL_BLEND, blend);
            GlStateManager.tryBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
            setCapability(GL11.GL_CULL_FACE, cullFace);
            GL11.glCullFace(cullFaceMode);
            setCapability(GL11.GL_SCISSOR_TEST, scissorTest);
            if (fullCapture) {
                setCapability(GL11.GL_TEXTURE_2D, texture2d);
                setCapability(GL11.GL_LIGHTING, lighting);
                setCapability(GL11.GL_COLOR_MATERIAL, colorMaterial);
                setCapability(GL11.GL_STENCIL_TEST, stencilTest);
                GL11.glStencilMask(stencilWriteMask);
                GL11.glStencilFunc(stencilFunc, stencilRef, stencilValueMask);
                GL11.glStencilOp(stencilFail, stencilPassDepthFail, stencilPassDepthPass);
                for (int i = 0; i < clipPlanes.length; i++) {
                    setCapability(GL11.GL_CLIP_PLANE0 + i, clipPlanes[i]);
                }
                setCapability(GL11.GL_POLYGON_OFFSET_FILL, polygonOffsetFill);
                GL11.glPolygonOffset(polygonOffsetFactor, polygonOffsetUnits);
                GL11.glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
                GL11.glColor4f(color[0], color[1], color[2], color[3]);
                GL11.glShadeModel(shadeModel);
                GL11.glMatrixMode(matrixMode);
            }
        }

        private static void captureTextureBindings(int previousActiveTexture, int[] bindings, boolean[] enabled) {
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

        private static int capturedTextureUnitCount() {
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

        private static void restoreTextureBindings(int activeTexture, int[] bindings, boolean[] enabled) {
            try {
                for (int i = 0; i < bindings.length; i++) {
                    int unit = GL13.GL_TEXTURE0 + i;
                    GL13.glActiveTexture(unit);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, bindings[i]);
                    setCapability(GL11.GL_TEXTURE_2D, enabled[i]);
                }
            } finally {
                if (isVanillaGlStateTextureUnit(activeTexture)) {
                    GlStateManager.setActiveTexture(activeTexture);
                    GL13.glActiveTexture(activeTexture);
                } else {
                    TextureBinder.restoreDefaultTextureUnit();
                }
            }
        }

        private static boolean isVanillaGlStateTextureUnit(int unit) {
            return unit >= GL13.GL_TEXTURE0 && unit < GL13.GL_TEXTURE0 + VANILLA_GL_STATE_TEXTURE_UNITS;
        }
    }

    private static boolean resolveViewPlanReflection() {
        if (viewPlanReflectionResolved) {
            return !viewPlanReflectionFailed;
        }

        viewPlanReflectionResolved = true;
        try {
            Class<?> viewRenderPlanClass = Class.forName(VIEW_RENDER_PLAN_CLASS, false, BetterPortalsCompat.class.getClassLoader());
            currentViewPlanField = viewRenderPlanClass.getDeclaredField("CURRENT");
            currentViewPlanField.setAccessible(true);
            mainViewPlanField = viewRenderPlanClass.getDeclaredField("MAIN");
            mainViewPlanField.setAccessible(true);
            renderPassParentMethod = viewRenderPlanClass.getMethod("getParent");
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            logViewPlanReflectionFailure(e);
            return false;
        }
    }

    private static Class<?> portalEntityClass() {
        if (portalEntityClassResolved) {
            return portalEntityClass;
        }

        portalEntityClassResolved = true;
        try {
            portalEntityClass = Class.forName(PORTAL_ENTITY_CLASS, false, BetterPortalsCompat.class.getClassLoader());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            portalEntityClass = null;
        }
        return portalEntityClass;
    }

    private static boolean resolveConfigReflection() {
        if (configReflectionResolved) {
            return !configReflectionFailed;
        }

        configReflectionResolved = true;
        try {
            Class<?> configClass = Class.forName(BETTER_PORTALS_CONFIG_CLASS, false, BetterPortalsCompat.class.getClassLoader());
            seeThroughPortalsField = configClass.getDeclaredField("seeThroughPortals");
            seeThroughPortalsField.setAccessible(true);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            logConfigReflectionFailure(e);
            return false;
        }
    }

    private static boolean classPresent(String className) {
        try {
            Class.forName(className, false, BetterPortalsCompat.class.getClassLoader());
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private static void logViewPlanReflectionFailure(Throwable throwable) {
        if (!viewPlanReflectionFailed) {
            viewPlanReflectionFailed = true;
            MainMod.LOGGER.warn("[BetterPortalsCompat] Better Portals view detection unavailable; nested views will use AUSM normally", throwable);
        }
    }

    private static void logConfigReflectionFailure(Throwable throwable) {
        if (!configReflectionFailed) {
            configReflectionFailed = true;
            MainMod.LOGGER.warn("[BetterPortalsCompat] Better Portals config detection unavailable; portal shader views will be disabled", throwable);
        }
    }
}
