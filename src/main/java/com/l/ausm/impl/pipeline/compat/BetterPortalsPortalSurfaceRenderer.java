package com.l.ausm.impl.pipeline.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.vertex.IBufferBuilderExtension;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

public final class BetterPortalsPortalSurfaceRenderer {
    private static final String VERTEX_SHADER = """
            #version 120
            void main() {
                gl_Position = ftransform();
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 120
            uniform sampler2D uPortalTexture;
            uniform vec2 uScreenSize;
            uniform float uOpacity;
            void main() {
                vec2 uv = gl_FragCoord.xy / uScreenSize;
                gl_FragData[0] = vec4(texture2D(uPortalTexture, uv).rgb, uOpacity);
            }
            """;

    private static Method getBlocksMethod;
    private static Method getLocalRotationMethod;
    private static Method getViewFacingMethod;
    private static boolean reflectionWarningLogged;
    private static boolean shaderWarningLogged;
    private static int program;
    private static int textureUniform = -1;
    private static int screenSizeUniform = -1;
    private static int opacityUniform = -1;
    private static boolean missingFramebufferLogged;

    private BetterPortalsPortalSurfaceRenderer() {
    }

    public static boolean renderPortalSurface(Object renderer, Object portal, Vec3d pos, Framebuffer framebuffer, Object renderPass) {
        if (!BetterPortalsCompat.shouldUseAusmPortalSurfaceReplacement()) {
            return false;
        }
        if (renderer == null || portal == null || pos == null || renderPass == null) {
            return false;
        }
        if (!BetterPortalsCompat.isSeeThroughPortalsEnabled()) {
            return false;
        }

        PortalGeometry geometry = portalGeometry(renderer, portal);
        if (geometry == null || geometry.blocks().isEmpty()) {
            return false;
        }

        if (framebuffer == null) {
            if (!missingFramebufferLogged) {
                missingFramebufferLogged = true;
                MainMod.LOGGER.info("[BetterPortalsCompat] Better Portals child framebuffer is not ready; using its fallback portal surface.");
            }
            return false;
        }

        int shader = ensureProgram();
        if (shader == 0) {
            return false;
        }

        drawPortalSurface(shader, geometry, pos, framebuffer, BetterPortalsCompat.portalOpacity(portal));
        BetterPortalsCompat.markPortalSurfaceCompositeHandled(framebuffer);
        return true;
    }

    private static void drawPortalSurface(int shader, PortalGeometry geometry, Vec3d pos, Framebuffer framebuffer, float opacity) {
        SurfaceRenderState state = new SurfaceRenderState();

        try {
            GlStateManager.enableTexture2D();
            GlStateManager.enableDepth();
            GL11.glDepthMask(true);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            if (opacity < 0.999F) {
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            } else {
                GlStateManager.disableBlend();
            }
            GL11.glColorMask(true, true, true, true);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

            OpenGlHelper.glUseProgram(shader);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, framebuffer.framebufferTexture);
            GL20.glUniform1i(textureUniform, 0);
            GL20.glUniform2f(screenSizeUniform,
                    Math.max(1.0F, framebuffer.framebufferTextureWidth),
                    Math.max(1.0F, framebuffer.framebufferTextureHeight));
            GL20.glUniform1f(opacityUniform, Math.max(0.0F, Math.min(1.0F, opacity)));

            drawFramedGeometry(geometry, pos);
        } finally {
            state.restore();
        }
    }

    private static void drawFramedGeometry(PortalGeometry geometry, Vec3d pos) {
        Vec3d origin = pos.subtract(0.5D, 0.5D, 0.5D);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        forceResetBuffer(buffer);
        try {
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);

            EnumFacing surfaceFacing = geometry.viewFacing().getOpposite();
            for (BlockPos block : geometry.blocks()) {
                buffer.setTranslation(origin.x + block.getX(), origin.y + block.getY(), origin.z + block.getZ());
                renderPartialPortalFace(buffer, surfaceFacing);
            }
            buffer.setTranslation(0.0D, 0.0D, 0.0D);

            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(-1.0F, -1.0F);
            tessellator.draw();
        } finally {
            buffer.setTranslation(0.0D, 0.0D, 0.0D);
            GL11.glPolygonOffset(0.0F, 0.0F);
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            forceResetBuffer(buffer);
        }
    }

    private static void forceResetBuffer(BufferBuilder buffer) {
        if (buffer instanceof IBufferBuilderExtension extension) {
            extension.ausm$forceResetDrawingState();
        } else if (buffer != null) {
            buffer.reset();
        }
    }

    private static void renderPartialPortalFace(BufferBuilder buffer, EnumFacing facing) {
        double x = facing.getXOffset() * 0.5D;
        double y = facing.getYOffset() * 0.5D;
        double z = facing.getZOffset() * 0.5D;
        EnumFacing rotFacing = facing.getAxis() == EnumFacing.Axis.Y ? EnumFacing.NORTH : EnumFacing.UP;

        for (int i = 0; i < 4; i++) {
            rotFacing = rotFacing.rotateAround(facing.getAxis());
            EnumFacing nextRotFacing = facing.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE
                    ? rotFacing
                    : rotFacing.getOpposite();
            buffer.pos(
                    x + rotFacing.getXOffset() * 0.5D + nextRotFacing.getXOffset() * 0.5D + 0.5D,
                    y + rotFacing.getYOffset() * 0.5D + nextRotFacing.getYOffset() * 0.5D + 0.5D,
                    z + rotFacing.getZOffset() * 0.5D + nextRotFacing.getZOffset() * 0.5D + 0.5D
            ).endVertex();
            rotFacing = nextRotFacing;
        }
    }

    @SuppressWarnings("unchecked")
    private static PortalGeometry portalGeometry(Object renderer, Object portal) {
        try {
            if (getBlocksMethod == null) {
                getBlocksMethod = portal.getClass().getMethod("getBlocks");
            }
            if (getLocalRotationMethod == null) {
                getLocalRotationMethod = findMethod(portal.getClass(), "getLocalRotation");
            }
            if (getViewFacingMethod == null) {
                getViewFacingMethod = findMethod(renderer.getClass(), "getViewFacing");
            }

            Object blocksObject = getBlocksMethod.invoke(portal);
            Object rotationObject = getLocalRotationMethod.invoke(portal);
            Object viewFacingObject = getViewFacingMethod.invoke(renderer);
            if (!(blocksObject instanceof Iterable) || !(rotationObject instanceof Rotation) || !(viewFacingObject instanceof EnumFacing)) {
                return null;
            }

            Rotation rotation = (Rotation) rotationObject;
            Set<BlockPos> rotatedBlocks = new LinkedHashSet<>();
            for (Object blockObject : (Iterable<Object>) blocksObject) {
                if (blockObject instanceof BlockPos) {
                    rotatedBlocks.add(((BlockPos) blockObject).rotate(rotation));
                }
            }
            return new PortalGeometry(rotatedBlocks, (EnumFacing) viewFacingObject);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!reflectionWarningLogged) {
                reflectionWarningLogged = true;
                MainMod.LOGGER.warn("[BetterPortalsCompat] AUSM portal surface replacement unavailable; falling back to Better Portals surface renderer", e);
            }
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String name) throws NoSuchMethodException {
        Class<?> cursor = type;
        while (cursor != null) {
            try {
                Method method = cursor.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name + " on " + type.getName());
    }

    private static int ensureProgram() {
        if (program != 0) {
            return program;
        }

        int vertexShader = compileShader(GL20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        if (vertexShader == 0 || fragmentShader == 0) {
            return 0;
        }

        int linkedProgram = GL20.glCreateProgram();
        GL20.glAttachShader(linkedProgram, vertexShader);
        GL20.glAttachShader(linkedProgram, fragmentShader);
        GL20.glLinkProgram(linkedProgram);
        GL20.glDetachShader(linkedProgram, vertexShader);
        GL20.glDetachShader(linkedProgram, fragmentShader);
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);

        if (GL20.glGetProgrami(linkedProgram, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            logShaderFailure("link", GL20.glGetProgramInfoLog(linkedProgram, GL20.glGetProgrami(linkedProgram, GL20.GL_INFO_LOG_LENGTH)));
            GL20.glDeleteProgram(linkedProgram);
            return 0;
        }

        textureUniform = GL20.glGetUniformLocation(linkedProgram, "uPortalTexture");
        screenSizeUniform = GL20.glGetUniformLocation(linkedProgram, "uScreenSize");
        opacityUniform = GL20.glGetUniformLocation(linkedProgram, "uOpacity");
        if (textureUniform < 0 || screenSizeUniform < 0 || opacityUniform < 0) {
            logShaderFailure("uniform", "missing uPortalTexture, uScreenSize, or uOpacity");
            GL20.glDeleteProgram(linkedProgram);
            return 0;
        }

        program = linkedProgram;
        MainMod.LOGGER.info("[BetterPortalsCompat] AUSM portal surface replacement shader initialized");
        return program;
    }

    private static int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            logShaderFailure(type == GL20.GL_VERTEX_SHADER ? "vertex compile" : "fragment compile",
                    GL20.glGetShaderInfoLog(shader, GL20.glGetShaderi(shader, GL20.GL_INFO_LOG_LENGTH)));
            GL20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private static void logShaderFailure(String stage, String info) {
        if (!shaderWarningLogged) {
            shaderWarningLogged = true;
            MainMod.LOGGER.warn("[BetterPortalsCompat] Failed to initialize AUSM portal surface replacement shader at {}: {}", stage, info);
        }
    }

    private record PortalGeometry(Set<BlockPos> blocks, EnumFacing viewFacing) {
    }

    private static final class SurfaceRenderState {
        private static final java.nio.ByteBuffer BOOLEAN_BUFFER = org.lwjgl.BufferUtils.createByteBuffer(4);
        private static final java.nio.FloatBuffer FLOAT_BUFFER = org.lwjgl.BufferUtils.createFloatBuffer(4);

        private final int program;
        private final int activeTexture;
        private final int texture;
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
        private final boolean polygonOffsetFill;
        private final float polygonOffsetFactor;
        private final float polygonOffsetUnits;
        private final boolean[] colorMask;
        private final float[] color;

        private SurfaceRenderState() {
            program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
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
            polygonOffsetFill = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
            polygonOffsetFactor = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR);
            polygonOffsetUnits = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS);
            colorMask = glBoolean4(GL11.GL_COLOR_WRITEMASK);
            color = glFloat4(GL11.GL_CURRENT_COLOR);
        }

        private void restore() {
            OpenGlHelper.glUseProgram(program);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL13.glActiveTexture(activeTexture);
            setCapability(GL11.GL_DEPTH_TEST, depthTest);
            GL11.glDepthMask(depthMask);
            GL11.glDepthFunc(depthFunc);
            setCapability(GL11.GL_ALPHA_TEST, alphaTest);
            GL11.glAlphaFunc(alphaFunc, alphaRef);
            setCapability(GL11.GL_BLEND, blend);
            GlStateManager.tryBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
            setCapability(GL11.GL_POLYGON_OFFSET_FILL, polygonOffsetFill);
            GL11.glPolygonOffset(polygonOffsetFactor, polygonOffsetUnits);
            GL11.glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
            GL11.glColor4f(color[0], color[1], color[2], color[3]);
        }

        private static void setCapability(int capability, boolean enabled) {
            if (enabled) {
                GL11.glEnable(capability);
            } else {
                GL11.glDisable(capability);
            }
        }

        private static boolean glBoolean(int parameter) {
            return GL11.glGetBoolean(parameter);
        }

        private static boolean[] glBoolean4(int parameter) {
            BOOLEAN_BUFFER.clear();
            GL11.glGetBoolean(parameter, BOOLEAN_BUFFER);
            return new boolean[]{
                    BOOLEAN_BUFFER.get(0) != 0,
                    BOOLEAN_BUFFER.get(1) != 0,
                    BOOLEAN_BUFFER.get(2) != 0,
                    BOOLEAN_BUFFER.get(3) != 0
            };
        }

        private static float[] glFloat4(int parameter) {
            FLOAT_BUFFER.clear();
            GL11.glGetFloat(parameter, FLOAT_BUFFER);
            return new float[]{FLOAT_BUFFER.get(0), FLOAT_BUFFER.get(1), FLOAT_BUFFER.get(2), FLOAT_BUFFER.get(3)};
        }
    }
}
