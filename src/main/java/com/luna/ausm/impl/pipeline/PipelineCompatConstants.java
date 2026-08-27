package com.luna.ausm.impl.pipeline;

import com.luna.ausm.impl.MainMod;
import net.minecraftforge.fml.common.Loader;

final class PipelineCompatConstants {
    static final String NOTHIRIUM_MOD_ID = "nothirium";
    static final String NAUGHTHIRIUM_MOD_ID = "naughthirium";
    private static volatile int nothiriumLoaded = -1;

    static boolean isNothiriumLoadedCached() {
        int cached = nothiriumLoaded;
        if (cached >= 0) {
            return cached != 0;
        }
        synchronized (PipelineCompatConstants.class) {
            cached = nothiriumLoaded;
            if (cached < 0) {
                boolean loaded = Loader.isModLoaded(NOTHIRIUM_MOD_ID)
                        || Loader.isModLoaded(NAUGHTHIRIUM_MOD_ID);
                cached = loaded ? 1 : 0;
                nothiriumLoaded = cached;
            }
        }
        return cached != 0;
    }

    static final String BLOCKCRAFTERY_TILE_EDITABLE_BLOCK_CLASS = "epicsquid.blockcraftery.tile.TileEditableBlock";
    static final String ARCHITECTURECRAFT_TILE_SHAPE_CLASS = "com.elytradev.architecture.common.tile.TileShape";
    static final String ARCHITECTURECRAFT_BLOCK_PACKAGE = "com.elytradev.architecture.common.block.";

    private PipelineCompatConstants() {
    }
}
