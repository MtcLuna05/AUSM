package com.l.ausm.api.pipeline.pack;

import com.l.ausm.api.pipeline.shader.ComputeProgramSource;
import com.l.ausm.api.pipeline.shader.ProgramArrayId;
import java.util.List;
import java.util.Map;

public record ShaderComputeDirectives(
        Map<ProgramArrayId, List<ComputeProgramSource>> computeArrays,
        List<ComputeProgramSource> shadowComputes,
        List<ComputeProgramSource> finalComputes
) {
    public static ShaderComputeDirectives empty() {
        return new ShaderComputeDirectives(Map.of(), List.of(), List.of());
    }

    public boolean hasComputes() {
        if (!shadowComputes.isEmpty() || !finalComputes.isEmpty()) {
            return true;
        }
        return computeArrays.values().stream().anyMatch(list -> !list.isEmpty());
    }
}
