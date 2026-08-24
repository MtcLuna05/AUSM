package com.luna.ausm.impl.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class ParticleCullingThreadTransformer implements IClassTransformer {
    private static final String TARGET = "bl4ckscor3.mod.particleculling.CullThread";
    private static final String MINECRAFT = "net/minecraft/client/Minecraft";
    private static final String PARTICLE_MANAGER_DESC = "Lnet/minecraft/client/particle/ParticleManager;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || (!TARGET.equals(name) && !TARGET.equals(transformedName))) {
            return basicClass;
        }

        ClassReader reader = new ClassReader(basicClass);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, ClassReader.EXPAND_FRAMES);

        boolean changed = false;
        for (MethodNode method : classNode.methods) {
            if ("run".equals(method.name) && "()V".equals(method.desc)) {
                changed = patchRunLoop(method);
                break;
            }
        }

        if (!changed) {
            return basicClass;
        }

        ClassWriter writer = new SafeClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private static boolean patchRunLoop(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof JumpInsnNode worldNullJump) || worldNullJump.getOpcode() != Opcodes.IFNULL) {
                continue;
            }
            AbstractInsnNode previous = previousRealInstruction(worldNullJump);
            if (!(previous instanceof FieldInsnNode worldField)
                    || !MINECRAFT.equals(worldField.owner)
                    || !"field_71441_e".equals(worldField.name)) {
                continue;
            }

            InsnList guard = new InsnList();
            guard.add(new VarInsnNode(Opcodes.ALOAD, 1));
            guard.add(new FieldInsnNode(
                    Opcodes.GETFIELD,
                    MINECRAFT,
                    "field_71452_i",
                    PARTICLE_MANAGER_DESC
            ));
            guard.add(new JumpInsnNode(Opcodes.IFNULL, worldNullJump.label));
            method.instructions.insert(worldNullJump, guard);
            return true;
        }
        return false;
    }

    private static AbstractInsnNode previousRealInstruction(AbstractInsnNode instruction) {
        AbstractInsnNode previous = instruction != null ? instruction.getPrevious() : null;
        while (previous != null
                && (previous.getType() == AbstractInsnNode.LABEL
                || previous.getType() == AbstractInsnNode.LINE
                || previous.getType() == AbstractInsnNode.FRAME)) {
            previous = previous.getPrevious();
        }
        return previous;
    }

    private static final class SafeClassWriter extends ClassWriter {
        private SafeClassWriter(ClassReader reader, int flags) {
            super(reader, flags);
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            return "java/lang/Object";
        }
    }
}
