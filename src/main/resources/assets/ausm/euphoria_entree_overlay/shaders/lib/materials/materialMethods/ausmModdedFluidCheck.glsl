#ifndef AUSM_MODDED_FLUIDS
    #define AUSM_MODDED_FLUIDS 1
    #define AUSM_MODDED_FLUID_WATERLIKE 1
    #define AUSM_MODDED_FLUID_EMISSIVE 1
#endif

bool ausmIsModdedColoredFluid = false;

#if AUSM_MODDED_FLUIDS == 1
    #if AUSM_MODDED_FLUID_WATERLIKE == 1
        ausmIsModdedColoredFluid = mat >= 32620 && mat <= 32639;
    #endif
    #if AUSM_MODDED_FLUID_EMISSIVE == 1
        ausmIsModdedColoredFluid = ausmIsModdedColoredFluid || (mat >= 32640 && mat <= 32645);
    #endif
#endif
