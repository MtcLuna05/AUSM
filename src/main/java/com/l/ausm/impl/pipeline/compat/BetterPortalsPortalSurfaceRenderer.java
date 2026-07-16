package com.l.ausm.impl.pipeline.compat;

import com.l.ausm.impl.util.MinecraftReflectionCompat;
import com.l.ausm.impl.MainMod;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
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
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
            GL11.glDepthMask(true);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            if (opacity < 0.999F) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
            }
            GL11.glColorMask(true, true, true, true);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);

            com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(shader);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferTexture(framebuffer));
            GL20.glUniform1i(textureUniform, 0);
            GL20.glUniform2f(screenSizeUniform,
                    Math.max(1.0F, com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt((framebuffer), 0, "field_147622_a", "framebufferTextureWidth")),
                    Math.max(1.0F, com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt((framebuffer), 0, "field_147620_b", "framebufferTextureHeight")));
            GL20.glUniform1f(opacityUniform, Math.max(0.0F, Math.min(1.0F, opacity)));

            drawFramedGeometry(geometry, pos);
        } finally {
            state.restore();
        }
    }

    private static void drawFramedGeometry(PortalGeometry geometry, Vec3d pos) {
        Vec3d origin = com.l.ausm.impl.util.MinecraftReflectionCompat.vecSubtract(pos, 0.5D, 0.5D, 0.5D);
        Tessellator tessellator = com.l.ausm.impl.util.MinecraftReflectionCompat.tessellator();
        BufferBuilder buffer = com.l.ausm.impl.util.MinecraftReflectionCompat.tessellatorBuffer(tessellator);
        forceResetBuffer(buffer);
        try {
            com.l.ausm.impl.util.MinecraftReflectionCompat.bufferBegin(buffer, GL11.GL_QUADS, com.l.ausm.impl.util.MinecraftReflectionCompat.field(net.minecraft.client.renderer.vertex.DefaultVertexFormats.class, net.minecraft.client.renderer.vertex.VertexFormat.class, null, "field_181705_e", "POSITION"));

            EnumFacing surfaceFacing = com.l.ausm.impl.util.MinecraftReflectionCompat.facingOpposite(geometry.viewFacing());
            for (BlockPos block : geometry.blocks()) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.bufferSetTranslation(buffer,
                        com.l.ausm.impl.util.MinecraftReflectionCompat.vecX(origin) + com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(block),
                        com.l.ausm.impl.util.MinecraftReflectionCompat.vecY(origin) + com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(block),
                        com.l.ausm.impl.util.MinecraftReflectionCompat.vecZ(origin) + com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(block));
                renderPartialPortalFace(buffer, surfaceFacing);
            }
            com.l.ausm.impl.util.MinecraftReflectionCompat.bufferSetTranslation(buffer, 0.0D, 0.0D, 0.0D);

            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(-1.0F, -1.0F);
            com.l.ausm.impl.util.MinecraftReflectionCompat.tessellatorDraw(tessellator);
        } finally {
            com.l.ausm.impl.util.MinecraftReflectionCompat.bufferSetTranslation(buffer, 0.0D, 0.0D, 0.0D);
            GL11.glPolygonOffset(0.0F, 0.0F);
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            forceResetBuffer(buffer);
        }
    }

    private static void forceResetBuffer(BufferBuilder buffer) {
        com.l.ausm.impl.util.MinecraftReflectionCompat.forceResetBufferDrawingState(buffer);
    }

    private static void renderPartialPortalFace(BufferBuilder buffer, EnumFacing facing) {
        double x = com.l.ausm.impl.util.MinecraftReflectionCompat.facingXOffset(facing) * 0.5D;
        double y = com.l.ausm.impl.util.MinecraftReflectionCompat.facingYOffset(facing) * 0.5D;
        double z = com.l.ausm.impl.util.MinecraftReflectionCompat.facingZOffset(facing) * 0.5D;
        EnumFacing.Axis axis = com.l.ausm.impl.util.MinecraftReflectionCompat.facingAxis(facing);
        EnumFacing rotFacing = axis == EnumFacing.Axis.Y ? EnumFacing.NORTH : EnumFacing.UP;

        for (int i = 0; i < 4; i++) {
            rotFacing = com.l.ausm.impl.util.MinecraftReflectionCompat.facingRotateAround(rotFacing, axis);
            EnumFacing nextRotFacing = com.l.ausm.impl.util.MinecraftReflectionCompat.facingAxisDirection(facing) == EnumFacing.AxisDirection.POSITIVE
                    ? rotFacing
                    : com.l.ausm.impl.util.MinecraftReflectionCompat.facingOpposite(rotFacing);
            com.l.ausm.impl.util.MinecraftReflectionCompat.bufferPosEnd(buffer,
                    x + com.l.ausm.impl.util.MinecraftReflectionCompat.facingXOffset(rotFacing) * 0.5D + com.l.ausm.impl.util.MinecraftReflectionCompat.facingXOffset(nextRotFacing) * 0.5D + 0.5D,
                    y + com.l.ausm.impl.util.MinecraftReflectionCompat.facingYOffset(rotFacing) * 0.5D + com.l.ausm.impl.util.MinecraftReflectionCompat.facingYOffset(nextRotFacing) * 0.5D + 0.5D,
                    z + com.l.ausm.impl.util.MinecraftReflectionCompat.facingZOffset(rotFacing) * 0.5D + com.l.ausm.impl.util.MinecraftReflectionCompat.facingZOffset(nextRotFacing) * 0.5D + 0.5D
            );
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
        private final int texture0;
        private final int activeTextureBinding;
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
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL13.glActiveTexture(activeTexture);
            activeTextureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
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
            com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(program);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateSetActiveTexture(GL13.GL_TEXTURE0);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(texture0);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateSetActiveTexture(activeTexture);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(activeTextureBinding);
            setManagedCapability(GL11.GL_DEPTH_TEST, depthTest);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(depthMask);
            com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(net.minecraft.client.renderer.GlStateManager.class, new String[] {"func_179143_c", "depthFunc"}, new Class<?>[] {int.class}, (depthFunc));;
            setManagedCapability(GL11.GL_ALPHA_TEST, alphaTest);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(alphaFunc, alphaRef);
            setManagedCapability(GL11.GL_BLEND, blend);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
            setCapability(GL11.GL_POLYGON_OFFSET_FILL, polygonOffsetFill);
            GL11.glPolygonOffset(polygonOffsetFactor, polygonOffsetUnits);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(color[0], color[1], color[2], color[3]);
        }

        private static void setManagedCapability(int capability, boolean enabled) {
            switch (capability) {
                case GL11.GL_DEPTH_TEST -> {
                    if (enabled) {
                        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
                    } else {
                        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableDepth();
                    }
                }
                case GL11.GL_ALPHA_TEST -> {
                    if (enabled) {
                        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
                    } else {
                        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableAlpha();
                    }
                }
                case GL11.GL_BLEND -> {
                    if (enabled) {
                        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
                    } else {
                        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
                    }
                }
                default -> setCapability(capability, enabled);
            }
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
