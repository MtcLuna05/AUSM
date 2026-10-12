package com.luna.ausm.impl.pipeline.compat;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import meldexun.nothirium.api.renderer.IVBOPart;
import net.minecraft.util.BlockRenderLayer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

abstract class NothiriumShadowCompileDiagnostics extends NothiriumShadowVisibleLayerRendering {
    protected String clipSummaryForFirstVertex(int stride) {
        if (stride < POSITION_OFFSET + 12) {
            return "no-position";
        }
        float x = visibleTerrainVertexProbe.getFloat(POSITION_OFFSET);
        float y = visibleTerrainVertexProbe.getFloat(POSITION_OFFSET + 4);
        float z = visibleTerrainVertexProbe.getFloat(POSITION_OFFSET + 8);

        float viewX = NothiriumShadowRenderer.multiplyMatrixVector(visibleTerrainMatrixProbe, 0, x, y, z, 1.0F);
        float viewY = NothiriumShadowRenderer.multiplyMatrixVector(visibleTerrainMatrixProbe, 1, x, y, z, 1.0F);
        float viewZ = NothiriumShadowRenderer.multiplyMatrixVector(visibleTerrainMatrixProbe, 2, x, y, z, 1.0F);
        float viewW = NothiriumShadowRenderer.multiplyMatrixVector(visibleTerrainMatrixProbe, 3, x, y, z, 1.0F);

        float clipX = NothiriumShadowRenderer.multiplyMatrixVector(visibleTerrainProjectionProbe, 0, viewX, viewY, viewZ, viewW);
        float clipY = NothiriumShadowRenderer.multiplyMatrixVector(visibleTerrainProjectionProbe, 1, viewX, viewY, viewZ, viewW);
        float clipZ = NothiriumShadowRenderer.multiplyMatrixVector(visibleTerrainProjectionProbe, 2, viewX, viewY, viewZ, viewW);
        float clipW = NothiriumShadowRenderer.multiplyMatrixVector(visibleTerrainProjectionProbe, 3, viewX, viewY, viewZ, viewW);
        if (!Float.isFinite(clipW) || Math.abs(clipW) < 1.0E-6F) {
            return "view=" + NothiriumShadowRenderer.formatVec4(viewX, viewY, viewZ, viewW)
                    + ",clip=" + NothiriumShadowRenderer.formatVec4(clipX, clipY, clipZ, clipW)
                    + ",ndc=invalid-w";
        }

        return "view=" + NothiriumShadowRenderer.formatVec4(viewX, viewY, viewZ, viewW)
                + ",clip=" + NothiriumShadowRenderer.formatVec4(clipX, clipY, clipZ, clipW)
                + ",ndc=" + NothiriumShadowRenderer.formatVec3(clipX / clipW, clipY / clipW, clipZ / clipW);
    }

    protected static float multiplyMatrixVector(FloatBuffer matrix, int row, float x, float y, float z, float w) {
        return matrix.get(row) * x
                + matrix.get(4 + row) * y
                + matrix.get(8 + row) * z
                + matrix.get(12 + row) * w;
    }

    protected static String matrixSummary(FloatBuffer matrix) {
        return "m00=" + NothiriumShadowRenderer.formatFloat(matrix.get(0))
                + ",m11=" + NothiriumShadowRenderer.formatFloat(matrix.get(5))
                + ",m22=" + NothiriumShadowRenderer.formatFloat(matrix.get(10))
                + ",m23=" + NothiriumShadowRenderer.formatFloat(matrix.get(14))
                + ",m32=" + NothiriumShadowRenderer.formatFloat(matrix.get(11))
                + ",m33=" + NothiriumShadowRenderer.formatFloat(matrix.get(15))
                + ",t=" + NothiriumShadowRenderer.formatVec3(matrix.get(12), matrix.get(13), matrix.get(14));
    }

    protected static String formatVec4(float x, float y, float z, float w) {
        return NothiriumShadowRenderer.formatFloat(x) + '/' + NothiriumShadowRenderer.formatFloat(y) + '/' + NothiriumShadowRenderer.formatFloat(z) + '/' + NothiriumShadowRenderer.formatFloat(w);
    }

    protected static String formatVec3(float x, float y, float z) {
        return NothiriumShadowRenderer.formatFloat(x) + '/' + NothiriumShadowRenderer.formatFloat(y) + '/' + NothiriumShadowRenderer.formatFloat(z);
    }

    protected static String formatFloat(float value) {
        if (!Float.isFinite(value)) {
            return "nan";
        }
        return String.format(Locale.ROOT, "%.4f", value);
    }

    protected int beginProbeQuery(NothiriumShadowRenderer.DrawProbe probe) {
        if (probe == null || !GLContext.getCapabilities().OpenGL15) {
            return 0;
        }
        try {
            int query = GL15.glGenQueries();
            GL15.glBeginQuery(GL15.GL_SAMPLES_PASSED, query);
            return query;
        } catch (RuntimeException | LinkageError exception) {
            return 0;
        }
    }

    protected void finishVisibleTerrainDrawProbe(NothiriumShadowRenderer.DrawProbe probe, int query) {
        if (probe == null) {
            return;
        }

        String samples = "unavailable";
        if (query > 0) {
            try {
                GL15.glEndQuery(GL15.GL_SAMPLES_PASSED);
                samples = Integer.toString(GL15.glGetQueryObjecti(query, GL15.GL_QUERY_RESULT));
            } catch (RuntimeException | LinkageError exception) {
                samples = "error=" + exception.getClass().getSimpleName();
            } finally {
                try {
                    GL15.glDeleteQueries(query);
                } catch (RuntimeException | LinkageError ignored) {
                }
            }
        }

        MainMod.LOGGER.warn(
                "[AUSMNothiriumDrawProbe] call={} layer={} chunk={}/{}/{} camera={}/{}/{} translate={}/{}/{} vbo={} first={} count={} offset={} size={} stride={} vboSize={} pipelineStride={} vertex={} modelView={} uniforms={} samples={} gl={}",
                probe.call(),
                probe.layer(),
                probe.chunkX(),
                probe.chunkY(),
                probe.chunkZ(),
                probe.cameraX(),
                probe.cameraY(),
                probe.cameraZ(),
                probe.chunkX() - probe.cameraX(),
                probe.chunkY() - probe.cameraY(),
                probe.chunkZ() - probe.cameraZ(),
                probe.vbo(),
                probe.first(),
                probe.count(),
                probe.offset(),
                probe.size(),
                probe.stride(),
                probe.vboSize(),
                probe.pipelineStride(),
                probe.vertex(),
                probe.modelView(),
                probe.uniforms(),
                samples,
                probe.gl()
        );
    }

    protected String terrainUniformSummary() {
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (program <= 0) {
            return "program=0";
        }
        return "gbufferModelView=" + self().matrixUniformTranslation(program, "gbufferModelView")
                + ",gbufferModelViewInverse=" + self().matrixUniformTranslation(program, "gbufferModelViewInverse")
                + ",gbufferProjection=" + self().matrixUniformTranslation(program, "gbufferProjection")
                + ",modelViewMatrix=" + self().matrixUniformTranslation(program, "modelViewMatrix");
    }

    protected String matrixUniformTranslation(int program, String name) {
        try {
            int location = GL20.glGetUniformLocation(program, name);
            if (location < 0) {
                return "missing";
            }
            visibleTerrainUniformProbe.clear();
            GL20.glGetUniform(program, location, visibleTerrainUniformProbe);
            return "m03=" + visibleTerrainUniformProbe.get(12)
                    + ",m13=" + visibleTerrainUniformProbe.get(13)
                    + ",m23=" + visibleTerrainUniformProbe.get(14)
                    + ",m33=" + visibleTerrainUniformProbe.get(15);
        } catch (RuntimeException | LinkageError exception) {
            return "error=" + exception.getClass().getSimpleName();
        }
    }

    protected String formatVertexProbe(int stride) {
        float x = visibleTerrainVertexProbe.getFloat(POSITION_OFFSET);
        float y = visibleTerrainVertexProbe.getFloat(POSITION_OFFSET + 4);
        float z = visibleTerrainVertexProbe.getFloat(POSITION_OFFSET + 8);
        int r = visibleTerrainVertexProbe.get(COLOR_OFFSET) & 0xFF;
        int g = visibleTerrainVertexProbe.get(COLOR_OFFSET + 1) & 0xFF;
        int b = visibleTerrainVertexProbe.get(COLOR_OFFSET + 2) & 0xFF;
        int a = visibleTerrainVertexProbe.get(COLOR_OFFSET + 3) & 0xFF;
        float u = stride >= TEX_COORD_OFFSET + 8 ? visibleTerrainVertexProbe.getFloat(TEX_COORD_OFFSET) : Float.NaN;
        float v = stride >= TEX_COORD_OFFSET + 8 ? visibleTerrainVertexProbe.getFloat(TEX_COORD_OFFSET + 4) : Float.NaN;
        int lightU = stride >= LIGHT_COORD_OFFSET + 4 ? visibleTerrainVertexProbe.getShort(LIGHT_COORD_OFFSET) & 0xFFFF : -1;
        int lightV = stride >= LIGHT_COORD_OFFSET + 4 ? visibleTerrainVertexProbe.getShort(LIGHT_COORD_OFFSET + 2) & 0xFFFF : -1;
        StringBuilder builder = new StringBuilder();
        builder.append("pos=").append(x).append('/').append(y).append('/').append(z)
                .append(",color=").append(r).append('/').append(g).append('/').append(b).append('/').append(a)
                .append(",uv=").append(u).append('/').append(v)
                .append(",light=").append(lightU).append('/').append(lightV);
        if (stride >= ExtendedVertexFormats.PIPELINE_BLOCK_NORMAL_OFFSET + 4) {
            builder.append(",normal=")
                    .append(visibleTerrainVertexProbe.get(ExtendedVertexFormats.PIPELINE_BLOCK_NORMAL_OFFSET))
                    .append('/')
                    .append(visibleTerrainVertexProbe.get(ExtendedVertexFormats.PIPELINE_BLOCK_NORMAL_OFFSET + 1))
                    .append('/')
                    .append(visibleTerrainVertexProbe.get(ExtendedVertexFormats.PIPELINE_BLOCK_NORMAL_OFFSET + 2));
        }
        if (stride >= ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET + 8) {
            builder.append(",mcEntity=")
                    .append(visibleTerrainVertexProbe.getShort(ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET) & 0xFFFF)
                    .append('/')
                    .append(visibleTerrainVertexProbe.getShort(ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET + 2) & 0xFFFF)
                    .append('/')
                    .append(visibleTerrainVertexProbe.getShort(ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET + 4) & 0xFFFF)
                    .append('/')
                    .append(visibleTerrainVertexProbe.getShort(ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET + 6) & 0xFFFF);
        }
        if (stride >= ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET + 4) {
            builder.append(",midBlock=")
                    .append(visibleTerrainVertexProbe.get(ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET))
                    .append('/')
                    .append(visibleTerrainVertexProbe.get(ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET + 1))
                    .append('/')
                    .append(visibleTerrainVertexProbe.get(ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET + 2))
                    .append('/')
                    .append(visibleTerrainVertexProbe.get(ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET + 3));
        }
        return builder.toString();
    }
}
