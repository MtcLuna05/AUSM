package com.l.ausm.impl.util;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

abstract class MinecraftClientReflection extends MinecraftReflectionCompatBase {
    public static Minecraft minecraft() {
        if (MINECRAFT_INSTANCE_HANDLE != null) {
            try {
                Object value = MINECRAFT_INSTANCE_HANDLE.invoke();
                if (value instanceof Minecraft) {
                    return (Minecraft) value;
                }
            } catch (Throwable ignored) {
            }
        }
        return MinecraftReflectionCompat.callStatic(Minecraft.class, Minecraft.class, null, new String[]{"func_71410_x", "getMinecraft"}, NO_PARAMETERS);
    }

    public static File gameDir(Minecraft minecraft) {
        return MinecraftReflectionCompat.field(minecraft, File.class, new File("."), "field_71412_D", "gameDir");
    }

    public static GuiScreen currentScreen(Minecraft minecraft) {
        if (minecraft == null || MINECRAFT_CURRENT_SCREEN_FIELD == null) {
            return null;
        }
        try {
            Object value = MINECRAFT_CURRENT_SCREEN_FIELD.get(minecraft);
            return value instanceof GuiScreen ? (GuiScreen) value : null;
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            return null;
        }
    }

    public static WorldProvider worldProvider(World world) {
        if (world == null || WORLD_PROVIDER_FIELD == null) {
            return null;
        }
        try {
            Object provider = WORLD_PROVIDER_FIELD.get(world);
            return provider instanceof WorldProvider ? (WorldProvider) provider : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    public static int providerDimension(WorldProvider provider) {
        if (provider != null && PROVIDER_DIMENSION_HANDLE != null) {
            try {
                return (int) PROVIDER_DIMENSION_HANDLE.invokeExact(provider);
            } catch (Throwable failure) {
                MinecraftReflectionCompat.logHotPathHandleFailure("providerDimension", failure);
            }
        }
        int direct = MinecraftReflectionCompat.callInt(provider, PROVIDER_DIMENSION_NAMES, NO_PARAMETERS, Integer.MIN_VALUE);
        if (direct != Integer.MIN_VALUE) {
            return direct;
        }
        Object dimensionType = MinecraftReflectionCompat.invoke(provider, PROVIDER_DIMENSION_TYPE_NAMES, NO_PARAMETERS);
        int dimensionTypeId = MinecraftReflectionCompat.dimensionTypeId(dimensionType);
        if (dimensionTypeId != Integer.MIN_VALUE) {
            return dimensionTypeId;
        }
        return MinecraftReflectionCompat.fieldInt(provider, Integer.MIN_VALUE, "field_76574_g", "dimension");
    }

    public static boolean providerHasSkyLight(WorldProvider provider) {
        return MinecraftReflectionCompat.callBoolean(provider, new String[]{"func_191066_m", "hasSkyLight"}, NO_PARAMETERS,
                MinecraftReflectionCompat.fieldBoolean(provider, false, "field_191067_f", "hasSkyLight"));
    }

    public static int vertexFormatSize(VertexFormat format) {
        if (format == null) {
            return -1;
        }
        if (VERTEX_FORMAT_SIZE_HANDLE != null) {
            try {
                return (int) VERTEX_FORMAT_SIZE_HANDLE.invokeExact(format);
            } catch (Throwable failure) {
                MinecraftReflectionCompat.logHotPathHandleFailure("vertexFormatSize", failure);
            }
        }
        return MinecraftReflectionCompat.callInt(format, new String[]{"func_177338_f", "getSize"}, NO_PARAMETERS, -1);
    }

    public static int vertexFormatIntegerSize(VertexFormat format) {
        if (format == null) {
            return -1;
        }
        if (VERTEX_FORMAT_INTEGER_SIZE_HANDLE != null) {
            try {
                return (int) VERTEX_FORMAT_INTEGER_SIZE_HANDLE.invokeExact(format);
            } catch (Throwable failure) {
                MinecraftReflectionCompat.logHotPathHandleFailure("vertexFormatIntegerSize", failure);
            }
        }
        return MinecraftReflectionCompat.callInt(format, new String[]{"func_181719_f", "getIntegerSize"}, NO_PARAMETERS, -1);
    }

    public static int vertexFormatElementCount(VertexFormat format) {
        if (format == null) {
            return -1;
        }
        if (VERTEX_FORMAT_ELEMENT_COUNT_HANDLE != null) {
            try {
                return (int) VERTEX_FORMAT_ELEMENT_COUNT_HANDLE.invokeExact(format);
            } catch (Throwable failure) {
                MinecraftReflectionCompat.logHotPathHandleFailure("vertexFormatElementCount", failure);
            }
        }
        return MinecraftReflectionCompat.callInt(format, new String[]{"func_177345_h", "getElementCount"}, NO_PARAMETERS, -1);
    }

    public static VertexFormatElement vertexFormatElement(VertexFormat format, int index) {
        if (format == null) {
            return null;
        }
        if (VERTEX_FORMAT_ELEMENT_HANDLE != null) {
            try {
                return (VertexFormatElement) VERTEX_FORMAT_ELEMENT_HANDLE.invokeExact(format, index);
            } catch (Throwable failure) {
                MinecraftReflectionCompat.logHotPathHandleFailure("vertexFormatElement", failure);
            }
        }
        return MinecraftReflectionCompat.call(format, VertexFormatElement.class, null,
                new String[]{"func_177348_c", "getElement"}, new Class<?>[]{int.class}, index);
    }

    public static int vertexFormatOffset(VertexFormat format, int index) {
        if (format == null) {
            return -1;
        }
        if (VERTEX_FORMAT_OFFSET_HANDLE != null) {
            try {
                return (int) VERTEX_FORMAT_OFFSET_HANDLE.invokeExact(format, index);
            } catch (Throwable failure) {
                MinecraftReflectionCompat.logHotPathHandleFailure("vertexFormatOffset", failure);
            }
        }
        return MinecraftReflectionCompat.callInt(format, new String[]{"func_181720_d", "getOffset"},
                new Class<?>[]{int.class}, -1, index);
    }

    public static boolean vertexFormatHasColor(VertexFormat format) {
        if (format == null) {
            return false;
        }
        if (VERTEX_FORMAT_HAS_COLOR_HANDLE != null) {
            try {
                return (boolean) VERTEX_FORMAT_HAS_COLOR_HANDLE.invokeExact(format);
            } catch (Throwable failure) {
                MinecraftReflectionCompat.logHotPathHandleFailure("vertexFormatHasColor", failure);
            }
        }
        return MinecraftReflectionCompat.callBoolean(format, new String[]{"func_177346_d", "hasColor"}, NO_PARAMETERS, false);
    }

    public static int vertexFormatColorOffset(VertexFormat format) {
        if (format == null) {
            return -1;
        }
        if (VERTEX_FORMAT_COLOR_OFFSET_HANDLE != null) {
            try {
                return (int) VERTEX_FORMAT_COLOR_OFFSET_HANDLE.invokeExact(format);
            } catch (Throwable failure) {
                MinecraftReflectionCompat.logHotPathHandleFailure("vertexFormatColorOffset", failure);
            }
        }
        return MinecraftReflectionCompat.callInt(format, new String[]{"func_177340_e", "getColorOffset"}, NO_PARAMETERS, -1);
    }

    public static boolean vertexFormatHasNormal(VertexFormat format) {
        if (format == null) {
            return false;
        }
        if (VERTEX_FORMAT_HAS_NORMAL_HANDLE != null) {
            try {
                return (boolean) VERTEX_FORMAT_HAS_NORMAL_HANDLE.invokeExact(format);
            } catch (Throwable failure) {
                MinecraftReflectionCompat.logHotPathHandleFailure("vertexFormatHasNormal", failure);
            }
        }
        return MinecraftReflectionCompat.callBoolean(format, new String[]{"func_177350_b", "hasNormal"}, NO_PARAMETERS, false);
    }

    public static boolean vertexFormatHasUvOffset(VertexFormat format, int id) {
        if (format == null) {
            return false;
        }
        if (VERTEX_FORMAT_HAS_UV_OFFSET_HANDLE != null) {
            try {
                return (boolean) VERTEX_FORMAT_HAS_UV_OFFSET_HANDLE.invokeExact(format, id);
            } catch (Throwable failure) {
                MinecraftReflectionCompat.logHotPathHandleFailure("vertexFormatHasUvOffset", failure);
            }
        }
        return MinecraftReflectionCompat.callBoolean(format, new String[]{"func_177347_a", "hasUvOffset"},
                new Class<?>[]{int.class}, false, id);
    }

    public static int vertexFormatUvOffset(VertexFormat format, int id) {
        if (format == null) {
            return -1;
        }
        if (VERTEX_FORMAT_UV_OFFSET_HANDLE != null) {
            try {
                return (int) VERTEX_FORMAT_UV_OFFSET_HANDLE.invokeExact(format, id);
            } catch (Throwable failure) {
                MinecraftReflectionCompat.logHotPathHandleFailure("vertexFormatUvOffset", failure);
            }
        }
        return MinecraftReflectionCompat.callInt(format, new String[]{"func_177344_b", "getUvOffsetById"},
                new Class<?>[]{int.class}, -1, id);
    }

    protected static int dimensionTypeId(Object dimensionType) {
        if (dimensionType == null) {
            return Integer.MIN_VALUE;
        }
        int id = MinecraftReflectionCompat.callInt(dimensionType, new String[]{"func_186068_a", "getId"}, NO_PARAMETERS, Integer.MIN_VALUE);
        if (id != Integer.MIN_VALUE) {
            return id;
        }
        return MinecraftReflectionCompat.fieldInt(dimensionType, Integer.MIN_VALUE, "field_186074_d", "id");
    }

    public static ExtendedBlockStorage[] chunkBlockStorageArray(Chunk chunk) {
        return MinecraftReflectionCompat.call(chunk, ExtendedBlockStorage[].class, null,
                new String[]{"func_76587_i", "getBlockStorageArray"}, NO_PARAMETERS);
    }

    public static boolean blockStorageEmpty(ExtendedBlockStorage section) {
        return section == null || MinecraftReflectionCompat.callBoolean(section, new String[]{"func_76663_a", "isEmpty"}, NO_PARAMETERS, false);
    }

    public static boolean worldIsBlockLoaded(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        if (WORLD_IS_BLOCK_LOADED_HANDLE != null) {
            try {
                return (boolean) WORLD_IS_BLOCK_LOADED_HANDLE.invoke(world, pos);
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public static boolean worldIsRemote(World world) {
        if (world == null || WORLD_REMOTE_FIELD == null) {
            return false;
        }
        try {
            return WORLD_REMOTE_FIELD.getBoolean(world);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    public static boolean worldIsBlockLoaded(World world, BlockPos pos, boolean allowEmpty) {
        if (world == null || pos == null) {
            return false;
        }
        if (WORLD_IS_BLOCK_LOADED_ALLOW_EMPTY_HANDLE != null) {
            try {
                return (boolean) WORLD_IS_BLOCK_LOADED_ALLOW_EMPTY_HANDLE.invoke(world, pos, allowEmpty);
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public static boolean worldCanSeeSky(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        if (WORLD_CAN_SEE_SKY_HANDLE != null) {
            try {
                return (boolean) WORLD_CAN_SEE_SKY_HANDLE.invoke(world, pos);
            } catch (Throwable ignored) {
            }
        }
        return MinecraftReflectionCompat.callBoolean(world, new String[]{"func_175678_i", "canSeeSky"},
                new Class<?>[]{BlockPos.class}, false, pos);
    }

    public static long worldTime(World world) {
        return MinecraftReflectionCompat.callLong(world, new String[]{"func_72820_D", "getWorldTime"}, NO_PARAMETERS, 0L);
    }

    public static float worldRainStrength(World world, float partialTicks) {
        return MinecraftReflectionCompat.callFloat(world, new String[]{"func_72867_j", "getRainStrength"},
                new Class<?>[]{float.class}, 0.0F, partialTicks);
    }

    public static float worldThunderStrength(World world, float partialTicks) {
        return MinecraftReflectionCompat.callFloat(world, new String[]{"func_72819_i", "getThunderStrength"},
                new Class<?>[]{float.class}, 0.0F, partialTicks);
    }

    public static float worldCelestialAngle(World world, float partialTicks) {
        return MinecraftReflectionCompat.callFloat(world, new String[]{"func_72826_c", "getCelestialAngle"},
                new Class<?>[]{float.class}, 0.0F, partialTicks);
    }

    public static int blockAccessCombinedLight(IBlockAccess access, BlockPos pos, int lightValue) {
        return MinecraftReflectionCompat.callInt(access, new String[]{"func_175626_b", "getCombinedLight"},
                new Class<?>[]{BlockPos.class, int.class}, 0, pos, lightValue);
    }

    public static IBlockState blockAccessBlockState(IBlockAccess access, BlockPos pos) {
        return MinecraftReflectionCompat.call(access, IBlockState.class, MinecraftReflectionCompat.airDefaultState(), new String[]{"func_180495_p", "getBlockState"},
                new Class<?>[]{BlockPos.class}, pos);
    }

    public static boolean blockAccessIsAirBlock(IBlockAccess access, BlockPos pos) {
        return MinecraftReflectionCompat.callBoolean(access, new String[]{"func_175623_d", "isAirBlock"},
                new Class<?>[]{BlockPos.class}, false, pos);
    }

    public static Biome blockAccessBiome(IBlockAccess access, BlockPos pos) {
        return MinecraftReflectionCompat.call(access, Biome.class, null, new String[]{"func_180494_b", "getBiome"},
                new Class<?>[]{BlockPos.class}, pos);
    }

    public static int blockAccessStrongPower(IBlockAccess access, BlockPos pos, EnumFacing direction) {
        return MinecraftReflectionCompat.callInt(access, new String[]{"func_175627_a", "getStrongPower"},
                new Class<?>[]{BlockPos.class, EnumFacing.class}, 0, pos, direction);
    }

    public static boolean blockAccessIsSideSolid(IBlockAccess access, BlockPos pos, EnumFacing side, boolean fallback) {
        return MinecraftReflectionCompat.callBoolean(access, new String[]{"isSideSolid"},
                new Class<?>[]{BlockPos.class, EnumFacing.class, boolean.class}, fallback, pos, side, fallback);
    }

    public static WorldType blockAccessWorldType(IBlockAccess access) {
        return MinecraftReflectionCompat.call(access, WorldType.class, WorldType.DEFAULT, new String[]{"func_175624_G", "getWorldType"}, NO_PARAMETERS);
    }

    public static int worldLightFor(World world, EnumSkyBlock skyBlock, BlockPos pos) {
        return MinecraftReflectionCompat.callInt(world, new String[]{"func_175642_b", "getLightFor"},
                new Class<?>[]{EnumSkyBlock.class, BlockPos.class}, 0, skyBlock, pos);
    }

    public static TileEntity blockAccessTileEntity(IBlockAccess access, BlockPos pos) {
        if (access == null || pos == null) {
            return null;
        }
        if (BLOCK_ACCESS_TILE_ENTITY_HANDLE != null) {
            try {
                return (TileEntity) BLOCK_ACCESS_TILE_ENTITY_HANDLE.invokeExact(access, pos);
            } catch (Throwable failure) {
                MinecraftReflectionCompat.logHotPathHandleFailure("blockAccessTileEntity", failure);
            }
        }
        return MinecraftReflectionCompat.call(access, TileEntity.class, null, new String[]{"func_175625_s", "getTileEntity"},
                new Class<?>[]{BlockPos.class}, pos);
    }

    @SuppressWarnings("unchecked")
    public static List<NBTTagCompound> chunkDataTileEntityTags(SPacketChunkData packet) {
        Object value = MinecraftReflectionCompat.invoke(packet, new String[]{"func_189554_f", "getTileEntityTags"}, NO_PARAMETERS);
        return value instanceof List<?> ? (List<NBTTagCompound>) value : Collections.emptyList();
    }

    public static int nbtInteger(NBTTagCompound tag, String key, int fallback) {
        return MinecraftReflectionCompat.callInt(tag, new String[]{"func_74762_e", "getInteger"},
                new Class<?>[]{String.class}, fallback, key);
    }

    public static TileEntity createTileEntity(World world, NBTTagCompound tag) {
        return MinecraftReflectionCompat.callStatic(TileEntity.class, TileEntity.class, null,
                new String[]{"func_190200_a", "create"},
                new Class<?>[]{World.class, NBTTagCompound.class}, world, tag);
    }

    public static boolean worldSetTileEntity(World world, BlockPos pos, TileEntity tileEntity) {
        if (world == null || pos == null || tileEntity == null) {
            return false;
        }
        MinecraftReflectionCompat.invoke(world, new String[]{"func_175690_a", "setTileEntity"},
                new Class<?>[]{BlockPos.class, TileEntity.class}, pos, tileEntity);
        return MinecraftReflectionCompat.blockAccessTileEntity(world, pos) != null;
    }

    @SuppressWarnings("unchecked")
    public static List<TileEntity> worldLoadedTileEntities(World world) {
        Object value = MinecraftReflectionCompat.getField(world, "field_147482_g", "loadedTileEntityList");
        if (value instanceof List<?>) {
            return (List<TileEntity>) value;
        }
        return Collections.emptyList();
    }

    public static int blockPosX(BlockPos pos) {
        // MutableBlockPos stores the live coordinates in subclass state while
        // BlockPos's immutable backing fields remain at its construction
        // value (usually 0/0/0). Invoke the virtual SRG/MCP accessor first so
        // the subclass override is honoured; fields remain a safe fallback.
        int accessorValue = MinecraftReflectionCompat.invokeInt(BLOCK_POS_X_HANDLE, pos, Integer.MIN_VALUE);
        if (accessorValue != Integer.MIN_VALUE) {
            return accessorValue;
        }
        return MinecraftReflectionCompat.blockPosFieldInt(BLOCK_POS_X_FIELD, pos, 0);
    }

    public static int blockPosY(BlockPos pos) {
        int accessorValue = MinecraftReflectionCompat.invokeInt(BLOCK_POS_Y_HANDLE, pos, Integer.MIN_VALUE);
        if (accessorValue != Integer.MIN_VALUE) {
            return accessorValue;
        }
        return MinecraftReflectionCompat.blockPosFieldInt(BLOCK_POS_Y_FIELD, pos, 0);
    }

    public static int blockPosZ(BlockPos pos) {
        int accessorValue = MinecraftReflectionCompat.invokeInt(BLOCK_POS_Z_HANDLE, pos, Integer.MIN_VALUE);
        if (accessorValue != Integer.MIN_VALUE) {
            return accessorValue;
        }
        return MinecraftReflectionCompat.blockPosFieldInt(BLOCK_POS_Z_FIELD, pos, 0);
    }

    public static BlockPos blockPosToImmutable(BlockPos pos) {
        if (pos == null) {
            return new BlockPos(0, 0, 0);
        }
        Object value = MinecraftReflectionCompat.invoke(pos, new String[]{"func_185334_h", "toImmutable"}, NO_PARAMETERS);
        return value instanceof BlockPos ? (BlockPos) value : pos;
    }

    public static long blockPosToLong(BlockPos pos) {
        if (pos == null) {
            return 0L;
        }
        Object value = MinecraftReflectionCompat.invoke(pos, new String[]{"func_177986_g", "toLong"}, NO_PARAMETERS);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return ((long) MinecraftReflectionCompat.blockPosX(pos) & 0x3FFFFFFL) << 38
                | ((long) MinecraftReflectionCompat.blockPosZ(pos) & 0x3FFFFFFL) << 12
                | ((long) MinecraftReflectionCompat.blockPosY(pos) & 0xFFFL);
    }

    public static BlockPos blockPosUp(BlockPos pos) {
        if (pos == null) {
            return new BlockPos(0, 1, 0);
        }
        if (BLOCK_POS_UP_HANDLE != null) {
            try {
                Object value = BLOCK_POS_UP_HANDLE.invoke(pos);
                if (value instanceof BlockPos) {
                    return (BlockPos) value;
                }
            } catch (Throwable ignored) {
            }
        }
        Object value = MinecraftReflectionCompat.invoke(pos, new String[]{"func_177984_a", "up"}, NO_PARAMETERS);
        return value instanceof BlockPos ? (BlockPos) value : new BlockPos(MinecraftReflectionCompat.blockPosX(pos), MinecraftReflectionCompat.blockPosY(pos) + 1, MinecraftReflectionCompat.blockPosZ(pos));
    }

    public static void mutableBlockPosSet(BlockPos.MutableBlockPos pos, int x, int y, int z) {
        Object value = MinecraftReflectionCompat.invoke(pos, new String[]{"func_181079_c", "setPos"},
                new Class<?>[]{int.class, int.class, int.class}, x, y, z);
        if (value == null) {
            MinecraftReflectionCompat.setField(pos, x, "field_177997_b", "field_177962_a", "x");
            MinecraftReflectionCompat.setField(pos, y, "field_177998_c", "field_177960_b", "y");
            MinecraftReflectionCompat.setField(pos, z, "field_177996_d", "field_177961_c", "z");
        }
    }

    public static IBlockState worldBlockState(World world, BlockPos pos) {
        return MinecraftReflectionCompat.call(world, IBlockState.class, null, new String[]{"func_180495_p", "getBlockState"},
                new Class<?>[]{BlockPos.class}, pos);
    }

    public static void worldMarkBlockRangeForRenderUpdate(World world, BlockPos from, BlockPos to) {
        MinecraftReflectionCompat.invoke(world, new String[]{"func_175704_b", "markBlockRangeForRenderUpdate"},
                new Class<?>[]{BlockPos.class, BlockPos.class}, from, to);
    }

    public static void worldMarkBlockRangeForRenderUpdate(World world, int minX, int minY, int minZ,
                                                          int maxX, int maxY, int maxZ) {
        MinecraftReflectionCompat.invoke(world, new String[]{"func_147458_c", "markBlockRangeForRenderUpdate"},
                new Class<?>[]{int.class, int.class, int.class, int.class, int.class, int.class},
                minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static int facingXOffset(EnumFacing facing) {
        return MinecraftReflectionCompat.intValue(MinecraftReflectionCompat.invoke(facing, new String[]{"func_82601_c", "getXOffset"}, NO_PARAMETERS), 0);
    }

    public static int facingYOffset(EnumFacing facing) {
        return MinecraftReflectionCompat.intValue(MinecraftReflectionCompat.invoke(facing, new String[]{"func_96559_d", "getYOffset"}, NO_PARAMETERS), 0);
    }

    public static int facingZOffset(EnumFacing facing) {
        return MinecraftReflectionCompat.intValue(MinecraftReflectionCompat.invoke(facing, new String[]{"func_82599_e", "getZOffset"}, NO_PARAMETERS), 0);
    }

    public static EnumFacing.Axis facingAxis(EnumFacing facing) {
        Object value = MinecraftReflectionCompat.invoke(facing, new String[]{"func_176740_k", "getAxis"}, NO_PARAMETERS);
        if (value instanceof EnumFacing.Axis) {
            return (EnumFacing.Axis) value;
        }
        if (MinecraftReflectionCompat.facingYOffset(facing) != 0) {
            return EnumFacing.Axis.Y;
        }
        return MinecraftReflectionCompat.facingXOffset(facing) != 0 ? EnumFacing.Axis.X : EnumFacing.Axis.Z;
    }

    public static EnumFacing.AxisDirection facingAxisDirection(EnumFacing facing) {
        Object value = MinecraftReflectionCompat.invoke(facing, new String[]{"func_176743_c", "getAxisDirection"}, NO_PARAMETERS);
        if (value instanceof EnumFacing.AxisDirection) {
            return (EnumFacing.AxisDirection) value;
        }
        int offset = MinecraftReflectionCompat.facingXOffset(facing) + MinecraftReflectionCompat.facingYOffset(facing) + MinecraftReflectionCompat.facingZOffset(facing);
        return offset >= 0 ? EnumFacing.AxisDirection.POSITIVE : EnumFacing.AxisDirection.NEGATIVE;
    }

    public static EnumFacing facingRotateAround(EnumFacing facing, EnumFacing.Axis axis) {
        Object value = MinecraftReflectionCompat.invoke(facing, new String[]{"func_176732_a", "rotateAround"},
                new Class<?>[]{EnumFacing.Axis.class}, axis);
        return value instanceof EnumFacing ? (EnumFacing) value : facing;
    }

    public static EnumFacing facingOpposite(EnumFacing facing) {
        Object value = MinecraftReflectionCompat.invoke(facing, new String[]{"func_176734_d", "getOpposite"}, NO_PARAMETERS);
        if (value instanceof EnumFacing) {
            return (EnumFacing) value;
        }
        if (facing == EnumFacing.DOWN) return EnumFacing.UP;
        if (facing == EnumFacing.UP) return EnumFacing.DOWN;
        if (facing == EnumFacing.NORTH) return EnumFacing.SOUTH;
        if (facing == EnumFacing.SOUTH) return EnumFacing.NORTH;
        if (facing == EnumFacing.WEST) return EnumFacing.EAST;
        return EnumFacing.WEST;
    }

    public static IBlockState blockStateAtEntityViewpoint(World world, Entity entity, float partialTicks) {
        Object value = MinecraftReflectionCompat.invoke(ActiveRenderInfo.class, new String[]{"func_186703_a", "getBlockStateAtEntityViewpoint"},
                new Class<?>[]{World.class, Entity.class, float.class}, world, entity, partialTicks);
        return value instanceof IBlockState ? (IBlockState) value : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Entity> loadedEntityList(World world) {
        Object value = MinecraftReflectionCompat.getField(world, "field_72996_f", "loadedEntityList");
        return value instanceof List<?> ? (List<Entity>) value : Collections.emptyList();
    }

    public static int entityId(Entity entity) {
        return MinecraftReflectionCompat.intValue(MinecraftReflectionCompat.invoke(entity, new String[]{"func_145782_y", "getEntityId"}, NO_PARAMETERS),
                MinecraftReflectionCompat.intValue(MinecraftReflectionCompat.getField(entity, "field_145783_c", "entityId"), 0));
    }

    public static ResourceLocation entityKey(Entity entity) {
        Object value = MinecraftReflectionCompat.invokeStatic(EntityList.class, new String[]{"func_191301_a", "getKey"}, new Class<?>[]{Entity.class}, entity);
        return value instanceof ResourceLocation ? (ResourceLocation) value : null;
    }

    public static double posX(Entity entity) {
        return MinecraftReflectionCompat.fieldDouble(entity, 0.0D, "field_70165_t", "posX");
    }

    public static double posY(Entity entity) {
        return MinecraftReflectionCompat.fieldDouble(entity, 0.0D, "field_70163_u", "posY");
    }

    public static double posZ(Entity entity) {
        return MinecraftReflectionCompat.fieldDouble(entity, 0.0D, "field_70161_v", "posZ");
    }

    public static double lastTickPosX(Entity entity) {
        return MinecraftReflectionCompat.fieldDouble(entity, MinecraftReflectionCompat.posX(entity), "field_70142_S", "lastTickPosX");
    }

    public static double lastTickPosY(Entity entity) {
        return MinecraftReflectionCompat.fieldDouble(entity, MinecraftReflectionCompat.posY(entity), "field_70137_T", "lastTickPosY");
    }

    public static double lastTickPosZ(Entity entity) {
        return MinecraftReflectionCompat.fieldDouble(entity, MinecraftReflectionCompat.posZ(entity), "field_70136_U", "lastTickPosZ");
    }

    public static double prevPosX(Entity entity) {
        return MinecraftReflectionCompat.fieldDouble(entity, MinecraftReflectionCompat.posX(entity), "field_70169_q", "prevPosX");
    }

    public static double prevPosY(Entity entity) {
        return MinecraftReflectionCompat.fieldDouble(entity, MinecraftReflectionCompat.posY(entity), "field_70167_r", "prevPosY");
    }

    public static double prevPosZ(Entity entity) {
        return MinecraftReflectionCompat.fieldDouble(entity, MinecraftReflectionCompat.posZ(entity), "field_70166_s", "prevPosZ");
    }

    public static float eyeHeight(Entity entity) {
        return MinecraftReflectionCompat.callFloat(entity, new String[]{"func_70047_e", "getEyeHeight"}, NO_PARAMETERS, 0.0F);
    }

    public static Vec3d positionEyes(Entity entity, float partialTicks) {
        Object value = MinecraftReflectionCompat.invoke(entity, new String[]{"func_174824_e", "getPositionEyes"}, new Class<?>[]{float.class}, partialTicks);
        if (value instanceof Vec3d) {
            return (Vec3d) value;
        }
        double x = MinecraftReflectionCompat.prevPosX(entity) + (MinecraftReflectionCompat.posX(entity) - MinecraftReflectionCompat.prevPosX(entity)) * partialTicks;
        double y = MinecraftReflectionCompat.prevPosY(entity) + (MinecraftReflectionCompat.posY(entity) - MinecraftReflectionCompat.prevPosY(entity)) * partialTicks + MinecraftReflectionCompat.eyeHeight(entity);
        double z = MinecraftReflectionCompat.prevPosZ(entity) + (MinecraftReflectionCompat.posZ(entity) - MinecraftReflectionCompat.prevPosZ(entity)) * partialTicks;
        return new Vec3d(x, y, z);
    }

    public static double vecX(Vec3d vec) {
        return MinecraftReflectionCompat.vecField(vec, VEC_X_FIELD, 0.0D, "field_72450_a", "x");
    }

    public static double vecY(Vec3d vec) {
        return MinecraftReflectionCompat.vecField(vec, VEC_Y_FIELD, 0.0D, "field_72448_b", "y");
    }

    public static double vecZ(Vec3d vec) {
        return MinecraftReflectionCompat.vecField(vec, VEC_Z_FIELD, 0.0D, "field_72449_c", "z");
    }

    protected static double vecField(Vec3d vec, Field field, double fallback, String srgName, String mcpName) {
        if (vec == null) {
            return fallback;
        }
        if (field != null) {
            try {
                return field.getDouble(vec);
            } catch (IllegalAccessException ignored) {
            }
        }
        return MinecraftReflectionCompat.fieldDouble(vec, fallback, srgName, mcpName);
    }

    public static Vec3d vecAdd(Vec3d vec, Vec3d other) {
        return new Vec3d(MinecraftReflectionCompat.vecX(vec) + MinecraftReflectionCompat.vecX(other), MinecraftReflectionCompat.vecY(vec) + MinecraftReflectionCompat.vecY(other), MinecraftReflectionCompat.vecZ(vec) + MinecraftReflectionCompat.vecZ(other));
    }

    public static Vec3d vecSubtract(Vec3d vec, double x, double y, double z) {
        return new Vec3d(MinecraftReflectionCompat.vecX(vec) - x, MinecraftReflectionCompat.vecY(vec) - y, MinecraftReflectionCompat.vecZ(vec) - z);
    }

    public static Vec3d vecScale(Vec3d vec, double scale) {
        return new Vec3d(MinecraftReflectionCompat.vecX(vec) * scale, MinecraftReflectionCompat.vecY(vec) * scale, MinecraftReflectionCompat.vecZ(vec) * scale);
    }

    public static Vec3d vecNormalize(Vec3d vec) {
        double x = MinecraftReflectionCompat.vecX(vec);
        double y = MinecraftReflectionCompat.vecY(vec);
        double z = MinecraftReflectionCompat.vecZ(vec);
        double length = Math.sqrt(x * x + y * y + z * z);
        return length < 1.0E-4D ? new Vec3d(0.0D, 0.0D, 0.0D) : new Vec3d(x / length, y / length, z / length);
    }

    public static double vecDistance(Vec3d from, Vec3d to) {
        double x = MinecraftReflectionCompat.vecX(from) - MinecraftReflectionCompat.vecX(to);
        double y = MinecraftReflectionCompat.vecY(from) - MinecraftReflectionCompat.vecY(to);
        double z = MinecraftReflectionCompat.vecZ(from) - MinecraftReflectionCompat.vecZ(to);
        return Math.sqrt(x * x + y * y + z * z);
    }

    public static float rotationYaw(Entity entity) {
        return MinecraftReflectionCompat.fieldFloat(entity, 0.0F, "field_70177_z", "rotationYaw");
    }

    public static float prevRotationYaw(Entity entity) {
        return MinecraftReflectionCompat.fieldFloat(entity, MinecraftReflectionCompat.rotationYaw(entity), "field_70126_B", "prevRotationYaw");
    }

    public static float rotationPitch(Entity entity) {
        return MinecraftReflectionCompat.fieldFloat(entity, 0.0F, "field_70125_A", "rotationPitch");
    }

    public static float prevRotationPitch(Entity entity) {
        return MinecraftReflectionCompat.fieldFloat(entity, MinecraftReflectionCompat.rotationPitch(entity), "field_70127_C", "prevRotationPitch");
    }
}
