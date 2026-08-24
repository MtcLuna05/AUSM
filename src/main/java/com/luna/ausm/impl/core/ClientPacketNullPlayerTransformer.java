package com.luna.ausm.impl.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class ClientPacketNullPlayerTransformer implements IClassTransformer {
    private static final String TOMBSTONE_RUNNABLE = "ovh.corail.tombstone.network.SyncCapClientMessage$Handler$1";
    private static final String PROJECTE_STEP_HEIGHT_RUNNABLE = "moze_intel.projecte.network.packets.StepHeightPKT$Handler$1";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        if (isTarget(name, transformedName, TOMBSTONE_RUNNABLE)) {
            return transformClientPlayerRunnable(basicClass);
        }
        if (isTarget(name, transformedName, PROJECTE_STEP_HEIGHT_RUNNABLE)) {
            return transformClientPlayerRunnable(basicClass);
        }
        return basicClass;
    }

    private static byte[] transformClientPlayerRunnable(byte[] basicClass) {
        ClassReader reader = new ClassReader(basicClass);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, ClassReader.EXPAND_FRAMES);

        boolean changed = false;
        for (MethodNode method : classNode.methods) {
            if ("run".equals(method.name) && "()V".equals(method.desc)) {
                method.instructions.insert(clientPlayerNullReturnGuard());
                changed = true;
                break;
            }
        }

        return changed ? writeClass(reader, classNode) : basicClass;
    }

    private static InsnList clientPlayerNullReturnGuard() {
        LabelNode continueLabel = new LabelNode();
        InsnList guard = new InsnList();
        guard.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "net/minecraft/client/Minecraft",
                "func_71410_x",
                "()Lnet/minecraft/client/Minecraft;",
                false
        ));
        guard.add(new FieldInsnNode(
                Opcodes.GETFIELD,
                "net/minecraft/client/Minecraft",
                "field_71439_g",
                "Lnet/minecraft/client/entity/EntityPlayerSP;"
        ));
        guard.add(new JumpInsnNode(Opcodes.IFNONNULL, continueLabel));
        guard.add(new InsnNode(Opcodes.RETURN));
        guard.add(continueLabel);
        return guard;
    }

    private static boolean isTarget(String name, String transformedName, String target) {
        return target.equals(name) || target.equals(transformedName);
    }

    private static byte[] writeClass(ClassReader reader, ClassNode classNode) {
        ClassWriter writer = new SafeClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
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
