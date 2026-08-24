package com.luna.ausm.impl.hotswap;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

final class DynamicClassDefinitionSupportTest {
    @Test
    void definesAStagedClassBesideALoadedSamePackageAnchor() throws Exception {
        Instrumentation instrumentation = (Instrumentation) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Instrumentation.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getAllLoadedClasses")) {
                        return new Class<?>[]{DynamicClassDefinitionSupport.class};
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        Class<?> defined = DynamicClassDefinitionSupport.tryDefine(
                instrumentation,
                "com.luna.ausm.impl.hotswap.RuntimeDefinedFixture",
                fixtureBytecode());

        assertNotNull(defined);
        assertSame(DynamicClassDefinitionSupport.class.getClassLoader(), defined.getClassLoader());
        assertEquals(7, defined.getDeclaredMethod("value").invoke(null));
    }

    private static byte[] fixtureBytecode() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17,
                Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
		        "com/luna/ausm/impl/hotswap/RuntimeDefinedFixture",
                null,
                "java/lang/Object",
                null);
        MethodVisitor value = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "value",
                "()I",
                null,
                null);
        value.visitCode();
        value.visitIntInsn(Opcodes.BIPUSH, 7);
        value.visitInsn(Opcodes.IRETURN);
        value.visitMaxs(0, 0);
        value.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
