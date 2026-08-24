package com.luna.ausm.impl.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Lazily instruments Draconic Evolution renderers when their classes actually load.
 * This deliberately avoids an optional-target Mixin: Foundation does not expose the
 * Draconic JAR to Mixin when AUSM's early config is selected.
 */
public final class DraconicRenderedEmissionTransformer implements IClassTransformer {
    private static final String ENERGY_TARGET =
            "com.brandon3055.draconicevolution.client.render.tile.RenderTileEnergyStorageCore";
    private static final String REACTOR_TARGET =
            "com.brandon3055.draconicevolution.client.render.tile.RenderTileReactorCore";
    private static final String ENERGY_RENDER_DESC =
            "(Lcom/brandon3055/draconicevolution/blocks/tileentity/TileEnergyStorageCore;DDDFIF)V";
    private static final String REACTOR_RENDER_DESC =
            "(Lcom/brandon3055/draconicevolution/blocks/reactor/tileentity/TileReactorCore;DDDFIF)V";
    private static final String MODEL_UTILS = "com/brandon3055/brandonscore/utils/ModelUtils";
    private static final String REACTOR_OWNER =
            "com/brandon3055/draconicevolution/client/render/tile/RenderTileReactorCore";
    private static final String REACTOR_CORE_DESC = "(DDDFFFDZ)V";
    private static final String BRIDGE =
		    "com/luna/ausm/impl/pipeline/compat/DraconicRenderedEmissionCompat";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        boolean energy = ENERGY_TARGET.equals(name) || ENERGY_TARGET.equals(transformedName);
        boolean reactor = REACTOR_TARGET.equals(name) || REACTOR_TARGET.equals(transformedName);
        if (!energy && !reactor) {
            return basicClass;
        }

        ClassReader reader = new ClassReader(basicClass);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, ClassReader.EXPAND_FRAMES);

        boolean changed = energy ? patchEnergyCore(classNode) : patchReactorCore(classNode);
        if (!changed) {
            return basicClass;
        }

        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private static boolean patchEnergyCore(ClassNode classNode) {
        boolean changed = false;
        for (MethodNode method : classNode.methods) {
            if (!"render".equals(method.name) || !ENERGY_RENDER_DESC.equals(method.desc)) {
                continue;
            }
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call)
                        || call.getOpcode() != Opcodes.INVOKESTATIC
                        || !MODEL_UTILS.equals(call.owner)
                        || !"renderQuadsRGB".equals(call.name)
                        || !"(Ljava/util/List;FFF)V".equals(call.desc)) {
                    continue;
                }
                method.instructions.insertBefore(call, beginEmissionCall());
                method.instructions.insert(call, endEmissionCall());
                changed = true;
            }
        }
        return changed;
    }

    private static boolean patchReactorCore(ClassNode classNode) {
        boolean changed = false;
        for (MethodNode method : classNode.methods) {
            if (!"render".equals(method.name) || !REACTOR_RENDER_DESC.equals(method.desc)) {
                continue;
            }
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call)
                        || call.getOpcode() != Opcodes.INVOKESTATIC
                        || !REACTOR_OWNER.equals(call.owner)
                        || !"renderCore".equals(call.name)
                        || !REACTOR_CORE_DESC.equals(call.desc)) {
                    continue;
                }
                InsnList before = new InsnList();
                before.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        BRIDGE,
                        "useDraconicReactorShader",
                        "(Z)Z",
                        false
                ));
                before.add(beginEmissionCall());
                method.instructions.insertBefore(call, before);
                method.instructions.insert(call, endEmissionCall());
                changed = true;
            }
        }
        return changed;
    }

    private static MethodInsnNode beginEmissionCall() {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "beginEmission", "()V", false);
    }

    private static MethodInsnNode endEmissionCall() {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "endEmission", "()V", false);
    }
}
