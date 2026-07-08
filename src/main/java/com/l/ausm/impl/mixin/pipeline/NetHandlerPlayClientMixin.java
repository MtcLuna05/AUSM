package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.network.play.server.SPacketBlockAction;
import net.minecraft.network.play.server.SPacketBlockChange;
import net.minecraft.network.play.server.SPacketCustomPayload;
import net.minecraft.network.play.server.SPacketEntityEffect;
import net.minecraft.network.play.server.SPacketEntityHeadLook;
import net.minecraft.network.play.server.SPacketEffect;
import net.minecraft.network.play.server.SPacketHeldItemChange;
import net.minecraft.network.play.server.SPacketMultiBlockChange;
import net.minecraft.network.play.server.SPacketPlayerAbilities;
import net.minecraft.network.play.server.SPacketPlayerPosLook;
import net.minecraft.network.play.server.SPacketSetExperience;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.network.play.server.SPacketSoundEffect;
import net.minecraft.network.play.server.SPacketTeams;
import net.minecraft.network.play.server.SPacketTimeUpdate;
import net.minecraft.network.play.server.SPacketUnloadChunk;
import net.minecraft.network.play.server.SPacketUpdateHealth;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerPlayClient.class)
public class NetHandlerPlayClientMixin {
    @Unique
    private double ausm$preTeleportX;
    @Unique
    private double ausm$preTeleportY;
    @Unique
    private double ausm$preTeleportZ;
    @Unique
    private int ausm$preTeleportDimension = Integer.MIN_VALUE;
    @Unique
    private boolean ausm$hasPreTeleportPosition;

    @Inject(method = {"func_147273_a", "handleUpdateTileEntity"}, at = @At("HEAD"), cancellable = true)
    private void ausm$ignoreUpdateTileEntityWithoutWorld(SPacketUpdateTileEntity packetIn, CallbackInfo ci) {
        if (ausm$worldUnavailable()) {
            ci.cancel();
        }
    }

    @Inject(method = {"func_147261_a", "handleBlockAction"}, at = @At("HEAD"), cancellable = true)
    private void ausm$ignoreBlockActionWithoutWorld(SPacketBlockAction packetIn, CallbackInfo ci) {
        if (ausm$worldUnavailable()) {
            ci.cancel();
        }
    }

    @Inject(method = {"func_147234_a", "handleBlockChange"}, at = @At("HEAD"), cancellable = true)
    private void ausm$ignoreBlockChangeWithoutWorld(SPacketBlockChange packetIn, CallbackInfo ci) {
        if (ausm$worldUnavailable()) {
            ci.cancel();
        }
    }

    @Inject(method = {"func_147287_a", "handleMultiBlockChange"}, at = @At("HEAD"), cancellable = true)
    private void ausm$ignoreMultiBlockChangeWithoutWorld(SPacketMultiBlockChange packetIn, CallbackInfo ci) {
        if (ausm$worldUnavailable()) {
            ci.cancel();
        }
    }

    @Inject(method = {"func_184326_a", "processChunkUnload"}, at = @At("HEAD"), cancellable = true)
    private void ausm$ignoreChunkUnloadWithoutWorld(SPacketUnloadChunk packetIn, CallbackInfo ci) {
        if (ausm$worldUnavailable()) {
            ci.cancel();
        }
    }

    @Inject(method = {"func_147285_a", "handleTimeUpdate"}, at = @At("HEAD"), cancellable = true)
    private void ausm$ignoreTimeUpdateWithoutWorld(SPacketTimeUpdate packetIn, CallbackInfo ci) {
        if (ausm$worldUnavailable()) {
            ci.cancel();
        }
    }

    @Inject(method = {"func_147260_a", "handleEntityEffect"}, at = @At("HEAD"), cancellable = true)
    private void ausm$ignoreEntityEffectWithoutWorld(SPacketEntityEffect packetIn, CallbackInfo ci) {
        if (ausm$worldUnavailable()) {
            ci.cancel();
        }
    }

    @Inject(method = {"func_147247_a", "handleTeams"}, at = @At("HEAD"), cancellable = true)
    private void ausm$ignoreTeamPacketWithoutWorld(SPacketTeams packetIn, CallbackInfo ci) {
        WorldClient world = ausm$world();
        if (world == null) {
            ci.cancel();
        }
    }

    @Inject(method = {"func_184327_a", "handleSoundEffect"}, at = @At("HEAD"), cancellable = true)
    private void ausm$ignoreSoundPacketWithoutRenderViewEntity(SPacketSoundEffect packetIn, CallbackInfo ci) {
        WorldClient world = ausm$world();
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (world == null || mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc) == null) {
            ci.cancel();
        }
    }

    @Inject(method = {"func_147257_a", "handleHeldItemChange"}, at = @At("HEAD"), cancellable = true)
    private void ausm$ignoreHeldItemChangeWithoutPlayer(SPacketHeldItemChange packetIn, CallbackInfo ci) {
        WorldClient world = ausm$world();
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (world == null || mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc) == null) {
            ci.cancel();
        }
    }

    @Inject(method = {"func_147266_a", "handleSetSlot"}, at = @At("HEAD"), cancellable = true)
    private void ausm$ignoreSetSlotWithoutPlayer(SPacketSetSlot packetIn, CallbackInfo ci) {
        WorldClient world = ausm$world();
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (world == null || mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc) == null) {
            ci.cancel();
        }
    }

    @Inject(method = {"func_147270_a", "handlePlayerAbilities"}, at = @At("HEAD"), cancellable = true)
    private void ausm$ignorePlayerAbilitiesWithoutPlayer(SPacketPlayerAbilities packetIn, CallbackInfo ci) {
        WorldClient world = ausm$world();
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (world == null || mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc) == null) {
            ci.cancel();
        }
    }

    @Inject(method = {"func_184330_a", "handlePlayerPosLook"}, at = @At("HEAD"), cancellable = true)
    private void ausm$ignorePlayerPosLookWithoutPlayer(SPacketPlayerPosLook packetIn, CallbackInfo ci) {
        WorldClient world = ausm$world();
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        EntityPlayerSP player = mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc) : null;
        if (world == null || player == null) {
            ausm$hasPreTeleportPosition = false;
            ci.cancel();
            return;
        }
        ausm$preTeleportX = com.l.ausm.impl.util.MinecraftReflectionCompat.posX(player);
        ausm$preTeleportY = com.l.ausm.impl.util.MinecraftReflectionCompat.posY(player);
        ausm$preTeleportZ = com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(player);
        ausm$preTeleportDimension = com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world) != null
                ? com.l.ausm.impl.util.MinecraftReflectionCompat.providerDimension(com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world))
                : Integer.MIN_VALUE;
        ausm$hasPreTeleportPosition = true;
    }

    @Inject(method = {"func_184330_a", "handlePlayerPosLook"}, at = @At("RETURN"))
    private void ausm$resyncTerrainAfterTeleport(SPacketPlayerPosLook packetIn, CallbackInfo ci) {
        if (!ausm$hasPreTeleportPosition) {
            return;
        }
        ausm$hasPreTeleportPosition = false;
        WorldClient world = ausm$world();
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        EntityPlayerSP player = mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc) : null;
        if (world == null || player == null) {
            return;
        }
        double dx = com.l.ausm.impl.util.MinecraftReflectionCompat.posX(player) - ausm$preTeleportX;
        double dy = com.l.ausm.impl.util.MinecraftReflectionCompat.posY(player) - ausm$preTeleportY;
        double dz = com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(player) - ausm$preTeleportZ;
        int currentDimension = com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world) != null
                ? com.l.ausm.impl.util.MinecraftReflectionCompat.providerDimension(com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world))
                : Integer.MIN_VALUE;
        PipelineContext.getInstance().handleClientTeleportResync(
                ausm$preTeleportDimension,
                currentDimension,
                dx * dx + dy * dy + dz * dz,
                dx * dx + dz * dz
        );
    }

    @Inject(method = {"func_147295_a", "handleSetExperience"}, at = @At("HEAD"), cancellable = true)
    private void ausm$ignoreSetExperienceWithoutPlayer(SPacketSetExperience packetIn, CallbackInfo ci) {
        WorldClient world = ausm$world();
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (world == null || mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc) == null) {
            ci.cancel();
        }
    }

    @Inject(method = {"func_147249_a", "handleUpdateHealth"}, at = @At("HEAD"), cancellable = true)
    private void ausm$ignoreUpdateHealthWithoutPlayer(SPacketUpdateHealth packetIn, CallbackInfo ci) {
        WorldClient world = ausm$world();
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (world == null || mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc) == null) {
            ci.cancel();
        }
    }

    @Inject(method = {"func_147277_a", "handleEffect"}, at = @At("HEAD"), cancellable = true)
    private void ausm$ignoreEffectWithoutRenderViewEntity(SPacketEffect packetIn, CallbackInfo ci) {
        WorldClient world = ausm$world();
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (world == null || mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc) == null) {
            ci.cancel();
        }
    }

    @Inject(method = {"func_147267_a", "handleEntityHeadLook"}, at = @At("HEAD"), cancellable = true)
    private void ausm$ignoreEntityHeadLookWithoutWorld(SPacketEntityHeadLook packetIn, CallbackInfo ci) {
        if (ausm$world() == null) {
            ci.cancel();
        }
    }

    @Inject(method = {"func_147240_a", "handleCustomPayload"}, at = @At("HEAD"), cancellable = true)
    private void ausm$ignoreCustomPayloadWithoutPlayer(SPacketCustomPayload packetIn, CallbackInfo ci) {
        WorldClient world = ausm$world();
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (world == null || mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc) == null) {
            ci.cancel();
        }
    }

    @Inject(method = {"func_147263_a", "handleChunkData"}, at = @At("HEAD"), cancellable = true)
    private void ausm$dropMalformedChunkData(SPacketChunkData packetIn, CallbackInfo ci) {
        WorldClient world = ausm$world();
        if (world == null || packetIn == null || !ausm$isChunkDataReadable(packetIn)) {
            ci.cancel();
        }
    }

    @Inject(method = {"func_147263_a", "handleChunkData"}, at = @At("RETURN"))
    private void ausm$queueShaderChunkRefresh(SPacketChunkData packetIn, CallbackInfo ci) {
        WorldClient world = ausm$world();
        if (world != null && packetIn != null && com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((packetIn), new String[] {"func_149274_i", "isFullChunk"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false)) {
            int chunkX = com.l.ausm.impl.util.MinecraftReflectionCompat.callInt((packetIn), new String[] {"func_149273_e", "getChunkX"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, 0);
            int chunkZ = com.l.ausm.impl.util.MinecraftReflectionCompat.callInt((packetIn), new String[] {"func_149271_f", "getChunkZ"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, 0);
            PipelineContext.getInstance().queueShaderChunkRefresh(world, chunkX, chunkZ);
            PipelineContext.getInstance().queueClientChunkRenderRefresh(
                    world,
                    chunkX,
                    chunkZ,
                    "chunk-data"
            );
        }
    }

    @Unique
    private WorldClient ausm$world() {
        return com.l.ausm.impl.util.MinecraftReflectionCompat.netHandlerWorld((NetHandlerPlayClient) (Object) this);
    }

    @Unique
    private boolean ausm$worldUnavailable() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        return ausm$world() == null || mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null;
    }

    @Unique
    private boolean ausm$isChunkDataReadable(SPacketChunkData packetIn) {
        WorldClient world = ausm$world();
        if (world == null) {
            return false;
        }
        PacketBuffer buffer = null;
        int readerIndex = -1;
        try {
            buffer = com.l.ausm.impl.util.MinecraftReflectionCompat.call(packetIn, PacketBuffer.class, null, new String[] {"func_186946_a", "getReadBuffer"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS);
            if (buffer == null) {
                return false;
            }
            readerIndex = buffer.readerIndex();
            int sections = com.l.ausm.impl.util.MinecraftReflectionCompat.callInt((packetIn), new String[] {"func_149276_g", "getExtractedSize"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, 0);
            boolean hasSkyLight = com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world) != null && com.l.ausm.impl.util.MinecraftReflectionCompat.providerHasSkyLight(com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world));

            if ((sections & ~0xFFFF) != 0) {
                return false;
            }

            for (int section = 0; section < 16; section++) {
                if ((sections & (1 << section)) == 0) {
                    continue;
                }

                if (!com.l.ausm.impl.util.MinecraftReflectionCompat.readChunkBlockStateContainer(buffer)) {
                    return false;
                }

                if (!ausm$skipChunkBytes(buffer, 2048)) {
                    return false;
                }

                if (hasSkyLight && !ausm$skipChunkBytes(buffer, 2048)) {
                    return false;
                }
            }

            if (com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((packetIn), new String[] {"func_149274_i", "isFullChunk"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false)) {
                int biomePayloadBytes = buffer.readableBytes();
                if (biomePayloadBytes == 256) {
                    return ausm$skipChunkBytes(buffer, 256);
                }

                return biomePayloadBytes > 0 && com.l.ausm.impl.util.MinecraftReflectionCompat.call(buffer, int[].class, new int[0], new String[] {"func_189424_c", "readVarIntArray"}, new Class<?>[] {int.class}, biomePayloadBytes).length == 256;
            }

            return true;
        } catch (RuntimeException ignored) {
            return false;
        } finally {
            if (buffer != null && readerIndex >= 0) {
                buffer.readerIndex(readerIndex);
            }
        }
    }

    @Unique
    private boolean ausm$skipChunkBytes(PacketBuffer buffer, int byteCount) {
        if (buffer.readableBytes() < byteCount) {
            return false;
        }

        buffer.skipBytes(byteCount);
        return true;
    }
}
