package com.luna.ausm.impl.util;

import com.google.common.util.concurrent.ListenableFuture;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.ChunkRenderContainer;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.BlockStateContainer;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

abstract class MinecraftGuiRenderingReflection extends MinecraftWorldEntityReflection {
    public static int fontStringWidth(FontRenderer fontRenderer, String text) {
        return MinecraftReflectionCompat.callInt(fontRenderer, new String[]{"func_78256_a", "getStringWidth"},
                new Class<?>[]{String.class}, 0, text);
    }

    public static float fontDrawStringWithShadow(FontRenderer fontRenderer, String text,
                                                 float x, float y, int color) {
        return MinecraftReflectionCompat.callFloat(fontRenderer, new String[]{"func_175063_a", "drawStringWithShadow"},
                new Class<?>[]{String.class, float.class, float.class, int.class},
                0.0F, text, x, y, color);
    }

    public static int fontDrawString(FontRenderer fontRenderer, String text, int x, int y, int color) {
        return MinecraftReflectionCompat.callInt(fontRenderer, new String[]{"func_78276_b", "drawString"},
                new Class<?>[]{String.class, int.class, int.class, int.class},
                0, text, x, y, color);
    }

    @SuppressWarnings("unchecked")
    public static List<String> fontListFormattedStringToWidth(FontRenderer fontRenderer, String text, int width) {
        Object value = MinecraftReflectionCompat.invoke(fontRenderer,
                new String[]{"func_78271_c", "listFormattedStringToWidth"},
                new Class<?>[]{String.class, int.class}, text, width);
        return value instanceof List<?> ? (List<String>) value : Collections.emptyList();
    }

    public static int scaledResolutionWidth(ScaledResolution resolution) {
        return MinecraftReflectionCompat.callInt(resolution, new String[]{"func_78326_a", "getScaledWidth"},
                NO_PARAMETERS, 0);
    }

    public static void guiDrawRect(int left, int top, int right, int bottom, int color) {
        MinecraftReflectionCompat.invoke(Gui.class, new String[]{"func_73734_a", "drawRect"},
                new Class<?>[]{int.class, int.class, int.class, int.class, int.class},
                left, top, right, bottom, color);
    }

    public static int guiButtonId(GuiButton button) {
        return MinecraftReflectionCompat.fieldInt(button, -1, "field_146127_k", "id");
    }

    public static int guiButtonX(GuiButton button) {
        return MinecraftReflectionCompat.fieldInt(button, 0, "field_146128_h", "x");
    }

    public static int guiButtonY(GuiButton button) {
        return MinecraftReflectionCompat.fieldInt(button, 0, "field_146129_i", "y");
    }

    public static int guiButtonWidth(GuiButton button) {
        return MinecraftReflectionCompat.fieldInt(button, 0, "field_146120_f", "width");
    }

    public static void setGuiButtonX(GuiButton button, int x) {
        MinecraftReflectionCompat.setField(button, x, "field_146128_h", "x");
    }

    public static void setGuiButtonWidth(GuiButton button, int width) {
        MinecraftReflectionCompat.setField(button, width, "field_146120_f", "width");
    }

    public static int guiButtonHeight(GuiButton button) {
        return MinecraftReflectionCompat.fieldInt(button, 0, "field_146121_g", "height");
    }

    public static boolean guiButtonEnabled(GuiButton button) {
        return MinecraftReflectionCompat.fieldBoolean(button, false, "field_146124_l", "enabled");
    }

    public static void setGuiButtonEnabled(GuiButton button, boolean enabled) {
        MinecraftReflectionCompat.setField(button, enabled, "field_146124_l", "enabled");
    }

    public static boolean guiButtonVisible(GuiButton button) {
        return MinecraftReflectionCompat.fieldBoolean(button, false, "field_146125_m", "visible");
    }

    public static void setGuiButtonVisible(GuiButton button, boolean visible) {
        MinecraftReflectionCompat.setField(button, visible, "field_146125_m", "visible");
    }

    public static String guiButtonText(GuiButton button) {
        return MinecraftReflectionCompat.field(button, String.class, "", "field_146126_j", "displayString");
    }

    public static void setGuiButtonText(GuiButton button, String text) {
        MinecraftReflectionCompat.setField(button, text, "field_146126_j", "displayString");
    }

    public static boolean guiButtonMousePressed(GuiButton button, Minecraft minecraft, int mouseX, int mouseY) {
        if (!MinecraftReflectionCompat.guiButtonEnabled(button) || !MinecraftReflectionCompat.guiButtonVisible(button)) {
            return false;
        }
        int x = MinecraftReflectionCompat.guiButtonX(button);
        int y = MinecraftReflectionCompat.guiButtonY(button);
        return mouseX >= x && mouseY >= y
                && mouseX < x + MinecraftReflectionCompat.guiButtonWidth(button)
                && mouseY < y + MinecraftReflectionCompat.guiButtonHeight(button);
    }

    public static int guiTextFieldX(GuiTextField field) {
        return MinecraftReflectionCompat.fieldInt(field, 0, "field_146209_f", "x");
    }

    public static int guiTextFieldY(GuiTextField field) {
        return MinecraftReflectionCompat.fieldInt(field, 0, "field_146210_g", "y");
    }

    public static int guiTextFieldWidth(GuiTextField field) {
        return MinecraftReflectionCompat.fieldInt(field, 0, "field_146218_h", "width");
    }

    public static int guiTextFieldHeight(GuiTextField field) {
        return MinecraftReflectionCompat.fieldInt(field, 0, "field_146219_i", "height");
    }

    public static String guiTextFieldText(GuiTextField field) {
        return MinecraftReflectionCompat.call(field, String.class, "", new String[]{"func_146179_b", "getText"}, NO_PARAMETERS);
    }

    public static boolean guiTextFieldFocused(GuiTextField field) {
        return MinecraftReflectionCompat.callBoolean(field, new String[]{"func_146206_l", "isFocused"}, NO_PARAMETERS, false);
    }

    public static void setGuiTextFieldFocused(GuiTextField field, boolean focused) {
        MinecraftReflectionCompat.invoke(field, new String[]{"func_146195_b", "setFocused"}, new Class<?>[]{boolean.class}, focused);
    }

    public static void setGuiTextFieldText(GuiTextField field, String text) {
        MinecraftReflectionCompat.invoke(field, new String[]{"func_146180_a", "setText"}, new Class<?>[]{String.class}, text);
    }

    public static void setGuiTextFieldMaxLength(GuiTextField field, int length) {
        MinecraftReflectionCompat.invoke(field, new String[]{"func_146203_f", "setMaxStringLength"}, new Class<?>[]{int.class}, length);
    }

    public static void setGuiTextFieldBackground(GuiTextField field, boolean enabled) {
        MinecraftReflectionCompat.invoke(field, new String[]{"func_146185_a", "setEnableBackgroundDrawing"},
                new Class<?>[]{boolean.class}, enabled);
    }

    public static boolean guiTextFieldKeyTyped(GuiTextField field, char typedChar, int keyCode) {
        return MinecraftReflectionCompat.callBoolean(field, new String[]{"func_146201_a", "textboxKeyTyped"},
                new Class<?>[]{char.class, int.class}, false, typedChar, keyCode);
    }

    public static boolean guiTextFieldMouseClicked(GuiTextField field, int mouseX, int mouseY, int mouseButton) {
        return MinecraftReflectionCompat.callBoolean(field, new String[]{"func_146192_a", "mouseClicked"},
                new Class<?>[]{int.class, int.class, int.class}, false, mouseX, mouseY, mouseButton);
    }

    public static void drawGuiTextField(GuiTextField field) {
        MinecraftReflectionCompat.invoke(field, new String[]{"func_146194_f", "drawTextBox"}, NO_PARAMETERS);
    }

    public static long minecraftSystemTime() {
        Object value = MinecraftReflectionCompat.invokeStatic(Minecraft.class, new String[]{"func_71386_F", "getSystemTime"}, NO_PARAMETERS);
        return MinecraftReflectionCompat.longValue(value, System.currentTimeMillis());
    }

    public static String i18nFormat(String key, Object... parameters) {
        Object value = MinecraftReflectionCompat.invokeStatic(I18n.class, new String[]{"func_135052_a", "format"},
                new Class<?>[]{String.class, Object[].class}, key, parameters);
        return value instanceof String ? (String) value : key;
    }

    public static int guiScreenWidth(GuiScreen screen) {
        return MinecraftReflectionCompat.fieldInt(screen, 0, "field_146294_l", "width");
    }

    public static Minecraft guiScreenMinecraft(GuiScreen screen) {
        return MinecraftReflectionCompat.field(screen, Minecraft.class, null, "field_146297_k", "mc");
    }

    @SuppressWarnings("unchecked")
    public static List<GuiButton> guiScreenButtons(GuiScreen screen) {
        return MinecraftReflectionCompat.field(screen, List.class, Collections.emptyList(), "field_146292_n", "buttonList");
    }

    public static BlockPos rayTraceBlockPos(RayTraceResult hit) {
        Object value = MinecraftReflectionCompat.getField(hit, "field_178783_e", "blockPos");
        if (value instanceof BlockPos) {
            return (BlockPos) value;
        }
        value = MinecraftReflectionCompat.invoke(hit, new String[]{"func_178782_a", "getBlockPos"}, NO_PARAMETERS);
        return value instanceof BlockPos ? (BlockPos) value : null;
    }

    public static int displayWidth(Minecraft minecraft) {
        return MinecraftReflectionCompat.fieldInt(minecraft, 1, "field_71443_c", "displayWidth");
    }

    public static int displayHeight(Minecraft minecraft) {
        return MinecraftReflectionCompat.fieldInt(minecraft, 1, "field_71440_d", "displayHeight");
    }

    public static EntityRenderer entityRenderer(Minecraft minecraft) {
        return MinecraftReflectionCompat.field(minecraft, EntityRenderer.class, null, "field_71460_t", "entityRenderer");
    }

    public static RenderManager renderManager(Minecraft minecraft) {
        return MinecraftReflectionCompat.call(minecraft, RenderManager.class, null, new String[]{"func_175598_ae", "getRenderManager"}, NO_PARAMETERS);
    }

    public static Entity renderViewEntity(Minecraft minecraft) {
        return MinecraftReflectionCompat.call(minecraft, Entity.class, null, new String[]{"func_175606_aa", "getRenderViewEntity"}, NO_PARAMETERS);
    }

    public static float renderPartialTicks(Minecraft minecraft) {
        return MinecraftReflectionCompat.callFloat(minecraft, RENDER_PARTIAL_TICKS_NAMES, NO_PARAMETERS, 0.0F);
    }

    @SuppressWarnings("unchecked")
    public static ListenableFuture<Object> addScheduledTask(Minecraft minecraft, Runnable task) {
        Object value = MinecraftReflectionCompat.invoke(minecraft, new String[]{"func_152344_a", "addScheduledTask"}, new Class<?>[]{Runnable.class}, task);
        return value instanceof ListenableFuture<?> ? (ListenableFuture<Object>) value : null;
    }

    public static void displayGuiScreen(Minecraft minecraft, GuiScreen screen) {
        MinecraftReflectionCompat.invoke(minecraft, new String[]{"func_147108_a", "displayGuiScreen"}, new Class<?>[]{GuiScreen.class}, screen);
    }

    public static void renderGameOverlay(GuiIngame guiIngame, float partialTicks) {
        MinecraftReflectionCompat.invokePropagating(guiIngame, new String[]{"func_175180_a", "renderGameOverlay"},
                new Class<?>[]{float.class}, partialTicks);
    }

    public static boolean isGamePaused(Minecraft minecraft) {
        return MinecraftReflectionCompat.booleanValue(MinecraftReflectionCompat.invoke(minecraft, new String[]{"func_147113_T", "isGamePaused"}, NO_PARAMETERS));
    }

    public static boolean guiScreenDoesPauseGame(GuiScreen screen) {
        return MinecraftReflectionCompat.callBoolean(screen, new String[]{"func_73868_f", "doesGuiPauseGame"}, NO_PARAMETERS, false);
    }

    public static TextureManager textureManager(Minecraft minecraft) {
        Object value = MinecraftReflectionCompat.invoke(minecraft, new String[]{"func_110434_K", "getTextureManager"}, NO_PARAMETERS);
        if (value instanceof TextureManager) {
            return (TextureManager) value;
        }
        value = MinecraftReflectionCompat.getField(minecraft, "field_71446_o", "renderEngine");
        return value instanceof TextureManager ? (TextureManager) value : null;
    }

    public static Framebuffer minecraftFramebuffer(Minecraft minecraft) {
        Object value = MinecraftReflectionCompat.invoke(minecraft, new String[]{"func_147110_a", "getFramebuffer"}, NO_PARAMETERS);
        return value instanceof Framebuffer ? (Framebuffer) value : null;
    }

    public static void bindFramebuffer(Framebuffer framebuffer, boolean setViewport) {
        MinecraftReflectionCompat.invoke(framebuffer, new String[]{"func_147610_a", "bindFramebuffer"}, new Class<?>[]{boolean.class}, setViewport);
    }

    public static void renderSky(RenderGlobal renderGlobal, float partialTicks, int pass) {
        MinecraftReflectionCompat.invokePropagating(renderGlobal, new String[]{"func_174976_a", "renderSky"},
                new Class<?>[]{float.class, int.class}, partialTicks, pass);
    }

    public static int renderBlockLayer(RenderGlobal renderGlobal, BlockRenderLayer layer, double partialTicks, int pass,
                                       Entity renderViewEntity) {
        Object value = MinecraftReflectionCompat.invokePropagating(renderGlobal, new String[]{"func_174977_a", "renderBlockLayer"},
                new Class<?>[]{BlockRenderLayer.class, double.class, int.class, Entity.class},
                layer, partialTicks, pass, renderViewEntity);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    public static void renderEntities(RenderGlobal renderGlobal, Entity renderViewEntity, ICamera camera, float partialTicks) {
        MinecraftReflectionCompat.invokePropagating(renderGlobal, new String[]{"func_180446_a", "renderEntities"},
                new Class<?>[]{Entity.class, ICamera.class, float.class}, renderViewEntity, camera, partialTicks);
    }

    public static void initializeChunkRenderContainer(ChunkRenderContainer container,
                                                      double cameraX, double cameraY, double cameraZ) {
        MinecraftReflectionCompat.invoke(container, new String[]{"func_178004_a", "initialize"},
                new Class<?>[]{double.class, double.class, double.class}, cameraX, cameraY, cameraZ);
    }

    public static void addChunkRenderContainerChunk(ChunkRenderContainer container,
                                                    RenderChunk renderChunk, BlockRenderLayer layer) {
        MinecraftReflectionCompat.invoke(container, new String[]{"func_178002_a", "addRenderChunk"},
                new Class<?>[]{RenderChunk.class, BlockRenderLayer.class}, renderChunk, layer);
    }

    public static void renderChunkContainerLayer(ChunkRenderContainer container, BlockRenderLayer layer) {
        MinecraftReflectionCompat.invoke(container, new String[]{"func_178001_a", "renderChunkLayer"},
                new Class<?>[]{BlockRenderLayer.class}, layer);
    }

    public static VertexBuffer renderChunkVertexBuffer(RenderChunk renderChunk, int layer) {
        return MinecraftReflectionCompat.call(renderChunk, VertexBuffer.class, null,
                new String[]{"func_178565_b", "getVertexBufferByLayer"}, new Class<?>[]{int.class}, layer);
    }

    public static VertexFormat vertexBufferFormat(VertexBuffer vertexBuffer) {
        return MinecraftReflectionCompat.field(vertexBuffer, VertexFormat.class, null,
                "field_177363_b", "vertexFormat");
    }

    public static void renderEntity(Render<?> renderer, Entity entity, double x, double y, double z,
                                    float yaw, float partialTicks) {
        MinecraftReflectionCompat.invoke(renderer, new String[]{"func_76986_a", "doRender"},
                new Class<?>[]{Entity.class, double.class, double.class, double.class, float.class, float.class},
                entity, x, y, z, yaw, partialTicks);
    }

    public static void vertexBufferDrawArrays(VertexBuffer vertexBuffer, int mode) {
        MinecraftReflectionCompat.invoke(vertexBuffer, new String[]{"func_177358_a", "drawArrays"}, new Class<?>[]{int.class}, mode);
    }

    public static boolean entityLivingIsPlayerSleeping(EntityLivingBase entity) {
        return MinecraftReflectionCompat.callBoolean(entity, new String[]{"func_70608_bn", "isPlayerSleeping"}, NO_PARAMETERS, false);
    }

    public static WorldServer playerServerWorld(EntityPlayerMP player) {
        return MinecraftReflectionCompat.call(player, WorldServer.class, null,
                new String[]{"func_71121_q", "getServerWorld"}, NO_PARAMETERS);
    }

    public static String entityName(Entity entity) {
        return MinecraftReflectionCompat.call(entity, String.class, "unknown", new String[]{"func_70005_c_", "getName"}, NO_PARAMETERS);
    }

    public static void setupTerrain(RenderGlobal renderGlobal, Entity renderViewEntity, double partialTicks,
                                    ICamera camera, int frameCount, boolean playerSpectator) {
        MinecraftReflectionCompat.invokePropagating(renderGlobal, new String[]{"func_174970_a", "setupTerrain"},
                new Class<?>[]{Entity.class, double.class, ICamera.class, int.class, boolean.class},
                renderViewEntity, partialTicks, camera, frameCount, playerSpectator);
    }

    public static void updateChunks(RenderGlobal renderGlobal, long finishTimeNano) {
        MinecraftReflectionCompat.invokePropagating(renderGlobal, new String[]{"func_174967_a", "updateChunks"},
                new Class<?>[]{long.class}, finishTimeNano);
    }

    public static void loadRenderers(RenderGlobal renderGlobal) {
        MinecraftReflectionCompat.invoke(renderGlobal, new String[]{"func_72712_a", "loadRenderers"}, NO_PARAMETERS);
    }

    public static void drawBlockDamageTexture(RenderGlobal renderGlobal, Tessellator tessellator,
                                              BufferBuilder bufferBuilder, Entity entity, float partialTicks) {
        MinecraftReflectionCompat.invokePropagating(renderGlobal, new String[]{"func_174981_a", "drawBlockDamageTexture"},
                new Class<?>[]{Tessellator.class, BufferBuilder.class, Entity.class, float.class},
                tessellator, bufferBuilder, entity, partialTicks);
    }

    public static void drawSelectionBox(RenderGlobal renderGlobal, EntityPlayer player,
                                        RayTraceResult target, int execute, float partialTicks) {
        MinecraftReflectionCompat.invokePropagating(renderGlobal, new String[]{"func_72731_b", "drawSelectionBox"},
                new Class<?>[]{EntityPlayer.class, RayTraceResult.class, int.class, float.class},
                player, target, execute, partialTicks);
    }

    public static RenderChunk[] viewFrustumRenderChunks(ViewFrustum viewFrustum) {
        Object value = MinecraftReflectionCompat.getField(viewFrustum, "field_178164_f", "renderChunks");
        return value instanceof RenderChunk[] ? (RenderChunk[]) value : null;
    }

    public static void deleteViewFrustumGlResources(ViewFrustum viewFrustum) {
        MinecraftReflectionCompat.invoke(viewFrustum, new String[]{"func_178160_a", "deleteGlResources"}, NO_PARAMETERS);
    }

    public static void renderLitParticles(ParticleManager particleManager, Entity entity, float partialTicks) {
        MinecraftReflectionCompat.invokePropagating(particleManager, new String[]{"func_78872_b", "renderLitParticles"},
                new Class<?>[]{Entity.class, float.class}, entity, partialTicks);
    }

    public static void renderParticles(ParticleManager particleManager, Entity entity, float partialTicks) {
        MinecraftReflectionCompat.invokePropagating(particleManager, new String[]{"func_78874_a", "renderParticles"},
                new Class<?>[]{Entity.class, float.class}, entity, partialTicks);
    }

    public static int framebufferObject(Framebuffer framebuffer) {
        return MinecraftReflectionCompat.framebufferInt(framebuffer, "field_147616_f", "framebufferObject");
    }

    public static int framebufferTexture(Framebuffer framebuffer) {
        return MinecraftReflectionCompat.framebufferInt(framebuffer, "field_147617_g", "framebufferTexture");
    }

    public static int framebufferWidth(Framebuffer framebuffer) {
        return MinecraftReflectionCompat.framebufferInt(framebuffer, "field_147621_c", "framebufferWidth");
    }

    public static int framebufferHeight(Framebuffer framebuffer) {
        return MinecraftReflectionCompat.framebufferInt(framebuffer, "field_147618_d", "framebufferHeight");
    }

    public static void deleteFramebuffer(Framebuffer framebuffer) {
        MinecraftReflectionCompat.invoke(framebuffer, new String[]{"func_147608_a", "deleteFramebuffer"}, NO_PARAMETERS);
    }

    public static WorldClient netHandlerWorld(NetHandlerPlayClient handler) {
        Object value = MinecraftReflectionCompat.getField(handler, "field_147300_g", "world", "clientWorldController");
        return value instanceof WorldClient ? (WorldClient) value : null;
    }

    public static boolean blockStateContainerRead(BlockStateContainer container, PacketBuffer buffer) {
        if (container == null || buffer == null) {
            return false;
        }
        for (String name : new String[]{"func_186010_a", "read"}) {
            try {
                Method method = container.getClass().getMethod(name, PacketBuffer.class);
                method.setAccessible(true);
                method.invoke(container, buffer);
                return true;
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                if (cause instanceof Error) {
                    throw (Error) cause;
                }
                return false;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return false;
    }

    public static boolean readChunkBlockStateContainer(PacketBuffer buffer) {
        return MinecraftReflectionCompat.blockStateContainerRead(new BlockStateContainer(), buffer);
    }

    public static int[] dynamicTextureData(DynamicTexture texture) {
        Object value = MinecraftReflectionCompat.invoke(texture, new String[]{"func_110565_c", "getTextureData"}, NO_PARAMETERS);
        return value instanceof int[] ? (int[]) value : new int[0];
    }

    public static void bindTexture(TextureManager textureManager, ResourceLocation location) {
        MinecraftReflectionCompat.invoke(textureManager, new String[]{"func_110577_a", "bindTexture"}, new Class<?>[]{ResourceLocation.class}, location);
    }

    @SuppressWarnings("unchecked")
    public static Render<Entity> entityRenderObject(RenderManager renderManager, Entity entity) {
        Object value = MinecraftReflectionCompat.invoke(renderManager, new String[]{"func_78713_a", "getEntityRenderObject"},
                new Class<?>[]{Entity.class}, entity);
        return value instanceof Render<?> ? (Render<Entity>) value : null;
    }

    public static void renderManagerCacheActiveRenderInfo(RenderManager renderManager, World world,
                                                          FontRenderer fontRenderer, Entity viewEntity,
                                                          Entity pointedEntity, GameSettings gameSettings,
                                                          float partialTicks) {
        MinecraftReflectionCompat.invoke(renderManager, new String[]{"func_180597_a", "cacheActiveRenderInfo"},
                new Class<?>[]{World.class, FontRenderer.class, Entity.class, Entity.class, GameSettings.class, float.class},
                world, fontRenderer, viewEntity, pointedEntity, gameSettings, partialTicks);
    }

    public static void renderManagerSetRenderPosition(RenderManager renderManager, double x, double y, double z) {
        MinecraftReflectionCompat.invoke(renderManager, new String[]{"func_78725_b", "setRenderPosition"},
                new Class<?>[]{double.class, double.class, double.class}, x, y, z);
    }

    public static boolean renderManagerShouldRender(RenderManager renderManager, Entity entity, ICamera camera,
                                                    double cameraX, double cameraY, double cameraZ) {
        return MinecraftReflectionCompat.callBoolean(renderManager, new String[]{"func_178635_a", "shouldRender"},
                new Class<?>[]{Entity.class, ICamera.class, double.class, double.class, double.class},
                true, entity, camera, cameraX, cameraY, cameraZ);
    }

    public static void renderManagerRenderEntityStatic(RenderManager renderManager, Entity entity,
                                                       float partialTicks, boolean debugBoundingBox) {
        MinecraftReflectionCompat.invoke(renderManager, new String[]{"func_188391_a", "renderEntityStatic"},
                new Class<?>[]{Entity.class, float.class, boolean.class}, entity, partialTicks, debugBoundingBox);
    }

    public static boolean renderManagerIsRenderMultipass(RenderManager renderManager, Entity entity) {
        return MinecraftReflectionCompat.callBoolean(renderManager, new String[]{"func_178627_a", "isRenderMultipass"},
                new Class<?>[]{Entity.class}, false, entity);
    }

    public static void renderManagerRenderMultipass(RenderManager renderManager, Entity entity, float partialTicks) {
        MinecraftReflectionCompat.invoke(renderManager, new String[]{"func_188389_a", "renderMultipass"},
                new Class<?>[]{Entity.class, float.class}, entity, partialTicks);
    }

    public static boolean entityIsRidingOrBeingRiddenBy(Entity entity, Entity other) {
        return MinecraftReflectionCompat.callBoolean(entity, new String[]{"func_184223_x", "isRidingOrBeingRiddenBy"},
                new Class<?>[]{Entity.class}, false, other);
    }

    public static boolean entityIsInRangeToRender3d(Entity entity, double x, double y, double z) {
        return MinecraftReflectionCompat.callBoolean(entity, new String[]{"func_70112_a", "isInRangeToRender3d"},
                new Class<?>[]{double.class, double.class, double.class}, true, x, y, z);
    }

    public static void enableStandardItemLighting() {
        MinecraftReflectionCompat.invoke(RenderHelper.class,
                new String[]{"func_74519_b", "enableStandardItemLighting"}, NO_PARAMETERS);
    }

    public static void disableStandardItemLighting() {
        MinecraftReflectionCompat.invoke(RenderHelper.class,
                new String[]{"func_74518_a", "disableStandardItemLighting"}, NO_PARAMETERS);
    }

    public static void enableGuiStandardItemLighting() {
        MinecraftReflectionCompat.invoke(RenderHelper.class,
                new String[]{"func_74520_c", "enableGUIStandardItemLighting"}, NO_PARAMETERS);
    }

    public static TileEntityRendererDispatcher tileEntityRendererDispatcher() {
        Object value = MinecraftReflectionCompat.getStaticField(TileEntityRendererDispatcher.class,
                "field_147556_a", "instance");
        return value instanceof TileEntityRendererDispatcher
                ? (TileEntityRendererDispatcher) value
                : null;
    }

    public static void tileEntityRendererPrepare(TileEntityRendererDispatcher dispatcher,
                                                 World world, TextureManager textureManager, FontRenderer fontRenderer,
                                                 Entity viewEntity, RayTraceResult hit, float partialTicks) {
        MinecraftReflectionCompat.invoke(dispatcher, new String[]{"func_190056_a", "prepare"},
                new Class<?>[]{World.class, TextureManager.class, FontRenderer.class, Entity.class, RayTraceResult.class, float.class},
                world, textureManager, fontRenderer, viewEntity, hit, partialTicks);
    }

    public static void tileEntityRendererRender(TileEntityRendererDispatcher dispatcher,
                                                TileEntity tileEntity, double x, double y, double z,
                                                float partialTicks, int destroyStage, float alpha) {
        MinecraftReflectionCompat.invoke(dispatcher, TILE_ENTITY_RENDER_NAMES, TILE_ENTITY_RENDER_PARAMETERS,
                tileEntity, x, y, z, partialTicks, destroyStage, alpha);
    }

    public static boolean cameraIsBoundingBoxInFrustum(ICamera camera, AxisAlignedBB box) {
        return MinecraftReflectionCompat.callBoolean(camera, CAMERA_FRUSTUM_NAMES, AXIS_ALIGNED_BB_PARAMETERS, true, box);
    }

    public static ITextureObject texture(TextureManager textureManager, ResourceLocation location) {
        Object value = MinecraftReflectionCompat.invoke(textureManager, new String[]{"func_110581_b", "getTexture"}, new Class<?>[]{ResourceLocation.class}, location);
        return value instanceof ITextureObject ? (ITextureObject) value : null;
    }

    public static int glTextureId(ITextureObject texture) {
        return MinecraftReflectionCompat.intValue(MinecraftReflectionCompat.invoke(texture, new String[]{"func_110552_b", "getGlTextureId"}, NO_PARAMETERS), -1);
    }

    public static void enableLightmap(EntityRenderer entityRenderer) {
        MinecraftReflectionCompat.invoke(entityRenderer, new String[]{"func_180436_i", "enableLightmap"}, NO_PARAMETERS);
    }

    public static void disableLightmap(EntityRenderer entityRenderer) {
        MinecraftReflectionCompat.invoke(entityRenderer, new String[]{"func_175072_h", "disableLightmap"}, NO_PARAMETERS);
    }

    public static void glUseProgram(int program) {
        MinecraftReflectionCompat.invoke(OpenGlHelper.class, new String[]{"func_153161_d", "glUseProgram"}, new Class<?>[]{int.class}, program);
    }

    public static int glFramebuffer() {
        return MinecraftReflectionCompat.openGlHelperInt("field_153198_e", "GL_FRAMEBUFFER", GL30.GL_FRAMEBUFFER);
    }

    public static int glDepthAttachment() {
        return MinecraftReflectionCompat.openGlHelperInt("field_153201_h", "GL_DEPTH_ATTACHMENT", GL30.GL_DEPTH_ATTACHMENT);
    }

    public static int glVertexShader() {
        return MinecraftReflectionCompat.openGlHelperInt("field_153209_q", "GL_VERTEX_SHADER", GL20.GL_VERTEX_SHADER);
    }

    public static boolean isFramebufferEnabled() {
        Object value = MinecraftReflectionCompat.invoke(OpenGlHelper.class, new String[]{"func_148822_b", "isFramebufferEnabled"}, NO_PARAMETERS);
        return value instanceof Boolean ? (Boolean) value : true;
    }

    public static int glCreateProgram() {
        Object value = MinecraftReflectionCompat.invoke(OpenGlHelper.class, new String[]{"func_153183_d", "glCreateProgram"}, NO_PARAMETERS);
        return value instanceof Number ? ((Number) value).intValue() : GL20.glCreateProgram();
    }

    public static void glDeleteProgram(int program) {
        MinecraftReflectionCompat.invoke(OpenGlHelper.class, new String[]{"func_153187_e", "glDeleteProgram"}, new Class<?>[]{int.class}, program);
    }

    public static void glAttachShader(int program, int shader) {
        MinecraftReflectionCompat.invoke(OpenGlHelper.class, new String[]{"func_153178_b", "glAttachShader"},
                new Class<?>[]{int.class, int.class}, program, shader);
    }

    public static void glLinkProgram(int program) {
        MinecraftReflectionCompat.invoke(OpenGlHelper.class, new String[]{"func_153179_f", "glLinkProgram"}, new Class<?>[]{int.class}, program);
    }

    public static int glGetProgrami(int program, int pname) {
        Object value = MinecraftReflectionCompat.invoke(OpenGlHelper.class, new String[]{"func_153175_a", "glGetProgrami"},
                new Class<?>[]{int.class, int.class}, program, pname);
        return value instanceof Number ? ((Number) value).intValue() : GL20.glGetProgrami(program, pname);
    }

    public static String glGetProgramInfoLog(int program, int maxLength) {
        Object value = MinecraftReflectionCompat.invoke(OpenGlHelper.class, new String[]{"func_153166_e", "glGetProgramInfoLog"},
                new Class<?>[]{int.class, int.class}, program, maxLength);
        return value instanceof String ? (String) value : GL20.glGetProgramInfoLog(program, maxLength);
    }

    public static int glGetUniformLocation(int program, CharSequence name) {
        Object value = MinecraftReflectionCompat.invoke(OpenGlHelper.class, new String[]{"func_153194_a", "glGetUniformLocation"},
                new Class<?>[]{int.class, CharSequence.class}, program, name);
        return value instanceof Number ? ((Number) value).intValue() : GL20.glGetUniformLocation(program, name);
    }

    public static int glCreateShader(int shaderType) {
        Object value = MinecraftReflectionCompat.invoke(OpenGlHelper.class, new String[]{"func_153195_b", "glCreateShader"},
                new Class<?>[]{int.class}, shaderType);
        return value instanceof Number ? ((Number) value).intValue() : GL20.glCreateShader(shaderType);
    }

    public static void glCompileShader(int shader) {
        MinecraftReflectionCompat.invoke(OpenGlHelper.class, new String[]{"func_153170_c", "glCompileShader"}, new Class<?>[]{int.class}, shader);
    }

    public static int glGetShaderi(int shader, int pname) {
        Object value = MinecraftReflectionCompat.invoke(OpenGlHelper.class, new String[]{"func_153157_c", "glGetShaderi"},
                new Class<?>[]{int.class, int.class}, shader, pname);
        return value instanceof Number ? ((Number) value).intValue() : GL20.glGetShaderi(shader, pname);
    }

    public static String glGetShaderInfoLog(int shader, int maxLength) {
        Object value = MinecraftReflectionCompat.invoke(OpenGlHelper.class, new String[]{"func_153158_d", "glGetShaderInfoLog"},
                new Class<?>[]{int.class, int.class}, shader, maxLength);
        return value instanceof String ? (String) value : GL20.glGetShaderInfoLog(shader, maxLength);
    }

    public static void glDeleteShader(int shader) {
        MinecraftReflectionCompat.invoke(OpenGlHelper.class, new String[]{"func_153180_a", "glDeleteShader"}, new Class<?>[]{int.class}, shader);
    }

    public static int glGenFramebuffers() {
        Object value = MinecraftReflectionCompat.invoke(OpenGlHelper.class, new String[]{"func_153165_e", "glGenFramebuffers"}, NO_PARAMETERS);
        return value instanceof Number ? ((Number) value).intValue() : GL30.glGenFramebuffers();
    }

    public static void glBindFramebuffer(int target, int framebuffer) {
        MinecraftReflectionCompat.invoke(OpenGlHelper.class, new String[]{"func_153171_g", "glBindFramebuffer"},
                new Class<?>[]{int.class, int.class}, target, framebuffer);
    }

    public static void glFramebufferTexture2D(int target, int attachment, int texTarget, int texture, int level) {
        MinecraftReflectionCompat.invoke(OpenGlHelper.class, new String[]{"func_153188_a", "glFramebufferTexture2D"},
                new Class<?>[]{int.class, int.class, int.class, int.class, int.class},
                target, attachment, texTarget, texture, level);
    }

    public static int glCheckFramebufferStatus(int target) {
        Object value = MinecraftReflectionCompat.invoke(OpenGlHelper.class, new String[]{"func_153167_i", "glCheckFramebufferStatus"},
                new Class<?>[]{int.class}, target);
        return value instanceof Number ? ((Number) value).intValue() : GL30.glCheckFramebufferStatus(target);
    }

    public static void glDeleteFramebuffers(int framebuffer) {
        MinecraftReflectionCompat.invoke(OpenGlHelper.class, new String[]{"func_153174_h", "glDeleteFramebuffers"}, new Class<?>[]{int.class}, framebuffer);
    }

    public static void glBindBuffer(int target, int buffer) {
        MinecraftReflectionCompat.invoke(OpenGlHelper.class, new String[]{"func_176072_g", "glBindBuffer"},
                new Class<?>[]{int.class, int.class}, target, buffer);
    }

    public static void glUniform1i(int location, int value) {
        MinecraftReflectionCompat.invoke(OpenGlHelper.class, GL_UNIFORM_1I_NAMES, INT_INT_PARAMETERS, location, value);
    }

    public static void glUniformMatrix4(int location, boolean transpose, FloatBuffer value) {
        MinecraftReflectionCompat.invoke(OpenGlHelper.class, new String[]{"func_153160_c", "glUniformMatrix4"},
                new Class<?>[]{int.class, boolean.class, FloatBuffer.class}, location, transpose, value);
    }

    public static int defaultTexUnit() {
        return DEFAULT_TEX_UNIT;
    }

    public static int lightmapTexUnit() {
        return LIGHTMAP_TEX_UNIT;
    }

    public static void setActiveTexture(int textureUnit) {
        MinecraftReflectionCompat.invoke(OpenGlHelper.class, new String[]{"func_77473_a", "setActiveTexture"}, new Class<?>[]{int.class}, textureUnit);
    }

    public static void glStateSetActiveTexture(int textureUnit) {
        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179138_g", "setActiveTexture"}, new Class<?>[]{int.class}, textureUnit);
    }

    public static void glStateEnableTexture2D() {
        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179098_w", "enableTexture2D"}, NO_PARAMETERS);
    }
}
