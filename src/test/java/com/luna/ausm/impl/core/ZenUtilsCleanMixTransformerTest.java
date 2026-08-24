package com.luna.ausm.impl.core;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ZenUtilsCleanMixTransformerTest {
    private static final String TARGET = "youyihj.zenutils.impl.zenscript.mixin.ZenMixin";

    @Test
    void replacesZenUtilsDirectProxyFieldAccess() {
        byte[] original = zenMixinStub();
        byte[] transformed = new ZenUtilsCleanMixTransformer().transform(TARGET, TARGET, original);

        ClassNode classNode = new ClassNode();
        new ClassReader(transformed).accept(classNode, 0);

        int directAccesses = 0;
        int bridgeCalls = 0;
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof FieldInsnNode field
                        && field.getOpcode() == Opcodes.GETSTATIC
                        && "org/spongepowered/asm/mixin/transformer/Proxy".equals(field.owner)
                        && "transformer".equals(field.name)) {
                    directAccesses++;
                }
                if (instruction instanceof MethodInsnNode call
                        && call.getOpcode() == Opcodes.INVOKESTATIC
                        && "com/luna/ausm/impl/core/ZenUtilsCleanMixTransformer".equals(call.owner)
                        && "getActiveMixinTransformer".equals(call.name)
                        && "()Ljava/lang/Object;".equals(call.desc)) {
                    bridgeCalls++;
                }
            }
        }

        assertEquals(0, directAccesses);
        assertEquals(1, bridgeCalls);
    }

    @Test
    void leavesOtherClassesUntouched() {
        byte[] original = zenMixinStub();
        byte[] transformed = new ZenUtilsCleanMixTransformer().transform("example.Other", "example.Other", original);
        assertArrayEquals(original, transformed);
    }

    private static byte[] zenMixinStub() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, TARGET.replace('.', '/'), null, "java/lang/Object", null);

        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "load", "()V", null, null);
        method.instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC,
                "org/spongepowered/asm/mixin/transformer/Proxy",
                "transformer",
                "Lorg/spongepowered/asm/mixin/transformer/MixinTransformer;"
        ));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = 1;
        method.maxLocals = 0;
        method.accept(writer);

        writer.visitEnd();
        return writer.toByteArray();
    }
}
