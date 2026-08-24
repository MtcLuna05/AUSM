package com.luna.ausm.impl.pipeline.bloom;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.IntBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

abstract class AusmBloomRendererBase {
    protected static final int HALF_RESOLUTION_DIVISOR = 2;

    protected static final int DEFAULT_BLUR_ITERATIONS = 2;

    protected static final float DEFAULT_BLOOM_STRENGTH = 0.825F;

    protected static final String BLOOM_VERTEX_PATH = "ausm/bloom.vsh";

    protected static final String BLOOM_FRAGMENT_PATH = "ausm/bloom.fsh";

    protected static final String BLOOM_STRENGTH_SETTING = "ausmBloomStrength";

    protected static final String BLOOM_BLUR_ITERATIONS_SETTING = "ausmBloomBlurIterations";

    /**
     * Lets a small experimental pack inspect its parsed bloom RGB without
     * screen blending it into the already-coloured world target.
     */
    protected static final String BLOOM_COMPOSITE_REPLACE_SETTING = "ausmBloomCompositeReplace";

    protected static final float FRAMEBUFFER_BLOOM_STRENGTH = 0.525F;

    protected static final float FRAMEBUFFER_BLOOM_THRESHOLD = 0.86F;

    protected static final boolean FRAMEBUFFER_BLOOM_FALLBACK_ENABLED = false;

    protected static final int BLOOM_RENDER_LOG_LIMIT = 8;

    protected static final int BLOOM_ZERO_RENDER_LOG_LIMIT = 8;

    protected static final int BLOOM_PROBE_LIMIT = 0;

    protected static final int BLOOM_DEPTH_LEAK_PROBE_ATTEMPT_LIMIT = 0;

    protected static final float SHADERLESS_EMISSIVE_DEPTH_BIAS_FACTOR = -1.0F;

    protected static final float SHADERLESS_EMISSIVE_DEPTH_BIAS_UNITS = -4.0F;

    protected static final float SHADERED_FRAMED_BLOOM_SOURCE_SCALE = 0.35F;

    protected final AusmBloomResourceIndex resourceIndex = new AusmBloomResourceIndex();

    protected final IntBuffer viewportBuffer = BufferUtils.createIntBuffer(16);

    protected Framebuffer bloomLayerTarget;

    protected Framebuffer bloomDownsampleTarget;

    protected Framebuffer bloomBlurTarget;

    protected Framebuffer translucentAttenuationTarget;

    protected int bloomDepthTexture;

    protected int finalDepthTexture;

    protected int translucentDepthTexture;

    protected int width = -1;

    protected int height = -1;

    protected int halfWidth = -1;

    protected int halfHeight = -1;

    protected int copyProgram = -1;

    protected int thresholdProgram = -1;

    protected int blurProgram = -1;

    protected int compositeProgram = -1;

    protected int nativeBloomGeometryProgram = -1;

    protected int emissiveExtractProgram = -1;

    protected int translucentAttenuationProgram = -1;

    protected String compositeVertexSource = VERTEX_SHADER;

    protected String compositeFragmentSource = COMPOSITE_FRAGMENT_SHADER;

    protected float bloomStrength = DEFAULT_BLOOM_STRENGTH;

    protected int blurIterations = DEFAULT_BLUR_ITERATIONS;

    protected boolean shaderPackCompositeOverride;

    protected boolean shaderPackCompositeReplace;

    protected boolean layerBloomPending;

    protected boolean translucentAttenuationAvailable;

    protected boolean loggedLayerRenderer;

    protected boolean loggedShaderlessEmissiveRenderer;

    protected boolean loggedProgramFailure;

    protected int bloomCompositeLogs;

    protected int framebufferBloomLogs;

    protected int bloomRenderLogs;

    protected int zeroBloomRenderLogs;

    protected int depthMaskProbeLogs;

    protected int bloomOcclusionProbeLogs;

    protected int bloomFrameProbeCalls;

    protected int bloomCompositeProbeCalls;

    protected int bloomPeakProbeCalls;

    protected int bloomOcclusionQuery;

    protected int bloomDepthProbeFramebuffer;

    protected int bloomDepthLeakProbeCalls;

    protected int bloomDepthLeakProbeAttempts;

    protected BloomPeakProbe pendingBloomPeakProbe;

    protected int depthAttachmentProbeLogs;

    protected LumenizedTicketBridge lumenizedTickets;

    protected boolean globalFacadesBloomResolved;

    protected Method globalFacadesBloomMethod;

    protected Method globalFacadesTranslucentAttenuationMethod;

    protected boolean loggedGlobalFacadesBloomBridge;

    protected static final class BloomLeakProbe {
        final String summary;

        BloomLeakProbe(String summary) {
            this.summary = summary;
        }

        @Override
        public String toString() {
            return summary;
        }
    }

    protected static final class RawBloomSource {
        static final RawBloomSource NONE = new RawBloomSource(-1, -1, 0, 0, 0, null, null);

        final int x;
        final int y;
        final int red;
        final int green;
        final int blue;
        final Float bloomDepth;
        final Float receiverSceneDepth;

        RawBloomSource(int x, int y, int red, int green, int blue,
                       Float bloomDepth, Float receiverSceneDepth) {
            this.x = x;
            this.y = y;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.bloomDepth = bloomDepth;
            this.receiverSceneDepth = receiverSceneDepth;
        }

        @Override
        public String toString() {
            if (x < 0) {
                return "none";
            }
            boolean rejected = bloomDepth != null && receiverSceneDepth != null
                    && bloomDepth > receiverSceneDepth + 0.000001F && receiverSceneDepth < 0.99999F;
            return x + "/" + y + ":" + red + "/" + green + "/" + blue
                    + ",depth=" + AusmBloomRenderer.depthSummary(bloomDepth)
                    + ",receiverDepth=" + AusmBloomRenderer.depthSummary(receiverSceneDepth)
                    + ",blurReject=" + rejected;
        }
    }

    protected static final class BloomPeakProbe {
        final int framebuffer;
        final int width;
        final int height;
        final int x;
        final int y;
        final int red;
        final int green;
        final int blue;
        final int alpha;
        final int nonBlack;

        BloomPeakProbe(int framebuffer, int width, int height, int x, int y,
                       int red, int green, int blue, int alpha, int nonBlack) {
            this.framebuffer = framebuffer;
            this.width = width;
            this.height = height;
            this.x = x;
            this.y = y;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.alpha = alpha;
            this.nonBlack = nonBlack;
        }

        @Override
        public String toString() {
            return framebuffer + "@" + width + "x" + height + ":" + x + "/" + y
                    + "=" + red + "/" + green + "/" + blue + "/" + alpha
                    + ",nonBlack=" + nonBlack;
        }
    }

    protected final class RenderState {
        final int readFramebuffer;
        final int drawFramebuffer;
        final int activeTexture;
        final int texture;
        final int texture0;
        final int program;
        final boolean blend;
        final boolean depthTest;
        final boolean alphaTest;
        final boolean cull;
        final boolean polygonOffsetFill;
        final boolean depthMask;
        final int depthFunc;
        final int alphaFunc;
        final float alphaRef;
        final float polygonOffsetFactor;
        final float polygonOffsetUnits;
        final int blendSrcRgb;
        final int blendDstRgb;
        final int blendSrcAlpha;
        final int blendDstAlpha;
        final int viewportX;
        final int viewportY;
        final int viewportWidth;
        final int viewportHeight;
        final boolean scissorTest;
        final int scissorX;
        final int scissorY;
        final int scissorWidth;
        final int scissorHeight;

        RenderState() {
            readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL13.glActiveTexture(activeTexture);
            program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            blend = GL11.glIsEnabled(GL11.GL_BLEND);
            depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
            cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            polygonOffsetFill = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
            depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
            alphaFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
            alphaRef = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
            polygonOffsetFactor = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR);
            polygonOffsetUnits = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS);
            blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
            blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
            blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
            blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
            viewportBuffer.clear();
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer);
            viewportX = viewportBuffer.get(0);
            viewportY = viewportBuffer.get(1);
            viewportWidth = viewportBuffer.get(2);
            viewportHeight = viewportBuffer.get(3);
            viewportBuffer.clear();
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, viewportBuffer);
            scissorX = viewportBuffer.get(0);
            scissorY = viewportBuffer.get(1);
            scissorWidth = viewportBuffer.get(2);
            scissorHeight = viewportBuffer.get(3);
            scissorTest = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        }

        void restore() {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL11.glViewport(viewportX, viewportY, viewportWidth, viewportHeight);
            GL11.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);
            if (scissorTest) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
            MinecraftReflectionCompat.glUseProgram(program);
            MinecraftReflectionCompat.glStateSetActiveTexture(GL13.GL_TEXTURE0);
            MinecraftReflectionCompat.glStateBindTexture(texture0);
            MinecraftReflectionCompat.glStateSetActiveTexture(activeTexture);
            MinecraftReflectionCompat.glStateBindTexture(texture);
            MinecraftReflectionCompat.glStateDepthMask(depthMask);
            GL11.glDepthFunc(depthFunc);
            MinecraftReflectionCompat.glStateAlphaFunc(alphaFunc, alphaRef);
            GL11.glPolygonOffset(polygonOffsetFactor, polygonOffsetUnits);
            if (polygonOffsetFill) {
                GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            } else {
                GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            }
            MinecraftReflectionCompat.glStateTryBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
            if (blend) {
                MinecraftReflectionCompat.glStateEnableBlend();
            } else {
                MinecraftReflectionCompat.glStateDisableBlend();
            }
            if (depthTest) {
                MinecraftReflectionCompat.glStateEnableDepth();
            } else {
                MinecraftReflectionCompat.glStateDisableDepth();
            }
            if (alphaTest) {
                MinecraftReflectionCompat.glStateEnableAlpha();
            } else {
                MinecraftReflectionCompat.glStateDisableAlpha();
            }
            if (cull) {
                MinecraftReflectionCompat.glStateEnableCull();
            } else {
                MinecraftReflectionCompat.glStateDisableCull();
            }
            MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
            MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        }
    }

    protected static final class LumenizedTicketBridge {
        static final String BLOOM_EFFECT_UTIL = "gregtech.client.utils.BloomEffectUtil";
        static final String EFFECT_RENDER_CONTEXT = "gregtech.client.utils.EffectRenderContext";

        boolean resolved;
        boolean loggedFailure;
        Method preDraw;
        Method draw;
        Method postDraw;
        Method effectContextGetInstance;
        Method effectContextUpdate;
        Field bloomRenders;

        int draw(Entity entity, float partialTicks) {
            if (!resolve()) {
                return 0;
            }

            try {
                preDraw.invoke(null);
                Object context = effectContextGetInstance.invoke(null);
                effectContextUpdate.invoke(context, entity, partialTicks);
                Object mapObject = bloomRenders.get(null);
                if (!(mapObject instanceof Map<?, ?> bloomRenderMap) || bloomRenderMap.isEmpty()) {
                    return 0;
                }

                BufferBuilder buffer = MinecraftReflectionCompat.tessellatorBuffer(MinecraftReflectionCompat.tessellator());
                int rendered = 0;
                Collection<?> ticketLists = bloomRenderMap.values();
                for (Object ticketList : ticketLists) {
                    if (ticketList instanceof List<?>) {
                        draw.invoke(null, buffer, context, ticketList);
                        rendered++;
                    }
                }
                return rendered;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                logFailure("Failed to draw Lumenized custom bloom tickets", error);
                return 0;
            } finally {
                try {
                    postDraw.invoke(null);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                }
            }
        }

        boolean resolve() {
            if (resolved) {
                return draw != null;
            }
            resolved = true;

            try {
                ClassLoader loader = LumenizedTicketBridge.class.getClassLoader();
                Class<?> bloomUtil = Class.forName(BLOOM_EFFECT_UTIL, false, loader);
                Class<?> context = Class.forName(EFFECT_RENDER_CONTEXT, false, loader);
                preDraw = accessible(bloomUtil.getDeclaredMethod("preDraw"));
                postDraw = accessible(bloomUtil.getDeclaredMethod("postDraw"));
                draw = accessible(bloomUtil.getDeclaredMethod("draw", BufferBuilder.class, context, List.class));
                bloomRenders = accessible(bloomUtil.getDeclaredField("BLOOM_RENDERS"));
                effectContextGetInstance = context.getMethod("getInstance");
                effectContextUpdate = context.getMethod("update", Entity.class, float.class);
                return true;
            } catch (ClassNotFoundException ignored) {
                return false;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                logFailure("Failed to resolve Lumenized custom bloom ticket bridge", error);
                return false;
            }
        }

        void logFailure(String message, Throwable throwable) {
            if (!loggedFailure) {
                loggedFailure = true;
                MainMod.LOGGER.warn("[AUSMBloom] {}", message, throwable);
            }
        }

        static Method accessible(Method method) {
            method.setAccessible(true);
            return method;
        }

        static Field accessible(Field field) {
            field.setAccessible(true);
            return field;
        }
    }

    protected static final String VERTEX_SHADER = """
            #version 120
            varying vec2 textureCoords;
            void main() {
                gl_Position = ftransform();
                textureCoords = gl_MultiTexCoord0.st;
            }
            """;

    protected static final String EMISSIVE_EXTRACT_VERTEX_SHADER = """
            #version 120
            attribute vec4 mc_Entity;
            attribute vec4 at_midBlock;
            uniform float forceEmission;
            varying vec2 textureCoords;
            varying vec4 vertexColor;
            varying float vertexEmission;
            void main() {
                gl_Position = ftransform();
                textureCoords = gl_MultiTexCoord0.st;
                vertexColor = gl_Color;
                float rawEmission = at_midBlock.w;
                float metadataEmission = rawEmission >= 0.5 && rawEmission <= 15.5 ? rawEmission / 15.0 : 0.0;
                // GPOM dual slopes keep both materials in one host mesh. Its
                // per-quad provenance marks native BLOOM material with the
                // framed marker even when that material emits no block light.
                // Treat only that marker as a bloom source; ordinary frame
                // geometry and the other half remain dark.
                if (abs(mc_Entity.w - 150.0) < 0.5) {
                    metadataEmission = max(metadataEmission, 0.8);
                }
                vertexEmission = max(metadataEmission, forceEmission);
            }
            """;

    protected static final String NATIVE_BLOOM_GEOMETRY_VERTEX_SHADER = """
            #version 120
            attribute vec4 mc_Entity;
            uniform vec3 ausm_ChunkOffset;
            uniform float framedBloomScale;
            varying vec2 textureCoords;
            varying vec4 vertexColor;
            void main() {
                vec4 position = gl_Vertex + vec4(ausm_ChunkOffset, 0.0);
                gl_Position = gl_ModelViewProjectionMatrix * position;
                textureCoords = gl_MultiTexCoord0.st;
                vertexColor = gl_Color;
                if (abs(mc_Entity.w - 151.0) < 0.5) {
                    vertexColor.rgb *= framedBloomScale;
                }
            }
            """;

    protected static final String NATIVE_BLOOM_GEOMETRY_FRAGMENT_SHADER = """
            #version 120
            uniform sampler2D terrain;
            varying vec2 textureCoords;
            varying vec4 vertexColor;
            void main() {
                vec4 color = texture2D(terrain, textureCoords) * vertexColor;
                if (color.a <= 0.1) {
                    discard;
                }
                gl_FragColor = color;
            }
            """;

    protected static final String EMISSIVE_EXTRACT_FRAGMENT_SHADER = """
            #version 120
            uniform sampler2D terrain;
            varying vec2 textureCoords;
            varying vec4 vertexColor;
            varying float vertexEmission;
            void main() {
                if (vertexEmission <= 0.0) {
                    discard;
                }
            vec4 albedo = texture2D(terrain, textureCoords) * vertexColor;
            if (albedo.a <= 0.003921569) {
                discard;
            }
            float emissionMask = smoothstep(0.04, 0.45, vertexEmission);
            vec3 bloom = albedo.rgb * (1.15 + vertexEmission * 4.25) * emissionMask;
            float bloomPeak = max(bloom.r, max(bloom.g, bloom.b));
            bloom /= 1.0 + max(bloomPeak - 1.0, 0.0) * 0.5;
            gl_FragColor = vec4(bloom, albedo.a * emissionMask);
            }
            """;

    protected static final String TRANSLUCENT_ATTENUATION_VERTEX_SHADER = """
            #version 120
            uniform vec3 ausm_ChunkOffset;
            varying vec2 textureCoords;
            varying vec4 vertexColor;
            void main() {
                vec4 position = gl_Vertex + vec4(ausm_ChunkOffset, 0.0);
                gl_Position = gl_ModelViewProjectionMatrix * position;
                textureCoords = gl_MultiTexCoord0.st;
                vertexColor = gl_Color;
            }
            """;

    protected static final String TRANSLUCENT_ATTENUATION_FRAGMENT_SHADER = """
            #version 120
            uniform sampler2D terrain;
            varying vec2 textureCoords;
            varying vec4 vertexColor;
            void main() {
                vec4 albedo = texture2D(terrain, textureCoords) * vertexColor;
                if (albedo.a <= 0.003921569) {
                    discard;
                }
                float opacity = clamp(albedo.a, 0.0, 1.0);
                vec3 tint = clamp(albedo.rgb, vec3(0.04), vec3(1.0));
                // White glass loses a little bloom energy; coloured glass also
                // filters the transmitted channels. Two visible faces naturally
                // compound, as light entering and leaving a block should.
                vec3 filtered = mix(vec3(0.72), tint, 0.65);
                vec3 transmission = mix(vec3(1.0), filtered, opacity);
                gl_FragColor = vec4(clamp(transmission, vec3(0.08), vec3(1.0)), 1.0);
            }
            """;

    protected static final String COPY_FRAGMENT_SHADER = """
            #version 120
            uniform sampler2D source;
            uniform sampler2D bloomDepth;
            uniform sampler2D sceneDepth;
            uniform vec2 sourceTexel;
            uniform int depthAware;
            varying vec2 textureCoords;
            void main() {
                if (depthAware == 0) {
                    gl_FragColor = texture2D(source, textureCoords);
                    return;
                }
                vec2 offset = sourceTexel * 0.5;
                vec2 uv0 = textureCoords + vec2(-offset.x, -offset.y);
                vec2 uv1 = textureCoords + vec2( offset.x, -offset.y);
                vec2 uv2 = textureCoords + vec2(-offset.x,  offset.y);
                vec2 uv3 = textureCoords + vec2( offset.x,  offset.y);
                vec4 c0 = texture2D(source, uv0);
                vec4 c1 = texture2D(source, uv1);
                vec4 c2 = texture2D(source, uv2);
                vec4 c3 = texture2D(source, uv3);
                vec3 color = (c0.rgb + c1.rgb + c2.rgb + c3.rgb) * 0.25;
                float depth = 1.0;
                if (max(c0.r, max(c0.g, c0.b)) > 0.00001) depth = min(depth, texture2D(bloomDepth, uv0).r);
                if (max(c1.r, max(c1.g, c1.b)) > 0.00001) depth = min(depth, texture2D(bloomDepth, uv1).r);
                if (max(c2.r, max(c2.g, c2.b)) > 0.00001) depth = min(depth, texture2D(bloomDepth, uv2).r);
                if (max(c3.r, max(c3.g, c3.b)) > 0.00001) depth = min(depth, texture2D(bloomDepth, uv3).r);
                gl_FragColor = vec4(color, depth);
            }
            """;

    protected static final String THRESHOLD_FRAGMENT_SHADER = """
            #version 120
            uniform sampler2D source;
            uniform float threshold;
            varying vec2 textureCoords;
            void main() {
                vec3 color = texture2D(source, textureCoords).rgb;
                float brightness = dot(color, vec3(0.2126, 0.7152, 0.0722));
                float bloom = smoothstep(threshold, 1.0, brightness);
                gl_FragColor = vec4(color * bloom, 1.0);
            }
            """;

    protected static final String BLUR_FRAGMENT_SHADER = """
            #version 120
            uniform sampler2D source;
            uniform sampler2D bloomDepth;
            uniform sampler2D sceneDepth;
            uniform vec2 direction;
            uniform int depthAware;
            varying vec2 textureCoords;
            void main() {
                vec4 c0 = texture2D(source, textureCoords);
                vec4 c1 = texture2D(source, textureCoords + direction * 1.3846153846);
                vec4 c2 = texture2D(source, textureCoords - direction * 1.3846153846);
                vec4 c3 = texture2D(source, textureCoords + direction * 3.2307692308);
                vec4 c4 = texture2D(source, textureCoords - direction * 3.2307692308);
                vec3 sum = c0.rgb * 0.2270270270;
                sum += c1.rgb * 0.3162162162;
                sum += c2.rgb * 0.3162162162;
                sum += c3.rgb * 0.0702702703;
                sum += c4.rgb * 0.0702702703;
                float depth = 1.0;
                if (depthAware == 1) {
                    if (max(c0.r, max(c0.g, c0.b)) > 0.000001) depth = min(depth, c0.a);
                    if (max(c1.r, max(c1.g, c1.b)) > 0.000001) depth = min(depth, c1.a);
                    if (max(c2.r, max(c2.g, c2.b)) > 0.000001) depth = min(depth, c2.a);
                    if (max(c3.r, max(c3.g, c3.b)) > 0.000001) depth = min(depth, c3.a);
                    if (max(c4.r, max(c4.g, c4.b)) > 0.000001) depth = min(depth, c4.a);
                }
                gl_FragColor = vec4(sum, depth);
            }
            """;

    protected static final String COMPOSITE_FRAGMENT_SHADER = """
            #version 120
            uniform sampler2D bloom;
            uniform sampler2D preHandDepth;
            uniform sampler2D postHandDepth;
            uniform sampler2D bloomDepth;
            uniform sampler2D finalDepth;
            uniform sampler2D translucentTransmission;
            uniform sampler2D translucentDepth;
            uniform float strength;
            uniform int useHandMask;
            uniform int useSceneDepthMask;
            uniform int useBloomTextureDepth;
            uniform int useTranslucentDampening;
            varying vec2 textureCoords;
            void main() {
                vec4 bloomSample = texture2D(bloom, textureCoords);
                float emissionDepth = useBloomTextureDepth == 1
                        ? bloomSample.a
                        : texture2D(bloomDepth, textureCoords).r;
                if (useSceneDepthMask == 1) {
                    float sceneDepth = texture2D(finalDepth, textureCoords).r;
                    if (emissionDepth > sceneDepth + 0.00002 && sceneDepth < 0.99999) {
                        discard;
                    }
                }
                if (useHandMask == 1) {
                    float preHand = texture2D(preHandDepth, textureCoords).r;
                    float postHand = texture2D(postHandDepth, textureCoords).r;
                    if (postHand < preHand - 0.00005 && postHand < 0.99999) {
                        discard;
                    }
                }
                vec3 source = bloomSample.rgb;
                if (useTranslucentDampening == 1) {
                    float filterDepth = texture2D(translucentDepth, textureCoords).r;
                    if (filterDepth + 0.00002 < emissionDepth && filterDepth < 0.99999) {
                        source *= texture2D(translucentTransmission, textureCoords).rgb;
                    }
                }
                source = source / (1.0 + max(source, vec3(0.0)));
                gl_FragColor = vec4(source * strength, 1.0);
            }
            """;

    protected final AusmBloomRenderer self() {
        return (AusmBloomRenderer) this;
    }
}
