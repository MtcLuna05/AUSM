package com.luna.ausm.impl.proxy;

import com.luna.ausm.impl.client.EuphoriaEntreePackGenerator;
import com.luna.ausm.impl.client.KeybindManager;
import com.luna.ausm.impl.client.ThaumcraftParticleBridge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        KeybindManager.init();
        EuphoriaEntreePackGenerator.init();
        ThaumcraftParticleBridge.init();
    }
}
