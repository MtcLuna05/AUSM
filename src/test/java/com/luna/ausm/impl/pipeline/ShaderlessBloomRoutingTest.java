package com.luna.ausm.impl.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.LdcInsnNode;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Routing contracts without initializing Minecraft or an OpenGL context. */
final class ShaderlessBloomRoutingTest {
    @Test
    void chunkOffsetsResolveTheLiveProgramInterface() throws IOException {
        MethodNode uniform = method("compat/NothiriumShadowCompileScheduling", "chunkOffsetUniformLocation");
        MethodNode attribute = method("compat/NothiriumShadowCompileScheduling", "chunkOffsetInstanceAttributeLocation");
        assertTrue(calls(uniform, "glGetUniformLocation"));
        assertTrue(calls(attribute, "glGetAttribLocation"));
        for (MethodNode lookup : new MethodNode[]{uniform, attribute}) {
            assertFalse(calls(lookup, "get"));
            assertFalse(calls(lookup, "put"));
        }
    }

    @Test
    void bloomSamplerBindsSynchronizeTheActiveTextureCache() throws IOException {
        MethodNode bind = method("bloom/AusmBloomProgramLifecycle", "bindTextureUniform");
        boolean selected = false;
        for (AbstractInsnNode instruction : bind.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                if (call.name.equals("glStateSetActiveTexture")) selected = true;
                if (call.name.equals("glStateBindTexture")) assertTrue(selected);
            }
        }
        assertTrue(selected);
        assertTrue(calls(bind, "glBindTexture"));
        MethodNode restore = method("bloom/AusmBloomRendererBase$RenderState", "restore");
        assertTrue(calls(restore, "glActiveTexture"));
        assertTrue(calls(restore, "glBindTexture"));
    }

    @Test
    void handRestoresAlphaReplacementAfterBindingItsProgram() throws IOException {
        MethodNode prepare = method("PipelineRuntimeDiagnosticsState7", "prepareVanillaHandRenderState");
        boolean bound = false;
        boolean restored = false;
        for (AbstractInsnNode instruction : prepare.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                if (call.name.equals("bindPass")) bound = true;
                if (call.owner.equals("org/lwjgl/opengl/GL14") && call.name.equals("glBlendFuncSeparate")) {
                    assertTrue(bound);
                    assertTrue(instruction.getPrevious().getOpcode() == org.objectweb.asm.Opcodes.ICONST_0);
                    assertTrue(instruction.getPrevious().getPrevious().getOpcode() == org.objectweb.asm.Opcodes.ICONST_1);
                    restored = true;
                }
            }
        }
        assertTrue(restored);
    }

    @Test
    void finalBloomMasksHandAgainstHandFreeDepth() throws IOException {
        MethodNode render = method("PipelineBloomRendering", "renderPostWorldBloom");
        int snapshots = 0;
        for (AbstractInsnNode instruction : render.instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals("getDepthSamplerTexture")) {
                // DEPTHTEX2_SNAPSHOT = 1: depthtex1 already contains the late hand.
                assertTrue(instruction.getPrevious().getOpcode() == org.objectweb.asm.Opcodes.ICONST_1);
                snapshots++;
            }
        }
        assertTrue(snapshots == 1);
        assertTrue(calls(render, "getDepthTexture"));
    }

    @Test
    void lateHandUpdatesOpaqueSnapshotWithoutReplacingHandFreeSnapshot() throws IOException {
        MethodNode finish = method("PipelineDeferredPassOrchestration", "finishHand");
        assertTrue(calls(finish, "merge"));
        assertFalse(calls(finish, "copyPreHandDepth"));
        assertFalse(calls(finish, "copyPreTranslucentDepth"));
    }

    @Test
    void handDepthCompressionIsCentredInWindowDepth() throws IOException {
        for (MethodNode method : new MethodNode[]{
                method("PipelineDeferredPassOrchestration", "beginHand"),
                method("PipelineRuntimeDiagnosticsState7", "prepareVanillaHandRenderState")}) {
            boolean found = false;
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.name.equals("glDepthRange")) {
                    assertTrue(instruction.getPrevious() instanceof LdcInsnNode far
                            && Double.valueOf(0.5625D).equals(far.cst));
                    assertTrue(instruction.getPrevious().getPrevious() instanceof LdcInsnNode near
                            && Double.valueOf(0.4375D).equals(near.cst));
                    found = true;
                }
            }
            assertTrue(found);
        }
    }

    @Test
    void noTerrainMaterialExtractionEntrypointsRemain() throws IOException {
        for (String owner : new String[]{"PipelineBloomRendering", "PipelineChunkUpdateTracking",
                "bloom/AusmBloomRenderPasses", "bloom/AusmBloomProgramLifecycle"}) {
            for (MethodNode method : node(owner).methods) {
                assertFalse(method.name.contains("EmissiveTerrainBloom"));
                assertFalse(method.name.contains("BloomExtraction"));
                assertFalse(method.name.equals("emissiveExtractProgram"));
                assertFalse(method.name.equals("setShaderlessForceEmission"));
                assertFalse(method.name.equals("renderFramebufferBloom"));
            }
        }
    }

    @Test
    void bloomEligibilityIsTextureBased() throws IOException {
        MethodNode method = method("PipelineRuntimeCompatibilityState", "stateUsesTextureBloomSource");
        assertTrue(calls(method, "stateHasBloomResourceGeometry"));
        assertFalse(calls(method, "blockShaderlessMaterialEmission"));
        assertFalse(calls(method, "blockRenderEmissionForState"));
        assertFalse(calls(method, "isLumenizedBloomState"));
    }

    @Test
    void framedBloomDoesNotUseLightLevel() throws IOException {
        MethodNode method = method("PipelineRuntimeDiagnosticsState0", "containedFrameHasBloom");
        assertTrue(calls(method, "stateUsesTextureBloomSource"));
        assertFalse(calls(method, "stateHasBloomLayerGeometry"));
        MethodNode provenance = method("PipelineRuntimeCompatibilityState", "applyFramedQuadMaterial");
        assertTrue(calls(provenance, "stateUsesTextureBloomSource"));
    }

    @Test
    void bloomShadersDoNotExtractEmissionOrFramebufferBrightness() throws IOException {
        ClassNode node = node("bloom/AusmBloomRendererBase");
        assertTrue(node.fields.stream().anyMatch(field -> field.name.equals("NATIVE_BLOOM_GEOMETRY_VERTEX_SHADER")));
        assertFalse(node.fields.stream().anyMatch(field -> field.name.startsWith("EMISSIVE_EXTRACT")
                || field.name.equals("THRESHOLD_FRAGMENT_SHADER")));
    }

    private static boolean calls(MethodNode method, String name) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals(name)) return true;
        }
        return false;
    }

    private static MethodNode method(String owner, String name) throws IOException {
        return node(owner).methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }

    private static ClassNode node(String owner) throws IOException {
        try (InputStream input = Objects.requireNonNull(ShaderlessBloomRoutingTest.class.getResourceAsStream(
                "/com/luna/ausm/impl/pipeline/" + owner + ".class"))) {
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }
}
