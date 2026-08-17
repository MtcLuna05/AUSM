package com.l.ausm.impl.compat.nothirium;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;

/** Mutable per-compile-task data kept outside the transformed target's public type surface. */
public final class NothiriumRenderChunkCompileState {
    int fireCutoutFallbackStart = -1;
    int bloomOnlyBaseFallbackStart = -1;
    BlockRenderLayer bloomOnlyBaseFallbackLayer;
    IBlockState bloomOnlyBaseFallbackState;

    boolean bloomBaseRouteProbeTarget;
    String bloomBaseRouteProbeKind = "";
    IBlockState bloomBaseRouteProbeEffectiveState;
    BlockRenderLayer bloomBaseRouteProbeCurrentLayer;
    BlockRenderLayer bloomBaseRouteProbeBaseLayer;
    int bloomBaseRouteProbeCurrentStart = -1;
    int bloomBaseRouteProbeBaseStart = -1;
    int bloomBaseRouteProbeBloomStart = -1;

    int emissiveFallbackStart = -1;
    int nativeBloomProbeStart = -1;
    BlockRenderLayer nativeBloomProbeLayer;

    boolean framedBloomRouteProbeTarget;
    IBlockState framedBloomRouteProbeContainedState;
    BlockRenderLayer framedBloomRouteProbeCurrentLayer;
    int framedBloomRouteProbeCurrentStart = -1;
    int framedBloomRouteProbeBloomStart = -1;

    int framedDiagnosticStart = -1;
    BlockRenderLayer framedDiagnosticLayer;
    int terrainCompileProbeStart = -1;
    BlockRenderLayer terrainCompileProbeLayer;
}
