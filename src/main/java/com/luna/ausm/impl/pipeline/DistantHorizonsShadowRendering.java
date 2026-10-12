package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.fbo.Attachment;
import com.luna.ausm.api.pipeline.shader.ProgramId;
import com.luna.ausm.api.pipeline.shader.RenderPass;
import com.luna.ausm.api.pipeline.shader.ShaderProgramSource;
import com.luna.ausm.impl.client.DistantHorizonsCompat;
import com.luna.ausm.impl.pipeline.pack.DistantHorizonsVertexTransform;
import com.luna.ausm.impl.pipeline.pack.ShaderPack;
import com.luna.ausm.impl.pipeline.pack.ShaderProperties;
import com.luna.ausm.impl.pipeline.pack.ShaderDimensionContext;
import com.luna.ausm.impl.pipeline.matrix.MatrixState;
import com.luna.ausm.impl.pipeline.shader.ShaderCompiler;
import com.luna.ausm.impl.pipeline.shader.ShaderProgram;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/** Separate LOD caster program sharing the terrain shadow targets, following
 * Iris compat/dh/LodRendererEvents and shadows/ShadowRenderer (branch 26.1).
 * DH 3.3.3 exposes no public shadow draw entrypoint, so its optional bridge
 * submits the same uploaded buffers under a separate light-space selection.
 */
public final class DistantHorizonsShadowRendering {
    private static final String VERTEX_PATH = "shaders/program/ausm_dh_shadow.vsh";
    private static final String FRAGMENT_PATH = "shaders/program/ausm_dh_shadow.fsh";
    // Complementary r5.8 has receivers but no dh_shadow program. Match its
    // shadow distortion and opaque shadow colors without atlas/voxel attributes.
    private static final String SOURCE = """
            #version 330 compatibility
            #include "/lib/common.glsl"
            #ifdef VERTEX_SHADER
            out float ausmShadowHeight;
            void main() {
                vec4 position = gl_Vertex;
                ausmShadowHeight = position.y;
                gl_Position = shadowProjection * shadowModelView * position;
                float radius = length(gl_Position.xy);
                gl_Position.xy /= radius * shadowMapBias + (1.0 - shadowMapBias);
                gl_Position.z *= 0.2;
            }
            #endif
            #ifdef FRAGMENT_SHADER
            in float ausmShadowHeight;
            void main() {
                /* DRAWBUFFERS:01 */
                gl_FragData[0] = vec4(0.0, 0.0, 0.0, 1.0);
                #if SHADOW_QUALITY >= 1
                    float shaftAlpha = 1.0;
                    #if defined LIGHTSHAFTS_ACTIVE && LIGHTSHAFT_BEHAVIOUR == 1 && defined OVERWORLD
                        shaftAlpha = 0.25 + max(0.0, ausmShadowHeight * 0.05);
                    #endif
                    gl_FragData[1] = vec4(0.0, 0.0, 0.0, shaftAlpha);
                #endif
            }
            #endif
            """;
    private static ShaderProgram program;
    private static int vertexArray;
    private static final float[] CLIP_MATRIX = new float[16];

    private DistantHorizonsShadowRendering() { }

    static void initialize(ShaderPack pack, ShaderProperties properties) {
        release();
        if (!ShadedDistantHorizonsRendering.requestedFor(pack.getName())) return;
        ShaderPack casterPack = new ShaderPack() {
            @Override public String getName() { return pack.getName(); }
            @Override public boolean hasResource(String path) {
                return VERTEX_PATH.equals(path) || FRAGMENT_PATH.equals(path) || pack.hasResource(path);
            }
            @Override public InputStream getResourceAsStream(String path) throws IOException {
                if (VERTEX_PATH.equals(path) || FRAGMENT_PATH.equals(path)) {
                    boolean vertex = VERTEX_PATH.equals(path);
                    String dimension = switch (ShaderDimensionContext.currentDimensionId()) {
                        case -1 -> "NETHER";
                        case 1 -> "END";
                        default -> "OVERWORLD";
                    };
                    String source = SOURCE.replace("#include", "#define " + (vertex ? "VERTEX_SHADER" : "FRAGMENT_SHADER")
                            + "\n#define " + dimension + "\n#define DH_SHADOW\n#include");
                    if (vertex) source = DistantHorizonsVertexTransform.transform(source);
                    return new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8));
                }
                return pack.getResourceAsStream(path);
            }
            @Override public void close() { }
        };
        program = ShaderCompiler.compileSource(casterPack, properties, new ShaderProgramSource(
                ProgramId.SHADOW, "dh_shadow", VERTEX_PATH, null, null, null, null, null,
                null, null, FRAGMENT_PATH, null, properties.directivesFor(ProgramId.SHADOW)), RenderPass.SHADOW);
    }

    static int render(PipelineContext context, float partialTicks) {
        if (program == null || !ShadedDistantHorizonsRendering.requested()
                || !ShadedDistantHorizonsRendering.shaderpackOverrideActive
                || !context.renderingShadowMap || context.isBetterPortalsExternalWorldTarget()) return 0;
        int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int oldVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int oldArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_POLYGON_BIT | GL11.GL_VIEWPORT_BIT);
        try {
            context.shadowFramebuffer.bindForProgramWrite(Attachment.COLOR, Attachment.DEPTH);
            program.bind();
            context.bindProgramResources(RenderPass.SHADOW, program);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(true);
            GL11.glColorMask(true, true, true, true);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            if (vertexArray == 0) vertexArray = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(vertexArray);
            var projection = MatrixState.shadowProjection();
            var modelView = MatrixState.shadowModelView();
            for (int column = 0; column < 4; column++) {
                for (int row = 0; row < 4; row++) {
                    float value = 0;
                    for (int k = 0; k < 4; k++) value += projection.get(k * 4 + row) * modelView.get(column * 4 + k);
                    CLIP_MATRIX[column * 4 + row] = value;
                }
            }
            return DistantHorizonsCompat.renderShadow(partialTicks);
        } finally {
            GL30.glBindVertexArray(oldVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, oldArrayBuffer);
            GL20.glUseProgram(oldProgram);
            GL11.glPopAttrib();
            context.shadowFramebuffer.bindForRendering();
        }
    }

    /** Conservative column bounds in light space, independent of the view camera.
     * Padding covers Complementary's radial distortion; its depth is compressed fivefold.
     */
    public static boolean intersects(double x, double y, double z, double width, double height) {
        for (int axis = 0; axis < 3; axis++) {
            double minimum = Double.POSITIVE_INFINITY;
            double maximum = Double.NEGATIVE_INFINITY;
            for (int corner = 0; corner < 8; corner++) {
                double px = x + ((corner & 1) == 0 ? 0 : width);
                double py = y + ((corner & 2) == 0 ? 0 : height);
                double pz = z + ((corner & 4) == 0 ? 0 : width);
                double value = CLIP_MATRIX[axis] * px + CLIP_MATRIX[4 + axis] * py
                        + CLIP_MATRIX[8 + axis] * pz + CLIP_MATRIX[12 + axis];
                minimum = Math.min(minimum, value);
                maximum = Math.max(maximum, value);
            }
            double extent = axis == 2 ? 5.0 : 2.0;
            if (minimum > extent || maximum < -extent) return false;
        }
        return true;
    }

    public static void draw(int vbo, int ibo, int indexType, int vertices, float x, float y, float z) {
        GL20.glUniform3f(program.getUniformLocation("ausmDhModelOffset"), x, y, z);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ibo);
        GL20.glEnableVertexAttribArray(0);
        GL30.glVertexAttribIPointer(0, 4, GL11.GL_UNSIGNED_SHORT, 16, 0L);
        GL11.glDrawElements(GL11.GL_TRIANGLES, vertices / 4 * 6, indexType, 0L);
    }

    static void release() {
        if (program != null) program.delete();
        program = null;
        if (vertexArray != 0) GL30.glDeleteVertexArrays(vertexArray);
        vertexArray = 0;
    }
}
