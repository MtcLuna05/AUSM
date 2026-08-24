package com.luna.ausm.impl.core;

import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class DraconicRenderedEmissionTransformerTest {
    private static final String ENERGY_TARGET =
            "com.brandon3055.draconicevolution.client.render.tile.RenderTileEnergyStorageCore";
    private static final String REACTOR_TARGET =
            "com.brandon3055.draconicevolution.client.render.tile.RenderTileReactorCore";
    private static final String ENERGY_RENDER_DESC =
            "(Lcom/brandon3055/draconicevolution/blocks/tileentity/TileEnergyStorageCore;DDDFIF)V";
    private static final String REACTOR_RENDER_DESC =
            "(Lcom/brandon3055/draconicevolution/blocks/reactor/tileentity/TileReactorCore;DDDFIF)V";
    private static final String BRIDGE =
		    "com/luna/ausm/impl/pipeline/compat/DraconicRenderedEmissionCompat";

    @Test
    void wrapsEveryEnergySphereSubmission() {
        byte[] transformed = new DraconicRenderedEmissionTransformer()
                .transform(ENERGY_TARGET, ENERGY_TARGET, energyRendererStub());

        assertEquals(List.of(
                BRIDGE + ".beginEmission()V",
                "com/brandon3055/brandonscore/utils/ModelUtils.renderQuadsRGB(Ljava/util/List;FFF)V",
                BRIDGE + ".endEmission()V",
                BRIDGE + ".beginEmission()V",
                "com/brandon3055/brandonscore/utils/ModelUtils.renderQuadsRGB(Ljava/util/List;FFF)V",
                BRIDGE + ".endEmission()V",
                BRIDGE + ".beginEmission()V",
                "com/brandon3055/brandonscore/utils/ModelUtils.renderQuadsRGB(Ljava/util/List;FFF)V",
                BRIDGE + ".endEmission()V"
        ), callsIn(transformed, "render", ENERGY_RENDER_DESC));
    }

    @Test
    void wrapsOnlyTheReactorCoreAndFiltersItsPrivateShaderFlag() {
        byte[] transformed = new DraconicRenderedEmissionTransformer()
                .transform(REACTOR_TARGET, REACTOR_TARGET, reactorRendererStub());

        assertEquals(List.of(
                BRIDGE + ".useDraconicReactorShader(Z)Z",
                BRIDGE + ".beginEmission()V",
                "com/brandon3055/draconicevolution/client/render/tile/RenderTileReactorCore.renderCore(DDDFFFDZ)V",
                BRIDGE + ".endEmission()V",
                "com/brandon3055/draconicevolution/client/render/tile/RenderTileReactorCore.renderShield(DDDFFFDZ)V"
        ), callsIn(transformed, "render", REACTOR_RENDER_DESC));
    }

    @Test
    void leavesUnrelatedClassesUntouched() {
        byte[] original = energyRendererStub();
        byte[] transformed = new DraconicRenderedEmissionTransformer()
                .transform("example.Other", "example.Other", original);
        assertArrayEquals(original, transformed);
    }

    @Test
    void transformsInstalledDraconicRendererBytecodeWhenProvided() throws Exception {
        String jarPath = System.getProperty("ausm.draconicJar", "");
        assumeTrue(!jarPath.isBlank());
        try (JarFile jar = new JarFile(jarPath)) {
            byte[] energy = readClass(jar, ENERGY_TARGET);
            byte[] reactor = readClass(jar, REACTOR_TARGET);
            DraconicRenderedEmissionTransformer transformer = new DraconicRenderedEmissionTransformer();

            List<String> energyCalls = callsIn(
                    transformer.transform(ENERGY_TARGET, ENERGY_TARGET, energy),
                    "render",
                    ENERGY_RENDER_DESC
            );
            assertEquals(3, countCall(energyCalls, BRIDGE + ".beginEmission()V"));
            assertEquals(3, countCall(energyCalls, BRIDGE + ".endEmission()V"));

            List<String> reactorCalls = callsIn(
                    transformer.transform(REACTOR_TARGET, REACTOR_TARGET, reactor),
                    "render",
                    REACTOR_RENDER_DESC
            );
            assertEquals(1, countCall(reactorCalls, BRIDGE + ".useDraconicReactorShader(Z)Z"));
            assertEquals(1, countCall(reactorCalls, BRIDGE + ".beginEmission()V"));
            assertEquals(1, countCall(reactorCalls, BRIDGE + ".endEmission()V"));
        }
    }

    private static byte[] readClass(JarFile jar, String className) throws Exception {
        JarEntry entry = jar.getJarEntry(className.replace('.', '/') + ".class");
        assumeTrue(entry != null);
        try (var input = jar.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    private static long countCall(List<String> calls, String expected) {
        return calls.stream().filter(expected::equals).count();
    }

    private static List<String> callsIn(byte[] bytecode, String methodName, String descriptor) {
        ClassNode classNode = new ClassNode();
        new ClassReader(bytecode).accept(classNode, 0);
        List<String> calls = new ArrayList<>();
        for (MethodNode method : classNode.methods) {
            if (!methodName.equals(method.name) || !descriptor.equals(method.desc)) {
                continue;
            }
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    calls.add(call.owner + "." + call.name + call.desc);
                }
            }
        }
        return calls;
    }

    private static byte[] energyRendererStub() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, ENERGY_TARGET.replace('.', '/'), null,
                "java/lang/Object", null);
        MethodNode render = new MethodNode(Opcodes.ACC_PUBLIC, "render", ENERGY_RENDER_DESC, null, null);
        for (int i = 0; i < 3; i++) {
            render.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
            render.instructions.add(new InsnNode(Opcodes.FCONST_0));
            render.instructions.add(new InsnNode(Opcodes.FCONST_0));
            render.instructions.add(new InsnNode(Opcodes.FCONST_0));
            render.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "com/brandon3055/brandonscore/utils/ModelUtils",
                    "renderQuadsRGB",
                    "(Ljava/util/List;FFF)V",
                    false
            ));
        }
        render.instructions.add(new InsnNode(Opcodes.RETURN));
        render.maxStack = 4;
        render.maxLocals = 11;
        render.accept(writer);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] reactorRendererStub() {
        String owner = REACTOR_TARGET.replace('.', '/');
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        MethodNode render = new MethodNode(Opcodes.ACC_PUBLIC, "render", REACTOR_RENDER_DESC, null, null);
        addReactorArguments(render);
        render.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC, owner, "renderCore", "(DDDFFFDZ)V", false));
        addReactorArguments(render);
        render.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC, owner, "renderShield", "(DDDFFFDZ)V", false));
        render.instructions.add(new InsnNode(Opcodes.RETURN));
        render.maxStack = 12;
        render.maxLocals = 11;
        render.accept(writer);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void addReactorArguments(MethodNode method) {
        method.instructions.add(new InsnNode(Opcodes.DCONST_0));
        method.instructions.add(new InsnNode(Opcodes.DCONST_0));
        method.instructions.add(new InsnNode(Opcodes.DCONST_0));
        method.instructions.add(new InsnNode(Opcodes.FCONST_0));
        method.instructions.add(new InsnNode(Opcodes.FCONST_0));
        method.instructions.add(new InsnNode(Opcodes.FCONST_0));
        method.instructions.add(new InsnNode(Opcodes.DCONST_0));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
    }
}
