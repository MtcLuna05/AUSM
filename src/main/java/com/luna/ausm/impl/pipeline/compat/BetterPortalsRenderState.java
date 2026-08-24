package com.luna.ausm.impl.pipeline.compat;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.pipeline.render.TextureBinder;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.block.Block;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.Event;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

abstract class BetterPortalsRenderState extends BetterPortalsCompatBase {
    public static boolean isInstalled() {
        if (installed == null) {
            installed = Loader.isModLoaded(MOD_ID) || BetterPortalsCompat.classPresent(BETTER_PORTALS_MOD_CLASS);
        }
        return installed;
    }

    public static boolean isRenderingNestedView() {
        if (!BetterPortalsCompat.isInstalled()) {
            return false;
        }

        if (nestedRenderPassDepth > 0) {
            return true;
        }

        if (!BetterPortalsCompat.resolveViewPlanReflection()) {
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
            BetterPortalsCompat.logViewPlanReflectionFailure(e);
            return false;
        }
    }

    public static boolean isRenderingRenderPass() {
        if (!BetterPortalsCompat.isInstalled()) {
            return false;
        }
        RenderPassState state = renderPassStack.peek();
        return state != null && state.nested() && (state.world() != null || state.framebuffer() != null);
    }

    public static boolean isPortalEntity(Entity entity) {
        if (entity == null || !BetterPortalsCompat.isInstalled()) {
            return false;
        }

        Class<?> portalEntityType = BetterPortalsCompat.portalEntityClass();
        if (portalEntityType != null && portalEntityType.isInstance(entity)) {
            return true;
        }

        String className = entity.getClass().getName();
        return className.startsWith("de.johni0702.minecraft.betterportals.")
                && className.contains("Portal")
                && className.endsWith("Entity");
    }

    public static boolean isSeeThroughPortalsEnabled() {
        if (!BetterPortalsCompat.isInstalled() || !BetterPortalsCompat.resolveConfigReflection()) {
            return false;
        }

        try {
            return seeThroughPortalsField.getBoolean(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            BetterPortalsCompat.logConfigReflectionFailure(e);
            return false;
        }
    }

    public static boolean shouldRenderNestedViewWithShaders() {
        boolean configured = BetterPortalsCompat.isInstalled()
                && BetterPortalsCompat.isSeeThroughPortalsEnabled()
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
        return BetterPortalsCompat.isInstalled()
                && BetterPortalsCompat.isSeeThroughPortalsEnabled()
                && BetterPortalsCompat.isRenderingNestedView()
                && !BetterPortalsCompat.shouldRenderNestedViewWithShaders();
    }

    public static boolean shouldUseAusmPortalShaderHandling() {
        return BetterPortalsCompat.shouldRenderNestedViewWithShaders();
    }

    protected static boolean shouldProtectBetterPortalsRenderState() {
        return BetterPortalsCompat.isInstalled() && BetterPortalsCompat.isSeeThroughPortalsEnabled();
    }

    public static int currentShaderRenderPassDimensionId() {
        RenderPassState state = renderPassStack.peek();
        if (!BetterPortalsCompat.shouldRenderNestedViewWithShaders()) {
            return Integer.MIN_VALUE;
        }
        if (state != null && state.nested()) {
            return state.dimensionId();
        }

        Object current = BetterPortalsCompat.currentViewPlan();
        if (BetterPortalsCompat.isNestedViewPlan(current)) {
            return BetterPortalsCompat.renderPassWorldDimension(current);
        }
        return Integer.MIN_VALUE;
    }

    public static Framebuffer currentShaderRenderPassFramebuffer() {
        RenderPassState state = renderPassStack.peek();
        if (!BetterPortalsCompat.shouldRenderNestedViewWithShaders()) {
            return null;
        }
        if (state != null && state.nested()) {
            return state.framebuffer();
        }

        Object current = BetterPortalsCompat.currentViewPlan();
        return BetterPortalsCompat.isNestedViewPlan(current) ? BetterPortalsCompat.renderPassFramebuffer(current) : null;
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

        Object current = BetterPortalsCompat.currentViewPlan();
        return current != null ? BetterPortalsCompat.renderPassWorld(current) : null;
    }

    public static Framebuffer currentRenderPassFramebuffer() {
        RenderPassState state = renderPassStack.peek();
        if (state != null) {
            return state.framebuffer();
        }

        Object current = BetterPortalsCompat.currentViewPlan();
        return current != null ? BetterPortalsCompat.renderPassFramebuffer(current) : null;
    }

    public static void startMainViewSwapRecovery(WorldClient world) {
        if (!BetterPortalsCompat.isInstalled()) {
            return;
        }

        int dimensionId = world != null && MinecraftReflectionCompat.worldProvider(world) != null
                ? MinecraftReflectionCompat.providerDimension(MinecraftReflectionCompat.worldProvider(world))
                : Integer.MIN_VALUE;
        mainViewSwapRecoveryFrames = Math.max(mainViewSwapRecoveryFrames, MAIN_VIEW_SWAP_RECOVERY_FRAMES);
        mainViewSwapRecoveryDimensionId = dimensionId;
        mainViewSwapRecoveryLogged = false;
    }

    public static void keepMainViewSwapRecoveryAlive(WorldClient world) {
        if (!BetterPortalsCompat.isMainViewSwapRecoveryActive()) {
            return;
        }
        BetterPortalsCompat.startMainViewSwapRecovery(world);
    }

    public static void beginMainViewSwapHandling() {
        if (BetterPortalsCompat.isInstalled()) {
            mainViewSwapHandlingDepth++;
        }
    }

    public static void endMainViewSwapHandling() {
        if (mainViewSwapHandlingDepth > 0) {
            mainViewSwapHandlingDepth--;
        }
    }

    public static boolean isMainViewSwapHandling() {
        return BetterPortalsCompat.isInstalled() && mainViewSwapHandlingDepth > 0;
    }

    public static void clearMainViewSwapTransientState() {
        if (!BetterPortalsCompat.isInstalled()) {
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
        if (!BetterPortalsCompat.isInstalled()) {
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
        return BetterPortalsCompat.isInstalled() && mainViewSwapRecoveryFrames > 0;
    }

    public static int mainViewSwapRecoveryDimensionId() {
        return BetterPortalsCompat.isMainViewSwapRecoveryActive() ? mainViewSwapRecoveryDimensionId : Integer.MIN_VALUE;
    }

    public static String describeTransitionState() {
        if (!BetterPortalsCompat.isInstalled()) {
            return "installed=false";
        }

        Object current = BetterPortalsCompat.currentViewPlan();
        Object main = BetterPortalsCompat.mainViewPlan();
        return "passDepth=" + renderPassDepth
                + " nestedDepth=" + nestedRenderPassDepth
                + " stack=" + renderPassStack.size()
                + " rendererStack=" + portalRendererStateStack.size()
                + " handlingDepth=" + mainViewSwapHandlingDepth
                + " recoveryFrames=" + mainViewSwapRecoveryFrames
                + " recoveryDim=" + mainViewSwapRecoveryDimensionId
                + " quietReloads=" + quietDimensionReloadRequests
                + " pendingParent=" + BetterPortalsCompat.dimensionId(pendingParentRenderWorld)
                + " current=" + BetterPortalsCompat.describeViewPlan(current)
                + " main=" + BetterPortalsCompat.describeViewPlan(main)
                + " currentIsMain=" + (current != null && current == main)
                + " currentNested=" + BetterPortalsCompat.isRenderingNestedView()
                + " renderPass=" + BetterPortalsCompat.isRenderingRenderPass()
                + " seeThrough=" + BetterPortalsCompat.isSeeThroughPortalsEnabled();
    }

    public static void logTransitionDiagnostic(String stage, Object viewPlan) {
        if (!BetterPortalsCompat.isInstalled() || transitionDiagnosticLogs >= MAX_TRANSITION_DIAGNOSTIC_LOGS) {
            return;
        }
        transitionDiagnosticLogs++;
        MainMod.LOGGER.info("[BetterPortalsTransition] call={} stage={} view={} state={} caller={}",
                transitionDiagnosticLogs,
                stage,
                BetterPortalsCompat.describeViewPlan(viewPlan),
                BetterPortalsCompat.describeTransitionState(),
                BetterPortalsCompat.externalCaller());
    }

    public static void logMainViewSwapRecoveryIfNeeded(WorldClient world) {
        if (!BetterPortalsCompat.isMainViewSwapRecoveryActive() || mainViewSwapRecoveryLogged) {
            return;
        }
        mainViewSwapRecoveryLogged = true;
        int dimensionId = world != null && MinecraftReflectionCompat.worldProvider(world) != null
                ? MinecraftReflectionCompat.providerDimension(MinecraftReflectionCompat.worldProvider(world))
                : mainViewSwapRecoveryDimensionId;
        MainMod.LOGGER.info("[BetterPortalsCompat] Main view swap recovery active: world={} frames={}",
                dimensionId,
                mainViewSwapRecoveryFrames);
    }

    public static float portalOpacity(Object portal) {
        if (portal == null) {
            return 1.0F;
        }

        return BetterPortalsCompat.portalOpacityForClassName(portal.getClass().getName());
    }

    public static boolean isBetterPortalsPortalBlock(Block block) {
        if (block == null) {
            return false;
        }
        Class<?> type = block.getClass();
        Boolean cached = PORTAL_BLOCK_CLASSES.get(type);
        if (cached != null) {
            return cached;
        }
        boolean portal = BetterPortalsCompat.isBetterPortalsPortalBlockClass(type);
        Boolean existing = PORTAL_BLOCK_CLASSES.putIfAbsent(type, portal);
        return existing != null ? existing : portal;
    }

    protected static boolean isBetterPortalsPortalBlockClass(Class<?> type) {
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

    protected static float portalOpacityForClassName(String portalClassName) {
        if (!BetterPortalsCompat.resolveConfigReflection() || portalClassName == null) {
            return 1.0F;
        }

        Object config = BetterPortalsCompat.portalConfig(portalClassName);
        if (config == null) {
            return 1.0F;
        }

        try {
            Field opacityField = config.getClass().getField("opacity");
            double opacity = opacityField.getDouble(config);
            if (!Double.isFinite(opacity)) {
                return 1.0F;
            }
            return (float) Math.clamp(opacity, 0.0D, 1.0D);
        } catch (ReflectiveOperationException | RuntimeException e) {
            BetterPortalsCompat.logConfigReflectionFailure(e);
            return 1.0F;
        }
    }

    public static boolean handleRenderPassEvent(Event event) {
        if (!BetterPortalsCompat.isInstalled() || event == null) {
            return false;
        }

        String eventClassName = event.getClass().getName();
        if (!eventClassName.startsWith(RENDER_PASS_EVENT_PREFIX)) {
            return false;
        }

        if (eventClassName.endsWith("$Start")) {
            BetterPortalsCompat.handleRenderPassStart(event);
            return false;
        } else if (eventClassName.endsWith("$End")) {
            return BetterPortalsCompat.handleRenderPassEnd(event);
        }
        return false;
    }

    public static void pushPortalRendererState() {
        if (!BetterPortalsCompat.shouldProtectBetterPortalsRenderState()) {
            return;
        }

        BetterPortalsCompat.logRenderStateDiagnostic("portal-renderer:before-capture");
        try {
            portalRendererStateStack.push(PortalRendererGlState.capture());
            BetterPortalsCompat.logRenderStateDiagnostic("portal-renderer:after-capture stack=" + portalRendererStateStack.size());
        } catch (RuntimeException e) {
            if (!portalRendererStateWarningLogged) {
                portalRendererStateWarningLogged = true;
                MainMod.LOGGER.warn("[BetterPortalsCompat] Failed to capture Better Portals renderer GL state", e);
            }
        }
    }

    public static void popPortalRendererState() {
        if (!BetterPortalsCompat.isInstalled()) {
            return;
        }

        PortalRendererGlState state = portalRendererStateStack.poll();
        if (state == null) {
            return;
        }

        BetterPortalsCompat.logRenderStateDiagnostic("portal-renderer:before-restore stack=" + portalRendererStateStack.size());
        try {
            state.restore();
            BetterPortalsCompat.logRenderStateDiagnostic("portal-renderer:after-restore stack=" + portalRendererStateStack.size());
            BetterPortalsCompat.restoreAfterPortalRender();
            BetterPortalsCompat.logRenderStateDiagnostic("portal-renderer:after-normalize stack=" + portalRendererStateStack.size());
        } catch (RuntimeException e) {
            if (!portalRendererStateWarningLogged) {
                portalRendererStateWarningLogged = true;
                MainMod.LOGGER.warn("[BetterPortalsCompat] Failed to restore Better Portals renderer GL state", e);
            }
            BetterPortalsCompat.restoreAfterPortalRender();
        }
    }

    public static void markPortalSurfaceCompositeHandled(Framebuffer framebuffer) {
        if (framebuffer == null || portalSurfaceCompositeLogged) {
            return;
        }
        portalSurfaceCompositeLogged = true;
        MainMod.LOGGER.info("[BetterPortalsCompat] AUSM portal surface composite: framebuffer={} size={}x{} seeThroughPortals={}",
                MinecraftReflectionCompat.framebufferObject(framebuffer),
                MinecraftReflectionCompat.framebufferWidth(framebuffer),
                MinecraftReflectionCompat.framebufferHeight(framebuffer),
                BetterPortalsCompat.isSeeThroughPortalsEnabled());
    }

    protected static void restoreAfterPortalRender() {
        if (!BetterPortalsCompat.isInstalled()) {
            return;
        }

        MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        MinecraftReflectionCompat.glStateEnableTexture2D();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        MinecraftReflectionCompat.glStateEnableDepth();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        MinecraftReflectionCompat.glStateEnableAlpha();
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        MinecraftReflectionCompat.glStateDisableLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        MinecraftReflectionCompat.glStateDisableColorMaterial();
        GL11.glDisable(GL11.GL_COLOR_MATERIAL);
        MinecraftReflectionCompat.glStateDisableBlend();
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

    protected static void handleRenderPassStart(Event event) {
        Object pass = BetterPortalsCompat.renderPassFromEvent(event);
        BetterPortalsCompat.logTransitionDiagnostic("render-pass-start:before", pass);
        boolean nested = BetterPortalsCompat.renderPassParent(pass) != null;
        WorldClient world = BetterPortalsCompat.renderPassWorld(pass);
        int dimensionId = BetterPortalsCompat.renderPassWorldDimension(pass);
        Framebuffer framebuffer = BetterPortalsCompat.renderPassFramebuffer(pass);
        PortalRendererGlState glState = null;
        if (nested) {
            renderStateDiagnosticFramesRemaining = Math.max(
                    renderStateDiagnosticFramesRemaining,
                    RENDER_STATE_DIAGNOSTIC_FRAMES_AFTER_NESTED
            );
            if (dimensionId != Integer.MIN_VALUE
                    && BetterPortalsCompat.shouldRenderNestedViewWithShaders()
                    && MainMod.getShaderPackManager() != null) {
                MainMod.getShaderPackManager().scheduleBetterPortalsDimensionPrewarm(dimensionId);
            }
        }
        BetterPortalsCompat.logRenderStateDiagnostic("render-pass-start:before nested=" + nested
                + " world=" + dimensionId
                + " fb=" + BetterPortalsCompat.describeFramebuffer(framebuffer));
        if (BetterPortalsCompat.shouldProtectBetterPortalsRenderState()) {
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
            BetterPortalsCompat.logNestedRenderPassState(pass);
        }
        BetterPortalsCompat.logTransitionDiagnostic("render-pass-start:after", pass);
        BetterPortalsCompat.logRenderStateDiagnostic("render-pass-start:after nested=" + nested
                + " world=" + dimensionId
                + " fb=" + BetterPortalsCompat.describeFramebuffer(framebuffer));
    }

    protected static boolean handleRenderPassEnd(Event event) {
        Object pass = BetterPortalsCompat.renderPassFromEvent(event);
        BetterPortalsCompat.logTransitionDiagnostic("render-pass-end:before", pass);
        RenderPassState state = renderPassStack.poll();
        boolean nested = state != null ? state.nested() : BetterPortalsCompat.renderPassParent(pass) != null;

        BetterPortalsCompat.logRenderStateDiagnostic("render-pass-end:before-restore nested=" + nested
                + " world=" + (state != null ? state.dimensionId() : BetterPortalsCompat.renderPassWorldDimension(pass))
                + " fb=" + BetterPortalsCompat.describeFramebuffer(state != null ? state.framebuffer() : BetterPortalsCompat.renderPassFramebuffer(pass)));
        if (state != null && state.glState() != null) {
            try {
                state.glState().restore();
                BetterPortalsCompat.logRenderStateDiagnostic("render-pass-end:after-gl-restore nested=" + nested);
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
            pendingParentRenderWorld = BetterPortalsCompat.renderPassWorld(BetterPortalsCompat.renderPassParent(pass));
            quietDimensionReloadRequests = Math.min(quietDimensionReloadRequests + 1, 4);
        }
        if (renderPassDepth > 0) {
            renderPassDepth--;
        } else {
            nestedRenderPassDepth = 0;
            quietDimensionReloadRequests = 0;
            nestedRenderStackEmpty = nested;
        }
        if (BetterPortalsCompat.shouldProtectBetterPortalsRenderState()) {
            BetterPortalsCompat.restoreAfterPortalRender();
            BetterPortalsCompat.logRenderStateDiagnostic("render-pass-end:after-normalize nested=" + nested);
        }
        BetterPortalsCompat.logTransitionDiagnostic("render-pass-end:after", pass);
        BetterPortalsCompat.logRenderStateDiagnostic("render-pass-end:after-pop nested=" + nested);
        return nested && nestedRenderStackEmpty;
    }

    public static void logRenderStateDiagnostic(String label) {
        if (!BetterPortalsCompat.isInstalled()
                || renderStateDiagnosticLogs >= MAX_RENDER_STATE_DIAGNOSTIC_LOGS
                || !BetterPortalsCompat.shouldLogRenderStateDiagnostic(label)) {
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
                    BetterPortalsCompat.isRenderingNestedView(),
                    BetterPortalsCompat.isSeeThroughPortalsEnabled(),
                    portalShaders,
                    BetterPortalsCompat.shouldUseAusmPortalShaderHandling(),
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

    protected static boolean shouldLogRenderStateDiagnostic(String label) {
        if (label.contains("nested=true")) {
            return true;
        }
        if (label.startsWith("portal-renderer")) {
            return PipelineContext.getInstance().isActive()
                    || renderStateDiagnosticFramesRemaining > 0
                    || renderPassDepth > 0 && nestedRenderPassDepth > 0
                    || BetterPortalsCompat.isRenderingNestedView()
                    || mainViewSwapHandlingDepth > 0
                    || mainViewSwapRecoveryFrames > 0;
        }
        return renderStateDiagnosticFramesRemaining > 0
                || renderPassDepth > 0 && nestedRenderPassDepth > 0
                || BetterPortalsCompat.isRenderingNestedView()
                || mainViewSwapHandlingDepth > 0
                || mainViewSwapRecoveryFrames > 0;
    }

    protected static Object renderPassFromEvent(Event event) {
        try {
            Method method = event.getClass().getMethod("getRenderPass");
            return method.invoke(event);
        } catch (ReflectiveOperationException | RuntimeException e) {
            BetterPortalsCompat.logViewPlanReflectionFailure(e);
            return null;
        }
    }

    protected static Object renderPassParent(Object pass) {
        if (pass == null) {
            return null;
        }

        try {
            return pass.getClass().getMethod("getParent").invoke(pass);
        } catch (ReflectiveOperationException | RuntimeException e) {
            BetterPortalsCompat.logViewPlanReflectionFailure(e);
            return null;
        }
    }

    protected static Object currentViewPlan() {
        if (!BetterPortalsCompat.isInstalled() || !BetterPortalsCompat.resolveViewPlanReflection()) {
            return null;
        }

        try {
            return currentViewPlanField.get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            BetterPortalsCompat.logViewPlanReflectionFailure(e);
            return null;
        }
    }

    protected static Object mainViewPlan() {
        if (!BetterPortalsCompat.isInstalled() || !BetterPortalsCompat.resolveViewPlanReflection()) {
            return null;
        }

        try {
            return mainViewPlanField.get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            BetterPortalsCompat.logViewPlanReflectionFailure(e);
            return null;
        }
    }

    protected static String describeViewPlan(Object pass) {
        if (pass == null) {
            return "null";
        }

        return pass.getClass().getName()
                + "@"
                + Integer.toHexString(System.identityHashCode(pass))
                + "{world="
                + BetterPortalsCompat.describeRenderPassWorld(pass)
                + ", fb="
                + BetterPortalsCompat.describeRenderPassFramebuffer(pass)
                + ", parent="
                + BetterPortalsCompat.describeParent(pass)
                + "}";
    }

    protected static String describeParent(Object pass) {
        Object parent = BetterPortalsCompat.renderPassParent(pass);
        if (parent == null) {
            return "null";
        }
        return parent.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(parent));
    }

    protected static boolean isNestedViewPlan(Object pass) {
        if (pass == null) {
            return false;
        }

        try {
            Object main = mainViewPlanField.get(null);
            if (main != null) {
                return pass != main;
            }
            return BetterPortalsCompat.renderPassParent(pass) != null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            BetterPortalsCompat.logViewPlanReflectionFailure(e);
            return false;
        }
    }

    protected static void logNestedRenderPassState(Object pass) {
        boolean seeThrough = BetterPortalsCompat.isSeeThroughPortalsEnabled();
        boolean portalShaders = MainMod.getClientSettingsConfig() != null
                && MainMod.getClientSettingsConfig().portalShadersEnabled();
        String framebuffer = BetterPortalsCompat.describeRenderPassFramebuffer(pass);
        String state = BetterPortalsCompat.describeRenderPassWorld(pass) + ":" + seeThrough + ":" + portalShaders;
        if (state.equals(lastLoggedNestedRenderState)) {
            return;
        }

        lastLoggedNestedRenderState = state;
        MainMod.LOGGER.info("[BetterPortalsCompat] Nested portal view detected: world={} framebuffer={} seeThroughPortals={} portalShaders={}",
                BetterPortalsCompat.describeRenderPassWorld(pass), framebuffer, seeThrough, portalShaders);
    }

    protected static String describeRenderPassWorld(Object pass) {
        int dimensionId = BetterPortalsCompat.renderPassWorldDimension(pass);
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

    protected static int renderPassWorldDimension(Object pass) {
        WorldClient world = BetterPortalsCompat.renderPassWorld(pass);
        return world != null && MinecraftReflectionCompat.worldProvider(world) != null ? MinecraftReflectionCompat.providerDimension(MinecraftReflectionCompat.worldProvider(world)) : Integer.MIN_VALUE;
    }

    protected static WorldClient renderPassWorld(Object pass) {
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
}
