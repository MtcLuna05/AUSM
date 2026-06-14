package com.l.ausm.impl.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class AusmBloomCtmTransformer implements IClassTransformer {
    private static final String CTM_CORE_TARGET = "team.chisel.ctm.client.asm.CTMCoreMethods";
    private static final String CTM_CORE_METHOD = "canRenderInLayer";
    private static final String CTM_CORE_METHOD_DESC = "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/BlockRenderLayer;)Ljava/lang/Boolean;";
    private static final String CTM_BAKED_MODEL_TARGET = "team.chisel.ctm.client.model.AbstractCTMBakedModel";
    private static final String CTM_BAKED_MODEL_METHOD = "func_188616_a";
    private static final String CTM_BAKED_MODEL_METHOD_DESC = "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/EnumFacing;J)Ljava/util/List;";
    private static final String MODEL_INTERFACE = "team/chisel/ctm/api/model/IModelCTM";
    private static final String CAN_RENDER_METHOD_DESC = "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/BlockRenderLayer;)Z";
    private static final String HOOK_OWNER = "com/l/ausm/impl/pipeline/bloom/AusmBloomCtmHooks";
    private static final String CAN_RENDER_HOOK_DESC = "(Ljava/lang/Object;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/BlockRenderLayer;)Z";
    private static final String GET_QUADS_HOOK_DESC = "(Ljava/util/List;Lnet/minecraft/util/BlockRenderLayer;Lnet/minecraft/client/renderer/block/model/IBakedModel;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/EnumFacing;J)Ljava/util/List;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }
        boolean ctmCore = CTM_CORE_TARGET.equals(name) || CTM_CORE_TARGET.equals(transformedName);
        boolean bakedModel = CTM_BAKED_MODEL_TARGET.equals(name) || CTM_BAKED_MODEL_TARGET.equals(transformedName);
        if (!ctmCore && !bakedModel) {
            return basicClass;
        }

        ClassReader reader = new ClassReader(basicClass);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, ClassReader.EXPAND_FRAMES);

        boolean changed = ctmCore
                ? patchCtmCoreMethods(classNode)
                : patchAbstractCtmBakedModel(classNode);

        if (!changed) {
            return basicClass;
        }

        ClassWriter writer = new SafeClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private static boolean patchCtmCoreMethods(ClassNode classNode) {
        boolean changed = false;
        for (MethodNode method : classNode.methods) {
            if (!CTM_CORE_METHOD.equals(method.name) || !CTM_CORE_METHOD_DESC.equals(method.desc)) {
                continue;
            }

            for (org.objectweb.asm.tree.AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call)) {
                    continue;
                }
                if (call.getOpcode() == Opcodes.INVOKEINTERFACE
                        && MODEL_INTERFACE.equals(call.owner)
                        && CTM_CORE_METHOD.equals(call.name)
                        && CAN_RENDER_METHOD_DESC.equals(call.desc)) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = HOOK_OWNER;
                    call.desc = CAN_RENDER_HOOK_DESC;
                    call.itf = false;
                    changed = true;
                }
            }
        }

        return changed;
    }

    private static boolean patchAbstractCtmBakedModel(ClassNode classNode) {
        boolean changed = false;
        for (MethodNode method : classNode.methods) {
            if (!CTM_BAKED_MODEL_METHOD.equals(method.name) || !CTM_BAKED_MODEL_METHOD_DESC.equals(method.desc)) {
                continue;
            }

            int returnIndex = 0;
            for (org.objectweb.asm.tree.AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction.getOpcode() != Opcodes.ARETURN) {
                    continue;
                }
                if (returnIndex++ != 1) {
                    continue;
                }

                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 7));
                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                hook.add(new VarInsnNode(Opcodes.ALOAD, 2));
                hook.add(new VarInsnNode(Opcodes.LLOAD, 3));
                hook.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        HOOK_OWNER,
                        "getQuadsWithAusmBloom",
                        GET_QUADS_HOOK_DESC,
                        false
                ));
                method.instructions.insertBefore(instruction, hook);
                changed = true;
                break;
            }
        }

        return changed;
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
