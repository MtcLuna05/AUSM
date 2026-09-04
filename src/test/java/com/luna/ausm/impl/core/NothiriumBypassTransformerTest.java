package com.luna.ausm.impl.core;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class NothiriumBypassTransformerTest {
    private static final String TARGET = "org.taumc.celeritas.mixin.core.MinecraftMixin";
    private static final String CALLBACK_DESCRIPTOR =
            "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";

    @Test
    void stripsOnlyCeleritasFrameAheadHooks() {
        byte[] transformed = new NothiriumBypassTransformer().transform(TARGET, TARGET, celeritasMinecraftMixinStub());

        ClassNode classNode = new ClassNode();
        new ClassReader(transformed).accept(classNode, 0);

        assertEquals(Set.of("<init>()V", "futureHook()V"), methodSignatures(classNode));
    }

    @Test
    void stripsFrameAheadCallsFromInstalledCeleritasFixture() throws IOException {
        String fixtureJar = System.getenv("AUSM_CELERITAS_TEST_JAR");
        assumeTrue(fixtureJar != null && !fixtureJar.isBlank());

        byte[] original;
        try (ZipFile zip = new ZipFile(fixtureJar)) {
            ZipEntry entry = Objects.requireNonNull(
                    zip.getEntry(TARGET.replace('.', '/') + ".class"),
                    "Celeritas MinecraftMixin fixture"
            );
            original = zip.getInputStream(entry).readAllBytes();
        }

        byte[] transformed = new NothiriumBypassTransformer().transform(TARGET, TARGET, original);
        ClassNode classNode = new ClassNode();
        new ClassReader(transformed).accept(classNode, 0);

        assertFalse(methodSignatures(classNode).contains("preRender" + CALLBACK_DESCRIPTOR));
        assertFalse(methodSignatures(classNode).contains("postRender" + CALLBACK_DESCRIPTOR));
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call
                        && "org/embeddedt/embeddium/impl/render/frame/RenderAheadManager".equals(call.owner)) {
                    assertFalse("startFrame".equals(call.name) || "endFrame".equals(call.name));
                }
            }
        }
    }

    @Test
    void leavesOtherClassesUntouched() {
        byte[] original = celeritasMinecraftMixinStub();
        byte[] transformed = new NothiriumBypassTransformer().transform("example.Other", "example.Other", original);
        assertArrayEquals(original, transformed);
    }

    private static byte[] celeritasMinecraftMixinStub() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, TARGET.replace('.', '/'), null, "java/lang/Object", null);
        writeVoidMethod(writer, Opcodes.ACC_PUBLIC, "<init>", "()V");
        writeVoidMethod(writer, Opcodes.ACC_PRIVATE, "preRender", CALLBACK_DESCRIPTOR);
        writeVoidMethod(writer, Opcodes.ACC_PRIVATE, "postRender", CALLBACK_DESCRIPTOR);
        writeVoidMethod(writer, Opcodes.ACC_PRIVATE, "futureHook", "()V");
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void writeVoidMethod(ClassWriter writer, int access, String name, String descriptor) {
        MethodNode method = new MethodNode(access, name, descriptor, null, null);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = 0;
        method.maxLocals = "()V".equals(descriptor) ? 1 : 2;
        method.accept(writer);
    }

    private static Set<String> methodSignatures(ClassNode classNode) {
        return classNode.methods.stream()
                .map(method -> method.name + method.desc)
                .collect(Collectors.toSet());
    }
}
