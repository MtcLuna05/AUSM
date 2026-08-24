package com.luna.ausm.impl.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class BetweenlandsMessageSyncChunkStorageTransformer implements IClassTransformer {
    private static final String TARGET = "thebetweenlands.common.network.clientbound.MessageSyncChunkStorage";
    private static final String GET_CHUNK_STORAGE = "getChunkStorage";
    private static final String GET_CHUNK_STORAGE_DESC =
            "(Lnet/minecraft/world/chunk/Chunk;)Lthebetweenlands/api/storage/IChunkStorage;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !isTarget(name, transformedName)) {
            return basicClass;
        }

        ClassReader reader = new ClassReader(basicClass);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, ClassReader.EXPAND_FRAMES);

        if (!patchHandle(classNode)) {
            return basicClass;
        }

        ClassWriter writer = new SafeClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private static boolean patchHandle(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            if (!"handle".equals(method.name) || !"()V".equals(method.desc)) {
                continue;
            }

            AbstractInsnNode[] instructions = method.instructions.toArray();
            for (int i = 0; i < instructions.length; i++) {
                AbstractInsnNode instruction = instructions[i];
                if (!(instruction instanceof MethodInsnNode call)
                        || !GET_CHUNK_STORAGE.equals(call.name)
                        || !GET_CHUNK_STORAGE_DESC.equals(call.desc)) {
                    continue;
                }

                VarInsnNode store = nextAstore(instructions, i + 1);
                if (store == null) {
                    return false;
                }

                LabelNode continueLabel = new LabelNode();
                InsnList guard = new InsnList();
                guard.add(new VarInsnNode(Opcodes.ALOAD, store.var));
                guard.add(new JumpInsnNode(Opcodes.IFNONNULL, continueLabel));
                guard.add(new InsnNode(Opcodes.RETURN));
                guard.add(continueLabel);
                method.instructions.insert(store, guard);
                return true;
            }
        }
        return false;
    }

    private static VarInsnNode nextAstore(AbstractInsnNode[] instructions, int start) {
        for (int i = start; i < instructions.length; i++) {
            AbstractInsnNode instruction = instructions[i];
            if (instruction instanceof VarInsnNode varInsn && varInsn.getOpcode() == Opcodes.ASTORE) {
                return varInsn;
            }
        }
        return null;
    }

    private static boolean isTarget(String name, String transformedName) {
        return TARGET.equals(name) || TARGET.equals(transformedName);
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
