package com.l.ausm.impl.pipeline;

import com.l.ausm.impl.mixin.pipeline.EntityRendererAccessor;
import com.l.ausm.impl.pipeline.render.TextureBinder;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * Restores and refreshes vanilla's lightmap using mapping-safe renderer access.
 */
final class PipelineVanillaLightmapState {
    private PipelineVanillaLightmapState() {
    }

    static void restore(Minecraft minecraft) {
        if (minecraft == null || MinecraftReflectionCompat.entityRenderer(minecraft) == null) {
            return;
        }
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        DynamicTexture lightmap = ((EntityRendererAccessor) MinecraftReflectionCompat.entityRenderer(minecraft)).ausm$getLightmapTexture();
        try {
            MinecraftReflectionCompat.glStateSetActiveTexture(MinecraftReflectionCompat.lightmapTexUnit());
            boolean enabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            int textureId = lightmap != null ? MinecraftReflectionCompat.glTextureId(lightmap) : 0;
            MinecraftReflectionCompat.glStateBindTexture(textureId);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            if (enabled) MinecraftReflectionCompat.glStateEnableTexture2D();
            else MinecraftReflectionCompat.glStateDisableTexture2D();
        } finally {
            GL13.glActiveTexture(previousActiveTexture);
            TextureBinder.restoreDefaultTextureUnit();
            MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
        }
    }

    static void refresh(Minecraft minecraft) {
        if (minecraft == null || MinecraftReflectionCompat.world(minecraft) == null
                || MinecraftReflectionCompat.player(minecraft) == null || MinecraftReflectionCompat.entityRenderer(minecraft) == null) {
            return;
        }
        EntityRendererAccessor accessor = (EntityRendererAccessor) MinecraftReflectionCompat.entityRenderer(minecraft);
        accessor.ausm$setLightmapUpdateNeeded(true);
        accessor.ausm$updateLightmap(MinecraftReflectionCompat.renderPartialTicks(minecraft));
        restore(minecraft);
    }

    static void disable(Minecraft minecraft) {
        if (minecraft == null || MinecraftReflectionCompat.entityRenderer(minecraft) == null) {
            return;
        }
        MinecraftReflectionCompat.disableLightmap(MinecraftReflectionCompat.entityRenderer(minecraft));
        TextureBinder.restoreDefaultTextureUnit();
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
    }
}
