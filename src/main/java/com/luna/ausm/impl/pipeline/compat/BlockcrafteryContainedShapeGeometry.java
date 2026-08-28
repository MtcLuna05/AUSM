package com.luna.ausm.impl.pipeline.compat;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.vertex.BlockRenderContext;
import com.luna.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.luna.ausm.impl.pipeline.vertex.IBufferBuilderExtension;
import com.luna.ausm.impl.pipeline.vertex.IrisVertexMath;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;

import static com.luna.ausm.impl.pipeline.compat.BlockcrafteryGeometryMath.area;
import static com.luna.ausm.impl.pipeline.compat.BlockcrafteryGeometryMath.bytes;
import static com.luna.ausm.impl.pipeline.compat.BlockcrafteryGeometryMath.bytesFloat;
import static com.luna.ausm.impl.pipeline.compat.BlockcrafteryGeometryMath.bytesInt;
import static com.luna.ausm.impl.pipeline.compat.BlockcrafteryGeometryMath.closestQuad;
import static com.luna.ausm.impl.pipeline.compat.BlockcrafteryGeometryMath.component;
import static com.luna.ausm.impl.pipeline.compat.BlockcrafteryGeometryMath.connectedFacePriority;
import static com.luna.ausm.impl.pipeline.compat.BlockcrafteryGeometryMath.copyOrientedQuad;
import static com.luna.ausm.impl.pipeline.compat.BlockcrafteryGeometryMath.difference;
import static com.luna.ausm.impl.pipeline.compat.BlockcrafteryGeometryMath.dominantAxis;
import static com.luna.ausm.impl.pipeline.compat.BlockcrafteryGeometryMath.faceGroup;
import static com.luna.ausm.impl.pipeline.compat.BlockcrafteryGeometryMath.integers;
import static com.luna.ausm.impl.pipeline.compat.BlockcrafteryGeometryMath.lightingValues;
import static com.luna.ausm.impl.pipeline.compat.BlockcrafteryGeometryMath.normal;
import static com.luna.ausm.impl.pipeline.compat.BlockcrafteryGeometryMath.point;
import static com.luna.ausm.impl.pipeline.compat.BlockcrafteryGeometryMath.position;
import static com.luna.ausm.impl.pipeline.compat.BlockcrafteryGeometryMath.positionBounds;
import static com.luna.ausm.impl.pipeline.compat.BlockcrafteryGeometryMath.quadPoints;
import static com.luna.ausm.impl.pipeline.compat.BlockcrafteryGeometryMath.read;
import static com.luna.ausm.impl.pipeline.compat.BlockcrafteryGeometryMath.replaceByte;
import static com.luna.ausm.impl.pipeline.compat.BlockcrafteryGeometryMath.squaredDistance;
import static com.luna.ausm.impl.pipeline.compat.BlockcrafteryGeometryMath.uvBounds;
import static com.luna.ausm.impl.pipeline.compat.BlockcrafteryGeometryMath.uvBoundsValues;
import static com.luna.ausm.impl.pipeline.compat.BlockcrafteryGeometryMath.vector;

/**
 * Combines two independently rendered spans of the same filled frame:
 * Blockcraftery's span contributes only positions (the actual frame shape),
 * while the contained block's span supplies every other vertex attribute.
 * <p>
 * A shaped host normally emits all six quads, whereas the contained block has
 * already culled the faces hidden by neighbours.  Therefore these spans are
 * not expected to have equal vertex counts.  Each host quad instead takes the
 * visual payload of the contained quad pointing most closely in the same
 * direction, then keeps the host quad's positions.
 */
public final class BlockcrafteryContainedShapeGeometry {
    /**
     * The copied bloom overlay occupies the same shaped surface as its base
     * material.  Move only that overlay just far enough along its final host
     * normal to survive the shared-depth LEQUAL test; the base never moves.
     */
    private static final float BLOOM_OVERLAY_DEPTH_LIFT = 0.002F;
    private static final AtomicInteger ENDER_IO_MAPPING_PROBE_COUNT = new AtomicInteger();
    private static final AtomicInteger ENDER_IO_PROJECTION_PROBE_COUNT = new AtomicInteger();
    private static final AtomicInteger ENDER_IO_FACE_PROJECTION_PROBE_COUNT = new AtomicInteger();
    private static final AtomicInteger ENDER_IO_FACE_ASSIGNMENT_PROBE_COUNT = new AtomicInteger();
    private static final AtomicInteger ENDER_IO_PROJECTION_FAILURE_PROBE_COUNT = new AtomicInteger();
    private static final AtomicInteger CONTAINED_LIGHTING_PROBE_COUNT = new AtomicInteger();

    private BlockcrafteryContainedShapeGeometry() {
    }

    public static boolean replaceWithContainedVisuals(BufferBuilder buffer, int start,
                                                      int containedEnd, int hostEnd,
                                                      boolean preserveHostSeparateAo,
                                                      boolean preserveHostLightmap,
                                                      boolean preserveEnderIoConnectedQuads,
                                                      boolean markFramedEmission,
                                                      boolean liftBloomOverlay,
                                                      IBlockState containedState) {
        if (!(buffer instanceof IBufferBuilderExtension extension)
                || start < 0 || containedEnd <= start || hostEnd <= containedEnd) {
            return false;
        }
        VertexFormat format = extension.ausm$vertexFormat();
        int stride = ExtendedVertexFormats.size(format);
        int intsPerVertex = ExtendedVertexFormats.integerSize(format);
        ByteBuffer raw = extension.ausm$byteBuffer();
        int containedVertices = containedEnd - start;
        int hostVertices = hostEnd - containedEnd;
        if (raw == null || stride < 12 || intsPerVertex <= 0
                || containedVertices % 4 != 0 || hostVertices % 4 != 0) {
            return false;
        }
        long startByte = (long) start * stride;
        long containedEndByte = (long) containedEnd * stride;
        long hostEndByte = (long) hostEnd * stride;
        if (startByte < 0 || hostEndByte > raw.capacity() || containedEndByte > hostEndByte) {
            return false;
        }

        ByteOrder order = raw.order() != null ? raw.order() : ByteOrder.nativeOrder();
        byte[] contained = read(raw, (int) startByte, (int) (containedEndByte - startByte));
        byte[] host = read(raw, (int) containedEndByte, (int) (hostEndByte - containedEndByte));
        int[] containedVisuals = integers(contained, order);
        if (containedVisuals.length != containedVertices * intsPerVertex) {
            return false;
        }

        boolean preferLargestMatchingFace = preserveEnderIoConnectedQuads;
        int[] selectedContainedQuads = preferLargestMatchingFace ? new int[hostVertices / 4] : null;
        if (preferLargestMatchingFace) {
            // EnderIO resolves one connected face as many small quads.  Do
            // not collapse them to a base sprite: map each *face group* to a
            // distinct host face, preserving its native CTM UV fragments.
            if (replaceWithEnderIoConnectedVisuals(extension, containedVisuals, contained, host, start,
                    containedVertices, hostVertices, stride, intsPerVertex, format, order, markFramedEmission,
                    liftBloomOverlay)) {
                return true;
            }
            // A malformed optional model must retain the established visible
            // fallback instead of dropping the entire framed material.
            logEnderIoProjectionProbe(containedVisuals, contained, host, containedVertices, hostVertices,
                    stride, intsPerVertex, order);
        }
        int[] mappedVisuals = mapContainedVisualsToHost(containedVisuals, contained, host,
                containedVertices, hostVertices, stride, intsPerVertex, order,
                preferLargestMatchingFace, selectedContainedQuads);
        if (mappedVisuals == null) {
            return false;
        }
        if (selectedContainedQuads != null) {
            logEnderIoMappingProbe(contained, host, containedVertices, hostVertices, stride, order,
                    selectedContainedQuads);
            normaliseEnderIoUvsToBaseSprite(mappedVisuals, selectedContainedQuads, contained,
                    stride, intsPerVertex, order, containedState);
        }

        logContainedLightingProbe(host, mappedVisuals, hostVertices, stride, intsPerVertex, format, order);

        extension.ausm$truncateVertexCount(start);
        if (extension.ausm$appendRawVertexData(mappedVisuals) != hostVertices) {
            return false;
        }
        ByteBuffer destination = extension.ausm$byteBuffer().duplicate().order(order);
        int colorOffset = ExtendedVertexFormats.colorOffset(format);
        int lightmapOffset = ExtendedVertexFormats.uvOffsetById(format, 1);
        for (int vertex = 0; vertex < hostVertices; vertex++) {
            int visualOffset = (int) startByte + vertex * stride;
            int hostOffset = vertex * stride;
            destination.putFloat(visualOffset, bytesFloat(host, hostOffset, order));
            destination.putFloat(visualOffset + 4, bytesFloat(host, hostOffset + 4, order));
            destination.putFloat(visualOffset + 8, bytesFloat(host, hostOffset + 8, order));
            // Pipeline separate-AO retains a quad's AO factor in alpha while
            // RGB stays the unshaded material colour.  The contained cube's
            // corner factors are invalid after its vertices move onto a thin
            // or sloped host face, so retain the host's factor for SOLID.
            if (preserveHostLightmap && colorOffset >= 0 && colorOffset + 4 <= stride) {
                // The host has already sampled AO and directional diffuse at
                // its shaped vertex. Keeping cube payload RGB here produces
                // the discontinuities captured by AUSMContainedLightingProbe.
                destination.put(visualOffset + colorOffset, host[hostOffset + colorOffset]);
                destination.put(visualOffset + colorOffset + 1, host[hostOffset + colorOffset + 1]);
                destination.put(visualOffset + colorOffset + 2, host[hostOffset + colorOffset + 2]);
                destination.put(visualOffset + colorOffset + 3, host[hostOffset + colorOffset + 3]);
            } else if (preserveHostSeparateAo && colorOffset >= 0 && colorOffset + 4 <= stride) {
                destination.put(visualOffset + colorOffset + 3, host[hostOffset + colorOffset + 3]);
            }
            // Light sampling is likewise tied to the original contained cube
            // positions.  On a sloped host it produces a visible hard seam
            // where the moved vertices meet.  Keep the host samples for
            // ordinary solid contents, but never replace a contained light
            // source's native full-bright lightmap.
            if (preserveHostLightmap && lightmapOffset >= 0 && lightmapOffset + 4 <= stride) {
                destination.put(visualOffset + lightmapOffset, host[hostOffset + lightmapOffset]);
                destination.put(visualOffset + lightmapOffset + 1, host[hostOffset + lightmapOffset + 1]);
                destination.put(visualOffset + lightmapOffset + 2, host[hostOffset + lightmapOffset + 2]);
                destination.put(visualOffset + lightmapOffset + 3, host[hostOffset + lightmapOffset + 3]);
            }
        }
        liftBloomOverlay(destination, startByte, hostVertices, stride, liftBloomOverlay);
        refreshDerivedPipelineAttributes(destination, host, startByte, hostVertices, stride, format, order);
        markFramedEmission(destination, startByte, hostVertices, stride, format, markFramedEmission);
        markFramedBloomOverlay(destination, startByte, hostVertices, stride, format, liftBloomOverlay);
        return true;
    }

    private static void normaliseEnderIoUvsToBaseSprite(int[] visuals, int[] selectedContainedQuads,
                                                        byte[] contained, int stride, int intsPerVertex,
                                                        ByteOrder order, IBlockState containedState) {
        String stateName = String.valueOf(containedState);
        String spriteName = stateName.contains("fused_quartz")
                ? "enderio:blocks/block_fused_quartz" : "enderio:blocks/block_fused_glass";
        TextureAtlasSprite sprite = MinecraftReflectionCompat.atlasSprite(
                MinecraftReflectionCompat.textureMapBlocks(MinecraftReflectionCompat.minecraft()), spriteName);
        if (sprite == null) {
            return;
        }
        float spriteMinU = MinecraftReflectionCompat.spriteMinU(sprite);
        float spriteMaxU = MinecraftReflectionCompat.spriteMaxU(sprite);
        float spriteMinV = MinecraftReflectionCompat.spriteMinV(sprite);
        float spriteMaxV = MinecraftReflectionCompat.spriteMaxV(sprite);
        for (int hostQuad = 0; hostQuad < selectedContainedQuads.length; hostQuad++) {
            float[] bounds = uvBoundsValues(contained, selectedContainedQuads[hostQuad] * 4 * stride, stride, order);
            float uRange = bounds[1] - bounds[0];
            float vRange = bounds[3] - bounds[2];
            if (Math.abs(uRange) < 0.000001F || Math.abs(vRange) < 0.000001F) {
                continue;
            }
            int outputOffset = hostQuad * 4 * intsPerVertex;
            for (int vertex = 0; vertex < 4; vertex++) {
                int output = outputOffset + vertex * intsPerVertex;
                float sourceU = Float.intBitsToFloat(visuals[output + 4]);
                float sourceV = Float.intBitsToFloat(visuals[output + 5]);
                float normalisedU = (sourceU - bounds[0]) / uRange;
                float normalisedV = (sourceV - bounds[2]) / vRange;
                visuals[output + 4] = Float.floatToRawIntBits(spriteMinU + normalisedU * (spriteMaxU - spriteMinU));
                visuals[output + 5] = Float.floatToRawIntBits(spriteMinV + normalisedV * (spriteMaxV - spriteMinV));
            }
        }
    }

    /**
     * EnderIO's connected renderer constructs a face from many small quads.
     * Selecting one and stretching its UVs loses the actual material (and
     * produces the visible white spark fragments). Keep every resolved quad,
     * then project its 0..1 cube-face position onto the corresponding frame
     * face. This reuses EnderIO's already-resolved single-block connectivity;
     * AUSM does not calculate any CTM neighbours itself.
     */
    private static boolean replaceWithEnderIoConnectedVisuals(IBufferBuilderExtension extension,
                                                              int[] containedVisuals, byte[] contained,
                                                              byte[] host, int start,
                                                              int containedVertices, int hostVertices,
                                                              int stride, int intsPerVertex,
                                                              VertexFormat format, ByteOrder order,
                                                              boolean markFramedEmission,
                                                              boolean liftBloomOverlay) {
        int containedQuads = containedVertices / 4;
        int hostQuads = hostVertices / 4;
        int[] hostForContained = assignConnectedFaces(contained, host, containedQuads, hostQuads, stride, order);
        if (hostForContained == null) {
            return false;
        }
        int[] projectedVisuals = new int[containedVisuals.length];
        int mappedQuads = 0;
        for (int containedQuad = 0; containedQuad < containedQuads; containedQuad++) {
            int hostQuad = hostForContained[containedQuad];
            if (hostQuad < 0) {
                continue;
            }
            if (!mapConnectedQuadPositions(projectedVisuals, contained, host, containedQuad, mappedQuads, hostQuad,
                    stride, intsPerVertex, order)) {
                return false;
            }
            mappedQuads++;
        }
        if (mappedQuads == 0) {
            return false;
        }
        logEnderIoFaceAssignment(hostForContained, containedQuads, mappedQuads);
        extension.ausm$truncateVertexCount(start);
        int mappedVertices = mappedQuads * 4;
        if (extension.ausm$appendRawVertexData(Arrays.copyOf(projectedVisuals,
                mappedVertices * intsPerVertex)) != mappedVertices) {
            return false;
        }
        ByteBuffer destination = extension.ausm$byteBuffer().duplicate().order(order);
        liftBloomOverlay(destination, (long) start * stride, mappedVertices, stride, liftBloomOverlay);
        refreshDerivedPipelineAttributes(destination, null, (long) start * stride, mappedVertices,
                stride, format, order);
        markFramedEmission(destination, (long) start * stride, mappedVertices, stride, format, markFramedEmission);
        markFramedBloomOverlay(destination, (long) start * stride, mappedVertices, stride, format, liftBloomOverlay);
        return true;
    }

    /**
     * The contained payload, not the temporary host draw, survives shape
     * mapping. Carry its emissive status into mc_Entity.w so the active
     * terrain shader gives a filled frame the same base emission as the
     * direct contained block.
     */
    private static void markFramedEmission(ByteBuffer destination, long startByte, int vertices,
                                           int stride, VertexFormat format, boolean enabled) {
        if (!enabled || !ExtendedVertexFormats.isPipelineBlock(format)) {
            return;
        }
        int entityOffset = ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET;
        if (entityOffset < 0 || entityOffset + 8 > stride) {
            return;
        }
        for (int vertex = 0; vertex < vertices; vertex++) {
            int offset = (int) startByte + vertex * stride + entityOffset + 6;
            if (offset < 0 || offset + Short.BYTES > destination.capacity()) {
                return;
            }
            destination.putShort(offset, (short) BlockRenderContext.FRAMED_BLOOM_BOOST_MARKER);
        }
    }

    /**
     * Tag only the copied BLOOM overlay. The native Bloom geometry program
     * reads this marker to attenuate the duplicate framed source only while
     * the shader pipeline is active; shaderless Bloom retains full energy.
     */
    private static void markFramedBloomOverlay(ByteBuffer destination, long startByte, int vertices,
                                               int stride, VertexFormat format, boolean enabled) {
        if (!enabled || !ExtendedVertexFormats.isPipelineBlock(format)) {
            return;
        }
        int entityOffset = ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET;
        if (entityOffset < 0 || entityOffset + 8 > stride) {
            return;
        }
        for (int vertex = 0; vertex < vertices; vertex++) {
            int offset = (int) startByte + vertex * stride + entityOffset + 6;
            if (offset < 0 || offset + Short.BYTES > destination.capacity()) {
                return;
            }
            destination.putShort(offset, (short) BlockRenderContext.FRAMED_BLOOM_OVERLAY_PROBE_MARKER);
        }
    }

    private static void liftBloomOverlay(ByteBuffer destination, long startByte, int vertices,
                                         int stride, boolean enabled) {
        if (!enabled || vertices % 4 != 0) {
            return;
        }
        float[] normal = new float[3];
        for (int quad = 0; quad < vertices / 4; quad++) {
            int quadStart = (int) startByte + quad * 4 * stride;
            if (quadStart < 0 || quadStart + 4 * stride > destination.capacity()) {
                return;
            }
            IrisVertexMath.computeFaceNormal(normal,
                    destination.getFloat(quadStart), destination.getFloat(quadStart + 4), destination.getFloat(quadStart + 8),
                    destination.getFloat(quadStart + stride), destination.getFloat(quadStart + stride + 4), destination.getFloat(quadStart + stride + 8),
                    destination.getFloat(quadStart + 2 * stride), destination.getFloat(quadStart + 2 * stride + 4), destination.getFloat(quadStart + 2 * stride + 8),
                    destination.getFloat(quadStart + 3 * stride), destination.getFloat(quadStart + 3 * stride + 4), destination.getFloat(quadStart + 3 * stride + 8));
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = quadStart + vertex * stride;
                destination.putFloat(offset, destination.getFloat(offset) + normal[0] * BLOOM_OVERLAY_DEPTH_LIFT);
                destination.putFloat(offset + 4, destination.getFloat(offset + 4) + normal[1] * BLOOM_OVERLAY_DEPTH_LIFT);
                destination.putFloat(offset + 8, destination.getFloat(offset + 8) + normal[2] * BLOOM_OVERLAY_DEPTH_LIFT);
            }
        }
    }

    /**
     * A shaped frame can have an oblique face.  Mapping each small resolved
     * EnderIO quad independently to its nearest host normal lets two cube
     * faces choose that same oblique face.  Group by the source cube face and
     * solve one compact maximum-score assignment instead, so every source
     * face is projected onto exactly one host face.
     */
    private static int[] assignConnectedFaces(byte[] contained, byte[] host, int containedQuads,
                                              int hostQuads, int stride, ByteOrder order) {
        int[] groupForQuad = new int[containedQuads];
        float[][] groupNormals = new float[6][];
        int groupCount = 0;
        for (int quad = 0; quad < containedQuads; quad++) {
            float[] faceNormal = normal(contained, quad * 4 * stride, stride, order);
            int group = faceGroup(faceNormal);
            if (group < 0) {
                return null;
            }
            groupForQuad[quad] = group;
            if (groupNormals[group] == null) {
                groupNormals[group] = faceNormal;
                groupCount++;
            }
        }
        if (groupCount == 0 || groupCount > hostQuads) {
            return null;
        }
        float[][] hostNormals = new float[hostQuads][];
        for (int hostQuad = 0; hostQuad < hostQuads; hostQuad++) {
            hostNormals[hostQuad] = normal(host, hostQuad * 4 * stride, stride, order);
        }
        int[] groups = new int[groupCount];
        int at = 0;
        for (int group = 0; group < groupNormals.length; group++) {
            if (groupNormals[group] != null) {
                groups[at++] = group;
            }
        }
        int[] working = new int[groupCount];
        int[] best = new int[groupCount];
        Arrays.fill(working, -1);
        Arrays.fill(best, -1);
        boolean[] used = new boolean[hostQuads];
        float[] bestScore = new float[]{-Float.MAX_VALUE};
        assignConnectedFaces(groups, 0, groupNormals, hostNormals, used, working, best, 0.0F, bestScore);
        int[] hostForGroup = new int[6];
        Arrays.fill(hostForGroup, -1);
        for (int index = 0; index < groups.length; index++) {
            hostForGroup[groups[index]] = best[index];
        }
        int[] result = new int[containedQuads];
        for (int quad = 0; quad < containedQuads; quad++) {
            result[quad] = hostForGroup[groupForQuad[quad]];
        }
        return result;
    }

    private static void assignConnectedFaces(int[] groups, int index, float[][] groupNormals,
                                             float[][] hostNormals, boolean[] used, int[] working,
                                             int[] best, float score, float[] bestScore) {
        if (index == groups.length) {
            if (score > bestScore[0]) {
                bestScore[0] = score;
                System.arraycopy(working, 0, best, 0, working.length);
            }
            return;
        }
        float[] sourceNormal = groupNormals[groups[index]];
        // A cube face can legitimately have no shaped counterpart (for
        // example the east and top of a wedge collapse into one slope).  It
        // must be omitted rather than duplicated onto that slope.
        working[index] = -1;
        assignConnectedFaces(groups, index + 1, groupNormals, hostNormals, used, working, best,
                score, bestScore);
        for (int hostQuad = 0; hostQuad < hostNormals.length; hostQuad++) {
            if (used[hostQuad] || hostNormals[hostQuad] == null) {
                continue;
            }
            float[] hostNormal = hostNormals[hostQuad];
            float dot = sourceNormal[0] * hostNormal[0]
                    + sourceNormal[1] * hostNormal[1]
                    + sourceNormal[2] * hostNormal[2];
            if (dot <= 0.0001F) {
                continue;
            }
            used[hostQuad] = true;
            working[index] = hostQuad;
            assignConnectedFaces(groups, index + 1, groupNormals, hostNormals, used, working, best,
                    score + dot + connectedFacePriority(sourceNormal), bestScore);
            used[hostQuad] = false;
        }
    }

    private static void logEnderIoFaceAssignment(int[] hostForContained, int containedQuads, int mappedQuads) {
        int call = ENDER_IO_FACE_ASSIGNMENT_PROBE_COUNT.incrementAndGet();
        if (call > 8) {
            return;
        }
        MainMod.LOGGER.info("[AUSMEnderIoFaceAssignmentProbe] call={} mappedQuads={}/{} hostForQuad={}",
                call, mappedQuads, containedQuads, Arrays.toString(hostForContained));
    }

    private static boolean mapConnectedQuadPositions(int[] destination, byte[] source, byte[] host,
                                                     int sourceQuad, int destinationQuad, int hostQuad, int stride,
                                                     int intsPerVertex, ByteOrder order) {
        int sourceOffset = sourceQuad * 4 * stride;
        int hostOffset = hostQuad * 4 * stride;
        float[] sourceNormal = normal(source, sourceOffset, stride, order);
        int ignoredAxis = dominantAxis(sourceNormal);
        if (ignoredAxis < 0) {
            logEnderIoProjectionFailure(sourceQuad, hostQuad, "missing-source-normal", source, host,
                    sourceOffset, hostOffset, stride, order);
            return false;
        }
        int axisA = (ignoredAxis + 1) % 3;
        int axisB = (ignoredAxis + 2) % 3;
        float[] hostNormal = normal(host, hostOffset, stride, order);
        if (hostNormal == null) {
            logEnderIoProjectionFailure(sourceQuad, hostQuad, "missing-host-normal", source, host,
                    sourceOffset, hostOffset, stride, order);
            return false;
        }
        HostFaceBasis hostBasis = hostFaceBasis(host, hostOffset, stride, hostNormal, order);
        if (hostBasis == null) {
            logEnderIoProjectionFailure(sourceQuad, hostQuad, "invalid-host-basis", source, host,
                    sourceOffset, hostOffset, stride, order);
            return false;
        }
        float sourceBlockA = sourceFaceMinimum(source, sourceOffset, stride, order, axisA);
        float sourceBlockB = sourceFaceMinimum(source, sourceOffset, stride, order, axisB);
        FaceTransform transform = chooseFaceTransform(source, sourceOffset, hostBasis,
                sourceBlockA, sourceBlockB, axisA, axisB, hostNormal, stride, order);
        if (transform == null) {
            logEnderIoProjectionFailure(sourceQuad, hostQuad, "unmatched-winding/" + hostBasis, source, host,
                    sourceOffset, hostOffset, stride, order);
            return false;
        }
        int destinationIntOffset = destinationQuad * 4 * intsPerVertex;
        float minU = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;
        float[] outputMin = new float[]{Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
        float[] outputMax = new float[]{Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (int vertex = 0; vertex < 4; vertex++) {
            int sourceByteOffset = sourceOffset + vertex * stride;
            int output = destinationIntOffset + vertex * intsPerVertex;
            for (int attribute = 0; attribute < intsPerVertex; attribute++) {
                destination[output + attribute] = bytesInt(source, sourceByteOffset + attribute * Integer.BYTES, order);
            }
            float[] sourcePosition = position(source, sourceByteOffset, order);
            // Nothirium stores chunk-relative positions in this span (for
            // example x=12..13), not a 0..1 model cube.  Feeding those
            // absolute coordinates into the frame face transform was the
            // source of the 12-block EnderIO projection displacement seen in
            // the probe.  Use the source block-local coordinate instead.
            float a = Math.clamp(component(sourcePosition, axisA) - sourceBlockA, 0.0F, 1.0F);
            float b = Math.clamp(component(sourcePosition, axisB) - sourceBlockB, 0.0F, 1.0F);
            float[] uv = transform.apply(a, b);
            float u = uv[0];
            float v = uv[1];
            float[] projected = hostBasis.project(u, v);
            float projectedX = projected[0];
            float projectedY = projected[1];
            float projectedZ = projected[2];
            destination[output] = Float.floatToRawIntBits(projectedX);
            destination[output + 1] = Float.floatToRawIntBits(projectedY);
            destination[output + 2] = Float.floatToRawIntBits(projectedZ);
            copyConnectedHostShading(destination, output, host, hostOffset, stride, intsPerVertex, order, u, v);
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
            minV = Math.min(minV, v);
            maxV = Math.max(maxV, v);
            outputMin[0] = Math.min(outputMin[0], projectedX);
            outputMax[0] = Math.max(outputMax[0], projectedX);
            outputMin[1] = Math.min(outputMin[1], projectedY);
            outputMax[1] = Math.max(outputMax[1], projectedY);
            outputMin[2] = Math.min(outputMin[2], projectedZ);
            outputMax[2] = Math.max(outputMax[2], projectedZ);
        }
        int probe = ENDER_IO_FACE_PROJECTION_PROBE_COUNT.incrementAndGet();
        if (probe <= 48) {
            MainMod.LOGGER.info("[AUSMEnderIoFaceProjectionProbe] call={} sourceQuad={} hostQuad={} normal={} axes={}/{} mapping={} sourceForHost={} hostBasis={} delta={}/{}/{}/{} uv={}..{}/{}..{} projected={}..{},{}..{},{}..{}",
                    probe, sourceQuad, hostQuad, vector(sourceNormal), axisA, axisB, transform,
                    "raw", hostBasis, 0.0F, 0.0F, 0.0F, 0.0F,
                    minU, maxU, minV, maxV, outputMin[0], outputMax[0], outputMin[1], outputMax[1], outputMin[2], outputMax[2]);
        }
        return true;
    }

    private static void logEnderIoProjectionFailure(int sourceQuad, int hostQuad, String reason,
                                                    byte[] source, byte[] host, int sourceOffset,
                                                    int hostOffset, int stride, ByteOrder order) {
        int call = ENDER_IO_PROJECTION_FAILURE_PROBE_COUNT.incrementAndGet();
        if (call > 24) {
            return;
        }
        MainMod.LOGGER.info("[AUSMEnderIoProjectionFailureProbe] call={} sourceQuad={} hostQuad={} reason={} source={} host={}",
                call, sourceQuad, hostQuad, reason, quadPoints(source, sourceOffset, stride, order),
                quadPoints(host, hostOffset, stride, order));
    }

    private static void copyConnectedHostShading(int[] destination, int output, byte[] host, int hostOffset,
                                                 int stride, int intsPerVertex, ByteOrder order, float u, float v) {
        int colorOffset = 3;
        int lightOffset = 6;
        if (intsPerVertex <= lightOffset) {
            return;
        }
        for (int byteIndex = 0; byteIndex < Integer.BYTES; byteIndex++) {
            int color = bilinearByte(host, hostOffset, stride, colorOffset * Integer.BYTES + byteIndex, u, v);
            int light = bilinearByte(host, hostOffset, stride, lightOffset * Integer.BYTES + byteIndex, u, v);
            destination[output + colorOffset] = replaceByte(destination[output + colorOffset], byteIndex, color, order);
            destination[output + lightOffset] = replaceByte(destination[output + lightOffset], byteIndex, light, order);
        }
    }

    private static int bilinearByte(byte[] data, int quadOffset, int stride, int attributeOffset,
                                    float u, float v) {
        float first = (data[quadOffset + attributeOffset] & 0xFF) * (1.0F - u)
                + (data[quadOffset + stride + attributeOffset] & 0xFF) * u;
        float second = (data[quadOffset + 3 * stride + attributeOffset] & 0xFF) * (1.0F - u)
                + (data[quadOffset + 2 * stride + attributeOffset] & 0xFF) * u;
        return Math.round(first * (1.0F - v) + second * v);
    }

    private static float sourceFaceMinimum(byte[] source, int sourceOffset, int stride,
                                           ByteOrder order, int axis) {
        float minimum = Float.POSITIVE_INFINITY;
        for (int vertex = 0; vertex < 4; vertex++) {
            minimum = Math.min(minimum, component(position(source, sourceOffset + vertex * stride, order), axis));
        }
        return (float) Math.floor(minimum + 0.00001F);
    }

    /**
     * Select a cube-face coordinate transform whose output winding agrees
     * with the chosen shaped host face.  This leaves the source fragment's
     * texture coordinates and vertex payload untouched; it only decides
     * where its positions land on the host plane.
     */
    private static FaceTransform chooseFaceTransform(byte[] source, int sourceOffset, HostFaceBasis hostBasis,
                                                     float blockA, float blockB, int axisA, int axisB,
                                                     float[] hostNormal, int stride, ByteOrder order) {
        FaceTransform best = null;
        float bestDot = -Float.MAX_VALUE;
        for (int swapped = 0; swapped <= 1; swapped++) {
            for (int flipU = 0; flipU <= 1; flipU++) {
                for (int flipV = 0; flipV <= 1; flipV++) {
                    FaceTransform candidate = new FaceTransform(swapped != 0, flipU != 0, flipV != 0);
                    float[][] projected = new float[3][];
                    for (int vertex = 0; vertex < 3; vertex++) {
                        float[] position = position(source, sourceOffset + vertex * stride, order);
                        float[] uv = candidate.apply(
                                Math.clamp(component(position, axisA) - blockA, 0.0F, 1.0F),
                                Math.clamp(component(position, axisB) - blockB, 0.0F, 1.0F));
                        projected[vertex] = hostBasis.project(uv[0], uv[1]);
                    }
                    float[] normal = normal(projected[0], projected[1], projected[2]);
                    if (normal == null) {
                        continue;
                    }
                    float dot = normal[0] * hostNormal[0] + normal[1] * hostNormal[1] + normal[2] * hostNormal[2];
                    if (dot > bestDot) {
                        bestDot = dot;
                        best = candidate;
                    }
                }
            }
        }
        return bestDot > 0.5F ? best : null;
    }

    private static final class FaceTransform {
        private final boolean swapped;
        private final boolean flipU;
        private final boolean flipV;

        private FaceTransform(boolean swapped, boolean flipU, boolean flipV) {
            this.swapped = swapped;
            this.flipU = flipU;
            this.flipV = flipV;
        }

        private float[] apply(float a, float b) {
            float u = swapped ? b : a;
            float v = swapped ? a : b;
            return new float[]{flipU ? 1.0F - u : u, flipV ? 1.0F - v : v};
        }

        @Override
        public String toString() {
            return "swap=" + swapped + ",flipU=" + flipU + ",flipV=" + flipV;
        }
    }

    /**
     * Blockcraftery can reverse an individual quad's indexed winding.  The
     * diagonal is therefore not consistently vertex 2 or vertex 3.  Recover
     * the two true edges by finding the pair whose parallelogram reaches the
     * remaining corner, then orient that pair to the emitted face normal.
     */
    private static HostFaceBasis hostFaceBasis(byte[] host, int hostOffset, int stride,
                                               float[] hostNormal, ByteOrder order) {
        float[][] points = new float[4][];
        for (int vertex = 0; vertex < 4; vertex++) {
            points[vertex] = position(host, hostOffset + vertex * stride, order);
        }
        int first = -1;
        int second = -1;
        float bestError = Float.POSITIVE_INFINITY;
        for (int left = 1; left < 4; left++) {
            for (int right = left + 1; right < 4; right++) {
                int opposite = 6 - left - right;
                float[] expected = new float[]{
                        points[left][0] + points[right][0] - points[0][0],
                        points[left][1] + points[right][1] - points[0][1],
                        points[left][2] + points[right][2] - points[0][2]
                };
                float error = squaredDistance(expected, points[opposite]);
                if (error < bestError) {
                    bestError = error;
                    first = left;
                    second = right;
                }
            }
        }
        boolean triangle = bestError > 0.0001F;
        if (first < 0) {
            return null;
        }
        if (triangle) {
            float largestArea = 0.0F;
            for (int left = 1; left < 4; left++) {
                for (int right = left + 1; right < 4; right++) {
                    float[] candidateNormal = normal(points[0], points[left], points[right]);
                    if (candidateNormal == null) {
                        continue;
                    }
                    float area = Math.abs(candidateNormal[0] * hostNormal[0]
                            + candidateNormal[1] * hostNormal[1]
                            + candidateNormal[2] * hostNormal[2]);
                    if (area > largestArea) {
                        largestArea = area;
                        first = left;
                        second = right;
                    }
                }
            }
            if (largestArea < 0.999F) {
                return null;
            }
        }
        float[] edgeOne = difference(points[first], points[0]);
        float[] edgeTwo = difference(points[second], points[0]);
        float[] basisNormal = normal(points[0], points[first], points[second]);
        if (basisNormal == null) {
            return null;
        }
        float dot = basisNormal[0] * hostNormal[0] + basisNormal[1] * hostNormal[1] + basisNormal[2] * hostNormal[2];
        if (dot < 0.0F) {
            float[] temporary = edgeOne;
            edgeOne = edgeTwo;
            edgeTwo = temporary;
            int temporaryIndex = first;
            first = second;
            second = temporaryIndex;
        }
        return new HostFaceBasis(points[0], edgeOne, edgeTwo, first, second, triangle);
    }

    private static final class HostFaceBasis {
        private final float[] origin;
        private final float[] edgeOne;
        private final float[] edgeTwo;
        private final int first;
        private final int second;
        private final boolean triangle;

        private HostFaceBasis(float[] origin, float[] edgeOne, float[] edgeTwo, int first, int second,
                              boolean triangle) {
            this.origin = origin;
            this.edgeOne = edgeOne;
            this.edgeTwo = edgeTwo;
            this.first = first;
            this.second = second;
            this.triangle = triangle;
        }

        private float[] project(float u, float v) {
            // The north/south host faces are encoded as a triangle with a
            // duplicated fourth vertex.  Fold the source square into that
            // triangle rather than rejecting all of its resolved fragments.
            // This preserves the frame silhouette while keeping every native
            // EnderIO texture fragment on the visible half-plane.
            float mappedU = u;
            float mappedV = triangle ? v * (1.0F - u) : v;
            return new float[]{
                    origin[0] + mappedU * edgeOne[0] + mappedV * edgeTwo[0],
                    origin[1] + mappedU * edgeOne[1] + mappedV * edgeTwo[1],
                    origin[2] + mappedU * edgeOne[2] + mappedV * edgeTwo[2]
            };
        }

        @Override
        public String toString() {
            return (triangle ? "triangle:" : "quad:") + "0->" + first + "/0->" + second + "@" + point(origin)
                    + "+" + point(edgeOne) + "+" + point(edgeTwo);
        }
    }

    private static void logContainedLightingProbe(byte[] host, int[] mappedVisuals, int hostVertices,
                                                  int stride, int intsPerVertex, VertexFormat format,
                                                  ByteOrder order) {
        int call = CONTAINED_LIGHTING_PROBE_COUNT.incrementAndGet();
        if (call > 24) {
            return;
        }
        int colorOffset = ExtendedVertexFormats.colorOffset(format);
        int lightmapOffset = ExtendedVertexFormats.uvOffsetById(format, 1);
        if (colorOffset < 0 || lightmapOffset < 0) {
            return;
        }
        byte[] mapped = bytes(mappedVisuals, order);
        StringBuilder summary = new StringBuilder();
        for (int quad = 0; quad < hostVertices / 4; quad++) {
            if (quad > 0) {
                summary.append(';');
            }
            int offset = quad * 4 * stride;
            summary.append('q').append(quad).append(" host=")
                    .append(lightingValues(host, offset, stride, colorOffset, lightmapOffset))
                    .append(" mapped=")
                    .append(lightingValues(mapped, offset, stride, colorOffset, lightmapOffset));
        }
        MainMod.LOGGER.info("[AUSMContainedLightingProbe] call={} values={}", call, summary);
    }

    /**
     * BufferBuilder derives terrain normal, tangent, midpoint UV, and
     * at_midBlock while it appends a quad. The frame mapper changes its
     * positions afterwards, so all of those source-cube attributes become
     * stale together. Rebuild them from host geometry and contained UVs;
     * retain the contained emission byte in at_midBlock.
     */
    private static void refreshDerivedPipelineAttributes(ByteBuffer destination, byte[] host,
                                                         long startByte, int vertices, int stride,
                                                         VertexFormat format, ByteOrder order) {
        if (!ExtendedVertexFormats.isPipelineBlock(format) || vertices % 4 != 0) {
            return;
        }
        int normalOffset = ExtendedVertexFormats.PIPELINE_BLOCK_NORMAL_OFFSET;
        int tangentOffset = ExtendedVertexFormats.PIPELINE_BLOCK_TANGENT_OFFSET;
        int midpointOffset = ExtendedVertexFormats.PIPELINE_BLOCK_MID_TEX_COORD_OFFSET;
        int midBlockOffset = ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET;
        if (normalOffset < 0 || tangentOffset < 0 || midpointOffset < 0 || midBlockOffset < 0
                || normalOffset + Integer.BYTES > stride
                || tangentOffset + Integer.BYTES > stride
                || midpointOffset + 2 * Float.BYTES > stride
                || midBlockOffset + Integer.BYTES > stride) {
            return;
        }
        float[] normal = new float[3];
        for (int quad = 0; quad < vertices / 4; quad++) {
            int quadStart = (int) startByte + quad * 4 * stride;
            float x0 = destination.getFloat(quadStart);
            float y0 = destination.getFloat(quadStart + 4);
            float z0 = destination.getFloat(quadStart + 8);
            float x1 = destination.getFloat(quadStart + stride);
            float y1 = destination.getFloat(quadStart + stride + 4);
            float z1 = destination.getFloat(quadStart + stride + 8);
            float x2 = destination.getFloat(quadStart + 2 * stride);
            float y2 = destination.getFloat(quadStart + 2 * stride + 4);
            float z2 = destination.getFloat(quadStart + 2 * stride + 8);
            float x3 = destination.getFloat(quadStart + 3 * stride);
            float y3 = destination.getFloat(quadStart + 3 * stride + 4);
            float z3 = destination.getFloat(quadStart + 3 * stride + 8);
            float u0 = destination.getFloat(quadStart + 16);
            float v0 = destination.getFloat(quadStart + 20);
            float u1 = destination.getFloat(quadStart + stride + 16);
            float v1 = destination.getFloat(quadStart + stride + 20);
            float u2 = destination.getFloat(quadStart + 2 * stride + 16);
            float v2 = destination.getFloat(quadStart + 2 * stride + 20);
            float u3 = destination.getFloat(quadStart + 3 * stride + 16);
            float v3 = destination.getFloat(quadStart + 3 * stride + 20);
            IrisVertexMath.computeFaceNormal(normal, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
            int packedNormal = IrisVertexMath.packNormal(normal[0], normal[1], normal[2]);
            int packedTangent = IrisVertexMath.computeTangent(normal[0], normal[1], normal[2],
                    x0, y0, z0, u0, v0, x1, y1, z1, u1, v1, x2, y2, z2, u2, v2);
            float midpointU = (u0 + u1 + u2 + u3) * 0.25F;
            float midpointV = (v0 + v1 + v2 + v3) * 0.25F;
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = quadStart + vertex * stride;
                int hostOffset = quad * 4 * stride + vertex * stride;
                int containedMidBlock = destination.getInt(offset + midBlockOffset);
                int hostMidBlock = host != null && hostOffset + midBlockOffset + Integer.BYTES <= host.length
                        ? bytesInt(host, hostOffset + midBlockOffset, order) : containedMidBlock;
                destination.putInt(offset + normalOffset, packedNormal);
                destination.putFloat(offset + midpointOffset, midpointU);
                destination.putFloat(offset + midpointOffset + 4, midpointV);
                destination.putInt(offset + tangentOffset, packedTangent);
                destination.putInt(offset + midBlockOffset,
                        (hostMidBlock & 0x00FFFFFF) | (containedMidBlock & 0xFF000000));
            }
        }
    }

    private static int[] mapContainedVisualsToHost(int[] containedVisuals, byte[] contained,
                                                   byte[] host, int containedVertices,
                                                   int hostVertices, int stride,
                                                   int intsPerVertex, ByteOrder order,
                                                   boolean preferLargestMatchingFace,
                                                   int[] selectedContainedQuads) {
        int containedQuads = containedVertices / 4;
        int hostQuads = hostVertices / 4;
        int[] result = new int[hostVertices * intsPerVertex];
        float[][] containedNormals = new float[containedQuads][];
        float[] containedAreas = new float[containedQuads];
        for (int quad = 0; quad < containedQuads; quad++) {
            containedNormals[quad] = normal(contained, quad * 4 * stride, stride, order);
            containedAreas[quad] = area(contained, quad * 4 * stride, stride, order);
        }

        for (int hostQuad = 0; hostQuad < hostQuads; hostQuad++) {
            float[] hostNormal = normal(host, hostQuad * 4 * stride, stride, order);
            int containedQuad = closestQuad(hostNormal, containedNormals, containedAreas, hostQuad,
                    preferLargestMatchingFace);
            if (selectedContainedQuads != null) {
                selectedContainedQuads[hostQuad] = containedQuad;
            }
            copyOrientedQuad(containedVisuals, contained, host, containedQuad, hostQuad,
                    result, stride, intsPerVertex, order);
        }
        return result;
    }

    private static void logEnderIoMappingProbe(byte[] contained, byte[] host, int containedVertices,
                                               int hostVertices, int stride, ByteOrder order,
                                               int[] selectedContainedQuads) {
        int call = ENDER_IO_MAPPING_PROBE_COUNT.incrementAndGet();
        if (call > 8) {
            return;
        }
        StringBuilder result = new StringBuilder();
        for (int hostQuad = 0; hostQuad < selectedContainedQuads.length; hostQuad++) {
            if (hostQuad > 0) {
                result.append(';');
            }
            int containedQuad = selectedContainedQuads[hostQuad];
            int containedOffset = containedQuad * 4 * stride;
            int hostOffset = hostQuad * 4 * stride;
            result.append("host").append(hostQuad).append("->payload").append(containedQuad)
                    .append(" area=").append(String.format(Locale.ROOT, "%.4f",
                            area(contained, containedOffset, stride, order)))
                    .append(" payloadUV=").append(uvBounds(contained, containedOffset, stride, order))
                    .append(" hostNormal=").append(vector(normal(host, hostOffset, stride, order)));
        }
        MainMod.LOGGER.info("[AUSMEnderIoFrameMappingProbe] call={} containedQuads={} hostQuads={} stride={} selections={}",
                call, containedVertices / 4, hostVertices / 4, stride, result);
    }

    /**
     * Keep the working single-face fallback visible while measuring the full
     * resolved EnderIO projection.  Earlier attempts emitted the correct CTM
     * fragments with positions outside the frame face; this reports exactly
     * how many fragments map and their resulting bounds before that path is
     * allowed to replace the visible fallback again.
     */
    private static void logEnderIoProjectionProbe(int[] containedVisuals, byte[] contained, byte[] host,
                                                  int containedVertices, int hostVertices, int stride,
                                                  int intsPerVertex, ByteOrder order) {
        int call = ENDER_IO_PROJECTION_PROBE_COUNT.incrementAndGet();
        if (call > 8) {
            return;
        }
        int[] projected = containedVisuals.clone();
        int containedQuads = containedVertices / 4;
        int hostQuads = hostVertices / 4;
        float[][] hostNormals = new float[hostQuads][];
        for (int hostQuad = 0; hostQuad < hostQuads; hostQuad++) {
            hostNormals[hostQuad] = normal(host, hostQuad * 4 * stride, stride, order);
        }
        int mapped = 0;
        for (int containedQuad = 0; containedQuad < containedQuads; containedQuad++) {
            float[] containedNormal = normal(contained, containedQuad * 4 * stride, stride, order);
            int hostQuad = closestQuad(containedNormal, hostNormals, null, containedQuad, false);
            if (mapConnectedQuadPositions(projected, contained, host, containedQuad, containedQuad, hostQuad,
                    stride, intsPerVertex, order)) {
                mapped++;
            }
        }
        MainMod.LOGGER.info("[AUSMEnderIoProjectionProbe] call={} mapped={}/{} sourceBounds={} projectedBounds={} hostBounds={}",
                call, mapped, containedQuads,
                positionBounds(contained, 0, containedVertices, stride, order),
                positionBounds(projected, 0, containedVertices, intsPerVertex),
                positionBounds(host, 0, hostVertices, stride, order));
    }

}
