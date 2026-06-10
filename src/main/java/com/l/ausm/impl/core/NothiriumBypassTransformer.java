package com.l.ausm.impl.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashSet;
import java.util.Set;

/**
 * Lets vanilla RenderGlobal run while AUSM shaders are active.
 *
 * Nothirium replaces RenderGlobal with mixin handlers that cancel terrain setup,
 * chunk updates, and block-layer rendering. That is fine for vanilla, but shader
 * packs need vanilla/AUSM terrain passes so water, shadows, and extended vertex
 * data line up with the active shader program.
 */
public final class NothiriumBypassTransformer implements IClassTransformer {

    private static final String TARGET = "meldexun.nothirium.mc.mixin.MixinRenderGlobal";
    private static final String BYPASS_OWNER = "com/l/ausm/impl/pipeline/compat/NothiriumBypass";
    private static final String CALLBACK_INFO = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
    private static final String MARK_BLOCKS_FOR_UPDATE = "markBlocksForUpdate(IIIIIIZLorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
    private static final Set<String> VOID_HANDLERS = new HashSet<>();

    static {
        VOID_HANDLERS.add("stopChunkUpdates(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
        VOID_HANDLERS.add("getDebugInfoRenders(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V");
        VOID_HANDLERS.add("getRenderedChunks(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V");
        VOID_HANDLERS.add("setupTerrain(Lnet/minecraft/entity/Entity;DLnet/minecraft/client/renderer/culling/ICamera;IZLorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
        VOID_HANDLERS.add("getRenderChunkOffset(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V");
        VOID_HANDLERS.add("renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V");
        VOID_HANDLERS.add("updateChunks(JLorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
        VOID_HANDLERS.add("hasNoChunkUpdates(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V");
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        if (!TARGET.equals(name) && !TARGET.equals(transformedName)) {
            return basicClass;
        }

        ClassReader reader = new ClassReader(basicClass);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, ClassReader.EXPAND_FRAMES);

        boolean changed = false;
        for (MethodNode method : classNode.methods) {
            String signature = method.name + method.desc;
            if (MARK_BLOCKS_FOR_UPDATE.equals(signature)) {
                changed |= insertBypassBeforeCancel(method);
            } else if (VOID_HANDLERS.contains(signature)) {
                insertBypassGuard(method);
                changed = true;
            }
        }

        if (!changed) {
            return basicClass;
        }

        ClassWriter writer = new SafeClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private static void insertBypassGuard(MethodNode method) {
        LabelNode continueLabel = new LabelNode();
        InsnList guard = new InsnList();
        guard.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                BYPASS_OWNER,
                "shouldBypass",
                "()Z",
                false
        ));
        guard.add(new JumpInsnNode(Opcodes.IFEQ, continueLabel));
        guard.add(new InsnNode(Opcodes.RETURN));
        guard.add(continueLabel);
        guard.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        method.instructions.insert(guard);
    }

    private static boolean insertBypassBeforeCancel(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode methodInsn)) {
                continue;
            }
            if (methodInsn.getOpcode() != Opcodes.INVOKEVIRTUAL
                    || !CALLBACK_INFO.equals(methodInsn.owner)
                    || !"cancel".equals(methodInsn.name)
                    || !"()V".equals(methodInsn.desc)) {
                continue;
            }

            AbstractInsnNode insertionPoint = instruction.getPrevious();
            if (insertionPoint == null) {
                insertionPoint = instruction;
            }

            LabelNode continueLabel = new LabelNode();
            InsnList guard = new InsnList();
            guard.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    BYPASS_OWNER,
                    "shouldBypass",
                    "()Z",
                    false
            ));
            guard.add(new JumpInsnNode(Opcodes.IFEQ, continueLabel));
            guard.add(new InsnNode(Opcodes.RETURN));
            guard.add(continueLabel);
            guard.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
            method.instructions.insertBefore(insertionPoint, guard);
            return true;
        }
        return false;
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
