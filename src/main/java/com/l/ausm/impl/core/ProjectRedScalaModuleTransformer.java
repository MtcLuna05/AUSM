package com.l.ausm.impl.core;

import com.l.ausm.impl.Reference;
import net.minecraft.launchwrapper.IClassTransformer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ProjectRed's Scala singletons assign generated final fields from trait helpers
 * and constructors. After mixin rewrites, modern JVMs can reject those writes.
 */
public final class ProjectRedScalaModuleTransformer implements IClassTransformer {
    private static final Logger LOGGER = LogManager.getLogger(Reference.MOD_NAME);
    private static final String PROJECT_RED_PREFIX = "mrtjp.projectred.";
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

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
            if (LOGGED.add(className)) {
                LOGGER.info("[MixinCompat] Relaxed {} final ProjectRed Scala singleton fields: {}",
                        visitor.changedFields, className);
            }
            return writer.toByteArray();
        } catch (Throwable throwable) {
            LOGGER.warn("[MixinCompat] Could not relax ProjectRed Scala singleton fields for {}", transformedName, throwable);
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
