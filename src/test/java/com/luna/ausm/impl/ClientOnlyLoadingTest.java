package com.luna.ausm.impl;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClientOnlyLoadingTest {
    @Test
    void renderingHooksAreGuardedByPhysicalClientSide() throws Exception {
        assertClientGuard("MainLoadingPlugin", "getASMTransformerClass", Opcodes.IF_ACMPEQ);
        assertClientGuard("MainLoadingPlugin", "injectData", Opcodes.IF_ACMPNE);
        assertClientGuard("MainMixinConfigPlugin", "shouldApplyMixin", Opcodes.IF_ACMPEQ);
    }

    private static void assertClientGuard(String owner, String name, int branchOpcode) throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream input = resource("/com/luna/ausm/impl/" + owner + ".class")) {
            new ClassReader(input).accept(node, 0);
        }
        MethodNode method = node.methods.stream().filter(candidate -> candidate.name.equals(name))
                .findFirst().orElseThrow();
        AbstractInsnNode instruction = nextInstruction(method.instructions.getFirst());
        MethodInsnNode side = assertInstanceOf(MethodInsnNode.class, instruction);
        assertEquals("net/minecraftforge/fml/relauncher/FMLLaunchHandler", side.owner);
        assertEquals("side", side.name);
        FieldInsnNode client = assertInstanceOf(FieldInsnNode.class, nextInstruction(side.getNext()));
        assertEquals("net/minecraftforge/fml/relauncher/Side", client.owner);
        assertEquals("CLIENT", client.name);
        JumpInsnNode branch = assertInstanceOf(JumpInsnNode.class, nextInstruction(client.getNext()));
        assertEquals(branchOpcode, branch.getOpcode());
        // Follow the non-client branch: it must return before calling anything else.
        instruction = nextInstruction(branchOpcode == Opcodes.IF_ACMPNE ? branch.label : branch.getNext());
        while (instruction.getOpcode() != Opcodes.RETURN
                && instruction.getOpcode() != Opcodes.ARETURN && instruction.getOpcode() != Opcodes.IRETURN) {
            assertFalse(instruction instanceof MethodInsnNode, "Server branch must not invoke rendering hooks");
            assertFalse(instruction instanceof JumpInsnNode, "Server branch must return directly");
            instruction = nextInstruction(instruction.getNext());
        }
    }

    private static AbstractInsnNode nextInstruction(AbstractInsnNode instruction) {
        while (instruction != null && instruction.getOpcode() < 0) instruction = instruction.getNext();
        return Objects.requireNonNull(instruction);
    }

    @Test
    void modIsClientOnlyAndAcceptsRemoteAbsence() throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream input = resource("/com/luna/ausm/impl/MainMod.class")) {
            new ClassReader(input).accept(node, ClassReader.SKIP_CODE);
        }
        AnnotationNode mod = node.visibleAnnotations.stream()
                .filter(annotation -> annotation.desc.equals("Lnet/minecraftforge/fml/common/Mod;"))
                .findFirst().orElseThrow();
        assertEquals(Boolean.TRUE, mod.values.get(mod.values.indexOf("clientSideOnly") + 1));
        assertEquals("*", mod.values.get(mod.values.indexOf("acceptableRemoteVersions") + 1));
    }

    @Test
    void mixinConfigurationsContainOnlyClientEntries() throws Exception {
        for (String name : new String[]{"/ausm.default.mixin.json", "/ausm.mod.mixin.json"}) {
            try (InputStream input = resource(name)) {
                String config = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(config.matches("(?s).*\"mixins\"\\s*:\\s*\\[\\s*].*"));
                assertTrue(config.matches("(?s).*\"server\"\\s*:\\s*\\[\\s*].*"));
            }
        }
    }

    private static InputStream resource(String name) {
        return Objects.requireNonNull(ClientOnlyLoadingTest.class.getResourceAsStream(name));
    }

}
