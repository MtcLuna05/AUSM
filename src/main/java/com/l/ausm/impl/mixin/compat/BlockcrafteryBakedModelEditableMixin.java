package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.bloom.AusmBloomLayer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.properties.IProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.BlockRenderLayer;
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

@Mixin(targets = "epicsquid.blockcraftery.model.BakedModelEditable", remap = false)
public abstract class BlockcrafteryBakedModelEditableMixin {
    @Unique
    private static final float AUSM_BLOOM_MASK_EXPANSION = 0.025f;

    @Unique
    private static final int AUSM_BLOOM_MASK_PASSES = 2;

    @Unique
    private static final ThreadLocal<Boolean> AUSM_BLOOM_QUAD_FALLBACK = ThreadLocal.withInitial(() -> false);

    @Inject(method = "func_188616_a", at = @At("RETURN"), remap = false, cancellable = true)
    private void ausm$logFramedModelQuads(IBlockState state, EnumFacing side, long rand,
                                          CallbackInfoReturnable<List<BakedQuad>> cir) {
        List<BakedQuad> quads = cir.getReturnValue();
        if (ausm$shouldCreateBloomQuads(state, quads)) {
            List<BakedQuad> bloomQuads = ausm$createBloomQuadsFromSolidLayer(state, side, rand);
            PipelineContext.getInstance().logCurrentProblemProbe("blockcraftery-model-bloom-create", state, null, null,
                    "side=" + side
                            + ", rand=" + rand
                            + ", inherited=" + PipelineContext.getInstance().diagnosticStateName(ausm$inheritedBloomSource(state))
                            + ", originalQuads=" + (quads != null ? quads.size() : -1)
                            + ", bloomQuads=" + bloomQuads.size()
                            + ", expansion=" + AUSM_BLOOM_MASK_EXPANSION
                            + ", layer=" + MinecraftForgeClient.getRenderLayer());
            if (!bloomQuads.isEmpty()) {
                quads = bloomQuads;
                cir.setReturnValue(bloomQuads);
            }
        } else if (AusmBloomLayer.isBloomLayer(MinecraftForgeClient.getRenderLayer())) {
            PipelineContext.getInstance().logCurrentProblemProbe("blockcraftery-model-bloom-skip", state, null, null,
                    "side=" + side
                            + ", rand=" + rand
                            + ", inherited=" + PipelineContext.getInstance().diagnosticStateName(ausm$inheritedBloomSource(state))
                            + ", quads=" + (quads != null ? quads.size() : -1)
                            + ", fallbackActive=" + AUSM_BLOOM_QUAD_FALLBACK.get()
                            + ", layer=" + MinecraftForgeClient.getRenderLayer());
        }
        PipelineContext.getInstance().logFramedBlockDiagnostic(
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

    @Unique
    private boolean ausm$shouldCreateBloomQuads(IBlockState state, List<BakedQuad> quads) {
        return !AUSM_BLOOM_QUAD_FALLBACK.get()
                && AusmBloomLayer.isBloomLayer(MinecraftForgeClient.getRenderLayer())
                && (quads == null || quads.isEmpty())
                && ausm$inheritedBloomSource(state) != null;
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

            int color = ausm$bloomMaskColor(inheritedState);
            TextureAtlasSprite maskSprite = ausm$bloomMaskSprite();
            List<BakedQuad> bloomQuads = new ArrayList<>(solidQuads.size() * AUSM_BLOOM_MASK_PASSES);
            for (BakedQuad quad : solidQuads) {
                BakedQuad bloomQuad = ausm$copyAsBloomMaskQuad(quad, color, maskSprite);
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
        IBlockState inheritedState = pipeline.inheritedBloomRenderState(state, null, null);
        if (inheritedState == null
                || inheritedState == state
                || inheritedState.getBlock() == null
                || pipeline.isBlockcrafteryEditableState(inheritedState)) {
            return null;
        }
        if (pipeline.blockRenderEmission(inheritedState, null, null) > 0) {
            return inheritedState;
        }
        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        try {
            return bloomLayer != null && inheritedState.getBlock().canRenderInLayer(inheritedState, bloomLayer)
                    ? inheritedState
                    : null;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Unique
    private static BakedQuad ausm$copyAsBloomMaskQuad(BakedQuad source, int color, TextureAtlasSprite maskSprite) {
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
            default -> ausm$packColor(0xFFFFFF);
        };
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
