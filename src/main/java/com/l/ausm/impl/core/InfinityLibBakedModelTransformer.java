package com.l.ausm.impl.core;

import com.google.common.collect.ImmutableList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Function;

public final class InfinityLibBakedModelTransformer implements IClassTransformer {
    private static final String BAKED_MODEL_TARGET = "com.infinityraider.infinitylib.render.block.BakedInfBlockModel";
    private static final String TESSELLATOR_TARGET = "com.infinityraider.infinitylib.render.tessellation.TessellatorBakedQuad";
    private static final String CUSTOM_WOOD_REGISTRY_TARGET = "com.infinityraider.agricraft.utility.CustomWoodTypeRegistry";
    private static final String BAKED_MODEL_INTERNAL = "com/infinityraider/infinitylib/render/block/BakedInfBlockModel";
    private static final String TESSELLATOR_INTERNAL = "com/infinityraider/infinitylib/render/tessellation/TessellatorBakedQuad";
    private static final String EXTENDED_BLOCK_STATE_INTERNAL = "net/minecraftforge/common/property/IExtendedBlockState";
    private static final String CUSTOM_WOOD_TYPE_INTERNAL = "com/infinityraider/agricraft/utility/CustomWoodType";
    private static final String GET_QUADS_DESC = "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/EnumFacing;J)Lcom/google/common/collect/ImmutableList;";
    private static final String CREATE_QUADS_DESC = "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/EnumFacing;J)Lcom/google/common/collect/ImmutableList;";
    private static final String AUSM_PARENT_FIELD = "ausm$parent";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        if (BAKED_MODEL_TARGET.equals(name) || BAKED_MODEL_TARGET.equals(transformedName)) {
            return transformBakedModel(basicClass);
        }
        if (TESSELLATOR_TARGET.equals(name) || TESSELLATOR_TARGET.equals(transformedName)) {
            return transformTessellator(basicClass);
        }
        if (CUSTOM_WOOD_REGISTRY_TARGET.equals(name) || CUSTOM_WOOD_REGISTRY_TARGET.equals(transformedName)) {
            return transformCustomWoodTypeRegistry(basicClass);
        }
        return basicClass;
    }

    private static byte[] transformBakedModel(byte[] basicClass) {
        ClassReader reader = new ClassReader(basicClass);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, ClassReader.EXPAND_FRAMES);

        boolean changed = false;
        for (MethodNode method : classNode.methods) {
            if (("getQuads".equals(method.name) && GET_QUADS_DESC.equals(method.desc))
                    || ("createQuads".equals(method.name) && CREATE_QUADS_DESC.equals(method.desc))) {
                if ((method.access & Opcodes.ACC_SYNCHRONIZED) == 0) {
                    method.access |= Opcodes.ACC_SYNCHRONIZED;
                    changed = true;
                }
            }
            if ("createQuads".equals(method.name) && CREATE_QUADS_DESC.equals(method.desc)) {
                int quadsLocal = method.maxLocals;
                method.maxLocals = Math.max(method.maxLocals, quadsLocal + 1);
                method.instructions.insert(cropStateRendererMismatchGuard(quadsLocal));
                changed = true;
            }
        }

        return changed ? writeClass(reader, classNode) : basicClass;
    }

    private static InsnList cropStateRendererMismatchGuard(int quadsLocal) {
        LabelNode continueOriginal = new LabelNode();
        InsnList code = new InsnList();

        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new JumpInsnNode(Opcodes.IFNULL, continueOriginal));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "com/l/ausm/impl/core/InfinityLibBakedModelTransformer",
                "rendererBlockMismatch",
                "(Ljava/lang/Object;Lnet/minecraft/block/state/IBlockState;)Z",
                false));
        code.add(new JumpInsnNode(Opcodes.IFEQ, continueOriginal));
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "com/l/ausm/impl/core/InfinityLibBakedModelTransformer",
                "isAgricraftCropState",
                "(Lnet/minecraft/block/state/IBlockState;)Z",
                false));
        code.add(new JumpInsnNode(Opcodes.IFEQ, continueOriginal));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new FieldInsnNode(Opcodes.GETFIELD, BAKED_MODEL_INTERNAL, "format",
                "Lnet/minecraft/client/renderer/vertex/VertexFormat;"));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new FieldInsnNode(Opcodes.GETFIELD, BAKED_MODEL_INTERNAL, "textureFunction",
                "Ljava/util/function/Function;"));
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new VarInsnNode(Opcodes.ALOAD, 2));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "com/l/ausm/impl/core/InfinityLibBakedModelTransformer",
                "renderAgricraftCropQuads",
                "(Lnet/minecraft/client/renderer/vertex/VertexFormat;Ljava/util/function/Function;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/EnumFacing;)Lcom/google/common/collect/ImmutableList;",
                false));
        code.add(new VarInsnNode(Opcodes.ASTORE, quadsLocal));
        code.add(new VarInsnNode(Opcodes.ALOAD, quadsLocal));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "com/google/common/collect/ImmutableList",
                "isEmpty", "()Z", false));
        code.add(new JumpInsnNode(Opcodes.IFNE, continueOriginal));
        code.add(new VarInsnNode(Opcodes.ALOAD, quadsLocal));
        code.add(new InsnNode(Opcodes.ARETURN));
        code.add(continueOriginal);
        return code;
    }

    private static byte[] transformTessellator(byte[] basicClass) {
        ClassReader reader = new ClassReader(basicClass);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, ClassReader.EXPAND_FRAMES);

        boolean changed = false;
        if (!hasField(classNode, AUSM_PARENT_FIELD, "L" + TESSELLATOR_INTERNAL + ";")) {
            classNode.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, AUSM_PARENT_FIELD,
                    "L" + TESSELLATOR_INTERNAL + ";", null, null));
            changed = true;
        }
        for (MethodNode method : classNode.methods) {
            if ("getInstance".equals(method.name) && ("()L" + TESSELLATOR_INTERNAL + ";").equals(method.desc)) {
                method.instructions.clear();
                method.tryCatchBlocks.clear();
                method.localVariables.clear();
                method.maxLocals = 2;
                method.maxStack = 3;
                method.instructions.add(stackedGetInstance());
                changed = true;
            }
            if ("onDrawCall".equals(method.name) && "()V".equals(method.desc)) {
                insertParentRestoreBeforeReturns(method);
                changed = true;
            }
        }

        return changed ? writeClass(reader, classNode) : basicClass;
    }

    private static boolean hasField(ClassNode classNode, String name, String desc) {
        for (FieldNode field : classNode.fields) {
            if (name.equals(field.name) && desc.equals(field.desc)) {
                return true;
            }
        }
        return false;
    }

    private static InsnList stackedGetInstance() {
        LabelNode checkReady = new LabelNode();
        LabelNode createChild = new LabelNode();
        InsnList code = new InsnList();

        code.add(new FieldInsnNode(Opcodes.GETSTATIC, TESSELLATOR_INTERNAL, "INSTANCE", "Ljava/lang/ThreadLocal;"));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/ThreadLocal", "get", "()Ljava/lang/Object;", false));
        code.add(new TypeInsnNode(Opcodes.CHECKCAST, TESSELLATOR_INTERNAL));
        code.add(new VarInsnNode(Opcodes.ASTORE, 0));

        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new JumpInsnNode(Opcodes.IFNONNULL, checkReady));
        code.add(new TypeInsnNode(Opcodes.NEW, TESSELLATOR_INTERNAL));
        code.add(new InsnNode(Opcodes.DUP));
        code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TESSELLATOR_INTERNAL, "<init>", "()V", false));
        code.add(new VarInsnNode(Opcodes.ASTORE, 0));
        code.add(new FieldInsnNode(Opcodes.GETSTATIC, TESSELLATOR_INTERNAL, "INSTANCE", "Ljava/lang/ThreadLocal;"));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/ThreadLocal", "set", "(Ljava/lang/Object;)V", false));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new InsnNode(Opcodes.ARETURN));

        code.add(checkReady);
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new FieldInsnNode(Opcodes.GETFIELD, TESSELLATOR_INTERNAL, "drawMode", "I"));
        code.add(new InsnNode(Opcodes.ICONST_M1));
        code.add(new JumpInsnNode(Opcodes.IF_ICMPNE, createChild));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new InsnNode(Opcodes.ARETURN));

        code.add(createChild);
        code.add(new TypeInsnNode(Opcodes.NEW, TESSELLATOR_INTERNAL));
        code.add(new InsnNode(Opcodes.DUP));
        code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TESSELLATOR_INTERNAL, "<init>", "()V", false));
        code.add(new VarInsnNode(Opcodes.ASTORE, 1));
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new FieldInsnNode(Opcodes.PUTFIELD, TESSELLATOR_INTERNAL, AUSM_PARENT_FIELD, "L" + TESSELLATOR_INTERNAL + ";"));
        code.add(new FieldInsnNode(Opcodes.GETSTATIC, TESSELLATOR_INTERNAL, "INSTANCE", "Ljava/lang/ThreadLocal;"));
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/ThreadLocal", "set", "(Ljava/lang/Object;)V", false));
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new InsnNode(Opcodes.ARETURN));
        return code;
    }

    private static void insertParentRestoreBeforeReturns(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (instruction.getOpcode() != Opcodes.RETURN) {
                continue;
            }
            method.instructions.insertBefore(instruction, restoreParentThreadLocal());
        }
    }

    private static InsnList restoreParentThreadLocal() {
        LabelNode skip = new LabelNode();
        InsnList code = new InsnList();

        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new FieldInsnNode(Opcodes.GETFIELD, TESSELLATOR_INTERNAL, AUSM_PARENT_FIELD, "L" + TESSELLATOR_INTERNAL + ";"));
        code.add(new JumpInsnNode(Opcodes.IFNULL, skip));
        code.add(new FieldInsnNode(Opcodes.GETSTATIC, TESSELLATOR_INTERNAL, "INSTANCE", "Ljava/lang/ThreadLocal;"));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new FieldInsnNode(Opcodes.GETFIELD, TESSELLATOR_INTERNAL, AUSM_PARENT_FIELD, "L" + TESSELLATOR_INTERNAL + ";"));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/ThreadLocal", "set", "(Ljava/lang/Object;)V", false));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new InsnNode(Opcodes.ACONST_NULL));
        code.add(new FieldInsnNode(Opcodes.PUTFIELD, TESSELLATOR_INTERNAL, AUSM_PARENT_FIELD, "L" + TESSELLATOR_INTERNAL + ";"));
        code.add(skip);
        return code;
    }

    private static byte[] transformCustomWoodTypeRegistry(byte[] basicClass) {
        ClassReader reader = new ClassReader(basicClass);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, ClassReader.EXPAND_FRAMES);

        boolean changed = false;
        for (MethodNode method : classNode.methods) {
            if ("getFromState".equals(method.name)
                    && "(Lnet/minecraft/block/state/IBlockState;)Ljava/util/Optional;".equals(method.desc)) {
                method.instructions.clear();
                method.tryCatchBlocks.clear();
                method.localVariables.clear();
                method.maxLocals = 1;
                method.maxStack = 2;
                method.instructions.add(safeCustomWoodGetFromState(method));
                changed = true;
            }
        }

        return changed ? writeClass(reader, classNode) : basicClass;
    }

    private static InsnList safeCustomWoodGetFromState(MethodNode method) {
        LabelNode tryStart = new LabelNode();
        LabelNode tryEnd = new LabelNode();
        LabelNode missing = new LabelNode();
        LabelNode failed = new LabelNode();
        InsnList code = new InsnList();

        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new TypeInsnNode(Opcodes.INSTANCEOF, EXTENDED_BLOCK_STATE_INTERNAL));
        code.add(new JumpInsnNode(Opcodes.IFEQ, missing));

        code.add(tryStart);
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new TypeInsnNode(Opcodes.CHECKCAST, EXTENDED_BLOCK_STATE_INTERNAL));
        code.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/infinityraider/agricraft/reference/AgriProperties",
                "CUSTOM_WOOD_TYPE", "Lnet/minecraftforge/common/property/IUnlistedProperty;"));
        code.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, EXTENDED_BLOCK_STATE_INTERNAL, "getValue",
                "(Lnet/minecraftforge/common/property/IUnlistedProperty;)Ljava/lang/Object;", true));
        code.add(new TypeInsnNode(Opcodes.CHECKCAST, CUSTOM_WOOD_TYPE_INTERNAL));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Optional", "ofNullable",
                "(Ljava/lang/Object;)Ljava/util/Optional;", false));
        code.add(tryEnd);
        code.add(new InsnNode(Opcodes.ARETURN));

        code.add(missing);
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Optional", "empty",
                "()Ljava/util/Optional;", false));
        code.add(new InsnNode(Opcodes.ARETURN));

        code.add(failed);
        code.add(new InsnNode(Opcodes.POP));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Optional", "empty",
                "()Ljava/util/Optional;", false));
        code.add(new InsnNode(Opcodes.ARETURN));

        method.tryCatchBlocks.add(new TryCatchBlockNode(tryStart, tryEnd, failed, "java/lang/IllegalArgumentException"));
        return code;
    }

    private static byte[] writeClass(ClassReader reader, ClassNode classNode) {
        ClassWriter writer = new SafeClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    public static boolean isAgricraftCropState(IBlockState state) {
        Object block = blockFromState(state);
        if (block == null) {
            return false;
        }
        try {
            Method getRegistryName = block.getClass().getMethod("getRegistryName");
            Object name = getRegistryName.invoke(block);
            return name != null && "agricraft:crop".equals(name.toString());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    public static boolean rendererBlockMismatch(Object model, IBlockState state) {
        Object stateBlock = blockFromState(state);
        Object modelBlock = blockFromModel(model);
        return stateBlock != null && modelBlock != null && stateBlock != modelBlock;
    }

    private static Object blockFromState(IBlockState state) {
        if (state == null) {
            return null;
        }
        Object block = invokeNoArg(state, "getBlock");
        return block != null ? block : invokeNoArg(state, "func_177230_c");
    }

    private static Object blockFromModel(Object model) {
        if (model == null) {
            return null;
        }
        for (Class<?> type = model.getClass(); type != null; type = type.getSuperclass()) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField("block");
                field.setAccessible(true);
                return field.get(model);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try the next superclass.
            }
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null || methodName == null) {
            return null;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try the next method spelling or superclass.
            }
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static ImmutableList renderAgricraftCropQuads(VertexFormat format, Function textureFunction,
                                                         IBlockState state, EnumFacing side) {
        Object block = blockFromState(state);
        if (format == null || textureFunction == null || block == null) {
            return ImmutableList.of();
        }
        try {
            Class<?> cropBlockClass = Class.forName("com.infinityraider.agricraft.blocks.BlockCrop");
            if (!cropBlockClass.isInstance(block)) {
                return ImmutableList.of();
            }

            Class<?> tessellatorClass = Class.forName(TESSELLATOR_TARGET);
            Object tessellator = tessellatorClass.getMethod("getInstance").invoke(null);
            tessellatorClass.getMethod("setCurrentFace", EnumFacing.class).invoke(tessellator, side);
            tessellatorClass.getMethod("setTextureFunction", Function.class).invoke(tessellator, textureFunction);
            tessellatorClass.getMethod("startDrawingQuads", VertexFormat.class).invoke(tessellator, format);

            Class<?> rendererClass = Class.forName("com.infinityraider.agricraft.renderers.blocks.RenderCrop");
            Constructor<?> constructor = rendererClass.getConstructor(cropBlockClass);
            Object renderer = constructor.newInstance(block);
            Class<?> itessellatorClass = Class.forName("com.infinityraider.infinitylib.render.tessellation.ITessellator");
            Method render = rendererClass.getMethod("renderWorldBlockStatic", itessellatorClass, IBlockState.class,
                    cropBlockClass, EnumFacing.class);
            render.invoke(renderer, tessellator, state, block, side);

            ImmutableList quads = (ImmutableList) tessellatorClass.getMethod("getQuads").invoke(tessellator);
            tessellatorClass.getMethod("draw").invoke(tessellator);
            return quads;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return ImmutableList.of();
        }
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
