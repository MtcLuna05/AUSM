package com.l.ausm.impl.proxy;

import com.l.ausm.impl.client.KeybindManager;
import com.l.ausm.impl.client.EuphoriaEntreePackGenerator;
import com.l.ausm.impl.client.ThaumcraftParticleBridge;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
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
