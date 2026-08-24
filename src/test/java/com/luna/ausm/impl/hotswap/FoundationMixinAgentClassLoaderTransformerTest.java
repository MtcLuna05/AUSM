package com.luna.ausm.impl.hotswap;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class FoundationMixinAgentClassLoaderTransformerTest {
    @Test
    void injectsMixinAgentPackageBesideSpongeMixinInclusion() {
        byte[] transformed = FoundationMixinAgentClassLoaderTransformer.addMixinAgentInclusion(foundationFixture());
        AtomicInteger inclusions = new AtomicInteger();
        new ClassReader(transformed).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access,
                                             String name,
                                             String descriptor,
                                             String signature,
                                             String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (FoundationMixinAgentClassLoaderTransformer.MIXIN_AGENT_PACKAGE.equals(value)) {
                            inclusions.incrementAndGet();
                        }
                    }
                };
            }
        }, 0);
        assertEquals(1, inclusions.get());
    }

    @Test
    void ignoresUnrelatedClasses() {
        FoundationMixinAgentClassLoaderTransformer transformer = new FoundationMixinAgentClassLoaderTransformer();
        assertNull(transformer.transform(null, null, "example/Other", null, null, foundationFixture()));
    }

    private static byte[] foundationFixture() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21,
                Opcodes.ACC_PUBLIC,
                FoundationMixinAgentClassLoaderTransformer.FOUNDATION_CLASS_LOADER,
                null,
                "java/net/URLClassLoader",
                null);
        MethodVisitor constructor = writer.visitMethod(
                Opcodes.ACC_PUBLIC,
                "<init>",
                "([Ljava/net/URL;Ljava/lang/ClassLoader;)V",
                null,
                null
        );
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitVarInsn(Opcodes.ALOAD, 1);
        constructor.visitVarInsn(Opcodes.ALOAD, 2);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/net/URLClassLoader",
                "<init>",
                "([Ljava/net/URL;Ljava/lang/ClassLoader;)V",
                false);
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitLdcInsn("org.spongepowered.asm.");
        constructor.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                FoundationMixinAgentClassLoaderTransformer.FOUNDATION_CLASS_LOADER,
                "addClassLoaderInclusion",
                "(Ljava/lang/String;)V",
                false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
