package com.l.ausm.impl.proxy;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.client.KeybindManager;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

public class ClientProxy extends CommonProxy{

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        KeybindManager.init();
    }
}
