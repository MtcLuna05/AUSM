package com.l.ausm.impl.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class StorageDrawersRendererTransformer implements IClassTransformer {
    private static final String TARGET = "com.jaquadro.minecraft.storagedrawers.client.renderer.TileEntityDrawersRenderer";
    private static final String RENDER_FAST_ITEM_SET = "renderFastItemSet";
    private static final String RENDER_FAST_ITEM_SET_DESC = "(Lcom/jaquadro/minecraft/chameleon/render/ChamRender;Lcom/jaquadro/minecraft/storagedrawers/block/tile/TileEntityDrawers;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/EnumFacing;FF)V";

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
            if (RENDER_FAST_ITEM_SET.equals(method.name) && RENDER_FAST_ITEM_SET_DESC.equals(method.desc)) {
                changed |= patchCountRenderDistance(method);
            }
        }

        if (!changed) {
            return basicClass;
        }

        ClassWriter writer = new SafeClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private static boolean patchCountRenderDistance(MethodNode method) {
        boolean changed = false;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof LdcInsnNode ldc) || !(ldc.cst instanceof Double distance)) {
                continue;
            }
            if (Math.abs(distance - 10.0D) < 0.0001D) {
                ldc.cst = 25.0D;
                changed = true;
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
