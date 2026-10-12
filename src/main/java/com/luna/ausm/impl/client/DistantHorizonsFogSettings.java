package com.luna.ausm.impl.client;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;

/** Temporary API override: shaderpack fog owns the scene while AUSM is active. */
final class DistantHorizonsFogSettings {
    private static IDhApiConfigValue<Boolean> fog;
    private static Boolean previous;

    private DistantHorizonsFogSettings() { }

    static void update(boolean shadersActive) {
        if (!shadersActive) {
            release();
        } else if (fog == null && DhApi.Delayed.configs != null) {
            IDhApiConfigValue<Boolean> setting = DhApi.Delayed.configs.graphics().fog().enableDhFog();
            Boolean old = setting.getApiValue();
            if (setting.setValue(false, "AUSM shaderpack fog")) {
                fog = setting;
                previous = old;
            }
        }
    }

    static void release() {
        if (fog == null) return;
        if (Boolean.FALSE.equals(fog.getApiValue())) {
            fog.setValue(previous, "AUSM shaderpack fog");
        }
        fog = null;
        previous = null;
    }
}
