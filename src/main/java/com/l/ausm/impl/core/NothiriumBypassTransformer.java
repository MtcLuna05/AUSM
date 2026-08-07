package com.l.ausm.impl.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.File;

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
    private static final String RENDER_GLOBAL = "net.minecraft.client.renderer.RenderGlobal";
    private static final String CELERITAS_MINECRAFT_MIXIN = "org.taumc.celeritas.mixin.core.MinecraftMixin";
    private static final String CELERITAS_RENDER_GLOBAL_MIXIN = "org.taumc.celeritas.mixin.core.terrain.RenderGlobalMixin";
    private static final String CELERITAS_VINTAGE = "org.taumc.celeritas.CeleritasVintage";
    private static final String UNIVERSAL_TWEAKS_FRUSTUM_MIXIN = "mod.acgaming.universaltweaks.bugfixes.world.culling.mixin.UTFrustumCullingMixin";
    private static final String NAUGHTHIRIUM_FLOAT_VERTEX_CONSUMER = "zone.rong.naughthirium.compat.loliasm.FloatVertexConsumer";
    private static final String NAUGHTHIRIUM_RENDER_TASK_MIXIN = "zone.rong.naughthirium.mixins.loliasm.RenderChunkTaskCompileMixin";
    private static final String BYPASS_OWNER = "com/l/ausm/impl/pipeline/compat/NothiriumBypass";
    private static final String MARK_BLOCKS_FOR_UPDATE = "markBlocksForUpdate(IIIIIIZLorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
    private static final Map<String, String> HANDLER_BYPASS_METHODS = new HashMap<>();
    private static final Set<String> VOID_HANDLERS = new HashSet<>();
    private static volatile Boolean celeritasInstalled;
    private static volatile Boolean nothiriumInstalled;

    static {
        VOID_HANDLERS.add("stopChunkUpdates(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
        VOID_HANDLERS.add("getDebugInfoRenders(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V");
        VOID_HANDLERS.add("getRenderedChunks(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V");
        VOID_HANDLERS.add("setupTerrain(Lnet/minecraft/entity/Entity;DLnet/minecraft/client/renderer/culling/ICamera;IZLorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
        VOID_HANDLERS.add("getRenderChunkOffset(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V");
        VOID_HANDLERS.add("renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V");
        VOID_HANDLERS.add("updateChunks(JLorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
        VOID_HANDLERS.add("hasNoChunkUpdates(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V");

        for (String signature : VOID_HANDLERS) {
            HANDLER_BYPASS_METHODS.put(signature, "shouldBypass");
        }
        HANDLER_BYPASS_METHODS.put(
                "setupTerrain(Lnet/minecraft/entity/Entity;DLnet/minecraft/client/renderer/culling/ICamera;IZLorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",
                "shouldBypassSetupTerrain"
        );
        HANDLER_BYPASS_METHODS.put(
                "updateChunks(JLorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",
                "shouldBypassChunkUpdates"
        );
        HANDLER_BYPASS_METHODS.put(
                "hasNoChunkUpdates(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V",
                "shouldBypassChunkUpdates"
        );
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        if (CELERITAS_RENDER_GLOBAL_MIXIN.equals(name)
                || CELERITAS_RENDER_GLOBAL_MIXIN.equals(transformedName)) {
            // Celeritas' RenderGlobal provider couples chunk construction to
            // CeleritasWorldRenderer. Remove it entirely so Nothirium/AUSM
            // own the renderer; independent Celeritas frame-ahead remains.
            return stripCeleritasRenderOwnership(basicClass);
        }
        if (CELERITAS_MINECRAFT_MIXIN.equals(name)
                || CELERITAS_MINECRAFT_MIXIN.equals(transformedName)) {
            return basicClass;
        }
        if (CELERITAS_VINTAGE.equals(name) || CELERITAS_VINTAGE.equals(transformedName)) {
            return stripCeleritasDebugOverlayHandler(basicClass);
        }
        // Celeritas prevents LoliASM's BufferBuilder primer interface from being
        // applied even when Nothirium remains the selected terrain renderer.
        if (shouldStripNaughthiriumHooks()
                && (NAUGHTHIRIUM_FLOAT_VERTEX_CONSUMER.equals(name) || NAUGHTHIRIUM_FLOAT_VERTEX_CONSUMER.equals(transformedName))) {
            return stripLoliTextureHook(basicClass);
        }
        if (shouldStripNaughthiriumHooks()
                && (NAUGHTHIRIUM_RENDER_TASK_MIXIN.equals(name) || NAUGHTHIRIUM_RENDER_TASK_MIXIN.equals(transformedName))) {
            return stripHandlers(basicClass);
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
                insertBlockUpdateBypassGuard(method);
                changed = true;
            } else if (VOID_HANDLERS.contains(signature)) {
                insertBypassGuard(method, HANDLER_BYPASS_METHODS.getOrDefault(signature, "shouldBypass"));
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

    private static byte[] stripHandlers(byte[] basicClass) {
        ClassReader reader = new ClassReader(basicClass);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        classNode.methods.removeIf(method -> !"<init>".equals(method.name));
        ClassWriter writer = new SafeClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    /**
     * Celeritas 2.4.0 expects three legacy RenderGlobal instructions which are
     * removed by earlier pack transforms. Its terrain overwrites remain valid,
     * but these redirects must tolerate their missing legacy targets.
     */
    private static byte[] relaxCeleritasRedirectRequirements(byte[] basicClass) {
        ClassReader reader = new ClassReader(basicClass);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        boolean changed = false;
        for (MethodNode method : classNode.methods) {
            if (!"nullifyBuiltChunkStorage".equals(method.name)
                    && !"alwaysHaveBuilders".equals(method.name)
                    && !"alwaysHaveNoTasks".equals(method.name)) {
                continue;
            }
            if (relaxRedirectAnnotation(method.visibleAnnotations)) {
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

    private static boolean relaxRedirectAnnotation(List<AnnotationNode> annotations) {
        if (annotations == null) {
            return false;
        }
        for (AnnotationNode annotation : annotations) {
            if (!"Lorg/spongepowered/asm/mixin/injection/Redirect;".equals(annotation.desc)) {
                continue;
            }
            if (annotation.values == null) {
                annotation.values = new java.util.ArrayList<>();
            }
            for (int index = 0; index < annotation.values.size(); index += 2) {
                if ("require".equals(annotation.values.get(index))) {
                    annotation.values.set(index + 1, 0);
                    return true;
                }
            }
            annotation.values.add("require");
            annotation.values.add(0);
            return true;
        }
        return false;
    }

    private static byte[] stripCeleritasRedirects(byte[] basicClass) {
        ClassReader reader = new ClassReader(basicClass);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        classNode.methods.removeIf(method -> "nullifyBuiltChunkStorage".equals(method.name)
                || "alwaysHaveBuilders".equals(method.name)
                || "alwaysHaveNoTasks".equals(method.name));
        ClassWriter writer = new SafeClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] stripCeleritasRenderOwnership(byte[] basicClass) {
        ClassReader reader = new ClassReader(basicClass);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        boolean changed = classNode.methods.removeIf(method -> !"<init>".equals(method.name));
        changed |= !classNode.interfaces.isEmpty();
        classNode.interfaces.clear();
        if (!changed) {
            return basicClass;
        }
        ClassWriter writer = new SafeClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    /**
     * AUSM deliberately prevents Celeritas from installing its terrain renderer.
     * Its F3 event still assumes that renderer is present and casts RenderGlobal
     * to Celeritas's provider interface, so leave its lifecycle hooks intact but
     * remove only the incompatible debug-overlay subscriber.
     */
    private static byte[] stripCeleritasDebugOverlayHandler(byte[] basicClass) {
        ClassReader reader = new ClassReader(basicClass);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        boolean changed = classNode.methods.removeIf(method -> "onF3Text".equals(method.name)
                && "(Lnet/minecraftforge/client/event/RenderGameOverlayEvent$Text;)V".equals(method.desc));
        if (!changed) {
            return basicClass;
        }
        ClassWriter writer = new SafeClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] stripLoliTextureHook(byte[] basicClass) {
        ClassReader reader = new ClassReader(basicClass);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        for (MethodNode method : classNode.methods) {
            if (!"tex".equals(method.name)) {
                continue;
            }
            AbstractInsnNode start = null;
            AbstractInsnNode end = null;
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof TypeInsnNode typeInsn
                        && instruction.getOpcode() == Opcodes.CHECKCAST
                        && "zone/rong/loliasm/client/sprite/ondemand/IBufferPrimerConfigurator".equals(typeInsn.desc)) {
                    start = instruction;
                } else if (start != null && instruction.getOpcode() == Opcodes.INVOKEINTERFACE) {
                    end = instruction;
                    break;
                }
            }
            if (start != null && end != null) {
                AbstractInsnNode instruction = start;
                while (instruction != null) {
                    AbstractInsnNode next = instruction.getNext();
                    method.instructions.remove(instruction);
                    if (instruction == end) {
                        break;
                    }
                    instruction = next;
                }
            }
        }
        ClassWriter writer = new SafeClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private static boolean celeritasPresent() {
        Boolean cached = celeritasInstalled;
        if (cached != null) {
            return cached;
        }
        boolean present = modPresent("celeritas");
        celeritasInstalled = present;
        return present;
    }

    private static boolean nothiriumPresent() {
        Boolean cached = nothiriumInstalled;
        if (cached != null) {
            return cached;
        }
        boolean present = modPresent("nothirium", "naughthirium");
        nothiriumInstalled = present;
        return present;
    }

    private static boolean shouldStripNaughthiriumHooks() {
        // AUSM owns the vertex metadata contract whenever Nothirium is the
        // active backend. LoliASM's optional Naughthirium hooks can append a
        // different primer/compile path even without Celeritas installed.
        return celeritasPresent() || nothiriumPresent();
    }

    private static boolean modPresent(String... prefixes) {
        File mods = new File(System.getProperty("user.dir", "."), "mods");
        File[] files = mods.listFiles((dir, fileName) -> {
            String lower = fileName.toLowerCase();
            if (!lower.endsWith(".jar") && !lower.endsWith(".zip")) {
                return false;
            }
            for (String prefix : prefixes) {
                if (lower.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        });
        return files != null && files.length > 0;
    }

    private static void insertBypassGuard(MethodNode method, String bypassMethodName) {
        LabelNode continueLabel = new LabelNode();
        InsnList guard = new InsnList();
        guard.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                BYPASS_OWNER,
                bypassMethodName,
                "()Z",
                false
        ));
        guard.add(new JumpInsnNode(Opcodes.IFEQ, continueLabel));
        guard.add(new InsnNode(Opcodes.RETURN));
        guard.add(continueLabel);
        guard.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        method.instructions.insert(guard);
    }

    private static void insertBlockUpdateBypassGuard(MethodNode method) {
        LabelNode continueLabel = new LabelNode();
        InsnList guard = new InsnList();
        for (int local = 1; local <= 6; local++) {
            guard.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ILOAD, local));
        }
        guard.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                BYPASS_OWNER,
                "shouldBypassBlockUpdates",
                "(IIIIII)Z",
                false
        ));
        guard.add(new JumpInsnNode(Opcodes.IFEQ, continueLabel));
        guard.add(new InsnNode(Opcodes.RETURN));
        guard.add(continueLabel);
        guard.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        method.instructions.insert(guard);
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
