package com.l.ausm.impl.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Field;

/**
 * Keeps ZenUtils' script-defined mixins working with CleanMix versions that no
 * longer expose {@code Proxy.transformer} as a public field.
 */
public final class ZenUtilsCleanMixTransformer implements IClassTransformer {
    private static final String TARGET = "youyihj.zenutils.impl.zenscript.mixin.ZenMixin";
    private static final String PROXY = "org/spongepowered/asm/mixin/transformer/Proxy";
    private static final String MIXIN_TRANSFORMER = "Lorg/spongepowered/asm/mixin/transformer/MixinTransformer;";
    private static final String HELPER = "com/l/ausm/impl/core/ZenUtilsCleanMixTransformer";

    private static volatile Object activeMixinTransformer;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || (!TARGET.equals(name) && !TARGET.equals(transformedName))) {
            return basicClass;
        }

        ClassReader reader = new ClassReader(basicClass);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);

        boolean changed = false;
        for (MethodNode method : classNode.methods) {
            if (!"load".equals(method.name) || !"()V".equals(method.desc)) {
                continue;
            }
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof FieldInsnNode field)
                        || field.getOpcode() != Opcodes.GETSTATIC
                        || !PROXY.equals(field.owner)
                        || !"transformer".equals(field.name)
                        || !MIXIN_TRANSFORMER.equals(field.desc)) {
                    continue;
                }
                method.instructions.set(field, new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        HELPER,
                        "getActiveMixinTransformer",
                        "()Ljava/lang/Object;",
                        false
                ));
                changed = true;
            }
        }

        if (!changed) {
            return basicClass;
        }

        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    public static Object getActiveMixinTransformer() {
        Object transformer = activeMixinTransformer;
        if (transformer != null) {
            return transformer;
        }

        synchronized (ZenUtilsCleanMixTransformer.class) {
            transformer = activeMixinTransformer;
            if (transformer != null) {
                return transformer;
            }
            try {
                Class<?> proxyClass = Class.forName(
                        PROXY.replace('/', '.'),
                        false,
                        ZenUtilsCleanMixTransformer.class.getClassLoader()
                );
                Field field = proxyClass.getDeclaredField("transformer");
                field.setAccessible(true);
                transformer = field.get(null);
                if (transformer == null) {
                    throw new IllegalStateException("CleanMix Proxy has no active transformer");
                }
                activeMixinTransformer = transformer;
                return transformer;
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to access the active CleanMix transformer", exception);
            }
        }
    }
}
