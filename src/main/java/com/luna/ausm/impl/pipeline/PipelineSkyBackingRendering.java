package com.luna.ausm.impl.pipeline;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.DEBUG_PROBES_ENABLED;

abstract class PipelineSkyBackingRendering extends PipelineBloomRendering {
    public boolean shouldUseShaderlessSkyOverride() {
        return ShaderlessSkyRenderer.shouldReplaceVanillaSky(isPipelineActive);
    }

    public void renderShaderlessSkyOverride() {
        ShaderlessSkyRenderer.renderSky();
    }

    public void renderShaderlessBotaniaSkyBacking(float partialTicks, WorldClient world, Minecraft mc) {
        if (isPipelineActive || mc == null || world == null || !self().isSimpleVoidWorld(world)) {
            return;
        }
        Vec3d skyColor = MinecraftReflectionCompat.call(
                world,
                Vec3d.class,
                null,
                new String[]{"func_72833_a", "getSkyColor"},
                new Class<?>[]{Entity.class, float.class},
                MinecraftReflectionCompat.renderViewEntity(mc),
                partialTicks
        );
        if (skyColor == null || MinecraftReflectionCompat.minecraftFramebuffer(mc) == null) {
            return;
        }
        bindMinecraftFramebufferForGui(mc);
        self().drawOwnedSkyBackingGradient(
                MinecraftReflectionCompat.framebufferWidth(MinecraftReflectionCompat.minecraftFramebuffer(mc)),
                MinecraftReflectionCompat.framebufferHeight(MinecraftReflectionCompat.minecraftFramebuffer(mc)),
                skyColor,
                mc
        );
    }

    public void renderShaderedOwnedVoidSkyBase(WorldClient world, Minecraft mc) {
        if (!isPipelineActive
                || mc == null
                || world == null
                || !self().isSimpleVoidWorld(world)
                || !self().isCustomVoidWorldSkyEnabled(world)
                || self().isRenderingBetterPortalsNestedView()
                || self().isRenderingBetterPortalsRenderPass()) {
            return;
        }
        self().renderShaderedSkyBaseQuad();
    }

    /**
     * Gives every shadered overworld-like dimension a continuous base sky
     * before vanilla submits its upper dome, stars, and celestial geometry.
     * The lower vanilla dome is deliberately not used: it carries the raw
     * gray Minecraft sky colour and overwrites the shader-authored horizon.
     */
    public void renderShaderedSkyBaseBacking() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        World world = mc == null ? null : MinecraftReflectionCompat.world(mc);
        if (!isPipelineActive
                || mc == null
                || world == null
                || !self().shouldUseOwnedSkyOverrideWorld(world)
                || self().isRenderingBetterPortalsNestedView()
                || self().isRenderingBetterPortalsRenderPass()) {
            return;
        }
        self().renderShaderedSkyBaseQuad();
    }

    protected void renderShaderedSkyBaseQuad() {
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int previousDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean previousDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean previousBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean previousCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean previousScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        ByteBuffer previousColorMask = BufferUtils.createByteBuffer(4);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, previousColorMask);
        boolean pushedProjection = false;
        boolean pushedModelView = false;
        try {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            MinecraftReflectionCompat.glStateDisableBlend();
            MinecraftReflectionCompat.glStateDisableCull();
            MinecraftReflectionCompat.glStateDisableDepth();
            MinecraftReflectionCompat.glStateDepthMask(false);
            GL11.glDepthFunc(GL11.GL_ALWAYS);

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            pushedProjection = true;
            GL11.glLoadIdentity();
            GL11.glOrtho(-1.0D, 1.0D, -1.0D, 1.0D, -1.0D, 1.0D);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            pushedModelView = true;
            GL11.glLoadIdentity();

            // Keep the active GBUFFERS_SKYBASIC program. Its fragment stage
            // reconstructs the view ray from gl_FragCoord, so one clip-space
            // quad provides a continuous upper and lower dome without replacing
            // Botania's textured details or Astral's compatibility wrapper.
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex3f(-1.0F, -1.0F, 0.0F);
            GL11.glVertex3f(1.0F, -1.0F, 0.0F);
            GL11.glVertex3f(1.0F, 1.0F, 0.0F);
            GL11.glVertex3f(-1.0F, 1.0F, 0.0F);
            GL11.glEnd();
        } finally {
            if (pushedModelView) {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
            }
            if (pushedProjection) {
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
            }
            GL11.glMatrixMode(previousMatrixMode);
            if (previousBlend) {
                MinecraftReflectionCompat.glStateEnableBlend();
            } else {
                MinecraftReflectionCompat.glStateDisableBlend();
            }
            if (previousCull) {
                MinecraftReflectionCompat.glStateEnableCull();
            } else {
                MinecraftReflectionCompat.glStateDisableCull();
            }
            if (previousDepthTest) {
                MinecraftReflectionCompat.glStateEnableDepth();
            } else {
                MinecraftReflectionCompat.glStateDisableDepth();
            }
            MinecraftReflectionCompat.glStateDepthMask(previousDepthMask);
            GL11.glDepthFunc(previousDepthFunc);
            if (previousScissor) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
            GL11.glColorMask(
                    previousColorMask.get(0) != 0,
                    previousColorMask.get(1) != 0,
                    previousColorMask.get(2) != 0,
                    previousColorMask.get(3) != 0
            );
            MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    protected void logOwnedSkyBackingDecisionProbe(String route, Minecraft mc, World world, boolean external,
                                                   boolean bpNested, boolean bpPass, boolean hasView,
                                                   boolean hasTarget, boolean owned, boolean shaderless,
                                                   boolean shadered) {
        if (!DEBUG_PROBES_ENABLED) {
            return;
        }
        boolean hideGui = mc != null && MinecraftReflectionCompat.hideGui(
                MinecraftReflectionCompat.gameSettings(mc));
        if (!hideGui || ownedSkyBackingDecisionProbeLogs++ >= 36) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMSkyBackingDecisionProbe] route={} active={} world={} dim={} simpleVoid={} customVoid={} owned={} shaderless={} shadered={} external={} bpNested={} bpPass={} hasView={} hasTarget={} screen={} hideGui={} paused={} drawFbo={} readFbo={} mcTarget={}",
                route,
                isPipelineActive,
                world == null ? "null" : world.getClass().getName(),
                world == null || MinecraftReflectionCompat.worldProvider(world) == null
                        ? Integer.MIN_VALUE
                        : MinecraftReflectionCompat.providerDimension(
                        MinecraftReflectionCompat.worldProvider(world)),
                self().isSimpleVoidWorld(world),
                self().isCustomVoidWorldSkyEnabled(world),
                owned,
                shaderless,
                shadered,
                external,
                bpNested,
                bpPass,
                hasView,
                hasTarget,
                mc != null && MinecraftReflectionCompat.currentScreen(mc) != null,
                hideGui,
                mc != null && MinecraftReflectionCompat.isGamePaused(mc),
                GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                self().describeFramebufferTargetDetailed(mc == null ? null : MinecraftReflectionCompat.minecraftFramebuffer(mc))
        );
    }

    protected void logOwnedSkyBackingProbe(String route, Minecraft mc) {
        if (!DEBUG_PROBES_ENABLED) {
            return;
        }
        if (mc == null
                || !MinecraftReflectionCompat.hideGui(
                MinecraftReflectionCompat.gameSettings(mc))
                || ownedSkyBackingProbeLogs++ >= 36) {
            return;
        }
        Framebuffer framebuffer = mc == null ? null : MinecraftReflectionCompat.minecraftFramebuffer(mc);
        MainMod.LOGGER.info(
                "[AUSMSkyBackingProbe] route={} active={} simpleVoid={} owned={} screen={} hideGui={} paused={} drawFbo={} readFbo={} mcTarget={} display={}x{}",
                route,
                isPipelineActive,
                self().isSimpleVoidWorld(renderWorld(mc)),
                self().shouldUseOwnedSkyOverrideWorld(renderWorld(mc)),
                mc != null && MinecraftReflectionCompat.currentScreen(mc) != null,
                mc != null && MinecraftReflectionCompat.hideGui(MinecraftReflectionCompat.gameSettings(mc)),
                mc != null && MinecraftReflectionCompat.isGamePaused(mc),
                GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                self().describeFramebufferTargetDetailed(framebuffer),
                mc == null ? -1 : MinecraftReflectionCompat.displayWidth(mc),
                mc == null ? -1 : MinecraftReflectionCompat.displayHeight(mc)
        );
    }

    public void renderShaderlessGuiCustomSkyBackingBeforeSky(float partialTicks) {
        self().renderOwnedSkyBackingBeforeSky(partialTicks);
    }

    protected boolean shouldRenderShaderedOwnedSkyBacking(Minecraft mc) {
        return false;
    }

    protected boolean shouldUseShaderedF1LowerSkyRepair(Minecraft mc, World world) {
        return false;
    }

    protected void drawOwnedSkyBackingGradient(int width, int height, Vec3d skyColor, Minecraft mc) {
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int previousShadeModel = GL11.glGetInteger(GL11.GL_SHADE_MODEL);
        int previousDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean previousDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean previousTexture2D = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean previousBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean previousCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean previousFog = GL11.glIsEnabled(GL11.GL_FOG);
        boolean previousScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        ByteBuffer previousColorMask = BufferUtils.createByteBuffer(4);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, previousColorMask);
        boolean pushedProjection = false;
        boolean pushedModelView = false;
        try {
            MinecraftReflectionCompat.glUseProgram(0);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            GL11.glViewport(0, 0, Math.max(1, width), Math.max(1, height));
            MinecraftReflectionCompat.glStateDisableTexture2D();
            MinecraftReflectionCompat.glStateDisableBlend();
            MinecraftReflectionCompat.glStateDisableCull();
            MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179106_n", "disableFog"}, MinecraftReflectionCompat.NO_PARAMETERS);
            // Keep the backing gradient depth-tested. It is drawn before normal
            // terrain, but this also protects against late/nested sky calls
            // painting Void bands over already-rendered geometry.
            MinecraftReflectionCompat.glStateEnableDepth();
            MinecraftReflectionCompat.glStateDepthMask(false);
            GL11.glDepthFunc(GL11.GL_LEQUAL);

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            pushedProjection = true;
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0D, Math.max(1, width), 0.0D, Math.max(1, height), -1.0D, 1.0D);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            pushedModelView = true;
            GL11.glLoadIdentity();
            GL11.glShadeModel(GL11.GL_SMOOTH);

            width = Math.max(1, width);
            height = Math.max(1, height);
            float[] bottom = self().officialOwnedSkyBackingColorAt(0.0D, height, skyColor, mc);
            float[] top = self().officialOwnedSkyBackingColorAt(height, height, skyColor, mc);
            // This backing is a render-boundary primitive, not terrain. Keep it
            // outside BufferBuilder so terrain vertex expansion and stale
            // per-thread compile context cannot suppress or reinterpret it.
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glColor4f(bottom[0], bottom[1], bottom[2], 1.0F);
            GL11.glVertex3d(0.0D, 0.0D, -1.0D);
            GL11.glVertex3d(width, 0.0D, -1.0D);
            GL11.glColor4f(top[0], top[1], top[2], 1.0F);
            GL11.glVertex3d(width, height, -1.0D);
            GL11.glVertex3d(0.0D, height, -1.0D);
            GL11.glEnd();
        } finally {
            if (pushedModelView) {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
            }
            if (pushedProjection) {
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
            }
            GL11.glMatrixMode(previousMatrixMode);
            GL11.glShadeModel(previousShadeModel);
            if (previousTexture2D) {
                MinecraftReflectionCompat.glStateEnableTexture2D();
            } else {
                MinecraftReflectionCompat.glStateDisableTexture2D();
            }
            if (previousBlend) {
                MinecraftReflectionCompat.glStateEnableBlend();
            } else {
                MinecraftReflectionCompat.glStateDisableBlend();
            }
            if (previousCull) {
                MinecraftReflectionCompat.glStateEnableCull();
            } else {
                MinecraftReflectionCompat.glStateDisableCull();
            }
            if (previousFog) {
                MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179127_m", "enableFog"}, MinecraftReflectionCompat.NO_PARAMETERS);
            } else {
                MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179106_n", "disableFog"}, MinecraftReflectionCompat.NO_PARAMETERS);
            }
            if (previousScissor) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
            GL11.glColorMask(
                    previousColorMask.get(0) != 0,
                    previousColorMask.get(1) != 0,
                    previousColorMask.get(2) != 0,
                    previousColorMask.get(3) != 0
            );
            if (previousDepthTest) {
                MinecraftReflectionCompat.glStateEnableDepth();
            } else {
                MinecraftReflectionCompat.glStateDisableDepth();
            }
            MinecraftReflectionCompat.glStateDepthMask(previousDepthMask);
            GL11.glDepthFunc(previousDepthFunc);
        }
    }

    protected void drawOwnedSkyDepthRepairGradient(int width, int height, Vec3d skyColor, Minecraft mc, Framebuffer target) {
        if (target == null || width <= 0 || height <= 0) {
            return;
        }
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int previousShadeModel = GL11.glGetInteger(GL11.GL_SHADE_MODEL);
        int previousDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean previousDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean previousTexture2D = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean previousBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean previousCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean previousFog = GL11.glIsEnabled(GL11.GL_FOG);
        boolean pushedProjection = false;
        boolean pushedModelView = false;
        try {
            MinecraftReflectionCompat.bindFramebuffer(target, false);
            GL11.glDrawBuffer(MinecraftReflectionCompat.framebufferObject(target) == 0
                    ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glViewport(0, 0, width, height);
            MinecraftReflectionCompat.glUseProgram(0);
            MinecraftReflectionCompat.glStateDisableTexture2D();
            MinecraftReflectionCompat.glStateDisableBlend();
            MinecraftReflectionCompat.glStateDisableCull();
            MinecraftReflectionCompat.invoke(GlStateManager.class,
                    new String[]{"func_179106_n", "disableFog"},
                    MinecraftReflectionCompat.NO_PARAMETERS);
            MinecraftReflectionCompat.glStateEnableDepth();
            MinecraftReflectionCompat.glStateDepthMask(false);
            GL11.glDepthFunc(GL11.GL_LEQUAL);

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            pushedProjection = true;
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0D, Math.max(1, width), 0.0D, Math.max(1, height), -1.0D, 1.0D);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            pushedModelView = true;
            GL11.glLoadIdentity();
            GL11.glShadeModel(GL11.GL_SMOOTH);

            float[] bottom = self().officialOwnedSkyBackingColorAt(0.0D, height, skyColor, mc);
            float[] top = self().officialOwnedSkyBackingColorAt(height, height, skyColor, mc);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glColor4f(bottom[0], bottom[1], bottom[2], 1.0F);
            GL11.glVertex3d(0.0D, 0.0D, -1.0D);
            GL11.glVertex3d(width, 0.0D, -1.0D);
            GL11.glColor4f(top[0], top[1], top[2], 1.0F);
            GL11.glVertex3d(width, height, -1.0D);
            GL11.glVertex3d(0.0D, height, -1.0D);
            GL11.glEnd();
        } finally {
            if (pushedModelView) {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
            }
            if (pushedProjection) {
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
            }
            GL11.glMatrixMode(previousMatrixMode);
            GL11.glShadeModel(previousShadeModel);
            if (previousTexture2D) {
                MinecraftReflectionCompat.glStateEnableTexture2D();
            } else {
                MinecraftReflectionCompat.glStateDisableTexture2D();
            }
            if (previousBlend) {
                MinecraftReflectionCompat.glStateEnableBlend();
            } else {
                MinecraftReflectionCompat.glStateDisableBlend();
            }
            if (previousCull) {
                MinecraftReflectionCompat.glStateEnableCull();
            } else {
                MinecraftReflectionCompat.glStateDisableCull();
            }
            if (previousFog) {
                MinecraftReflectionCompat.invoke(GlStateManager.class,
                        new String[]{"func_179127_m", "enableFog"},
                        MinecraftReflectionCompat.NO_PARAMETERS);
            } else {
                MinecraftReflectionCompat.invoke(GlStateManager.class,
                        new String[]{"func_179106_n", "disableFog"},
                        MinecraftReflectionCompat.NO_PARAMETERS);
            }
            if (previousDepthTest) {
                MinecraftReflectionCompat.glStateEnableDepth();
            } else {
                MinecraftReflectionCompat.glStateDisableDepth();
            }
            MinecraftReflectionCompat.glStateDepthMask(previousDepthMask);
            GL11.glDepthFunc(previousDepthFunc);
        }
    }

    protected void renderShaderlessBotaniaVoidDetails(float partialTicks, WorldClient world, Minecraft mc) {
        TextureManager textureManager = MinecraftReflectionCompat.textureManager(mc);
        if (textureManager == null) {
            return;
        }

        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int previousShadeModel = GL11.glGetInteger(GL11.GL_SHADE_MODEL);
        int previousDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int previousBlendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int previousBlendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int previousBlendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int previousBlendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        boolean previousTexture2D = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean previousBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean previousAlpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean previousDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean previousCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean previousFog = GL11.glIsEnabled(GL11.GL_FOG);
        boolean previousLighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean pushed = false;
        try {
            MinecraftReflectionCompat.glStateEnableTexture2D();
            MinecraftReflectionCompat.glStateEnableBlend();
            MinecraftReflectionCompat.glStateDisableAlpha();
            MinecraftReflectionCompat.glStateDisableDepth();
            MinecraftReflectionCompat.glStateDepthMask(false);
            MinecraftReflectionCompat.glStateDisableCull();
            MinecraftReflectionCompat.glStateDisableLighting();
            MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179106_n", "disableFog"}, MinecraftReflectionCompat.NO_PARAMETERS);
            GL11.glDepthFunc(GL11.GL_LEQUAL);

            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            pushed = true;

            float rainFade = 1.0F - self().clamp01(MinecraftReflectionCompat.worldRainStrength(world, partialTicks));
            float celestial = MinecraftReflectionCompat.worldCelestialAngle(world, partialTicks);
            float dayDistance = celestial > 0.5F ? 1.0F - celestial : celestial;
            float nightAlpha = self().clamp01((dayDistance - 0.30F) * 5.0F) * rainFade;
            float ornamentAlpha = 1.0F;
            long time = MinecraftReflectionCompat.worldTime(world);

            self().drawBotaniaVoidPlanets(textureManager, time, partialTicks, ornamentAlpha);
            self().drawBotaniaVoidSkyBands(textureManager, time, partialTicks, ornamentAlpha);
            self().drawBotaniaVoidRainbow(textureManager, time, partialTicks, rainFade, celestial);
        } finally {
            if (pushed) {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
            }
            GL11.glMatrixMode(previousMatrixMode);
            GL11.glShadeModel(previousShadeModel);
            if (previousTexture2D) {
                MinecraftReflectionCompat.glStateEnableTexture2D();
            } else {
                MinecraftReflectionCompat.glStateDisableTexture2D();
            }
            if (previousBlend) {
                MinecraftReflectionCompat.glStateEnableBlend();
            } else {
                MinecraftReflectionCompat.glStateDisableBlend();
            }
            MinecraftReflectionCompat.glStateTryBlendFuncSeparate(previousBlendSrcRgb, previousBlendDstRgb, previousBlendSrcAlpha, previousBlendDstAlpha);
            if (previousAlpha) {
                MinecraftReflectionCompat.glStateEnableAlpha();
            } else {
                MinecraftReflectionCompat.glStateDisableAlpha();
            }
            if (previousDepthTest) {
                MinecraftReflectionCompat.glStateEnableDepth();
            } else {
                MinecraftReflectionCompat.glStateDisableDepth();
            }
            MinecraftReflectionCompat.glStateDepthMask(previousDepthMask);
            if (previousCull) {
                MinecraftReflectionCompat.glStateEnableCull();
            } else {
                MinecraftReflectionCompat.glStateDisableCull();
            }
            if (previousLighting) {
                MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179145_e", "enableLighting"}, MinecraftReflectionCompat.NO_PARAMETERS);
            } else {
                MinecraftReflectionCompat.glStateDisableLighting();
            }
            if (previousFog) {
                MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179127_m", "enableFog"}, MinecraftReflectionCompat.NO_PARAMETERS);
            } else {
                MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179106_n", "disableFog"}, MinecraftReflectionCompat.NO_PARAMETERS);
            }
            GL11.glDepthFunc(previousDepthFunc);
            MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
