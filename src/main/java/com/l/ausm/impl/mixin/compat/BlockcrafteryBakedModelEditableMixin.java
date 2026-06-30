package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.l.ausm.impl.pipeline.compat.BloomMaskColor;
import com.l.ausm.impl.pipeline.vertex.BlockRenderContext;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.properties.IProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.ForgeHooksClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(targets = "epicsquid.blockcraftery.model.BakedModelEditable", remap = false)
public abstract class BlockcrafteryBakedModelEditableMixin {
    @Unique
    private static final float AUSM_BLOOM_MASK_EXPANSION = 0.025f;

    @Unique
    private static final int AUSM_BLOOM_MASK_PASSES = 2;

    @Unique
    private static final ThreadLocal<Boolean> AUSM_BLOOM_QUAD_FALLBACK = ThreadLocal.withInitial(() -> false);

    @Unique
    private static final AtomicInteger AUSM_BLOCKCRAFTERY_BLOOM_PROBES = new AtomicInteger();

    @Unique
    private static final int AUSM_BLOCKCRAFTERY_BLOOM_PROBE_INITIAL = 4;

    @Unique
    private static final int AUSM_BLOCKCRAFTERY_BLOOM_PROBE_INTERVAL = 128;

    @Unique
    private static final int AUSM_BLOCKCRAFTERY_BLOOM_PROBE_LIMIT = 8192;

    @Unique
    private static final Map<String, Integer> AUSM_SPRITE_BLOOM_COLORS = new ConcurrentHashMap<>();

    @Inject(method = "func_188616_a", at = @At("RETURN"), remap = false, cancellable = true)
    private void ausm$logFramedModelQuads(IBlockState state, EnumFacing side, long rand,
                                          CallbackInfoReturnable<List<BakedQuad>> cir) {
        List<BakedQuad> quads = cir.getReturnValue();
        PipelineContext pipeline = PipelineContext.getInstance();
        IBlockAccess blockAccess = BlockRenderContext.blockAccess();
        BlockPos pos = BlockRenderContext.blockPos();
        IBlockState rawInheritedSource = pipeline.firstInheritedRenderState(state, blockAccess, pos);
        IBlockState inheritedBloomSource = ausm$inheritedBloomSource(state);
        boolean rawBloomProbeCandidate = ausm$isBloomProbeCandidate(rawInheritedSource)
                || pipeline.blockIntrinsicEmission(state) > 0;
        if (ausm$shouldCreateBloomQuads(state, quads, inheritedBloomSource)) {
            List<BakedQuad> bloomQuads = ausm$createBloomQuadsFromSolidLayer(state, side, rand);
            ausm$logBloomProbe(
                    "create",
                    state,
                    inheritedBloomSource,
                    side,
                    rand,
                    quads,
                    bloomQuads,
                    "replaced=" + !bloomQuads.isEmpty()
                            + ", raw=" + pipeline.diagnosticStateName(rawInheritedSource)
                            + ", rawEmission=" + pipeline.blockIntrinsicEmission(rawInheritedSource)
                            + ", rawBloom=" + pipeline.stateHasBloomLayerGeometry(rawInheritedSource)
                            + ", pos=" + pos
                            + ", access=" + (blockAccess != null ? blockAccess.getClass().getName() : "null")
            );
            if (pipeline.currentProblemProbesEnabled() && rawBloomProbeCandidate) {
                pipeline.logCurrentProblemProbe("blockcraftery-model-bloom-create", state, null, null,
                        "side=" + side
                                + ", rand=" + rand
                                + ", inherited=" + pipeline.diagnosticStateName(inheritedBloomSource)
                                + ", originalQuads=" + (quads != null ? quads.size() : -1)
                                + ", bloomQuads=" + bloomQuads.size()
                                + ", expansion=" + AUSM_BLOOM_MASK_EXPANSION
                                + ", layer=" + MinecraftForgeClient.getRenderLayer());
            }
            if (!bloomQuads.isEmpty()) {
                quads = bloomQuads;
                cir.setReturnValue(bloomQuads);
            }
        } else if (AusmBloomLayer.isBloomLayer(MinecraftForgeClient.getRenderLayer())) {
            if (inheritedBloomSource != null) {
                ausm$logBloomProbe(
                        "skip",
                        state,
                        inheritedBloomSource,
                        side,
                        rand,
                        quads,
                        null,
                        "shouldCreate=false"
                                + ", raw=" + pipeline.diagnosticStateName(rawInheritedSource)
                                + ", rawEmission=" + pipeline.blockIntrinsicEmission(rawInheritedSource)
                                + ", rawBloom=" + pipeline.stateHasBloomLayerGeometry(rawInheritedSource)
                                + ", pos=" + pos
                                + ", access=" + (blockAccess != null ? blockAccess.getClass().getName() : "null")
                );
            }
            if (pipeline.currentProblemProbesEnabled() && inheritedBloomSource != null) {
                pipeline.logCurrentProblemProbe("blockcraftery-model-bloom-skip", state, null, null,
                        "side=" + side
                                + ", rand=" + rand
                                + ", inherited=" + pipeline.diagnosticStateName(inheritedBloomSource)
                                + ", quads=" + (quads != null ? quads.size() : -1)
                                + ", fallbackActive=" + AUSM_BLOOM_QUAD_FALLBACK.get()
                                + ", layer=" + MinecraftForgeClient.getRenderLayer());
            }
        }
        if (pipeline.framedBlockDiagnosticsEnabled() && rawBloomProbeCandidate) {
            pipeline.logFramedBlockDiagnostic(
                    "blockcraftery-model",
                    state,
                    null,
                    null,
                    MinecraftForgeClient.getRenderLayer(),
                    -1,
                    -1,
                    quads != null && !quads.isEmpty(),
                    "side=" + side + ", rand=" + rand + ", quads=" + (quads != null ? quads.size() : -1)
            );
        }
    }

    @Unique
    private boolean ausm$shouldCreateBloomQuads(IBlockState state, List<BakedQuad> quads, IBlockState inheritedBloomSource) {
        return !AUSM_BLOOM_QUAD_FALLBACK.get()
                && AusmBloomLayer.isBloomLayer(MinecraftForgeClient.getRenderLayer())
                && inheritedBloomSource != null;
    }

    @Unique
    private static boolean ausm$isBloomProbeCandidate(IBlockState state) {
        PipelineContext pipeline = PipelineContext.getInstance();
        return state != null
                && !pipeline.isBlockcrafteryEditableState(state)
                && (pipeline.blockIntrinsicEmission(state) > 0 || pipeline.stateHasBloomLayerGeometry(state));
    }

    @Unique
    private static void ausm$logBloomProbe(String action, IBlockState state, IBlockState inheritedState,
                                           EnumFacing side, long rand, List<BakedQuad> originalQuads,
                                           List<BakedQuad> bloomQuads, String detail) {
        if (AUSM_BLOCKCRAFTERY_BLOOM_PROBE_LIMIT <= 0) {
            return;
        }
        PipelineContext pipeline = PipelineContext.getInstance();
        if (inheritedState == null || !ausm$isBloomProbeCandidate(inheritedState)) {
            return;
        }
        int count = AUSM_BLOCKCRAFTERY_BLOOM_PROBES.incrementAndGet();
        if (count > AUSM_BLOCKCRAFTERY_BLOOM_PROBE_LIMIT
                || (count > AUSM_BLOCKCRAFTERY_BLOOM_PROBE_INITIAL
                && count % AUSM_BLOCKCRAFTERY_BLOOM_PROBE_INTERVAL != 0)) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMBlockcrafteryBloomProbe] call={} action={} layer={} bloomLayer={} fallbackActive={} side={} rand={} state={} inherited={} originalQuads={} bloomQuads={} detail={}",
                count,
                action,
                MinecraftForgeClient.getRenderLayer(),
                AusmBloomLayer.layer(),
                AUSM_BLOOM_QUAD_FALLBACK.get(),
                side,
                rand,
                pipeline.diagnosticStateName(state),
                pipeline.diagnosticStateName(inheritedState),
                originalQuads != null ? originalQuads.size() : -1,
                bloomQuads != null ? bloomQuads.size() : -1,
                detail
        );
    }

    @Unique
    private List<BakedQuad> ausm$createBloomQuadsFromSolidLayer(IBlockState state, EnumFacing side, long rand) {
        IBlockState inheritedState = ausm$inheritedBloomSource(state);
        if (inheritedState == null) {
            return java.util.Collections.emptyList();
        }

        BlockRenderLayer previousLayer = MinecraftForgeClient.getRenderLayer();
        try {
            AUSM_BLOOM_QUAD_FALLBACK.set(Boolean.TRUE);
            ForgeHooksClient.setRenderLayer(BlockRenderLayer.SOLID);
            List<BakedQuad> solidQuads = ((IBakedModel) (Object) this).getQuads(state, side, rand);
            if (solidQuads == null || solidQuads.isEmpty()) {
                return java.util.Collections.emptyList();
            }

            int fallbackColor = BloomMaskColor.colorForState(inheritedState);
            TextureAtlasSprite maskSprite = ausm$bloomMaskSprite();
            List<BakedQuad> bloomQuads = new ArrayList<>(solidQuads.size() * AUSM_BLOOM_MASK_PASSES);
            for (BakedQuad quad : solidQuads) {
                BakedQuad bloomQuad = ausm$copyAsBloomMaskQuad(quad, fallbackColor, maskSprite);
                if (bloomQuad != null) {
                    for (int pass = 0; pass < AUSM_BLOOM_MASK_PASSES; pass++) {
                        bloomQuads.add(bloomQuad);
                    }
                }
            }
            return bloomQuads;
        } finally {
            ForgeHooksClient.setRenderLayer(previousLayer);
            AUSM_BLOOM_QUAD_FALLBACK.remove();
        }
    }

    @Unique
    private static IBlockState ausm$inheritedBloomSource(IBlockState state) {
        PipelineContext pipeline = PipelineContext.getInstance();
        IBlockAccess blockAccess = BlockRenderContext.blockAccess();
        BlockPos pos = BlockRenderContext.blockPos();
        IBlockState inheritedState = pipeline.inheritedBloomRenderState(state, blockAccess, pos);
        if ((inheritedState == null
                || inheritedState == state
                || inheritedState.getBlock() == null
                || pipeline.isBlockcrafteryEditableState(inheritedState))
                && (blockAccess != null || pos != null)) {
            inheritedState = pipeline.inheritedBloomRenderState(state, null, null);
        }
        if (inheritedState == null
                || inheritedState == state
                || inheritedState.getBlock() == null
                || pipeline.isBlockcrafteryEditableState(inheritedState)) {
            return null;
        }
        if (pipeline.blockIntrinsicEmission(inheritedState) > 0) {
            return inheritedState;
        }
        return pipeline.stateHasBloomLayerGeometry(inheritedState) ? inheritedState : null;
    }

    @Unique
    private static BakedQuad ausm$copyAsBloomMaskQuad(BakedQuad source, int fallbackColor, TextureAtlasSprite maskSprite) {
        if (source == null || source.getVertexData() == null) {
            return null;
        }
        int[] data = source.getVertexData().clone();
        VertexFormat format = source.getFormat();
        int stride = format != null ? format.getIntegerSize() : data.length / 4;
        if (stride <= 0 || data.length < stride * 4) {
            return source;
        }

        int colorOffset = format != null && format.hasColor() ? format.getColorOffset() / Integer.BYTES : 3;
        int uvOffset = format != null && format.hasUvOffset(0) ? format.getUvOffsetById(0) / Integer.BYTES : 4;
        int lightOffset = format != null && format.hasUvOffset(1) ? format.getUvOffsetById(1) / Integer.BYTES : 6;
        float maskU = maskSprite != null ? (maskSprite.getMinU() + maskSprite.getMaxU()) * 0.5f : 0.5f;
        float maskV = maskSprite != null ? (maskSprite.getMinV() + maskSprite.getMaxV()) * 0.5f : 0.5f;
        int color = fallbackColor != -1 ? fallbackColor : ausm$sourceQuadMaskColor(source);

        for (int vertex = 0; vertex < data.length / stride; vertex++) {
            int base = vertex * stride;
            data[base] = Float.floatToRawIntBits(ausm$expandedMaskCoordinate(Float.intBitsToFloat(data[base])));
            data[base + 1] = Float.floatToRawIntBits(ausm$expandedMaskCoordinate(Float.intBitsToFloat(data[base + 1])));
            data[base + 2] = Float.floatToRawIntBits(ausm$expandedMaskCoordinate(Float.intBitsToFloat(data[base + 2])));
            if (colorOffset >= 0 && base + colorOffset < data.length) {
                data[base + colorOffset] = color;
            }
            if (uvOffset >= 0 && base + uvOffset + 1 < data.length) {
                data[base + uvOffset] = Float.floatToRawIntBits(maskU);
                data[base + uvOffset + 1] = Float.floatToRawIntBits(maskV);
            }
            if (lightOffset >= 0 && base + lightOffset < data.length) {
                data[base + lightOffset] = (240 << 16) | 240;
            }
        }

        return new BakedQuad(data, -1, source.getFace(), maskSprite != null ? maskSprite : source.getSprite(),
                source.shouldApplyDiffuseLighting(), source.getFormat());
    }

    @Unique
    private static TextureAtlasSprite ausm$bloomMaskSprite() {
        try {
            TextureAtlasSprite sprite = Minecraft.getMinecraft().getTextureMapBlocks()
                    .getAtlasSprite("minecraft:blocks/quartz_block_top");
            if (sprite != null && !sprite.getIconName().contains("missingno")) {
                return sprite;
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    @Unique
    private static float ausm$expandedMaskCoordinate(float value) {
        if (!Float.isFinite(value)) {
            return value;
        }
        float delta = value - 0.5f;
        if (Math.abs(delta) < 1.0e-4f) {
            return value;
        }
        return value + Math.copySign(AUSM_BLOOM_MASK_EXPANSION, delta);
    }

    @Unique
    private static int ausm$bloomMaskColor(IBlockState sourceState) {
        String color = ausm$statePropertyValue(sourceState, "color");
        if (color == null) {
            color = ausm$statePropertyValue(sourceState, "colour");
        }
        if (color == null) {
            return -1;
        }
        return ausm$dyeMaskColor(color);
    }

    @Unique
    private static String ausm$statePropertyValue(IBlockState state, String propertyName) {
        if (state == null) {
            return null;
        }
        try {
            for (IProperty<?> property : state.getPropertyKeys()) {
                if (property != null && propertyName.equalsIgnoreCase(property.getName())) {
                    Object value = state.getValue(property);
                    return value != null ? value.toString().toLowerCase(java.util.Locale.ROOT) : null;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    @Unique
    private static int ausm$dyeMaskColor(String color) {
        String normalized = color == null ? "" : color.toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "red" -> ausm$packColor(0xFF7070);
            case "orange", "brown" -> ausm$packColor(0xFFB050);
            case "yellow" -> ausm$packColor(0xFFFF70);
            case "lime" -> ausm$packColor(0xB8FF70);
            case "green" -> ausm$packColor(0x70FF90);
            case "cyan" -> ausm$packColor(0x70FFFF);
            case "light_blue", "lightblue" -> ausm$packColor(0x90D0FF);
            case "blue" -> ausm$packColor(0x8080FF);
            case "purple" -> ausm$packColor(0xC080FF);
            case "magenta" -> ausm$packColor(0xFF80FF);
            case "pink" -> ausm$packColor(0xFF98C8);
            default -> -1;
        };
    }

    @Unique
    private static int ausm$sourceQuadMaskColor(BakedQuad source) {
        int spriteColor = ausm$spriteAverageMaskColor(source.getSprite());
        if (spriteColor != -1) {
            return spriteColor;
        }

        int vertexColor = ausm$averageVertexMaskColor(source);
        return vertexColor != -1 ? vertexColor : ausm$packColor(0xFFFFFF);
    }

    @Unique
    private static int ausm$spriteAverageMaskColor(TextureAtlasSprite sprite) {
        if (sprite == null || sprite.getIconName() == null || sprite.getIconName().contains("missingno")) {
            return -1;
        }
        return AUSM_SPRITE_BLOOM_COLORS.computeIfAbsent(sprite.getIconName(), spriteName -> {
            try {
                int[][] frames = sprite.getFrameTextureData(0);
                if (frames == null || frames.length == 0 || frames[0] == null) {
                    return -1;
                }
                long red = 0L;
                long green = 0L;
                long blue = 0L;
                long weight = 0L;
                for (int pixel : frames[0]) {
                    int alpha = (pixel >>> 24) & 0xFF;
                    if (alpha <= 16) {
                        continue;
                    }
                    red += ((pixel >>> 16) & 0xFF) * (long) alpha;
                    green += ((pixel >>> 8) & 0xFF) * (long) alpha;
                    blue += (pixel & 0xFF) * (long) alpha;
                    weight += alpha;
                }
                if (weight <= 0L) {
                    return -1;
                }
                int r = (int) (red / weight);
                int g = (int) (green / weight);
                int b = (int) (blue / weight);
                return ausm$packColor((r << 16) | (g << 8) | b);
            } catch (RuntimeException ignored) {
                return -1;
            }
        });
    }

    @Unique
    private static int ausm$averageVertexMaskColor(BakedQuad source) {
        int[] data = source.getVertexData();
        VertexFormat format = source.getFormat();
        int stride = format != null ? format.getIntegerSize() : data.length / 4;
        int colorOffset = format != null && format.hasColor() ? format.getColorOffset() / Integer.BYTES : 3;
        if (data == null || stride <= 0 || colorOffset < 0 || data.length < stride * 4) {
            return -1;
        }

        int vertices = data.length / stride;
        int red = 0;
        int green = 0;
        int blue = 0;
        int count = 0;
        for (int vertex = 0; vertex < vertices; vertex++) {
            int index = vertex * stride + colorOffset;
            if (index < 0 || index >= data.length) {
                continue;
            }
            int packed = data[index];
            int r;
            int g;
            int b;
            if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                r = packed & 0xFF;
                g = (packed >>> 8) & 0xFF;
                b = (packed >>> 16) & 0xFF;
            } else {
                r = (packed >>> 24) & 0xFF;
                g = (packed >>> 16) & 0xFF;
                b = (packed >>> 8) & 0xFF;
            }
            red += r;
            green += g;
            blue += b;
            count++;
        }
        if (count <= 0) {
            return -1;
        }
        return ausm$packColor(((red / count) << 16) | ((green / count) << 8) | (blue / count));
    }

    @Unique
    private static int ausm$packColor(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
                ? (0xFF << 24) | (blue << 16) | (green << 8) | red
                : (red << 24) | (green << 16) | (blue << 8) | 0xFF;
    }
}
