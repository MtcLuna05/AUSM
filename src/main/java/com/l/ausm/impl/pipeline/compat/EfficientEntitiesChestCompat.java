package com.l.ausm.impl.pipeline.compat;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/** Replaces Efficient Entities' per-model stack-trace chest detection with a
 * render-dispatch context established once per tile entity. */
public final class EfficientEntitiesChestCompat {
    private static final ThreadLocal<Deque<Boolean>> CHEST_RENDER_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private EfficientEntitiesChestCompat() {
    }

    public static void beginTileEntity(TileEntity tileEntity) {
        CHEST_RENDER_STACK.get().push(isChest(tileEntity));
    }

    public static void endTileEntity() {
        Deque<Boolean> stack = CHEST_RENDER_STACK.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            CHEST_RENDER_STACK.remove();
        }
    }

    public static boolean isChestRenderActive() {
        Deque<Boolean> stack = CHEST_RENDER_STACK.get();
        return !stack.isEmpty() && stack.peek();
    }

    /**
     * Efficient Entities cancels vanilla {@code ModelRenderer.render} when its
     * helper returns false. Its shared persistent model buffers are not safe
     * across AUSM's shader phases, so retain vanilla model submission for the
     * whole time the shader pipeline is active as well as for chests.
     */
    public static boolean shouldUseVanillaModelRenderer(boolean shaderPipelineActive) {
        return shaderPipelineActive || isChestRenderActive();
    }

    static boolean isChest(TileEntity tileEntity) {
        if (tileEntity == null) {
            return false;
        }
        if (tileEntity instanceof TileEntityChest || tileEntity instanceof TileEntityEnderChest) {
            return true;
        }
        return isChestClassName(tileEntity.getClass().getName());
    }

    static boolean isChestClassName(String className) {
        return className != null && className.toLowerCase(Locale.ROOT).contains("chest");
    }
}
