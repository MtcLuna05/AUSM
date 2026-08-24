package com.luna.ausm.impl.hotswap;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.concurrent.atomic.AtomicBoolean;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class FoundationMixinAgentClassLoaderTransformer implements ClassFileTransformer {
    static final String FOUNDATION_CLASS_LOADER = "top/outlands/foundation/boot/ActualClassLoader";
    static final String MIXIN_AGENT_PACKAGE = "org.spongepowered.tools.agent.";
    private static final String ASM_PACKAGE = "org.spongepowered.asm.";
    private static final String CONSTRUCTOR_DESCRIPTOR = "([Ljava/net/URL;Ljava/lang/ClassLoader;)V";
    private static final String INCLUSION_METHOD = "addClassLoaderInclusion";
    private static final String INCLUSION_DESCRIPTOR = "(Ljava/lang/String;)V";

    @Override
    public byte[] transform(Module module,
                            ClassLoader loader,
                            String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
        if (!FOUNDATION_CLASS_LOADER.equals(className)) {
            return null;
        }
        try {
            byte[] transformed = addMixinAgentInclusion(classfileBuffer);
            System.err.println("[AUSM HotSwap] Patched Foundation to child-load Sponge Mixin's hot-swap agent.");
            return transformed;
        } catch (RuntimeException exception) {
            System.err.println("[AUSM HotSwap] Foundation bootstrap patch was not applied: " + exception);
            return null;
        }
    }

    static byte[] addMixinAgentInclusion(byte[] original) {
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        AtomicBoolean injected = new AtomicBoolean();
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access,
                                             String name,
                                             String descriptor,
                                             String signature,
                                             String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"<init>".equals(name) || !CONSTRUCTOR_DESCRIPTOR.equals(descriptor)) {
                    return delegate;
                }
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    private boolean sawAsmPackage;

                    @Override
                    public void visitLdcInsn(Object value) {
                        sawAsmPackage = ASM_PACKAGE.equals(value);
                        super.visitLdcInsn(value);
                    }

                    @Override
                    public void visitMethodInsn(int opcode,
                                                String owner,
                                                String methodName,
                                                String methodDescriptor,
                                                boolean isInterface) {
                        super.visitMethodInsn(opcode, owner, methodName, methodDescriptor, isInterface);
                        if (!injected.get()
                                && sawAsmPackage
                                && opcode == Opcodes.INVOKEVIRTUAL
                                && FOUNDATION_CLASS_LOADER.equals(owner)
                                && INCLUSION_METHOD.equals(methodName)
                                && INCLUSION_DESCRIPTOR.equals(methodDescriptor)) {
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitLdcInsn(MIXIN_AGENT_PACKAGE);
                            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                                    FOUNDATION_CLASS_LOADER,
                                    INCLUSION_METHOD,
                                    INCLUSION_DESCRIPTOR,
                                    false);
                            injected.set(true);
                        }
                        sawAsmPackage = false;
                    }
                };
            }
        }, 0);
        if (!injected.get()) {
            throw new IllegalStateException("Foundation class-loader inclusion anchor was not found");
        }
        return writer.toByteArray();
    }
}
