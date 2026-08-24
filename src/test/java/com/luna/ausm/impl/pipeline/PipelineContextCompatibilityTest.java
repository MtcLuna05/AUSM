package com.luna.ausm.impl.pipeline;

import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PipelineContextCompatibilityTest {
    @Test
    void exposesAnInvocablePublicFactoryForExternalReflectionBridges() throws Exception {
        ClassNode context = new ClassNode();
        try (InputStream bytecode = getClass().getResourceAsStream(
		        "/com/luna/ausm/impl/pipeline/PipelineContext.class")) {
            assertNotNull(bytecode);
            new ClassReader(bytecode).accept(context, ClassReader.SKIP_CODE);
        }

        MethodNode factory = context.methods.stream()
                .filter(method -> "getInstance".equals(method.name))
                .filter(method -> "()Lcom/l/ausm/impl/pipeline/PipelineContext;".equals(method.desc))
                .findFirst()
                .orElse(null);

        assertNotNull(factory);
        assertTrue((factory.access & Opcodes.ACC_PUBLIC) != 0);
        assertTrue((factory.access & Opcodes.ACC_STATIC) != 0);
    }
}
