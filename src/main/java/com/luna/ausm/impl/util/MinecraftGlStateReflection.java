package com.luna.ausm.impl.util;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.compat.BlockRendererDispatcherHooks;
import com.luna.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.luna.ausm.impl.pipeline.vertex.IBufferBuilderExtension;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.client.model.pipeline.IVertexConsumer;
import org.lwjgl.opengl.GL11;

abstract class MinecraftGlStateReflection extends MinecraftGuiRenderingReflection {
    public static void glStateDisableTexture2D() {
        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179090_x", "disableTexture2D"}, NO_PARAMETERS);
    }

    public static void glStateEnableDepth() {
        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179126_j", "enableDepth"}, NO_PARAMETERS);
    }

    public static void glStateDisableDepth() {
        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179097_i", "disableDepth"}, NO_PARAMETERS);
    }

    public static void glStateDepthMask(boolean flag) {
        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179132_a", "depthMask"}, new Class<?>[]{boolean.class}, flag);
    }

    public static void glStateEnableAlpha() {
        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179141_d", "enableAlpha"}, NO_PARAMETERS);
    }

    public static void glStateDisableAlpha() {
        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179118_c", "disableAlpha"}, NO_PARAMETERS);
    }

    public static void glStateAlphaFunc(int func, float ref) {
        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179092_a", "alphaFunc"},
                new Class<?>[]{int.class, float.class}, func, ref);
    }

    public static void glStateEnableBlend() {
        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179147_l", "enableBlend"}, NO_PARAMETERS);
    }

    public static void glStateDisableBlend() {
        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179084_k", "disableBlend"}, NO_PARAMETERS);
    }

    public static void glStateTryBlendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179120_a", "tryBlendFuncSeparate"},
                new Class<?>[]{int.class, int.class, int.class, int.class},
                srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    public static void glStateDisableLighting() {
        MinecraftReflectionCompat.invoke(GlStateManager.class, GL_DISABLE_LIGHTING_NAMES, NO_PARAMETERS);
    }

    public static void glStateDisableColorMaterial() {
        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179119_h", "disableColorMaterial"}, NO_PARAMETERS);
    }

    public static void glStateEnableCull() {
        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179089_o", "enableCull"}, NO_PARAMETERS);
    }

    public static void glStateDisableCull() {
        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179129_p", "disableCull"}, NO_PARAMETERS);
    }

    public static void glStateCullFaceBack() {
        if (!CullFaceBackCall.invoke()) {
            GL11.glCullFace(GL11.GL_BACK);
        }
    }

    public static void glStateBindTexture(int texture) {
        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179144_i", "bindTexture"}, new Class<?>[]{int.class}, texture);
    }

    public static void glStateViewport(int x, int y, int width, int height) {
        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179083_b", "viewport"},
                new Class<?>[]{int.class, int.class, int.class, int.class}, x, y, width, height);
    }

    public static void glStateColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179135_a", "colorMask"},
                new Class<?>[]{boolean.class, boolean.class, boolean.class, boolean.class},
                red, green, blue, alpha);
    }

    public static void glStateClearDepth(double depth) {
        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179151_a", "clearDepth"}, new Class<?>[]{double.class}, depth);
    }

    public static void glStateColor(float red, float green, float blue, float alpha) {
        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179131_c", "color"},
                new Class<?>[]{float.class, float.class, float.class, float.class},
                red, green, blue, alpha);
    }

    public static void glStateGlTexCoordPointer(int size, int type, int stride, int pointer) {
        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_187405_c", "glTexCoordPointer"},
                new Class<?>[]{int.class, int.class, int.class, int.class}, size, type, stride, pointer);
    }

    public static void setClientActiveTexture(int textureUnit) {
        if (CLIENT_ACTIVE_TEXTURE_HANDLE != null) {
            try {
                CLIENT_ACTIVE_TEXTURE_HANDLE.invokeExact(textureUnit);
                return;
            } catch (Throwable failure) {
                MinecraftReflectionCompat.logHotPathHandleFailure("setClientActiveTexture", failure);
            }
        }
        MinecraftReflectionCompat.invoke(OpenGlHelper.class, new String[]{"func_77472_b", "setClientActiveTexture"}, new Class<?>[]{int.class}, textureUnit);
    }

    /**
     * Emits resolution data once during client setup, never from a render or
     * chunk-compiler loop. Invocation failures are separately reported by the
     * bounded fallback probe below.
     */
    public static void logPerformanceRouteProbe() {
        MainMod.LOGGER.info(
                "[AUSMPerformanceRouteProbe] renderTypeHandle={} blockHandle={} tileEntityHandle={} clientTextureHandle={} forgeRenderPassHandle={}",
                STATE_RENDER_TYPE_HANDLE != null,
                BLOCK_FROM_STATE_HANDLE != null,
                BLOCK_ACCESS_TILE_ENTITY_HANDLE != null,
                CLIENT_ACTIVE_TEXTURE_HANDLE != null,
                FORGE_RENDER_PASS_HANDLE != null
        );
    }

    public static BlockRendererDispatcher blockRendererDispatcher(Minecraft minecraft) {
        return MinecraftReflectionCompat.call(minecraft, BlockRendererDispatcher.class, null,
                new String[]{"func_175602_ab", "getBlockRendererDispatcher"}, NO_PARAMETERS);
    }

    public static boolean renderLiquidBlock(BlockRendererDispatcher dispatcher, IBlockAccess access,
                                            IBlockState state, BlockPos pos, BufferBuilder buffer) {
        if (dispatcher == null || access == null || state == null || pos == null || buffer == null) {
            return false;
        }
        try {
            Object fluidRenderer = MinecraftReflectionCompat.getField(dispatcher, "field_175025_e", "fluidRenderer");
            VertexFormat blockFormat = MinecraftReflectionCompat.blockFormat();
            if (fluidRenderer == null || blockFormat == null) {
                MinecraftReflectionCompat.logAstralLiquidRendererProbe("missing-renderer-or-format", state, buffer, fluidRenderer, -1, -1, false, null);
                return false;
            }

            // Nothirium/Celeritas may provide AUSM's 56-byte terrain format.
            // Render directly into that buffer so BufferBuilder's metadata
            // hooks can expand each vanilla fluid vertex correctly; copying
            // 32-byte vanilla vertices into it corrupts the destination
            // stride and makes custom fluids disappear.
            if (ExtendedVertexFormats.isPipelineBlock(MinecraftReflectionCompat.bufferVertexFormat(buffer))) {
                Object rawResult = MinecraftReflectionCompat.invokePropagating(fluidRenderer,
                        new String[]{"func_178270_a", "renderFluid"},
                        new Class<?>[]{IBlockAccess.class, IBlockState.class, BlockPos.class, BufferBuilder.class},
                        access, state, pos, buffer);
                boolean rendered = rawResult instanceof Boolean && (Boolean) rawResult;
                MinecraftReflectionCompat.logAstralLiquidRendererProbe("pipeline-direct", state, buffer, fluidRenderer,
                        MinecraftReflectionCompat.bufferVertexCount(buffer), MinecraftReflectionCompat.bufferVertexCount(buffer), rendered, null);
                return rendered;
            }

            BufferBuilder staging = THREAD_FLUID_STAGING_BUFFER.get();
            MinecraftReflectionCompat.forceResetBufferDrawingState(staging);
            MinecraftReflectionCompat.bufferBegin(staging, GL11.GL_QUADS, blockFormat);
            MinecraftReflectionCompat.bufferSetTranslation(staging,
                    MinecraftReflectionCompat.fieldDouble(buffer, 0.0D, "field_179004_l", "xOffset"),
                    MinecraftReflectionCompat.fieldDouble(buffer, 0.0D, "field_179005_m", "yOffset"),
                    MinecraftReflectionCompat.fieldDouble(buffer, 0.0D, "field_179002_n", "zOffset"));

            Object rawResult = MinecraftReflectionCompat.invokePropagating(fluidRenderer,
                    new String[]{"func_178270_a", "renderFluid"},
                    new Class<?>[]{IBlockAccess.class, IBlockState.class, BlockPos.class, BufferBuilder.class},
                    access, state, pos, staging);
            boolean rendered = rawResult instanceof Boolean && (Boolean) rawResult;
            int vertices = MinecraftReflectionCompat.bufferVertexCount(staging);
            int bytes = vertices * MinecraftReflectionCompat.callInt(blockFormat,
                    new String[]{"func_177338_f", "getSize"}, NO_PARAMETERS, 0);
            ByteBuffer stagingBytes = MinecraftReflectionCompat.bufferByteBuffer(staging);
            if (!rendered || vertices <= 0 || bytes <= 0 || stagingBytes == null || bytes > stagingBytes.capacity()) {
                MinecraftReflectionCompat.logAstralLiquidRendererProbe("staging-empty-or-invalid", state, staging, fluidRenderer,
                        vertices, MinecraftReflectionCompat.bufferVertexCount(buffer), rendered, null);
                return false;
            }

            ByteBuffer source = stagingBytes.duplicate();
            source.position(0);
            source.limit(bytes);
            int before = MinecraftReflectionCompat.bufferVertexCount(buffer);
            BlockRendererDispatcherHooks.LIQUID_RENDER.remove();
            try {
                MinecraftReflectionCompat.invoke(buffer, new String[]{"putBulkData"}, new Class<?>[]{ByteBuffer.class}, source);
            } finally {
                BlockRendererDispatcherHooks.LIQUID_RENDER.set(Boolean.TRUE);
            }
            boolean copied = MinecraftReflectionCompat.bufferVertexCount(buffer) > before;
            MinecraftReflectionCompat.logAstralLiquidRendererProbe("staging-copy", state, buffer, fluidRenderer,
                    vertices, MinecraftReflectionCompat.bufferVertexCount(buffer), copied, null);
            return copied;
        } catch (RuntimeException | LinkageError failure) {
            MinecraftReflectionCompat.logAstralLiquidRendererProbe("exception", state, buffer, null, -1, -1, false, failure);
            return false;
        }
    }

    protected static void logAstralLiquidRendererProbe(String stage, IBlockState state, BufferBuilder buffer,
                                                       Object fluidRenderer, int rendererVertices, int bufferVertices,
                                                       boolean rendered, Throwable failure) {
        // Probe disabled.
    }

    public static boolean hasField(Object target, String... names) {
        if (target == null) {
            return false;
        }
        for (String name : names) {
            if (MinecraftReflectionCompat.findField(target.getClass(), name) != null) {
                return true;
            }
        }
        return false;
    }

    public static boolean stateIsLiquid(IBlockState state) {
        if (state == null) {
            return false;
        }
        Material material = MinecraftReflectionCompat.stateMaterial(state);
        if (material != null && MATERIAL_IS_LIQUID_HANDLE != null) {
            try {
                return (boolean) MATERIAL_IS_LIQUID_HANDLE.invokeExact(material);
            } catch (Throwable failure) {
                MinecraftReflectionCompat.logHotPathHandleFailure("materialIsLiquid", failure);
                return false;
            }
        }
        return material != null && MinecraftReflectionCompat.callBoolean(material,
                new String[]{"func_76224_d", "isLiquid"}, NO_PARAMETERS, false);
    }

    public static boolean stateIsLiquidOrWater(IBlockState state) {
        return MinecraftReflectionCompat.stateIsLiquid(state)
                || MinecraftReflectionCompat.stateMaterialIsWater(state)
                || MinecraftReflectionCompat.stateRenderType(state) == EnumBlockRenderType.LIQUID
                || MinecraftReflectionCompat.stateHasFluidLikeRegistryName(state);
    }

    public static boolean stateIsVanillaLiquid(IBlockState state) {
        Block block = MinecraftReflectionCompat.blockFromState(state);
        ResourceLocation name = block != null ? MinecraftReflectionCompat.blockRegistryName(block) : null;
        if (name == null || !"minecraft".equals(MinecraftReflectionCompat.resourceNamespace(name))) {
            return false;
        }
        String path = MinecraftReflectionCompat.resourcePathLower(name);
        return "water".equals(path) || "flowing_water".equals(path)
                || "lava".equals(path) || "flowing_lava".equals(path);
    }

    protected static boolean stateHasFluidLikeRegistryName(IBlockState state) {
        Block block = MinecraftReflectionCompat.blockFromState(state);
        if (block == null) {
            return false;
        }
        ResourceLocation name = MinecraftReflectionCompat.blockRegistryName(block);
        if (name == null) {
            return false;
        }
        String path = MinecraftReflectionCompat.resourcePathLower(name);
        // This is only a last-resort compatibility path.  Registry/class-name
        // substring matching turns ordinary machines such as
        // fluid_dictionary_converter into water; real Forge fluids already
        // report a liquid material or LIQUID render type above.
        return "fluid".equals(path)
                || "fluids".equals(path)
                || "liquid".equals(path)
                || "liquids".equals(path)
                || "fluid_block".equals(path)
                || "fluidblock".equals(path)
                || "block_fluid".equals(path)
                || "blockfluid".equals(path)
                || "liquid_block".equals(path)
                || "liquidblock".equals(path);
    }

    public static IBakedModel blockModel(BlockRendererDispatcher dispatcher, IBlockState state) {
        return MinecraftReflectionCompat.call(dispatcher, IBakedModel.class, null,
                new String[]{"func_184389_a", "getModelForState"},
                new Class<?>[]{IBlockState.class}, state);
    }

    public static IBlockState blockExtendedState(IBlockState state, IBlockAccess access, BlockPos pos) {
        Block block = MinecraftReflectionCompat.blockFromState(state);
        if (block == null || access == null || pos == null) {
            return state;
        }
        return MinecraftReflectionCompat.call(block, IBlockState.class, state,
                new String[]{"getExtendedState"},
                new Class<?>[]{IBlockState.class, IBlockAccess.class, BlockPos.class},
                state, access, pos);
    }

    public static ResourceLocation blocksTexture() {
        return MinecraftReflectionCompat.field(TextureMap.class, ResourceLocation.class, new ResourceLocation("textures/atlas/blocks.png"),
                "field_110575_b", "LOCATION_BLOCKS_TEXTURE");
    }

    public static TextureMap textureMapBlocks(Minecraft minecraft) {
        return MinecraftReflectionCompat.call(minecraft, TextureMap.class, null,
                new String[]{"func_147117_R", "getTextureMapBlocks"}, NO_PARAMETERS);
    }

    public static TextureAtlasSprite atlasSprite(TextureMap textureMap, String name) {
        if (textureMap == null || name == null || name.isBlank()) {
            return null;
        }
        return MinecraftReflectionCompat.call(textureMap, TextureAtlasSprite.class, null,
                new String[]{"func_110572_b", "getAtlasSprite"},
                new Class<?>[]{String.class}, name);
    }

    @SuppressWarnings("unchecked")
    public static List<BakedQuad> bakedModelQuads(IBakedModel model, IBlockState state, EnumFacing side, long rand) {
        Object value = MinecraftReflectionCompat.invoke(model, new String[]{"func_188616_a", "getQuads"},
                new Class<?>[]{IBlockState.class, EnumFacing.class, long.class}, state, side, rand);
        return value instanceof List<?> ? (List<BakedQuad>) value : Collections.emptyList();
    }

    public static TextureAtlasSprite bakedQuadSprite(BakedQuad quad) {
        Object direct = MinecraftReflectionCompat.invokeReference(BAKED_QUAD_SPRITE_HANDLE, quad);
        if (direct instanceof TextureAtlasSprite) {
            return (TextureAtlasSprite) direct;
        }
        Object value = MinecraftReflectionCompat.invoke(quad, new String[]{"func_187508_a", "getSprite"}, NO_PARAMETERS);
        if (value instanceof TextureAtlasSprite) {
            return (TextureAtlasSprite) value;
        }
        value = MinecraftReflectionCompat.getField(quad, "field_187509_d", "sprite");
        return value instanceof TextureAtlasSprite ? (TextureAtlasSprite) value : null;
    }

    public static int[] bakedQuadVertexData(BakedQuad quad) {
        Object direct = MinecraftReflectionCompat.invokeReference(BAKED_QUAD_VERTEX_DATA_HANDLE, quad);
        if (direct instanceof int[]) {
            return (int[]) direct;
        }
        Object value = MinecraftReflectionCompat.invoke(quad, new String[]{"func_178209_a", "getVertexData"}, NO_PARAMETERS);
        if (value instanceof int[]) {
            return (int[]) value;
        }
        value = MinecraftReflectionCompat.getField(quad, "field_178215_a", "vertexData");
        return value instanceof int[] ? (int[]) value : null;
    }

    public static boolean bakedQuadPipe(BakedQuad quad, IVertexConsumer consumer) {
        if (quad == null || consumer == null) {
            return false;
        }
        if (BAKED_QUAD_PIPE_HANDLE != null) {
            try {
                BAKED_QUAD_PIPE_HANDLE.invokeExact(quad, consumer);
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }
        Method method = MinecraftReflectionCompat.findMethod(quad.getClass(), "pipe", new Class<?>[]{IVertexConsumer.class});
        if (method == null) {
            return false;
        }
        try {
            method.invoke(quad, consumer);
            return true;
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            return false;
        }
    }

    public static int bakedQuadTintIndex(BakedQuad quad) {
        Object value = MinecraftReflectionCompat.invoke(quad, new String[]{"func_178211_c", "getTintIndex"}, NO_PARAMETERS);
        return MinecraftReflectionCompat.intValue(value, MinecraftReflectionCompat.intValue(MinecraftReflectionCompat.getField(quad, "field_178213_b", "tintIndex"), -1));
    }

    public static int blockColorMultiplier(Minecraft minecraft, IBlockState state, IBlockAccess blockAccess, BlockPos pos, int tintIndex) {
        if (minecraft == null || state == null) {
            return -1;
        }
        try {
            Object blockColors = MinecraftReflectionCompat.invoke(minecraft, new String[]{"func_184125_al", "getBlockColors"}, NO_PARAMETERS);
            Object value = MinecraftReflectionCompat.invoke(blockColors, new String[]{"func_189991_a", "colorMultiplier"},
                    new Class<?>[]{IBlockState.class, IBlockAccess.class, BlockPos.class, int.class},
                    state, blockAccess, pos, tintIndex);
            return value instanceof Number ? ((Number) value).intValue() : -1;
        } catch (RuntimeException | LinkageError ignored) {
            return -1;
        }
    }

    public static int blockColorMultiplier(BlockColors blockColors, IBlockState state, IBlockAccess blockAccess,
                                           BlockPos pos, int tintIndex) {
        int direct = MinecraftReflectionCompat.invokeInt4(BLOCK_COLOR_MULTIPLIER_HANDLE, blockColors, state, blockAccess, pos, tintIndex,
                Integer.MIN_VALUE);
        if (direct != Integer.MIN_VALUE) {
            return direct;
        }
        return MinecraftReflectionCompat.callInt(blockColors, new String[]{"func_186724_a", "func_189991_a", "colorMultiplier"},
                new Class<?>[]{IBlockState.class, IBlockAccess.class, BlockPos.class, int.class},
                0xFFFFFF, state, blockAccess, pos, tintIndex);
    }

    public static boolean bakedQuadHasTintIndex(BakedQuad quad) {
        Object value = MinecraftReflectionCompat.invoke(quad, new String[]{"hasTintIndex"}, NO_PARAMETERS);
        return value instanceof Boolean ? (Boolean) value : MinecraftReflectionCompat.bakedQuadTintIndex(quad) >= 0;
    }

    public static EnumFacing bakedQuadFace(BakedQuad quad) {
        Object value = MinecraftReflectionCompat.invoke(quad, new String[]{"func_178210_d", "getFace"}, NO_PARAMETERS);
        if (value instanceof EnumFacing) {
            return (EnumFacing) value;
        }
        value = MinecraftReflectionCompat.getField(quad, "field_178214_c", "face");
        return value instanceof EnumFacing ? (EnumFacing) value : null;
    }

    public static VertexFormat bakedQuadFormat(BakedQuad quad) {
        Object value = MinecraftReflectionCompat.invoke(quad, new String[]{"getFormat"}, NO_PARAMETERS);
        if (value instanceof VertexFormat) {
            return (VertexFormat) value;
        }
        value = MinecraftReflectionCompat.getField(quad, "format");
        return value instanceof VertexFormat ? (VertexFormat) value : MinecraftReflectionCompat.blockFormat();
    }

    public static boolean bakedQuadApplyDiffuseLighting(BakedQuad quad) {
        Object value = MinecraftReflectionCompat.invoke(quad, new String[]{"shouldApplyDiffuseLighting"}, NO_PARAMETERS);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        value = MinecraftReflectionCompat.getField(quad, "applyDiffuseLighting");
        return !(value instanceof Boolean) || (Boolean) value;
    }

    public static String spriteIconName(TextureAtlasSprite sprite) {
        return MinecraftReflectionCompat.call(sprite, String.class, null, new String[]{"func_94215_i", "getIconName"}, NO_PARAMETERS);
    }

    public static float spriteMinU(TextureAtlasSprite sprite) {
        return MinecraftReflectionCompat.callFloat(sprite, new String[]{"func_94209_e", "getMinU"}, NO_PARAMETERS,
                MinecraftReflectionCompat.fieldFloat(sprite, 0.0F, "field_110979_l", "minU"));
    }

    public static float spriteMaxU(TextureAtlasSprite sprite) {
        return MinecraftReflectionCompat.callFloat(sprite, new String[]{"func_94212_f", "getMaxU"}, NO_PARAMETERS,
                MinecraftReflectionCompat.fieldFloat(sprite, 0.0F, "field_110980_m", "maxU"));
    }

    public static float spriteMinV(TextureAtlasSprite sprite) {
        return MinecraftReflectionCompat.callFloat(sprite, new String[]{"func_94206_g", "getMinV"}, NO_PARAMETERS,
                MinecraftReflectionCompat.fieldFloat(sprite, 0.0F, "field_110977_n", "minV"));
    }

    public static float spriteMaxV(TextureAtlasSprite sprite) {
        return MinecraftReflectionCompat.callFloat(sprite, new String[]{"func_94210_h", "getMaxV"}, NO_PARAMETERS,
                MinecraftReflectionCompat.fieldFloat(sprite, 0.0F, "field_110978_o", "maxV"));
    }

    public static boolean renderBlock(BlockRendererDispatcher dispatcher, IBlockState state, BlockPos pos,
                                      IBlockAccess blockAccess, BufferBuilder buffer) {
        if (dispatcher == null || state == null || pos == null || blockAccess == null || buffer == null
                || RENDER_BLOCK_HANDLE == null) {
            return false;
        }
        try {
            return (boolean) RENDER_BLOCK_HANDLE.invoke(dispatcher, state, pos, blockAccess, buffer);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static BlockPos renderChunkPosition(Object renderChunk) {
        Object value = MinecraftReflectionCompat.invoke(renderChunk, new String[]{"func_178568_j", "getPosition"}, NO_PARAMETERS);
        return value instanceof BlockPos ? (BlockPos) value : null;
    }

    public static World renderChunkWorld(RenderChunk renderChunk) {
        Object value = MinecraftReflectionCompat.getField(renderChunk, "field_178588_d", "world");
        return value instanceof World ? (World) value : null;
    }

    public static BlockPos tileEntityPos(TileEntity tileEntity) {
        if (tileEntity == null) {
            return null;
        }
        if (TILE_ENTITY_POS_HANDLE != null) {
            try {
                return (BlockPos) TILE_ENTITY_POS_HANDLE.invokeExact(tileEntity);
            } catch (Throwable ignored) {
            }
        }
        Object value = MinecraftReflectionCompat.invoke(tileEntity, TILE_ENTITY_POS_NAMES, NO_PARAMETERS);
        return value instanceof BlockPos ? (BlockPos) value : null;
    }

    public static boolean tileEntityInvalid(TileEntity tileEntity) {
        if (tileEntity == null) {
            return false;
        }
        if (TILE_ENTITY_INVALID_HANDLE != null) {
            try {
                return (boolean) TILE_ENTITY_INVALID_HANDLE.invokeExact(tileEntity);
            } catch (Throwable ignored) {
            }
        }
        return MinecraftReflectionCompat.callBoolean(tileEntity, TILE_ENTITY_INVALID_NAMES, NO_PARAMETERS, false);
    }

    public static BufferBuilder regionBufferForLayer(RegionRenderCacheBuilder builder, BlockRenderLayer layer) {
        if (builder == null || layer == null) {
            return null;
        }
        if (REGION_BUFFER_FOR_LAYER_HANDLE != null) {
            try {
                return (BufferBuilder) REGION_BUFFER_FOR_LAYER_HANDLE.invoke(builder, layer);
            } catch (Throwable ignored) {
            }
        }
        if (REGION_BUFFER_FOR_LAYER_ID_HANDLE != null) {
            try {
                return (BufferBuilder) REGION_BUFFER_FOR_LAYER_ID_HANDLE.invoke(builder, layer.ordinal());
            } catch (Throwable ignored) {
            }
        }
        Object worldRenderers = MinecraftReflectionCompat.getField(builder, "field_179040_a", "worldRenderers");
        if (worldRenderers instanceof BufferBuilder[] buffers
                && layer.ordinal() >= 0
                && layer.ordinal() < buffers.length) {
            return buffers[layer.ordinal()];
        }
        return null;
    }

    public static int bufferVertexCount(BufferBuilder buffer) {
        if (buffer instanceof IBufferBuilderExtension extension) {
            return extension.ausm$vertexCount();
        }
        return MinecraftReflectionCompat.intValue(MinecraftReflectionCompat.invoke(buffer, new String[]{"func_178989_h", "getVertexCount"}, NO_PARAMETERS), 0);
    }

    public static Tessellator tessellator() {
        Object value = MinecraftReflectionCompat.invokeStatic(Tessellator.class, new String[]{"func_178181_a", "getInstance"}, NO_PARAMETERS);
        return value instanceof Tessellator ? (Tessellator) value : null;
    }

    public static BufferBuilder tessellatorBuffer(Tessellator tessellator) {
        Object value = MinecraftReflectionCompat.invoke(tessellator, new String[]{"func_178180_c", "getBuffer"}, NO_PARAMETERS);
        return value instanceof BufferBuilder ? (BufferBuilder) value : null;
    }

    public static void tessellatorDraw(Tessellator tessellator) {
        MinecraftReflectionCompat.invoke(tessellator, new String[]{"func_78381_a", "draw"}, NO_PARAMETERS);
    }

    public static boolean bufferIsDrawing(BufferBuilder buffer) {
        if (buffer instanceof IBufferBuilderExtension extension) {
            return extension.ausm$isDrawing();
        }
        return MinecraftReflectionCompat.fieldBoolean(buffer, false, "field_179010_r", "isDrawing");
    }

    public static void forceResetBufferDrawingState(BufferBuilder buffer) {
        if (buffer == null) {
            return;
        }
        if (buffer instanceof IBufferBuilderExtension extension) {
            extension.ausm$forceResetDrawingState();
            return;
        }
        MinecraftReflectionCompat.setField(buffer, false, "field_179010_r", "isDrawing");
        MinecraftReflectionCompat.invoke(buffer, new String[]{"func_178965_a", "reset"}, NO_PARAMETERS);
    }

    public static VertexFormat bufferVertexFormat(BufferBuilder buffer) {
        if (buffer instanceof IBufferBuilderExtension extension) {
            return extension.ausm$vertexFormat();
        }
        Object value = MinecraftReflectionCompat.invoke(buffer, BUFFER_VERTEX_FORMAT_NAMES, NO_PARAMETERS);
        return value instanceof VertexFormat ? (VertexFormat) value : null;
    }

    public static ByteBuffer bufferByteBuffer(BufferBuilder buffer) {
        if (buffer instanceof IBufferBuilderExtension extension) {
            return extension.ausm$byteBuffer();
        }
        Object value = MinecraftReflectionCompat.invoke(buffer, new String[]{"func_178966_f", "getByteBuffer"}, NO_PARAMETERS);
        return value instanceof ByteBuffer ? (ByteBuffer) value : null;
    }

    public static void bufferBegin(BufferBuilder buffer, int drawMode, VertexFormat format) {
        MinecraftReflectionCompat.invoke(buffer, new String[]{"func_181668_a", "begin"}, new Class<?>[]{int.class, VertexFormat.class}, drawMode, format);
    }

    public static void bufferSetTranslation(BufferBuilder buffer, double x, double y, double z) {
        MinecraftReflectionCompat.invoke(buffer, new String[]{"func_178969_c", "setTranslation"}, new Class<?>[]{double.class, double.class, double.class}, x, y, z);
    }

    public static Object bufferPos(BufferBuilder buffer, double x, double y, double z) {
        return MinecraftReflectionCompat.invoke(buffer, new String[]{"func_181662_b", "pos"},
                new Class<?>[]{double.class, double.class, double.class}, x, y, z);
    }

    public static Object bufferTex(Object target, double u, double v) {
        return MinecraftReflectionCompat.invoke(target, new String[]{"func_187315_a", "tex"}, new Class<?>[]{double.class, double.class}, u, v);
    }

    public static Object bufferColor(Object target, int red, int green, int blue, int alpha) {
        return MinecraftReflectionCompat.invoke(target, new String[]{"func_181669_b", "color"},
                new Class<?>[]{int.class, int.class, int.class, int.class}, red, green, blue, alpha);
    }

    public static void bufferEndVertex(Object target) {
        MinecraftReflectionCompat.invoke(target, new String[]{"func_181675_d", "endVertex"}, NO_PARAMETERS);
    }

    public static void bufferPosTexEnd(BufferBuilder buffer, double x, double y, double z, double u, double v) {
        Object positioned = MinecraftReflectionCompat.bufferPos(buffer, x, y, z);
        Object textured = MinecraftReflectionCompat.bufferTex(positioned != null ? positioned : buffer, u, v);
        MinecraftReflectionCompat.bufferEndVertex(textured != null ? textured : positioned != null ? positioned : buffer);
    }

    public static void bufferPosColorEnd(BufferBuilder buffer, double x, double y, double z,
                                         int red, int green, int blue, int alpha) {
        Object positioned = MinecraftReflectionCompat.bufferPos(buffer, x, y, z);
        Object colored = MinecraftReflectionCompat.bufferColor(positioned != null ? positioned : buffer, red, green, blue, alpha);
        MinecraftReflectionCompat.bufferEndVertex(colored != null ? colored : positioned != null ? positioned : buffer);
    }

    public static void bufferPosEnd(BufferBuilder buffer, double x, double y, double z) {
        Object positioned = MinecraftReflectionCompat.bufferPos(buffer, x, y, z);
        MinecraftReflectionCompat.bufferEndVertex(positioned != null ? positioned : buffer);
    }

    public static boolean renderChunkLayerEmpty(Object renderChunk, BlockRenderLayer layer) {
        Object compiledChunk = MinecraftReflectionCompat.invoke(renderChunk, new String[]{"func_178571_g", "getCompiledChunk"}, NO_PARAMETERS);
        Object value = MinecraftReflectionCompat.invoke(compiledChunk, new String[]{"func_178491_b", "isLayerEmpty"},
                new Class<?>[]{BlockRenderLayer.class}, layer);
        return !(value instanceof Boolean) || (Boolean) value;
    }

    public static VertexFormat blockFormat() {
        return MinecraftReflectionCompat.defaultVertexFormat("field_176600_a", "BLOCK");
    }

    protected static VertexFormat defaultVertexFormat(String srgName, String mcpName) {
        Object value = MinecraftReflectionCompat.getStaticField(DefaultVertexFormats.class, srgName, mcpName);
        return value instanceof VertexFormat ? (VertexFormat) value : null;
    }

    public static VertexFormat addElement(VertexFormat format, VertexFormatElement element) {
        Object value = MinecraftReflectionCompat.invoke(format, new String[]{"func_181721_a", "addElement"}, new Class<?>[]{VertexFormatElement.class}, element);
        return value instanceof VertexFormat ? (VertexFormat) value : format;
    }

    public static float[] ambientOcclusionFaceVertexColorMultiplier(Object ambientOcclusionFace) {
        if (ambientOcclusionFace == null) {
            return null;
        }
        Field field = AMBIENT_OCCLUSION_MULTIPLIER_FIELDS.get(ambientOcclusionFace.getClass());
        if (field != null) {
            try {
                Object value = field.get(ambientOcclusionFace);
                if (value instanceof float[]) {
                    return (float[]) value;
                }
            } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            }
        }
        Object value = MinecraftReflectionCompat.getField(ambientOcclusionFace, "field_178206_b", "vertexColorMultiplier");
        return value instanceof float[] ? (float[]) value : null;
    }
}
