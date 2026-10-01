package com.luna.ausm.impl.core;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class NothiriumBypassTransformerTest {
    private static final String TARGET = "org.taumc.celeritas.mixin.core.MinecraftMixin";
    private static final String CALLBACK_DESCRIPTOR =
            "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
    private static final String NAUGHTHIRIUM_COMPILE =
            "zone.rong.naughthirium.mixins.loliasm.RenderChunkTaskCompileMixin";
    private static final String NAUGHTHIRIUM_VERTEX =
            "zone.rong.naughthirium.compat.loliasm.FloatVertexConsumer";

    @TempDir
    Path gameDirectory;

    @Test
    void preservesNaughthiriumAnimationTrackingWithoutCeleritas() throws Exception {
        withCeleritasPresent(false, () -> {
            for (String target : Set.of(NAUGHTHIRIUM_COMPILE, NAUGHTHIRIUM_VERTEX)) {
                byte[] original = naughthiriumStub(target);
                assertArrayEquals(original, new NothiriumBypassTransformer().transform(target, target, original));
            }
        });
    }

    @Test
    void retainsCeleritasProtectionForNaughthirium() throws Exception {
        withCeleritasPresent(true, () -> {
            NothiriumBypassTransformer transformer = new NothiriumBypassTransformer();
            ClassNode compile = new ClassNode();
            new ClassReader(transformer.transform(NAUGHTHIRIUM_COMPILE, NAUGHTHIRIUM_COMPILE,
                    naughthiriumStub(NAUGHTHIRIUM_COMPILE))).accept(compile, 0);
            assertEquals(Set.of("<init>()V"), methodSignatures(compile));

            ClassNode vertex = new ClassNode();
            new ClassReader(transformer.transform(NAUGHTHIRIUM_VERTEX, NAUGHTHIRIUM_VERTEX,
                    naughthiriumStub(NAUGHTHIRIUM_VERTEX))).accept(vertex, 0);
            for (MethodNode method : vertex.methods) {
                for (AbstractInsnNode instruction : method.instructions) {
                    assertFalse(instruction instanceof MethodInsnNode call && "hookTexture".equals(call.name));
                }
            }
        });
    }

    private void withCeleritasPresent(boolean present, Runnable check) throws Exception {
        Path mods = Files.createDirectory(gameDirectory.resolve("mods"));
        Files.createFile(mods.resolve("Nothirium-1.12.2-0.4.8-beta.jar"));
        Files.createFile(mods.resolve("naughthirium-2.3.0.jar"));
        if (present) {
            Files.createFile(mods.resolve("celeritas-2.4.0.jar"));
        }
        Map<Field, Object> detectionCaches = new HashMap<>();
        for (Field field : NothiriumBypassTransformer.class.getDeclaredFields()) {
            if (field.getType() == Boolean.class) {
                field.setAccessible(true);
                detectionCaches.put(field, field.get(null));
                field.set(null, null);
            }
        }
        String previousDirectory = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", gameDirectory.toString());
            check.run();
        } finally {
            for (Map.Entry<Field, Object> entry : detectionCaches.entrySet()) {
                entry.getKey().set(null, entry.getValue());
            }
            System.setProperty("user.dir", previousDirectory);
        }
    }

    private static byte[] naughthiriumStub(String target) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, target.replace('.', '/'), null, "java/lang/Object", null);
        writeVoidMethod(writer, Opcodes.ACC_PUBLIC, "<init>", "()V");
        if (NAUGHTHIRIUM_COMPILE.equals(target)) {
            writeVoidMethod(writer, Opcodes.ACC_PRIVATE, "startCollectingVisibleTextures", CALLBACK_DESCRIPTOR);
            writeVoidMethod(writer, Opcodes.ACC_PRIVATE, "finishCollectingVisibleTextures", CALLBACK_DESCRIPTOR);
        } else {
            MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "tex", "(Ljava/lang/Object;DD)V", null, null);
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST,
                    "zone/rong/loliasm/client/sprite/ondemand/IBufferPrimerConfigurator"));
            method.instructions.add(new VarInsnNode(Opcodes.DLOAD, 2));
            method.instructions.add(new InsnNode(Opcodes.D2F));
            method.instructions.add(new VarInsnNode(Opcodes.DLOAD, 4));
            method.instructions.add(new InsnNode(Opcodes.D2F));
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                    "zone/rong/loliasm/client/sprite/ondemand/IBufferPrimerConfigurator", "hookTexture", "(FF)V", true));
            method.instructions.add(new InsnNode(Opcodes.RETURN));
            method.maxStack = 4;
            method.maxLocals = 6;
            method.accept(writer);
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

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
