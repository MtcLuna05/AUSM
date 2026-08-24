package com.luna.ausm.impl.pipeline;

import net.minecraft.client.Minecraft;

abstract class PipelineWorldRenderScope extends PipelineFrameLifecycle2 {

    public abstract void observePresentationBeforeWorldRendering(Minecraft minecraft);

}
