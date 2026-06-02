package com.l.ausm.impl.pipeline.shader;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.pipeline.PipelineContext;

/**
 * Small Iris-shaped phase facade.
 *
 * <p>Iris exposes render-phase changes through GbufferPrograms instead of
 * letting every hook bind a concrete program directly. AUSM still has to bind
 * 1.12/OptiFine-style {@link RenderPass} programs, but new hooks should move
 * through this facade when they only need to communicate the current phase.</p>
 */
public final class GbufferPrograms {
    private GbufferPrograms() {
    }

    public static WorldRenderingPhase getCurrentPhase() {
        return PipelineContext.getInstance().getPhase();
    }

    public static void setPhase(WorldRenderingPhase phase) {
        PipelineContext.getInstance().setPhase(phase);
    }

    public static void clearPhase(WorldRenderingPhase expectedPhase) {
        PipelineContext.getInstance().clearPhase(expectedPhase);
    }

    public static void setOverridePhase(WorldRenderingPhase phase) {
        PipelineContext.getInstance().setOverridePhase(phase);
    }

    public static void clearOverridePhase() {
        PipelineContext.getInstance().clearOverridePhase();
    }
}
