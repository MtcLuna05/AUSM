package com.l.ausm.api.pipeline.shader;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

/**
 * Parsed identity for an Iris-style compute shader source.
 *
 * <p>Execution is intentionally not wired yet. Keeping the source metadata in
 * the program set lets later compute support follow Iris' loading model instead
 * of bolting compute shaders onto the old render-pass enum.</p>
 */
public record ComputeProgramSource(
        String name,
        String path,
        String source,
        int[] workGroups,
        float[] workGroupRelative
) {
    public boolean hasFixedWorkGroups() {
        return workGroups != null;
    }

    public boolean hasRelativeWorkGroups() {
        return workGroupRelative != null;
    }
}
