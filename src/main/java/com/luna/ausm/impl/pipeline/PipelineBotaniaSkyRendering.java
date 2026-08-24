package com.luna.ausm.impl.pipeline;

import com.luna.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.luna.ausm.impl.pipeline.bloom.BloomExtractionPlan;
import com.luna.ausm.impl.pipeline.render.FixedFunctionGlState;
import com.luna.ausm.impl.pipeline.render.TextureBinder;
import com.luna.ausm.impl.pipeline.vertex.BlockRenderContext;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import static com.luna.ausm.impl.pipeline.PipelineGlState.disablePipelineVertexAttributes;
import static com.luna.ausm.impl.pipeline.PipelineGlState.resetIndexedBlendState;
import static com.luna.ausm.impl.pipeline.PipelineGlState.unbindShaderImages;
import static com.luna.ausm.impl.pipeline.PipelineSkyConstants.BOTANIA_VOID_PLANET_TEXTURES;
import static com.luna.ausm.impl.pipeline.PipelineSkyConstants.BOTANIA_VOID_RAINBOW_TEXTURE;
import static com.luna.ausm.impl.pipeline.PipelineSkyConstants.BOTANIA_VOID_SKYBOX_TEXTURE;

abstract class PipelineBotaniaSkyRendering extends PipelineSkyBackingRendering {
    protected void drawBotaniaVoidPlanets(TextureManager textureManager, long time, float partialTicks, float alpha) {
        if (alpha <= 0.01F) {
            return;
        }
        GL11.glPushMatrix();
        try {
            MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                    GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, self().clamp01(alpha));
            GL11.glRotatef(90.0F, 0.5F, 0.5F, 0.0F);
            float size = 20.0F;
            for (int i = 0; i < BOTANIA_VOID_PLANET_TEXTURES.length; i++) {
                MinecraftReflectionCompat.bindTexture(textureManager, BOTANIA_VOID_PLANET_TEXTURES[i]);
                self().drawBotaniaVoidBillboard(size);
                switch (i) {
                    case 0:
                        GL11.glRotatef(70.0F, 1.0F, 0.0F, 0.0F);
                        size = 12.0F;
                        break;
                    case 1:
                        GL11.glRotatef(120.0F, 0.0F, 0.0F, 1.0F);
                        size = 15.0F;
                        break;
                    case 2:
                        GL11.glRotatef(80.0F, 1.0F, 0.0F, 1.0F);
                        size = 25.0F;
                        break;
                    case 3:
                        GL11.glRotatef(100.0F, 0.0F, 0.0F, 1.0F);
                        size = 10.0F;
                        break;
                    case 4:
                        GL11.glRotatef(-60.0F, 1.0F, 0.0F, 0.5F);
                        size = 40.0F;
                        break;
                    default:
                        GL11.glRotatef(((time + (long) (partialTicks * 20.0F)) % 360L) * 0.02F, 0.0F, 1.0F, 0.0F);
                        break;
                }
            }
        } finally {
            GL11.glPopMatrix();
            MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    protected void drawBotaniaVoidSkyBands(TextureManager textureManager, long time, float partialTicks, float alpha) {
        if (alpha <= 0.01F) {
            return;
        }
        MinecraftReflectionCompat.bindTexture(textureManager, BOTANIA_VOID_SKYBOX_TEXTURE);
        GL11.glPushMatrix();
        try {
            MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                    GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, self().clamp01(alpha));
            GL11.glTranslatef(0.0F, -1.0F, 0.0F);
            GL11.glRotatef(220.0F, 1.0F, 0.0F, 0.0F);
            self().drawBotaniaRibbon((time + partialTicks) * 0.16F, 20.0F, 2.0F, 90);
            MinecraftReflectionCompat.glStateColor(1.0F, 0.4F, 0.4F, self().clamp01(alpha * 0.75F));
            GL11.glRotatef(20.0F, 1.0F, 0.0F, 0.0F);
            self().drawBotaniaRibbon((time + partialTicks) * 0.04F, 20.0F, 2.0F, 90);
            MinecraftReflectionCompat.glStateColor(0.4F, 1.0F, 0.7F, self().clamp01(alpha * 0.75F));
            GL11.glRotatef(50.0F, 1.0F, 0.0F, 0.0F);
            self().drawBotaniaRibbon((time + partialTicks) * 0.40F, 20.0F, 2.0F, 90);
        } finally {
            GL11.glPopMatrix();
            MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    protected void drawBotaniaVoidRainbow(TextureManager textureManager, long time, float partialTicks, float rainFade, float celestial) {
        float daySide = celestial > 0.25F ? 1.0F - celestial : celestial;
        float alpha = self().clamp01((0.25F - Math.min(0.25F, daySide)) * 4.0F) * rainFade * 0.35F;
        if (alpha <= 0.01F) {
            return;
        }
        MinecraftReflectionCompat.bindTexture(textureManager, BOTANIA_VOID_RAINBOW_TEXTURE);
        GL11.glPushMatrix();
        try {
            MinecraftReflectionCompat.glStateTryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, alpha);
            GL11.glRotatef(35.0F + ((time + partialTicks) % 24000.0F) * 0.002F, 0.0F, 0.0F, 1.0F);
            GL11.glTranslatef(0.0F, 18.0F, 0.0F);
            self().drawBotaniaRibbon((time + partialTicks) * 0.02F, 30.0F, 2.5F, 96);
        } finally {
            GL11.glPopMatrix();
            MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    protected void drawBotaniaVoidBillboard(float size) {
        Tessellator tessellator = MinecraftReflectionCompat.tessellator();
        BufferBuilder buffer = MinecraftReflectionCompat.tessellatorBuffer(tessellator);
        MinecraftReflectionCompat.bufferBegin(buffer, GL11.GL_QUADS, MinecraftReflectionCompat.field(DefaultVertexFormats.class, VertexFormat.class, null, "field_181707_g", "POSITION_TEX"));
        MinecraftReflectionCompat.bufferPosTexEnd(buffer, -size, 100.0D, -size, 0.0D, 0.0D);
        MinecraftReflectionCompat.bufferPosTexEnd(buffer, size, 100.0D, -size, 1.0D, 0.0D);
        MinecraftReflectionCompat.bufferPosTexEnd(buffer, size, 100.0D, size, 1.0D, 1.0D);
        MinecraftReflectionCompat.bufferPosTexEnd(buffer, -size, 100.0D, size, 0.0D, 1.0D);
        MinecraftReflectionCompat.tessellatorDraw(tessellator);
    }

    protected void drawBotaniaRibbon(float scrollDegrees, float radius, float height, int segments) {
        Tessellator tessellator = MinecraftReflectionCompat.tessellator();
        BufferBuilder buffer = MinecraftReflectionCompat.tessellatorBuffer(tessellator);
        MinecraftReflectionCompat.bufferBegin(buffer, GL11.GL_QUAD_STRIP, MinecraftReflectionCompat.field(DefaultVertexFormats.class, VertexFormat.class, null, "field_181707_g", "POSITION_TEX"));
        double scroll = scrollDegrees / 360.0D;
        for (int i = 0; i <= segments; i++) {
            double angle = ((i / (double) segments) * Math.PI * 2.0D);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            double wave = Math.sin(angle * 5.0D) * 0.75D;
            double u = (i / (double) segments) + scroll;
            MinecraftReflectionCompat.bufferPosTexEnd(buffer, x, wave, z, u, 1.0D);
            MinecraftReflectionCompat.bufferPosTexEnd(buffer, x, wave + height, z, u, 0.0D);
        }
        MinecraftReflectionCompat.tessellatorDraw(tessellator);
    }

    protected float[] officialOwnedSkyBackingColorAt(double y, int height, Vec3d skyColor, Minecraft mc) {
        double uvY = self().clamp01(y / Math.max(1.0D, height));
        return self().vec3Color(self().officialOwnedSkyBackingColor(uvY, skyColor, mc));
    }

    protected Vec3d officialOwnedSkyBackingColor(double uvY, Vec3d skyColor, Minecraft mc) {
        if (!self().isSimpleVoidWorld(renderWorld(mc))) {
            return self().dimensionOwnedSkyBackingColor(uvY, skyColor, mc);
        }

        double horizonY = 0.50D;
        double softness = 0.70D;
        Vec3d source = skyColor != null ? skyColor : new Vec3d(0.0D, 0.0D, 0.0D);
        Vec3d dayTop = self().maxSkyColor(source, new Vec3d(0.45D, 0.62D, 0.86D));
        Vec3d dayHorizon = self().mixSkyColors(self().desaturateSkyColor(dayTop, 0.35D), new Vec3d(0.84D, 0.90D, 1.0D), 0.62D);
        World world = renderWorld(mc);
        double celestial = world == null ? 0.25D
                : MinecraftReflectionCompat.worldCelestialAngle(world,
                mc == null ? 0.0F : MinecraftReflectionCompat.renderPartialTicks(mc));
        // This world's celestial angle is zero at midday and one-half at
        // midnight. Use cosine rather than the standard quarter-shifted sine.
        double sunHeight = Math.cos(celestial * Math.PI * 2.0D);
        double day = self().smoothstep(-0.12D, 0.20D, sunHeight);
        double night = 1.0D - self().smoothstep(-0.30D, 0.08D, sunHeight);
        double twilight = self().clamp01(1.0D - day - night);
        double sunsetSide = self().clamp01((1.0D + Math.sin(celestial * Math.PI * 2.0D)) * 0.5D);

        Vec3d nightTop = new Vec3d(0.012D, 0.021D, 0.075D);
        Vec3d nightHorizon = new Vec3d(0.042D, 0.058D, 0.125D);
        Vec3d sunrise = new Vec3d(0.96D, 0.47D, 0.32D);
        Vec3d sunset = new Vec3d(0.62D, 0.16D, 0.30D);
        Vec3d twilightColor = self().mixSkyColors(sunrise, sunset, sunsetSide);

        Vec3d topColor = self().mixSkyColors(self().mixSkyColors(nightTop, twilightColor, twilight), dayTop, day);
        Vec3d lowerColor = self().mixSkyColors(self().mixSkyColors(nightHorizon, twilightColor, twilight), dayHorizon, day);

        double band = (uvY - (horizonY - softness)) / (softness * 2.0D);
        Vec3d result = self().mixSkyColors(lowerColor, topColor, self().smootherstep(band));

        double rainAmount = self().officialSkyRainFactor(mc);
        Vec3d rainyDome = self().mixSkyColors(lowerColor, topColor, 0.48D);
        result = self().mixSkyColors(result, rainyDome, self().clamp01(rainAmount * 0.75D));

        Vec3d rainColor = new Vec3d(
                Math.min(MinecraftReflectionCompat.vecX(result), 0.17D),
                Math.min(MinecraftReflectionCompat.vecY(result), 0.185D),
                Math.min(MinecraftReflectionCompat.vecZ(result), 0.235D)
        );
        return self().mixSkyColors(result, rainColor, self().clamp01(rainAmount * 0.50D));
    }

    protected double smoothstep(double edge0, double edge1, double value) {
        double t = self().clamp01((value - edge0) / Math.max(1.0E-6D, edge1 - edge0));
        return t * t * (3.0D - 2.0D * t);
    }

    protected Vec3d dimensionOwnedSkyBackingColor(double uvY, Vec3d skyColor, Minecraft mc) {
        double horizonY = 0.50D;
        double softness = 0.70D;
        Vec3d source = skyColor != null ? skyColor : new Vec3d(0.0D, 0.0D, 0.0D);
        World world = renderWorld(mc);
        if (world != null
                && MinecraftReflectionCompat.worldProvider(world) != null
                && "twilightforest.world.WorldProviderTwilightForest".equals(
                MinecraftReflectionCompat.worldProvider(world).getClass().getName())) {
            // TFSkyRenderer's upper plane is fogged at the horizon while its
            // lower dome uses the vanilla sky-coloured lower-dome tint. Match
            // those two native endpoints in the AUSM safety underlay so it can
            // fill a missed lower plane without creating a screen-space seam.
            float[] liveFog = self().currentGlFogColor();
            float partialTicks = mc == null ? 0.0F : MinecraftReflectionCompat.renderPartialTicks(mc);
            Vec3d worldFogColor = MinecraftReflectionCompat.call(
                    world,
                    Vec3d.class,
                    null,
                    new String[]{"func_72948_g", "getFogColor", "func_72824_f"},
                    new Class<?>[]{float.class},
                    partialTicks);
            // EntityRenderer has already applied Twilight's biome/weather fog
            // by this point. Its GL value is the exact green-grey band seen on
            // the upper dome; World#getFogColor is only a fallback and misses
            // that final tint.
            Vec3d horizonColor = self().isProbablyUnsetFogColor(liveFog)
                    ? worldFogColor != null ? worldFogColor : self().desaturateSkyColor(source, 0.55D)
                    : new Vec3d(liveFog[0], liveFog[1], liveFog[2]);
            Vec3d lowerColor = MinecraftReflectionCompat.worldProvider(world).isSkyColored()
                    ? new Vec3d(
                    MinecraftReflectionCompat.vecX(source) * 0.2D + 0.04D,
                    MinecraftReflectionCompat.vecY(source) * 0.2D + 0.04D,
                    MinecraftReflectionCompat.vecZ(source) * 0.6D + 0.10D)
                    : source;
            // Keep a visible section of Twilight's green fog below the join,
            // then soften into the lower-dome tint instead of changing colour
            // on the exact upper/lower plane boundary.
            double greenBandBottom = 0.34D;
            return self().mixSkyColors(lowerColor, horizonColor,
                    self().smootherstep(self().clamp01(
                            (uvY - greenBandBottom) / (horizonY - greenBandBottom))));
        }
        Vec3d topColor = self().scaleSkyColor(source, 1.08D);
        Vec3d horizonColor = self().mixSkyColors(source, self().desaturateSkyColor(source, 0.55D), 0.35D);
        Vec3d lowerColor = self().mixSkyColors(horizonColor, source, 0.20D);

        double band = (uvY - (horizonY - softness)) / (softness * 2.0D);
        Vec3d result = self().mixSkyColors(lowerColor, topColor, self().smootherstep(band));

        return result;
    }

    protected double officialSkyNightFactor(Minecraft mc) {
        World world = renderWorld(mc);
        if (world == null) {
            return 0.0D;
        }
        double timeAngle = ((MinecraftReflectionCompat.worldTime(world) % 24000L) / 24000.0D) % 1.0D;
        return self().clamp01(Math.max(Math.sin(timeAngle * -Math.PI * 2.0D), 0.0D));
    }

    protected double officialSkyRainFactor(Minecraft mc) {
        World world = renderWorld(mc);
        if (world == null) {
            return 0.0D;
        }
        float partialTicks = mc != null ? MinecraftReflectionCompat.renderPartialTicks(mc) : 0.0F;
        return self().clamp01(MinecraftReflectionCompat.worldRainStrength(world, partialTicks));
    }

    protected Vec3d maxSkyColor(Vec3d left, Vec3d right) {
        return new Vec3d(
                Math.max(MinecraftReflectionCompat.vecX(left), MinecraftReflectionCompat.vecX(right)),
                Math.max(MinecraftReflectionCompat.vecY(left), MinecraftReflectionCompat.vecY(right)),
                Math.max(MinecraftReflectionCompat.vecZ(left), MinecraftReflectionCompat.vecZ(right))
        );
    }

    protected Vec3d scaleSkyColor(Vec3d color, double scale) {
        return new Vec3d(
                self().clamp01(MinecraftReflectionCompat.vecX(color) * scale),
                self().clamp01(MinecraftReflectionCompat.vecY(color) * scale),
                self().clamp01(MinecraftReflectionCompat.vecZ(color) * scale)
        );
    }

    protected double smootherstep(double value) {
        double t = self().clamp01(value);
        return t * t * t * (t * (t * 6.0D - 15.0D) + 10.0D);
    }

    protected float[] vec3Color(Vec3d color) {
        return new float[]{
                self().clamp01((float) MinecraftReflectionCompat.vecX(color)),
                self().clamp01((float) MinecraftReflectionCompat.vecY(color)),
                self().clamp01((float) MinecraftReflectionCompat.vecZ(color))
        };
    }

    protected void putGradientQuad(BufferBuilder buffer, double x0, double y0, double x1, double y1, float[] bottom, float[] top) {
        self().putGradientQuad(buffer, x0, y0, x1, y1, bottom, top, 0.0D);
    }

    protected void putGradientQuad(BufferBuilder buffer, double x0, double y0, double x1, double y1, float[] bottom, float[] top, double z) {
        MinecraftReflectionCompat.bufferPosColorEnd(buffer, x0, y0, z, self().colorByte(bottom[0]), self().colorByte(bottom[1]), self().colorByte(bottom[2]), 255);
        MinecraftReflectionCompat.bufferPosColorEnd(buffer, x1, y0, z, self().colorByte(bottom[0]), self().colorByte(bottom[1]), self().colorByte(bottom[2]), 255);
        MinecraftReflectionCompat.bufferPosColorEnd(buffer, x1, y1, z, self().colorByte(top[0]), self().colorByte(top[1]), self().colorByte(top[2]), 255);
        MinecraftReflectionCompat.bufferPosColorEnd(buffer, x0, y1, z, self().colorByte(top[0]), self().colorByte(top[1]), self().colorByte(top[2]), 255);
    }

    protected int colorByte(float value) {
        return PipelineContext.clampInt((int) (self().clamp01(value) * 255.0F + 0.5F), 0, 255);
    }

    protected void sealShaderlessWorldFramebufferAlpha(String stage) {
        if (isPipelineActive
                || externalWorldFramebufferTarget != null
                || self().isRenderingBetterPortalsNestedView()
                || self().isRenderingBetterPortalsRenderPass()) {
            return;
        }
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null
                || MinecraftReflectionCompat.world(mc) == null
                || MinecraftReflectionCompat.minecraftFramebuffer(mc) == null) {
            return;
        }

        Framebuffer target = MinecraftReflectionCompat.minecraftFramebuffer(mc);
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        FloatBuffer previousClearColor = BufferUtils.createFloatBuffer(4);
        ByteBuffer previousColorMask = BufferUtils.createByteBuffer(4);
        GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, previousClearColor);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, previousColorMask);
        try {
            MinecraftReflectionCompat.bindFramebuffer(target, false);
            GL11.glDrawBuffer(MinecraftReflectionCompat.framebufferObject(target) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glColorMask(false, false, false, true);
            GL11.glClearColor(0.0F, 0.0F, 0.0F, 1.0F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        } catch (RuntimeException | LinkageError ignored) {
        } finally {
            GL11.glClearColor(
                    previousClearColor.get(0),
                    previousClearColor.get(1),
                    previousClearColor.get(2),
                    previousClearColor.get(3)
            );
            GL11.glColorMask(
                    previousColorMask.get(0) != 0,
                    previousColorMask.get(1) != 0,
                    previousColorMask.get(2) != 0,
                    previousColorMask.get(3) != 0
            );
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            restoreReadBufferForFramebuffer(previousReadFramebuffer, previousReadBuffer);
            restoreDrawBufferForFramebuffer(previousDrawFramebuffer, previousDrawBuffer);
        }
    }

    public void sealShaderlessSkyFramebufferAlpha() {
        self().sealShaderlessWorldFramebufferAlpha("post-sky");
    }

    protected void restoreShaderlessBloomExitState(Minecraft mc) {
        MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        TextureBinder.disableShaderOnlyFixedFunctionTextureUnits();
        MinecraftReflectionCompat.glStateBindTexture(0);
        disablePipelineVertexAttributes();
        unbindShaderImages();
        self().unbindShaderStorageBuffers();
        resetIndexedBlendState();
        FixedFunctionGlState.resetClientArrayState(false);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        MinecraftReflectionCompat.glStateEnableTexture2D();
        MinecraftReflectionCompat.glStateEnableDepth();
        MinecraftReflectionCompat.glStateDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        MinecraftReflectionCompat.glStateEnableAlpha();
        MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        if (mc != null) {
            MinecraftReflectionCompat.glStateViewport(0, 0, MinecraftReflectionCompat.displayWidth(mc), MinecraftReflectionCompat.displayHeight(mc));
        }
    }

    public void prepareShaderlessUiRenderingBoundary() {
        if (disableShaderlessPreGuiHooks) {
            return;
        }
        if (isPipelineActive
                || externalWorldFramebufferTarget != null
                || self().isRenderingBetterPortalsNestedView()
                || self().isRenderingBetterPortalsRenderPass()) {
            return;
        }
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.world(mc) == null || MinecraftReflectionCompat.renderViewEntity(mc) == null) {
            return;
        }
        bindMinecraftFramebufferForGui(mc);
        self().restoreShaderlessBloomExitState(mc);
        self().sealShaderlessWorldFramebufferAlpha("ui-boundary");
        if (MinecraftReflectionCompat.hideGui(
                MinecraftReflectionCompat.gameSettings(mc))) {
            // F1 has no subsequent HUD draw to consume/reset GUI blending. Keep
            // the world framebuffer opaque for the final presentation blit.
            MinecraftReflectionCompat.glStateDisableBlend();
            MinecraftReflectionCompat.glStateDisableAlpha();
            MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
            return;
        }
        if (MinecraftReflectionCompat.currentScreen(mc) != null) {
            MinecraftReflectionCompat.glStateDisableDepth();
            MinecraftReflectionCompat.glStateDepthMask(false);
        }
        MinecraftReflectionCompat.glStateEnableAlpha();
        MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        MinecraftReflectionCompat.glStateEnableBlend();
        MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        MinecraftReflectionCompat.glStateDisableLighting();
        MinecraftReflectionCompat.glStateDisableColorMaterial();
    }

    protected void refreshShaderlessBloomVertexFormatIfNeeded() {
        self().refreshShaderlessBloomVertexFormatIfNeeded(bloomRenderer.hasBloomResources());
    }

    protected void refreshShaderlessBloomVertexFormatIfNeeded(boolean hasBloomResources) {
        if (isPipelineActive
                || shaderlessBloomVertexFormatRefreshRequested
                || !hasBloomResources) {
            return;
        }

        shaderlessBloomVertexFormatRefreshRequested = true;
        boolean recreateNothirium = self().updateNothiriumPipelineBlockFormatMode();
        self().rebuildTerrainRenderers(recreateNothirium, false);
    }

    protected boolean hasShaderlessFramedBloomBootstrapCandidate() {
        return false;
    }

    public void recordCurrentShaderlessBloomMetadata(BlockRenderLayer layer) {
        self().recordShaderlessBloomMetadata(
                BlockRenderContext.blockX(),
                BlockRenderContext.blockY(),
                BlockRenderContext.blockZ(),
                layer
        );
    }

    public void recordShaderlessBloomMetadata(BlockPos pos, BlockRenderLayer layer) {
        if (pos == null) {
            return;
        }
        self().recordShaderlessBloomMetadata(MinecraftReflectionCompat.blockPosX(pos), MinecraftReflectionCompat.blockPosY(pos), MinecraftReflectionCompat.blockPosZ(pos), layer);
    }

    public void recordShaderlessBloomMetadata(int blockX, int blockY, int blockZ, BlockRenderLayer layer) {
        self().recordShaderlessBloomMetadata(blockX, blockY, blockZ, layer, true);
    }

    public void recordShaderlessBloomMetadata(BlockPos pos, BlockRenderLayer layer, boolean hasBloom) {
        if (pos == null) {
            return;
        }
        self().recordShaderlessBloomMetadata(MinecraftReflectionCompat.blockPosX(pos), MinecraftReflectionCompat.blockPosY(pos), MinecraftReflectionCompat.blockPosZ(pos), layer, hasBloom);
    }

    public void recordShaderlessBloomMetadata(int blockX, int blockY, int blockZ, BlockRenderLayer layer, boolean hasBloom) {
        if (layer == null) {
            return;
        }
        long key = BloomExtractionPlan.metadataKey(
                self().currentClientDimensionId(),
                blockX >> 4,
                blockY >> 4,
                blockZ >> 4,
                layer
        );
        shaderlessBloomMetadataKnownChunkLayers.add(key);
        if (hasBloom) {
            shaderlessBloomMetadataChunkLayers.add(key);
        }
    }

    public void recordShaderlessBloomLayerSummary(BlockPos pos, BlockRenderLayer layer, boolean hasBloom) {
        if (pos == null) {
            return;
        }
        self().recordShaderlessBloomLayerSummary(
                MinecraftReflectionCompat.blockPosX(pos),
                MinecraftReflectionCompat.blockPosY(pos),
                MinecraftReflectionCompat.blockPosZ(pos),
                layer,
                hasBloom
        );
    }

    public void recordShaderlessBloomLayerSummary(int blockX, int blockY, int blockZ, BlockRenderLayer layer, boolean hasBloom) {
        if (layer == null) {
            return;
        }
        long key = BloomExtractionPlan.metadataKey(
                self().currentClientDimensionId(),
                blockX >> 4,
                blockY >> 4,
                blockZ >> 4,
                layer
        );
        shaderlessBloomMetadataKnownChunkLayers.add(key);
        if (hasBloom) {
            shaderlessBloomMetadataChunkLayers.add(key);
        } else {
            shaderlessBloomMetadataChunkLayers.remove(key);
        }
    }

    public void clearShaderlessBloomMetadata() {
        shaderlessBloomMetadataKnownChunkLayers.clear();
        shaderlessBloomMetadataChunkLayers.clear();
    }

    public void rebuildShaderlessBloomTerrain(String reason) {
        self().clearShaderlessBloomMetadata();
        self().scheduleBloomTerrainRefresh(reason);
    }

    public void handleShaderlessBloomBlockUpdate(World world, BlockPos pos, IBlockState oldState, IBlockState newState, int flags) {
        if (world == null || pos == null) {
            return;
        }
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.world(mc) != world) {
            return;
        }

        int dimension = self().safeDimensionId(world);
        int sectionX = MinecraftReflectionCompat.blockPosX(pos) >> 4;
        int sectionY = Math.clamp(MinecraftReflectionCompat.blockPosY(pos) >> 4, 0, 15);
        int sectionZ = MinecraftReflectionCompat.blockPosZ(pos) >> 4;
        boolean hadBloomMetadata = self().invalidateShaderlessBloomMetadataSection(dimension, sectionX, sectionY, sectionZ);
        boolean bloomSourceChanged = stateHasShaderlessBloomSource(oldState) || stateHasShaderlessBloomSource(newState);
        if (!hadBloomMetadata && !bloomSourceChanged) {
            return;
        }

        // Lumenized's native BLOOM layer is rebuilt by the normal world block
        // update. Scheduling our legacy shaderless extractor here recompiled
        // every populated section in the column several times and caused
        // visible flicker after ordinary block placement.
        if (AusmBloomLayer.shouldUseShaderlessNativeHook()) {
            return;
        }

        int x = MinecraftReflectionCompat.blockPosX(pos);
        int y = MinecraftReflectionCompat.blockPosY(pos);
        int z = MinecraftReflectionCompat.blockPosZ(pos);
        MinecraftReflectionCompat.worldMarkBlockRangeForRenderUpdate(
                world,
                x - 1,
                Math.max(0, y - 1),
                z - 1,
                x + 1,
                Math.min(255, y + 1),
                z + 1
        );
        self().queueShaderlessBloomClientChunkRefresh(world, sectionX, sectionZ);
    }

    public void handleClientBlockRenderUpdate(World world, BlockPos pos) {
        if (pos == null) {
            return;
        }
        self().handleClientBlockRenderUpdateRange(world,
                MinecraftReflectionCompat.blockPosX(pos),
                MinecraftReflectionCompat.blockPosY(pos),
                MinecraftReflectionCompat.blockPosZ(pos),
                MinecraftReflectionCompat.blockPosX(pos),
                MinecraftReflectionCompat.blockPosY(pos),
                MinecraftReflectionCompat.blockPosZ(pos));
    }
}
