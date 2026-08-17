package com.l.ausm.impl.util;

import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class MinecraftReflectionCompatTest {

    @Test
    void inheritedLookupSurvivesMissingOptionalTypeInUnrelatedMethod() throws Exception {
        String suffix = Long.toUnsignedString(System.nanoTime());
        String parentName = "com.l.ausm.test.generated.SafeParent" + suffix;
        String childName = "com.l.ausm.test.generated.PoisonedChild" + suffix;
        String missingName = "com.l.ausm.test.generated.MissingOptionalType" + suffix;
        Map<String, byte[]> definitions = Map.of(
                parentName, parentClass(parentName),
                childName, childClass(childName, parentName, missingName)
        );
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] definition = definitions.get(name);
                if (definition != null) {
                    return defineClass(name, definition, 0, definition.length);
                }
                return super.findClass(name);
            }
        };

        Class<?> poisonedClass = Class.forName(childName, true, loader);
        assertThrows(NoClassDefFoundError.class, () -> poisonedClass.getMethod("safeMethod"));

        Method resolved = LinkageSafeMethodLookup.find(poisonedClass, "safeMethod", new Class<?>[0]);
        Object result = resolved.invoke(poisonedClass.getConstructor().newInstance());
        assertEquals(Boolean.TRUE, result);
    }

    private static byte[] parentClass(String binaryName) {
        String internalName = binaryName.replace('.', '/');
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        addConstructor(writer, "java/lang/Object");

        MethodVisitor safe = writer.visitMethod(Opcodes.ACC_PUBLIC, "safeMethod", "()Z", null, null);
        safe.visitCode();
        safe.visitInsn(Opcodes.ICONST_1);
        safe.visitInsn(Opcodes.IRETURN);
        safe.visitMaxs(0, 0);
        safe.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] childClass(String binaryName, String parentName, String missingName) {
        String internalName = binaryName.replace('.', '/');
        String parentInternalName = parentName.replace('.', '/');
        String missingInternalName = missingName.replace('.', '/');
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, internalName, null, parentInternalName, null);
        addConstructor(writer, parentInternalName);

        MethodVisitor poison = writer.visitMethod(
                Opcodes.ACC_PUBLIC,
                "optionalApiMethod",
                "()[L" + missingInternalName + ";",
                null,
                null
        );
        poison.visitCode();
        poison.visitInsn(Opcodes.ACONST_NULL);
        poison.visitInsn(Opcodes.ARETURN);
        poison.visitMaxs(0, 0);
        poison.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void addConstructor(ClassWriter writer, String parentInternalName) {
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, parentInternalName, "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
    }
}
