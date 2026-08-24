package com.luna.ausm.impl.pipeline.compat;

import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

public final class AppliedEnergisticsFacadeQuadMetadata {
    private static final Map<BakedQuad, Metadata> METADATA =
            Collections.synchronizedMap(new WeakHashMap<>());

    private AppliedEnergisticsFacadeQuadMetadata() {
    }

    public static void mark(BakedQuad quad, IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (quad == null || state == null) {
            return;
        }

        PipelineContext pipeline = PipelineContext.getInstance();
        IBlockState contextState = pipeline.effectiveBlockRenderState(state, null, null);
        if (contextState == null) {
            contextState = state;
        }
        int blockEntityId = pipeline.blockEntityId(state, null, null);
        int emission = pipeline.blockIntrinsicEmission(state);
        if (emission == 0 && pipeline.stateHasBloomLayerGeometry(state)) {
            emission = 15;
        }
        if (blockEntityId == 0 && emission > 0) {
            blockEntityId = 10028;
        }
        METADATA.put(quad, new Metadata(
                blockEntityId,
                (short) MinecraftReflectionCompat.stateRenderTypeOrdinal(contextState),
                pipeline.blockMetadata(state, null, null),
                emission,
                null
        ));
    }

    public static void markCableBusTint(BakedQuad quad, int[] tintColors) {
        if (quad == null || tintColors == null || tintColors.length == 0 || METADATA.containsKey(quad)) {
            return;
        }
        METADATA.put(quad, new Metadata(-1, (short) -1, 0, -1, tintColors.clone()));
    }

    public static Metadata get(BakedQuad quad) {
        return quad != null ? METADATA.get(quad) : null;
    }

    public static final class Metadata {
        private final int blockEntityId;
        private final short renderType;
        private final int metadata;
        private final int emission;
        private final int[] tintColors;

        private Metadata(int blockEntityId, short renderType, int metadata, int emission, int[] tintColors) {
            this.blockEntityId = blockEntityId;
            this.renderType = renderType;
            this.metadata = metadata;
            this.emission = emission;
            this.tintColors = tintColors;
        }

        public int blockEntityId() {
            return blockEntityId;
        }

        public short renderType() {
            return renderType;
        }

        public int metadata() {
            return metadata;
        }

        public int emission() {
            return emission;
        }

        public boolean hasBlockMetadata() {
            return blockEntityId >= 0 && renderType >= 0 && emission >= 0;
        }

        public int tintColor(int tintIndex) {
            if (tintColors == null || tintIndex < 0 || tintIndex >= tintColors.length) {
                return -1;
            }
            return tintColors[tintIndex];
        }
    }
}
