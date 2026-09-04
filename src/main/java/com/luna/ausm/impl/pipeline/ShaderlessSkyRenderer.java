package com.luna.ausm.impl.pipeline;

import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.lang.reflect.Method;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

/**
 * Owns the complete AUSM shaderless sky replacement. It reproduces the
 * original fixed-function sky without
 * re-entering {@code RenderGlobal.renderSky} or consuming its sky display
 * lists/VBOs. Keeping the existing entry point allows a running client that
 * already has the sky mixin applied to receive the replacement through normal
 * class redefinition.
 */
final class ShaderlessSkyRenderer {
    private ShaderlessSkyRenderer() {
    }

    static boolean shouldReplaceVanillaSky(boolean pipelineActive) {
        return ShaderlessSkyOwnership.shouldReplaceVanillaSky(pipelineActive);
    }

    static void renderSky() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        WorldClient world = mc == null ? null : MinecraftReflectionCompat.world(mc);
        WorldProvider provider = MinecraftReflectionCompat.worldProvider(world);
        TextureManager textureManager = mc == null ? null : MinecraftReflectionCompat.textureManager(mc);
        Entity viewEntity = mc == null ? null : MinecraftReflectionCompat.renderViewEntity(mc);
        if (mc == null || world == null || provider == null || textureManager == null || viewEntity == null) {
            return;
        }

        float partialTicks = MinecraftReflectionCompat.renderPartialTicks(mc);
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousShadeModel = GL11.glGetInteger(GL11.GL_SHADE_MODEL);
        boolean previousTexture2D = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean previousAlpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean previousBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean previousFog = GL11.glIsEnabled(GL11.GL_FOG);
        boolean previousLighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean pushedProjection = false;
        boolean pushedModelView = false;
        try {
            MinecraftReflectionCompat.glUseProgram(0);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            pushedProjection = true;
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            pushedModelView = true;

            Object customRenderer = MinecraftReflectionCompat.worldProviderSkyRenderer(provider);
            boolean astralWrapper = customRenderer != null
                    && "hellfirepvp.astralsorcery.client.sky.RenderSkybox"
                    .equals(customRenderer.getClass().getName());
            if (customRenderer != null
                    && provider.isSurfaceWorld()
                    && MinecraftReflectionCompat.minecraftFramebuffer(mc) != null) {
                // Mod sky renderers reproduce vanilla's finite upper/lower
                // planes, but their lower plane can leave the framebuffer's
                // black clear colour exposed at the horizon.  This is most
                // visible through GUI/F1 presentation but is not GUI state:
                // make the shared AUSM backing continuous first, then retain
                // the renderer's own stars and dimension-specific details.
                PipelineContext.getInstance().drawOwnedSkyBackingGradient(
                        MinecraftReflectionCompat.framebufferWidth(
                                MinecraftReflectionCompat.minecraftFramebuffer(mc)),
                        MinecraftReflectionCompat.framebufferHeight(
                                MinecraftReflectionCompat.minecraftFramebuffer(mc)),
                        world.getSkyColor(viewEntity, partialTicks),
                        mc);
            }
            Object astralDetailsRenderer = null;
            Object delegatedSkyRenderer = null;
            boolean delegatedSkyNeedsOwnedBacking = false;
            if (astralWrapper) {
                // RenderSkybox is Astral's top-level owner. It either redraws
                // the complete sky itself or temporarily removes the provider
                // renderer and recursively calls RenderGlobal.renderSky. Both
                // routes defeat the AUSM ownership boundary. Retain the real
                // dimension renderer captured by Astral and its inner detail
                // renderer, then submit both explicitly from AUSM.
                delegatedSkyRenderer = MinecraftReflectionCompat.field(
                        customRenderer, Object.class, null, "otherSkyRenderer");
                delegatedSkyNeedsOwnedBacking = delegatedSkyRenderer != null
                        && "vazkii.botania.client.render.world.SkyblockSkyRenderer"
                        .equals(delegatedSkyRenderer.getClass().getName());
                astralDetailsRenderer = MinecraftReflectionCompat.field(
                        customRenderer.getClass(), Object.class, null, "astralSky");
                if (astralDetailsRenderer != null
                        && !MinecraftReflectionCompat.callBoolean(
                        astralDetailsRenderer,
                        new String[]{"isInitialized"},
                        new Class<?>[0],
                        false)) {
                    boolean assetsReloading = true;
                    try {
                        Class<?> assetLibrary = Class.forName(
                                "hellfirepvp.astralsorcery.client.util.resource.AssetLibrary",
                                false,
                                customRenderer.getClass().getClassLoader());
                        assetsReloading = MinecraftReflectionCompat.fieldBoolean(
                                assetLibrary, false, "reloading");
                    } catch (ClassNotFoundException | LinkageError ignored) {
                        // Leave the detail renderer uninitialized until Astral's
                        // resources are available; the AUSM sky still renders.
                    }
                    if (!assetsReloading) {
                        MinecraftReflectionCompat.invoke(
                                astralDetailsRenderer,
                                new String[]{"setInitialized"},
                                new Class<?>[]{long.class},
                                world.getWorldInfo().getSeed());
                    }
                }

                if (delegatedSkyRenderer != null
                        && delegatedSkyRenderer != customRenderer
                        && !delegatedSkyNeedsOwnedBacking) {
                    // A wrapped renderer is the dimension's actual sky (for
                    // example Botania's SkyblockSkyRenderer in dimension 43).
                    // Calling Astral's outer wrapper would either discard it or
                    // recurse into this RenderGlobal mixin. Call it directly,
                    // then reproduce Astral's weak-sky constellation overlay.
                    MinecraftReflectionCompat.invoke(
                            delegatedSkyRenderer,
                            new String[]{"render"},
                            new Class<?>[]{float.class, WorldClient.class, Minecraft.class},
                            partialTicks,
                            world,
                            mc);
                    if (astralDetailsRenderer != null
                            && MinecraftReflectionCompat.callBoolean(
                            astralDetailsRenderer,
                            new String[]{"isInitialized"},
                            new Class<?>[0],
                            false)) {
                        try {
                            Method constellationPass = astralDetailsRenderer.getClass()
                                    .getDeclaredMethod("renderConstellationsWrapped", World.class, float.class);
                            constellationPass.setAccessible(true);
                            constellationPass.invoke(null, world, partialTicks);
                        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                            // The dimension's own renderer remains authoritative
                            // if Astral's optional detail ABI changes.
                        }
                    }
                    return;
                }
            } else if (customRenderer != null) {
                MinecraftReflectionCompat.invoke(
                        customRenderer,
                        new String[]{"render"},
                        new Class<?>[]{float.class, WorldClient.class, Minecraft.class},
                        partialTicks,
                        world,
                        mc);
                return;
            }

            if (provider.getDimensionType().getId() == 1) {
                GlStateManager.disableFog();
                GlStateManager.disableAlpha();
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(
                        GlStateManager.SourceFactor.SRC_ALPHA,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                        GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ZERO);
                RenderHelper.disableStandardItemLighting();
                GlStateManager.depthMask(false);
                // The pipeline can issue raw fixed-function state changes, so
                // GlStateManager's cache is not always authoritative here.
                // Force the driver state as well or the sky cube writes depth
                // and clips later Astral details against itself.
                GL11.glDepthMask(false);
                MinecraftReflectionCompat.bindTexture(textureManager,
                        new ResourceLocation("textures/environment/end_sky.png"));

                for (int face = 0; face < 6; face++) {
                    GlStateManager.pushMatrix();
                    if (face == 1) {
                        GlStateManager.rotate(90.0F, 1.0F, 0.0F, 0.0F);
                    } else if (face == 2) {
                        GlStateManager.rotate(-90.0F, 1.0F, 0.0F, 0.0F);
                    } else if (face == 3) {
                        GlStateManager.rotate(180.0F, 1.0F, 0.0F, 0.0F);
                    } else if (face == 4) {
                        GlStateManager.rotate(90.0F, 0.0F, 0.0F, 1.0F);
                    } else if (face == 5) {
                        GlStateManager.rotate(-90.0F, 0.0F, 0.0F, 1.0F);
                    }
                    GL11.glBegin(GL11.GL_QUADS);
                    GL11.glColor4ub((byte) 40, (byte) 40, (byte) 40, (byte) 255);
                    GL11.glTexCoord2f(0.0F, 0.0F);
                    GL11.glVertex3d(-100.0D, -100.0D, -100.0D);
                    GL11.glTexCoord2f(0.0F, 16.0F);
                    GL11.glVertex3d(-100.0D, -100.0D, 100.0D);
                    GL11.glTexCoord2f(16.0F, 16.0F);
                    GL11.glVertex3d(100.0D, -100.0D, 100.0D);
                    GL11.glTexCoord2f(16.0F, 0.0F);
                    GL11.glVertex3d(100.0D, -100.0D, -100.0D);
                    GL11.glEnd();
                    GlStateManager.popMatrix();
                }
                if (astralDetailsRenderer != null) {
                    try {
                        Method constellationPass = astralDetailsRenderer.getClass()
                                .getDeclaredMethod("renderConstellationsWrapped", World.class, float.class);
                        constellationPass.setAccessible(true);
                        constellationPass.invoke(null, world, partialTicks);
                    } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                        // The End base remains fully AUSM-owned even if an
                        // incompatible Astral version cannot add its optional
                        // constellation overlay.
                    }
                }
                return;
            }

            if (!provider.isSurfaceWorld() && !astralWrapper) {
                return;
            }

            if (delegatedSkyNeedsOwnedBacking) {
                // The Void World's finite top/bottom planes need a closure at
                // the horizon, but a 3D perimeter face becomes a visible
                // camera-relative rectangle under GUI/F1 projection changes.
                // Use AUSM's projection-independent full-frame backing for
                // this dimension and layer only Botania/Astral details over it.
                PipelineContext.getInstance().renderShaderlessBotaniaSkyBacking(
                        partialTicks, world, mc);
                GlStateManager.disableFog();
                GlStateManager.disableAlpha();
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(
                        GlStateManager.SourceFactor.SRC_ALPHA,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                        GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ZERO);
                RenderHelper.disableStandardItemLighting();
                MinecraftReflectionCompat.invoke(
                        delegatedSkyRenderer,
                        new String[]{"render"},
                        new Class<?>[]{float.class, WorldClient.class, Minecraft.class},
                        partialTicks,
                        world,
                        mc);
                if (astralDetailsRenderer != null
                        && MinecraftReflectionCompat.callBoolean(
                        astralDetailsRenderer,
                        new String[]{"isInitialized"},
                        new Class<?>[0],
                        false)) {
                    try {
                        Method constellationPass = astralDetailsRenderer.getClass()
                                .getDeclaredMethod("renderConstellationsWrapped", World.class, float.class);
                        constellationPass.setAccessible(true);
                        constellationPass.invoke(null, world, partialTicks);
                    } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                        // The owned backing and Botania details remain valid if
                        // Astral changes its optional constellation detail ABI.
                    }
                }
                return;
            }

            Vec3d skyColor = world.getSkyColor(viewEntity, partialTicks);
            float red = (float) skyColor.x;
            float green = (float) skyColor.y;
            float blue = (float) skyColor.z;
            GlStateManager.disableTexture2D();
            GlStateManager.color(red, green, blue);
            GlStateManager.depthMask(false);
            // A stale GlStateManager depth-mask cache previously left the real
            // mask enabled. The upper plane then occluded the sunrise fan,
            // producing the huge hard-edged red half-dome.
            GL11.glDepthMask(false);
            GlStateManager.enableFog();
            int skyMin = -384;
            int skyMax = 448;
            GL11.glBegin(GL11.GL_QUADS);
            for (int x = skyMin; x < skyMax; x += 64) {
                for (int z = skyMin; z < skyMax; z += 64) {
                    GL11.glVertex3f(x, 16.0F, z);
                    GL11.glVertex3f(x + 64.0F, 16.0F, z);
                    GL11.glVertex3f(x + 64.0F, 16.0F, z + 64.0F);
                    GL11.glVertex3f(x, 16.0F, z + 64.0F);
                }
            }
            for (int x = skyMin; x < skyMax; x += 64) {
                for (int z = skyMin; z < skyMax; z += 64) {
                    GL11.glVertex3f(x + 64.0F, -16.0F, z);
                    GL11.glVertex3f(x, -16.0F, z);
                    GL11.glVertex3f(x, -16.0F, z + 64.0F);
                    GL11.glVertex3f(x + 64.0F, -16.0F, z + 64.0F);
                }
            }
            // The two finite planes do not meet at the horizon: their far
            // edges leave a visible angular strip that becomes black behind a
            // GUI. Join those edges so the backing is a closed sky box while
            // leaving sunrise, stars, and Astral details ordered above it.
            GL11.glVertex3f(skyMin, -16.0F, skyMin);
            GL11.glVertex3f(skyMax, -16.0F, skyMin);
            GL11.glVertex3f(skyMax, 16.0F, skyMin);
            GL11.glVertex3f(skyMin, 16.0F, skyMin);

            GL11.glVertex3f(skyMax, -16.0F, skyMax);
            GL11.glVertex3f(skyMin, -16.0F, skyMax);
            GL11.glVertex3f(skyMin, 16.0F, skyMax);
            GL11.glVertex3f(skyMax, 16.0F, skyMax);

            GL11.glVertex3f(skyMin, -16.0F, skyMax);
            GL11.glVertex3f(skyMin, -16.0F, skyMin);
            GL11.glVertex3f(skyMin, 16.0F, skyMin);
            GL11.glVertex3f(skyMin, 16.0F, skyMax);

            GL11.glVertex3f(skyMax, -16.0F, skyMin);
            GL11.glVertex3f(skyMax, -16.0F, skyMax);
            GL11.glVertex3f(skyMax, 16.0F, skyMax);
            GL11.glVertex3f(skyMax, 16.0F, skyMin);
            GL11.glEnd();

            GlStateManager.disableFog();
            GlStateManager.disableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO);
            RenderHelper.disableStandardItemLighting();
            float celestialAngle = world.getCelestialAngle(partialTicks);
            float[] sunrise = provider.calcSunriseSunsetColors(celestialAngle, partialTicks);
            if (sunrise != null) {
                GlStateManager.disableTexture2D();
                GlStateManager.shadeModel(GL11.GL_SMOOTH);
                GlStateManager.pushMatrix();
                GlStateManager.rotate(90.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.rotate(MathHelper.sin(world.getCelestialAngleRadians(partialTicks)) < 0.0F
                        ? 180.0F : 0.0F, 0.0F, 0.0F, 1.0F);
                GlStateManager.rotate(90.0F, 0.0F, 0.0F, 1.0F);
                GL11.glBegin(GL11.GL_TRIANGLE_FAN);
                GL11.glColor4f(sunrise[0], sunrise[1], sunrise[2], sunrise[3]);
                GL11.glVertex3d(0.0D, 100.0D, 0.0D);
                for (int vertex = 0; vertex <= 16; vertex++) {
                    float angle = vertex * (float) (Math.PI * 2.0D) / 16.0F;
                    float sin = MathHelper.sin(angle);
                    float cos = MathHelper.cos(angle);
                    GL11.glColor4f(sunrise[0], sunrise[1], sunrise[2], 0.0F);
                    GL11.glVertex3d(sin * 120.0F, cos * 120.0F, -cos * 40.0F * sunrise[3]);
                }
                GL11.glEnd();
                GlStateManager.popMatrix();
                GlStateManager.shadeModel(GL11.GL_FLAT);
            }

            boolean renderedAstralDetails = false;
            if (astralDetailsRenderer != null
                    && MinecraftReflectionCompat.callBoolean(
                    astralDetailsRenderer,
                    new String[]{"isInitialized"},
                    new Class<?>[0],
                    false)) {
                try {
                    Method detailPass = astralDetailsRenderer.getClass()
                            .getDeclaredMethod("renderDefaultCelestials", float.class);
                    detailPass.setAccessible(true);
                    detailPass.invoke(astralDetailsRenderer, partialTicks);
                    renderedAstralDetails = true;
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                    // Never return ownership to Astral's outer renderer. If its
                    // detail ABI changes, retain the AUSM vanilla celestial
                    // fallback for this frame.
                }
            }

            if (!renderedAstralDetails) {
                GlStateManager.enableTexture2D();
                GlStateManager.tryBlendFuncSeparate(
                        GlStateManager.SourceFactor.SRC_ALPHA,
                        GlStateManager.DestFactor.ONE,
                        GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ZERO);
                GlStateManager.pushMatrix();
                float rainFade = 1.0F - world.getRainStrength(partialTicks);
                GlStateManager.color(1.0F, 1.0F, 1.0F, rainFade);
                GlStateManager.rotate(-90.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotate(celestialAngle * 360.0F, 1.0F, 0.0F, 0.0F);

                float size = 30.0F;
                MinecraftReflectionCompat.bindTexture(textureManager,
                        new ResourceLocation("textures/environment/sun.png"));
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glTexCoord2f(0.0F, 0.0F);
                GL11.glVertex3f(-size, 100.0F, -size);
                GL11.glTexCoord2f(1.0F, 0.0F);
                GL11.glVertex3f(size, 100.0F, -size);
                GL11.glTexCoord2f(1.0F, 1.0F);
                GL11.glVertex3f(size, 100.0F, size);
                GL11.glTexCoord2f(0.0F, 1.0F);
                GL11.glVertex3f(-size, 100.0F, size);
                GL11.glEnd();

                size = 20.0F;
                MinecraftReflectionCompat.bindTexture(textureManager,
                        new ResourceLocation("textures/environment/moon_phases.png"));
                int moonPhase = world.getMoonPhase();
                int moonColumn = moonPhase % 4;
                int moonRow = moonPhase / 4 % 2;
                float minU = moonColumn / 4.0F;
                float minV = moonRow / 2.0F;
                float maxU = (moonColumn + 1) / 4.0F;
                float maxV = (moonRow + 1) / 2.0F;
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glTexCoord2f(maxU, maxV);
                GL11.glVertex3f(-size, -100.0F, size);
                GL11.glTexCoord2f(minU, maxV);
                GL11.glVertex3f(size, -100.0F, size);
                GL11.glTexCoord2f(minU, minV);
                GL11.glVertex3f(size, -100.0F, -size);
                GL11.glTexCoord2f(maxU, minV);
                GL11.glVertex3f(-size, -100.0F, -size);
                GL11.glEnd();

                GlStateManager.disableTexture2D();
                float starBrightness = world.getStarBrightness(partialTicks) * rainFade;
                if (starBrightness > 0.0F) {
                    GlStateManager.color(starBrightness, starBrightness, starBrightness, starBrightness);
                    Random random = new Random(10842L);
                    GL11.glBegin(GL11.GL_QUADS);
                    for (int star = 0; star < 1500; star++) {
                        double x = random.nextFloat() * 2.0F - 1.0F;
                        double y = random.nextFloat() * 2.0F - 1.0F;
                        double z = random.nextFloat() * 2.0F - 1.0F;
                        double starSize = 0.15F + random.nextFloat() * 0.1F;
                        double length = x * x + y * y + z * z;
                        if (length >= 1.0D || length <= 0.01D) {
                            continue;
                        }
                        length = 1.0D / Math.sqrt(length);
                        x *= length;
                        y *= length;
                        z *= length;
                        double centerX = x * 100.0D;
                        double centerY = y * 100.0D;
                        double centerZ = z * 100.0D;
                        double yaw = Math.atan2(x, z);
                        double yawSin = Math.sin(yaw);
                        double yawCos = Math.cos(yaw);
                        double pitch = Math.atan2(Math.sqrt(x * x + z * z), y);
                        double pitchSin = Math.sin(pitch);
                        double pitchCos = Math.cos(pitch);
                        double roll = random.nextDouble() * Math.PI * 2.0D;
                        double rollSin = Math.sin(roll);
                        double rollCos = Math.cos(roll);
                        for (int corner = 0; corner < 4; corner++) {
                            double horizontal = ((corner & 2) - 1) * starSize;
                            double vertical = (((corner + 1) & 2) - 1) * starSize;
                            double rolledX = horizontal * rollCos - vertical * rollSin;
                            double rolledY = vertical * rollCos + horizontal * rollSin;
                            double pitchedY = rolledX * pitchSin;
                            double pitchedZ = -rolledX * pitchCos;
                            double offsetX = pitchedZ * yawSin - rolledY * yawCos;
                            double offsetZ = rolledY * yawSin + pitchedZ * yawCos;
                            GL11.glVertex3d(centerX + offsetX, centerY + pitchedY, centerZ + offsetZ);
                        }
                    }
                    GL11.glEnd();
                }
                GlStateManager.popMatrix();
            }

            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.disableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.enableFog();
            GlStateManager.disableTexture2D();
            GlStateManager.color(0.0F, 0.0F, 0.0F);

            Vec3d eyes = MinecraftReflectionCompat.positionEyes(viewEntity, partialTicks);
            double belowHorizon = eyes.y - world.getHorizon();
            if (belowHorizon < 0.0D) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(0.0F, 12.0F, 0.0F);
                GL11.glBegin(GL11.GL_QUADS);
                for (int x = -384; x <= 384; x += 64) {
                    for (int z = -384; z <= 384; z += 64) {
                        GL11.glVertex3f(x + 64.0F, -16.0F, z);
                        GL11.glVertex3f(x, -16.0F, z);
                        GL11.glVertex3f(x, -16.0F, z + 64.0F);
                        GL11.glVertex3f(x + 64.0F, -16.0F, z + 64.0F);
                    }
                }
                GL11.glEnd();
                GlStateManager.popMatrix();

                float horizonBottom = -((float) (belowHorizon + 65.0D));
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glColor4ub((byte) 0, (byte) 0, (byte) 0, (byte) 255);
                GL11.glVertex3f(-1.0F, horizonBottom, 1.0F);
                GL11.glVertex3f(1.0F, horizonBottom, 1.0F);
                GL11.glVertex3f(1.0F, -1.0F, 1.0F);
                GL11.glVertex3f(-1.0F, -1.0F, 1.0F);
                GL11.glVertex3f(-1.0F, -1.0F, -1.0F);
                GL11.glVertex3f(1.0F, -1.0F, -1.0F);
                GL11.glVertex3f(1.0F, horizonBottom, -1.0F);
                GL11.glVertex3f(-1.0F, horizonBottom, -1.0F);
                GL11.glVertex3f(1.0F, -1.0F, -1.0F);
                GL11.glVertex3f(1.0F, -1.0F, 1.0F);
                GL11.glVertex3f(1.0F, horizonBottom, 1.0F);
                GL11.glVertex3f(1.0F, horizonBottom, -1.0F);
                GL11.glVertex3f(-1.0F, horizonBottom, -1.0F);
                GL11.glVertex3f(-1.0F, horizonBottom, 1.0F);
                GL11.glVertex3f(-1.0F, -1.0F, 1.0F);
                GL11.glVertex3f(-1.0F, -1.0F, -1.0F);
                GL11.glVertex3f(-1.0F, -1.0F, -1.0F);
                GL11.glVertex3f(-1.0F, -1.0F, 1.0F);
                GL11.glVertex3f(1.0F, -1.0F, 1.0F);
                GL11.glVertex3f(1.0F, -1.0F, -1.0F);
                GL11.glEnd();
            }

            if (belowHorizon < 0.0D) {
                if (provider.isSkyColored()) {
                    GlStateManager.color(
                            red * 0.2F + 0.04F,
                            green * 0.2F + 0.04F,
                            blue * 0.6F + 0.1F);
                } else {
                    GlStateManager.color(red, green, blue);
                }
                GlStateManager.pushMatrix();
                // Only the below-horizon correction remains late. At normal
                // player altitude the lower backing was already drawn before
                // sunrise, stars, and Astral's detail pass.
                GlStateManager.translate(0.0F, -((float) (belowHorizon - 16.0D)), 0.0F);
                GL11.glBegin(GL11.GL_QUADS);
                for (int x = -384; x <= 384; x += 64) {
                    for (int z = -384; z <= 384; z += 64) {
                        GL11.glVertex3f(x + 64.0F, -16.0F, z);
                        GL11.glVertex3f(x, -16.0F, z);
                        GL11.glVertex3f(x, -16.0F, z + 64.0F);
                        GL11.glVertex3f(x + 64.0F, -16.0F, z + 64.0F);
                    }
                }
                GL11.glEnd();
                GlStateManager.popMatrix();
            }
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
            GlStateManager.shadeModel(previousShadeModel);
            GlStateManager.depthMask(previousDepthMask);
            GL11.glDepthMask(previousDepthMask);
            if (previousLighting) {
                GlStateManager.enableLighting();
            } else {
                GlStateManager.disableLighting();
            }
            if (previousFog) {
                GlStateManager.enableFog();
            } else {
                GlStateManager.disableFog();
            }
            if (previousBlend) {
                GlStateManager.enableBlend();
            } else {
                GlStateManager.disableBlend();
            }
            if (previousAlpha) {
                GlStateManager.enableAlpha();
            } else {
                GlStateManager.disableAlpha();
            }
            if (previousTexture2D) {
                GlStateManager.enableTexture2D();
            } else {
                GlStateManager.disableTexture2D();
            }
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.bindTexture(previousTexture);
            GlStateManager.setActiveTexture(previousActiveTexture);
            MinecraftReflectionCompat.glUseProgram(previousProgram);
        }
    }
}
