package com.luna.ausm.impl.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class LumenizedEntityRendererBloomTransformer implements IClassTransformer {
    private static final String TARGET = "github.kasuminova.lumenized.mixin.vanilla.MixinEntityRenderer";
    private static final String INJECT_BLOOM_RENDERER = "injectBloomRenderer";
    private static final String INJECT_BLOOM_RENDERER_DESC =
            "(IFJLorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;Lnet/minecraft/client/renderer/RenderGlobal;Lnet/minecraft/entity/Entity;)V";

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
            if (INJECT_BLOOM_RENDERER.equals(method.name) && INJECT_BLOOM_RENDERER_DESC.equals(method.desc)) {
                method.instructions.insert(nothiriumReturnGuard());
                changed = true;
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

    private static InsnList nothiriumReturnGuard() {
        LabelNode returnLabel = new LabelNode();
        LabelNode continueLabel = new LabelNode();
        InsnList guard = new InsnList();
        guard.add(new LdcInsnNode("nothirium"));
        guard.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "net/minecraftforge/fml/common/Loader",
                "isModLoaded",
                "(Ljava/lang/String;)Z",
                false
        ));
        guard.add(new JumpInsnNode(Opcodes.IFNE, returnLabel));
        guard.add(new LdcInsnNode("naughthirium"));
        guard.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "net/minecraftforge/fml/common/Loader",
                "isModLoaded",
                "(Ljava/lang/String;)Z",
                false
        ));
        guard.add(new JumpInsnNode(Opcodes.IFEQ, continueLabel));
        guard.add(returnLabel);
        guard.add(new InsnNode(Opcodes.RETURN));
        guard.add(continueLabel);
        guard.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        return guard;
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
