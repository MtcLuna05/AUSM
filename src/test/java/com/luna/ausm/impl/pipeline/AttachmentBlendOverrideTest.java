package com.luna.ausm.impl.pipeline;

import java.io.InputStream;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bytecode contracts for blend overrides, without requiring an OpenGL context. */
final class AttachmentBlendOverrideTest {
    @Test
    void gbufferAttachmentOverridesDoNotEnableGlobalBlending() throws Exception {
        assertGlobalEnableIsGuarded("PipelineRuntimeDiagnosticsState8", "applyBlendMode");
    }

    @Test
    void fullscreenAttachmentOverridesDoNotEnableGlobalBlending() throws Exception {
        assertGlobalEnableIsGuarded("PipelineWorldFramebufferFinalization", "applyFullscreenArrayRenderState");
    }

    private static void assertGlobalEnableIsGuarded(String owner, String name) throws Exception {
        ClassNode type = new ClassNode();
        try (InputStream input = Objects.requireNonNull(AttachmentBlendOverrideTest.class.getResourceAsStream(
                "/com/luna/ausm/impl/pipeline/" + owner + ".class"))) {
            new ClassReader(input).accept(type, 0);
        }
        MethodNode method = type.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
        int enables = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (!(instruction instanceof MethodInsnNode call) || !call.name.equals("glStateEnableBlend")) continue;
            enables++;
            AbstractInsnNode previous = call.getPrevious();
            while (previous.getOpcode() < 0) previous = previous.getPrevious();
            JumpInsnNode guard = assertInstanceOf(JumpInsnNode.class, previous);
            assertEquals(Opcodes.IFNULL, guard.getOpcode(), "No global override must skip global enable");
            assertTrue(method.instructions.indexOf(guard.label) > method.instructions.indexOf(call));
        }
        assertEquals(1, enables);
    }
}
