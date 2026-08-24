package com.luna.ausm.impl.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

/**
 * ProjectRed's Scala singletons assign generated final fields from trait helpers
 * and constructors. After mixin rewrites, modern JVMs can reject those writes.
 */
public final class ProjectRedScalaModuleTransformer implements IClassTransformer {
    private static final String PROJECT_RED_PREFIX = "mrtjp.projectred.";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !isProjectRedScalaModule(name, transformedName)) {
            return basicClass;
        }

        try {
            ClassReader reader = new ClassReader(basicClass);
            ClassWriter writer = new ClassWriter(reader, 0);
            ModuleFieldVisitor visitor = new ModuleFieldVisitor(writer);
            reader.accept(visitor, 0);
            if (!visitor.changed) {
                return basicClass;
            }

            String className = transformedName != null ? transformedName : name;
            return writer.toByteArray();
        } catch (Throwable throwable) {
            return basicClass;
        }
    }

    private static boolean isProjectRedScalaModule(String name, String transformedName) {
        return isProjectRedScalaModule(name) || isProjectRedScalaModule(transformedName);
    }

    private static boolean isProjectRedScalaModule(String className) {
        if (className == null) {
            return false;
        }
        String normalized = className.replace('/', '.');
        return normalized.startsWith(PROJECT_RED_PREFIX) && normalized.endsWith("$");
    }

    private static final class ModuleFieldVisitor extends ClassVisitor {
        private boolean changed;
        private int changedFields;

        private ModuleFieldVisitor(ClassVisitor delegate) {
            super(Opcodes.ASM5, delegate);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if ((access & Opcodes.ACC_FINAL) != 0) {
                access &= ~Opcodes.ACC_FINAL;
                changed = true;
                changedFields++;
            }
            return super.visitField(access, name, descriptor, signature, value);
        }
    }
}
