package com.l.ausm.impl.pipeline.shader;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.pack.ShaderDimensionContext;
import com.l.ausm.impl.pipeline.pack.ShaderPack;
import com.l.ausm.impl.pipeline.pack.ShaderPackLayout;
import com.l.ausm.api.pipeline.pack.ShaderComputeDirectives;
import com.l.ausm.impl.pipeline.pack.ShaderProperties;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Iris-style container for all shaderpack programs.
 *
 * <p>For now, AUSM's 1.12 renderer still consumes concrete {@link RenderPass}
 * hooks. This object is the primary source inventory and directive owner; the
 * pass layer should increasingly become a backport adapter.</p>
 */
public final class ShaderProgramSet {
    private static final Pattern WORK_GROUPS_PATTERN = Pattern.compile("\\bconst\\s+ivec3\\s+workGroups\\s*=\\s*ivec3\\s*\\(([^)]*)\\)\\s*;.*");
    private static final Pattern WORK_GROUPS_RENDER_PATTERN = Pattern.compile("\\bconst\\s+vec2\\s+workGroupsRender\\s*=\\s*vec2\\s*\\(([^)]*)\\)\\s*;.*");

    private final Map<ProgramId, ShaderProgramSource> programs;
    private final Map<ProgramArrayId, List<ShaderProgramSource>> programArrays;
    private final Map<ProgramArrayId, List<ComputeProgramSource>> computeArrays;
    private final List<ComputeProgramSource> shadowComputes;
    private final List<ComputeProgramSource> finalComputes;

    private ShaderProgramSet(
            Map<ProgramId, ShaderProgramSource> programs,
            Map<ProgramArrayId, List<ShaderProgramSource>> programArrays,
            Map<ProgramArrayId, List<ComputeProgramSource>> computeArrays,
            List<ComputeProgramSource> shadowComputes,
            List<ComputeProgramSource> finalComputes
    ) {
        this.programs = Map.copyOf(programs);
        this.programArrays = Map.copyOf(programArrays);
        this.computeArrays = Map.copyOf(computeArrays);
        this.shadowComputes = List.copyOf(shadowComputes);
        this.finalComputes = List.copyOf(finalComputes);
    }

    public static ShaderProgramSet load(ShaderPack pack, ShaderProperties properties) {
        ShaderPackLayout layout = ShaderPackLayout.detect(pack);
        Map<ProgramId, ShaderProgramSource> programs = new EnumMap<>(ProgramId.class);
        for (ProgramId programId : ProgramId.values()) {
            ProgramSourceSet paths = ProgramSourceResolver.resolve(pack, programId);
            programs.put(programId, new ShaderProgramSource(
                    programId,
                    programId.sourceName(),
                    paths.vertexPath(),
                    null,
                    paths.tessellationControlPath(),
                    null,
                    paths.tessellationEvaluationPath(),
                    null,
                    paths.geometryPath(),
                    null,
                    paths.fragmentPath(),
                    null,
                    properties.directivesFor(programId)
            ));
        }

        Map<ProgramArrayId, List<ShaderProgramSource>> programArrays = new EnumMap<>(ProgramArrayId.class);
        Map<ProgramArrayId, List<ComputeProgramSource>> computeArrays = new EnumMap<>(ProgramArrayId.class);
        for (ProgramArrayId arrayId : ProgramArrayId.values()) {
            programArrays.put(arrayId, loadProgramArray(pack, layout, properties, arrayId));
            computeArrays.put(arrayId, loadComputeArray(pack, layout, properties, arrayId, arrayId.sourcePrefix()));
        }

        return new ShaderProgramSet(
                programs,
                programArrays,
                computeArrays,
                loadComputeArray(pack, layout, null, null, "shadow"),
                loadComputeArray(pack, layout, null, null, "final")
        );
    }

    public ShaderProgramSource source(ProgramId programId) {
        return programs.get(programId);
    }

    public List<ShaderProgramSource> programArray(ProgramArrayId arrayId) {
        return programArrays.getOrDefault(arrayId, List.of());
    }

    public List<ComputeProgramSource> computeArray(ProgramArrayId arrayId) {
        return computeArrays.getOrDefault(arrayId, List.of());
    }

    public List<ComputeProgramSource> shadowComputes() {
        return shadowComputes;
    }

    public List<ComputeProgramSource> finalComputes() {
        return finalComputes;
    }

    public Map<ProgramArrayId, List<ComputeProgramSource>> computeArrays() {
        return computeArrays;
    }

    public ShaderComputeDirectives computeDirectives() {
        return new ShaderComputeDirectives(computeArrays, shadowComputes, finalComputes);
    }

    public boolean hasTessellationSources() {
        for (ShaderProgramSource source : programs.values()) {
            if (hasTessellationStage(source)) {
                return true;
            }
        }
        for (List<ShaderProgramSource> sources : programArrays.values()) {
            for (ShaderProgramSource source : sources) {
                if (hasTessellationStage(source)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasGeometrySources() {
        for (ShaderProgramSource source : programs.values()) {
            if (hasGeometryStage(source)) {
                return true;
            }
        }
        for (List<ShaderProgramSource> sources : programArrays.values()) {
            for (ShaderProgramSource source : sources) {
                if (hasGeometryStage(source)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasTessellationStage(ShaderProgramSource source) {
        return source != null
                && (source.tessellationControlPath() != null || source.tessellationEvaluationPath() != null);
    }

    private static boolean hasGeometryStage(ShaderProgramSource source) {
        return source != null && source.geometryPath() != null;
    }

    private static List<ShaderProgramSource> loadProgramArray(
            ShaderPack pack,
            ShaderPackLayout layout,
            ShaderProperties properties,
            ProgramArrayId arrayId
    ) {
        java.util.ArrayList<ShaderProgramSource> sources = new java.util.ArrayList<>(arrayId.programCount());
        for (int index = 0; index < arrayId.programCount(); index++) {
            String name = arrayId.sourcePrefix() + (index == 0 ? "" : Integer.toString(index));
            String vertexPath = resolveProgramArrayStage(pack, layout, name, ".vsh");
            String tessellationControlPath = resolveProgramArrayStage(pack, layout, name, ".tcs");
            String tessellationEvaluationPath = resolveProgramArrayStage(pack, layout, name, ".tes");
            String geometryPath = resolveProgramArrayStage(pack, layout, name, ".gsh");
            String fragmentPath = resolveProgramArrayStage(pack, layout, name, ".fsh");
            ShaderProgramSource source = new ShaderProgramSource(
                    null,
                    name,
                    vertexPath,
                    null,
                    tessellationControlPath,
                    null,
                    tessellationEvaluationPath,
                    null,
                    geometryPath,
                    null,
                    fragmentPath,
                    null,
                    null
            );
            sources.add(source);
        }
        return List.copyOf(sources);
    }

    private static String resolveProgramArrayStage(ShaderPack pack, ShaderPackLayout layout, String name, String extension) {
        int dimensionId = ShaderDimensionContext.currentDimensionId();
        for (int candidateDimensionId : dimensionFallbackOrder(dimensionId)) {
            for (String dimensionBase : layout.dimensionSourceBaseAliases(candidateDimensionId, name)) {
                String dimensionPath = existingPath(pack, dimensionBase + extension);
                if (dimensionPath != null) {
                    return dimensionPath;
                }
            }
        }
        return existingPath(pack, layout.rootPath(name + extension));
    }

    private static List<ComputeProgramSource> loadComputeArray(
            ShaderPack pack,
            ShaderPackLayout layout,
            ShaderProperties properties,
            ProgramArrayId arrayId,
            String prefix
    ) {
        java.util.ArrayList<ComputeProgramSource> sources = new java.util.ArrayList<>();
        if (arrayId == null) {
            addComputeFamily(pack, layout, properties, null, sources, prefix, 0);
            return List.copyOf(sources);
        }

        for (int index = 0; index < arrayId.programCount(); index++) {
            String name = prefix + (index == 0 ? "" : Integer.toString(index));
            addComputeFamily(pack, layout, properties, arrayId, sources, name, index);
        }
        return List.copyOf(sources);
    }

    private static void addComputeFamily(
            ShaderPack pack,
            ShaderPackLayout layout,
            ShaderProperties properties,
            ProgramArrayId arrayId,
            List<ComputeProgramSource> sources,
            String prefix,
            int arrayIndex
    ) {
        addComputeIfPresent(pack, layout, properties, arrayId, sources, prefix, arrayIndex);
        for (char suffix = 'a'; suffix <= 'z'; suffix++) {
            if (!addComputeIfPresent(pack, layout, properties, arrayId, sources, prefix + "_" + suffix, arrayIndex)) {
                break;
            }
        }
    }

    private static boolean addComputeIfPresent(
            ShaderPack pack,
            ShaderPackLayout layout,
            ShaderProperties properties,
            ProgramArrayId arrayId,
            List<ComputeProgramSource> sources,
            String name,
            int arrayIndex
    ) {
        String path = resolveComputePath(pack, layout, name);
        if (path == null) {
            return false;
        }
        if (properties != null && arrayId != null && !properties.isProgramArrayEnabled(arrayId, arraySourceName(arrayId, arrayIndex))) {
            MainMod.LOGGER.debug(
                    "[ShaderProgramSet] Skipping disabled compute '{}'.",
                    name
            );
            return true;
        }
        String source = readKnownExisting(pack, path);
        sources.add(new ComputeProgramSource(
                name,
                arrayIndex,
                path,
                source,
                parseWorkGroups(source),
                parseWorkGroupRelative(source),
                properties == null ? null : properties.indirectPointer(name)
        ));
        return true;
    }

    private static String arraySourceName(ProgramArrayId arrayId, int index) {
        return arrayId.sourcePrefix() + (index == 0 ? "" : Integer.toString(index));
    }

    private static String resolveComputePath(ShaderPack pack, ShaderPackLayout layout, String name) {
        int dimensionId = ShaderDimensionContext.currentDimensionId();
        for (int candidateDimensionId : dimensionFallbackOrder(dimensionId)) {
            for (String dimensionBase : layout.dimensionSourceBaseAliases(candidateDimensionId, name)) {
                String dimensionPath = existingPath(pack, dimensionBase + ".csh");
                if (dimensionPath != null) {
                    return dimensionPath;
                }
            }
        }
        String rootPath = existingPath(pack, layout.rootPath(name + ".csh"));
        if (rootPath != null) {
            return rootPath;
        }
        return null;
    }

    private static int[] dimensionFallbackOrder(int dimensionId) {
        if (dimensionId == 0) {
            return new int[]{0};
        }
        return new int[]{dimensionId, 0};
    }

    private static int[] parseWorkGroups(String source) {
        Matcher matcher = WORK_GROUPS_PATTERN.matcher(source == null ? "" : source);
        if (!matcher.find()) {
            return null;
        }
        String[] parts = matcher.group(1).split(",");
        if (parts.length != 3) {
            MainMod.LOGGER.warn("[ShaderProgramSet] Ignoring malformed workGroups directive: {}", matcher.group(0));
            return null;
        }
        try {
            return new int[]{
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())
            };
        } catch (NumberFormatException e) {
            MainMod.LOGGER.warn("[ShaderProgramSet] Ignoring malformed workGroups directive: {}", matcher.group(0));
            return null;
        }
    }

    private static float[] parseWorkGroupRelative(String source) {
        Matcher matcher = WORK_GROUPS_RENDER_PATTERN.matcher(source == null ? "" : source);
        if (!matcher.find()) {
            return null;
        }
        String[] parts = matcher.group(1).split(",");
        if (parts.length != 2) {
            MainMod.LOGGER.warn("[ShaderProgramSet] Ignoring malformed workGroupsRender directive: {}", matcher.group(0));
            return null;
        }
        try {
            return new float[]{
                    Float.parseFloat(parts[0].trim()),
                    Float.parseFloat(parts[1].trim())
            };
        } catch (NumberFormatException e) {
            MainMod.LOGGER.warn("[ShaderProgramSet] Ignoring malformed workGroupsRender directive: {}", matcher.group(0));
            return null;
        }
    }

    private static String existingPath(ShaderPack pack, String path) {
        return path != null && pack.hasResource(path) ? path : null;
    }

    private static String read(ShaderPack pack, String path) {
        if (path == null || !pack.hasResource(path)) {
            return null;
        }
        return readKnownExisting(pack, path);
    }

    private static String readKnownExisting(ShaderPack pack, String path) {
        if (path == null) {
            return null;
        }
        try (InputStream stream = pack.getResourceAsStream(path)) {
            return stream == null ? null : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MainMod.LOGGER.warn("[ShaderProgramSet] Failed to read {}", path, e);
            return null;
        }
    }
}
