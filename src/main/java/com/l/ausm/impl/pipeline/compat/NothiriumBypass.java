package com.l.ausm.impl.pipeline.compat;

import com.l.ausm.impl.pipeline.PipelineContext;

public final class NothiriumBypass {

    private NothiriumBypass() {
    }

    public static boolean shouldBypass() {
        try {
            return PipelineContext.getInstance().isActive();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
