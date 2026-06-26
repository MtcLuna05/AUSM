package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.network.play.server.SPacketCustomPayload;
import net.minecraft.network.play.server.SPacketEffect;
import net.minecraft.network.play.server.SPacketHeldItemChange;
import net.minecraft.network.play.server.SPacketPlayerAbilities;
import net.minecraft.network.play.server.SPacketPlayerPosLook;
import net.minecraft.network.play.server.SPacketSetExperience;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.network.play.server.SPacketSoundEffect;
import net.minecraft.network.play.server.SPacketTeams;
import net.minecraft.network.play.server.SPacketUpdateHealth;
import net.minecraft.world.chunk.BlockStateContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerPlayClient.class)
public class NetHandlerPlayClientMixin {
    @Shadow
    private WorldClient world;

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

    @Inject(method = "handleTeams", at = @At("HEAD"), cancellable = true)
    private void ausm$ignoreTeamPacketWithoutWorld(SPacketTeams packetIn, CallbackInfo ci) {
        if (world == null) {
            ci.cancel();
        }
    }

    @Inject(method = "handleSoundEffect", at = @At("HEAD"), cancellable = true)
    private void ausm$ignoreSoundPacketWithoutRenderViewEntity(SPacketSoundEffect packetIn, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (world == null || mc == null || mc.getRenderViewEntity() == null) {
            ci.cancel();
        }
    }

    @Inject(method = "handleHeldItemChange", at = @At("HEAD"), cancellable = true)
    private void ausm$ignoreHeldItemChangeWithoutPlayer(SPacketHeldItemChange packetIn, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (world == null || mc == null || mc.player == null) {
            ci.cancel();
        }
    }

    @Inject(method = "handleSetSlot", at = @At("HEAD"), cancellable = true)
    private void ausm$ignoreSetSlotWithoutPlayer(SPacketSetSlot packetIn, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (world == null || mc == null || mc.player == null) {
            ci.cancel();
        }
    }

    @Inject(method = "handlePlayerAbilities", at = @At("HEAD"), cancellable = true)
    private void ausm$ignorePlayerAbilitiesWithoutPlayer(SPacketPlayerAbilities packetIn, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (world == null || mc == null || mc.player == null) {
            ci.cancel();
        }
    }

    @Inject(method = "handlePlayerPosLook", at = @At("HEAD"), cancellable = true)
    private void ausm$ignorePlayerPosLookWithoutPlayer(SPacketPlayerPosLook packetIn, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (world == null || mc == null || mc.player == null) {
            ausm$hasPreTeleportPosition = false;
            ci.cancel();
            return;
        }
        ausm$preTeleportX = mc.player.posX;
        ausm$preTeleportY = mc.player.posY;
        ausm$preTeleportZ = mc.player.posZ;
        ausm$preTeleportDimension = world.provider != null ? world.provider.getDimension() : Integer.MIN_VALUE;
        ausm$hasPreTeleportPosition = true;
    }

    @Inject(method = "handlePlayerPosLook", at = @At("RETURN"))
    private void ausm$resyncTerrainAfterTeleport(SPacketPlayerPosLook packetIn, CallbackInfo ci) {
        if (!ausm$hasPreTeleportPosition) {
            return;
        }
        ausm$hasPreTeleportPosition = false;
        Minecraft mc = Minecraft.getMinecraft();
        if (world == null || mc == null || mc.player == null) {
            return;
        }
        double dx = mc.player.posX - ausm$preTeleportX;
        double dy = mc.player.posY - ausm$preTeleportY;
        double dz = mc.player.posZ - ausm$preTeleportZ;
        int currentDimension = world.provider != null ? world.provider.getDimension() : Integer.MIN_VALUE;
        PipelineContext.getInstance().handleClientTeleportResync(
                ausm$preTeleportDimension,
                currentDimension,
                dx * dx + dy * dy + dz * dz
        );
    }

    @Inject(method = "handleSetExperience", at = @At("HEAD"), cancellable = true)
    private void ausm$ignoreSetExperienceWithoutPlayer(SPacketSetExperience packetIn, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (world == null || mc == null || mc.player == null) {
            ci.cancel();
        }
    }

    @Inject(method = "handleUpdateHealth", at = @At("HEAD"), cancellable = true)
    private void ausm$ignoreUpdateHealthWithoutPlayer(SPacketUpdateHealth packetIn, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (world == null || mc == null || mc.player == null) {
            ci.cancel();
        }
    }

    @Inject(method = "handleEffect", at = @At("HEAD"), cancellable = true)
    private void ausm$ignoreEffectWithoutRenderViewEntity(SPacketEffect packetIn, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (world == null || mc == null || mc.getRenderViewEntity() == null) {
            ci.cancel();
        }
    }

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void ausm$ignoreCustomPayloadWithoutPlayer(SPacketCustomPayload packetIn, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (world == null || mc == null || mc.player == null) {
            ci.cancel();
        }
    }

    @Inject(method = "handleChunkData", at = @At("HEAD"), cancellable = true)
    private void ausm$dropMalformedChunkData(SPacketChunkData packetIn, CallbackInfo ci) {
        if (world == null || packetIn == null || !ausm$isChunkDataReadable(packetIn)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleChunkData", at = @At("RETURN"))
    private void ausm$queueShaderChunkRefresh(SPacketChunkData packetIn, CallbackInfo ci) {
        if (world != null && packetIn != null && packetIn.isFullChunk()) {
            PipelineContext.getInstance().queueShaderChunkRefresh(world, packetIn.getChunkX(), packetIn.getChunkZ());
            PipelineContext.getInstance().queueClientChunkRenderRefresh(
                    world,
                    packetIn.getChunkX(),
                    packetIn.getChunkZ(),
                    "chunk-data"
            );
        }
    }

    private boolean ausm$isChunkDataReadable(SPacketChunkData packetIn) {
        PacketBuffer buffer = null;
        int readerIndex = -1;
        try {
            buffer = packetIn.getReadBuffer();
            readerIndex = buffer.readerIndex();
            int sections = packetIn.getExtractedSize();
            boolean hasSkyLight = world.provider != null && world.provider.hasSkyLight();

            if ((sections & ~0xFFFF) != 0) {
                return false;
            }

            for (int section = 0; section < 16; section++) {
                if ((sections & (1 << section)) == 0) {
                    continue;
                }

                new BlockStateContainer().read(buffer);

                if (!ausm$skipChunkBytes(buffer, 2048)) {
                    return false;
                }

                if (hasSkyLight && !ausm$skipChunkBytes(buffer, 2048)) {
                    return false;
                }
            }

            if (packetIn.isFullChunk()) {
                int biomePayloadBytes = buffer.readableBytes();
                if (biomePayloadBytes == 256) {
                    return ausm$skipChunkBytes(buffer, 256);
                }

                return biomePayloadBytes > 0 && buffer.readVarIntArray(biomePayloadBytes).length == 256;
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

    private boolean ausm$skipChunkBytes(PacketBuffer buffer, int byteCount) {
        if (buffer.readableBytes() < byteCount) {
            return false;
        }

        buffer.skipBytes(byteCount);
        return true;
    }
}
