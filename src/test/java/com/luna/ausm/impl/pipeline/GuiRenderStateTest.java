package com.luna.ausm.impl.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GUI boundary contracts that do not require a live OpenGL context. */
final class GuiRenderStateTest {
    @Test
    void indexedBlendResetAlsoSynchronizesMinecraftCache() throws IOException {
        MethodNode reset = method("pipeline/PipelineGlState", "resetIndexedBlendState");
        List<String> calls = new ArrayList<>();
        for (AbstractInsnNode instruction : reset.instructions) {
            if (instruction instanceof MethodInsnNode call) calls.add(call.name);
        }
        assertEquals(List.of("glStateDisableBlend", "glDisable"), calls);
    }

    @Test
    void backgroundWritesDepthWithoutTestingAgainstWorldGeometry() throws IOException {
        MethodNode draw = method("client/AusmGuiRenderController", "drawOwnedWorldBackground");
        boolean always = false;
        boolean writesDepth = false;
        boolean depthEnabled = false;
        for (AbstractInsnNode instruction : draw.instructions) {
            if (!(instruction instanceof MethodInsnNode call)) continue;
            if (call.name.equals("glDepthFunc") && previous(call) instanceof IntInsnNode value
                    && value.operand == 519) always = true; // GL_ALWAYS
            if (call.name.equals("glDepthMask") && previous(call).getOpcode() == Opcodes.ICONST_1) {
                writesDepth = true;
            }
            if (call.name.equals("glStateEnableDepth")) depthEnabled = true;
            if (call.name.equals("glBegin")) {
                assertTrue(always && writesDepth && depthEnabled);
                return;
            }
        }
        throw new AssertionError("Missing background draw");
    }

    @Test
    void backgroundUsesVanillaWindingInYDownProjection() throws IOException {
        MethodNode draw = method("client/AusmGuiRenderController", "drawOwnedWorldBackground");
        List<MethodInsnNode> vertices = new ArrayList<>();
        for (AbstractInsnNode instruction : draw.instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals("glVertex3f")) vertices.add(call);
        }
        assertEquals(4, vertices.size());
        // Top edge goes right to left, so the Y-flipped projection leaves it front-facing.
        assertEquals(Opcodes.I2F, previous(previous(previous(vertices.get(0)))).getOpcode());
        assertEquals(Opcodes.FCONST_0, previous(previous(previous(vertices.get(1)))).getOpcode());
        assertEquals(Opcodes.I2F, previous(previous(vertices.get(2))).getOpcode());
        assertEquals(Opcodes.I2F, previous(previous(vertices.get(3))).getOpcode());
    }

    private static AbstractInsnNode previous(AbstractInsnNode instruction) {
        do {
            instruction = instruction.getPrevious();
        } while (instruction != null && instruction.getOpcode() < 0);
        return Objects.requireNonNull(instruction);
    }

    private static MethodNode method(String owner, String name) throws IOException {
        String resource = "/com/luna/ausm/impl/" + owner + ".class";
        try (InputStream input = Objects.requireNonNull(GuiRenderStateTest.class.getResourceAsStream(resource))) {
            ClassNode type = new ClassNode();
            new ClassReader(input).accept(type, 0);
            return type.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
        }
    }
}
