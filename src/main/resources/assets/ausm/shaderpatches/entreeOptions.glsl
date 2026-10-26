#ifndef AUSM_ENTREE_OPTIONS
#define AUSM_ENTREE_OPTIONS

#ifndef AUSM_VOID_CELESTIALS
    #define AUSM_VOID_CELESTIALS 1 //[0 1]
#endif
#ifndef AUSM_VOID_STARS
    #define AUSM_VOID_STARS 1 //[0 1]
#endif
#ifndef AUSM_VOID_ASTRAL_STARS
    #define AUSM_VOID_ASTRAL_STARS 1 //[0 1]
#endif
#ifndef AUSM_VOID_NEBULA
    #define AUSM_VOID_NEBULA 0 //[0 1]
#endif
#ifndef AUSM_VOID_CELESTIAL_BRIGHTNESS
    #define AUSM_VOID_CELESTIAL_BRIGHTNESS 100 //[0 25 50 75 100 150 200 300]
#endif
#ifndef AUSM_VOID_STAR_BRIGHTNESS
    #define AUSM_VOID_STAR_BRIGHTNESS 100 //[0 25 50 75 100 150 200 300]
#endif
#ifndef AUSM_VOID_ASTRAL_STAR_BRIGHTNESS
    #define AUSM_VOID_ASTRAL_STAR_BRIGHTNESS 100 //[0 25 50 75 100 150 200 300]
#endif
#ifndef AUSM_VOID_NEBULA_BRIGHTNESS
    #define AUSM_VOID_NEBULA_BRIGHTNESS 100 //[0 25 50 75 100 150 200 300]
#endif
#ifndef AUSM_ABYSSAL_SKYBOX
    #define AUSM_ABYSSAL_SKYBOX 1 //[0 1]
#endif
#ifndef AUSM_ABYSSAL_SUNLIGHT
    #define AUSM_ABYSSAL_SUNLIGHT 1 //[0 1]
#endif

// Finite Botania mesh reconstruction was already disabled in Entree. Ordinary
// derivatives leave those feature macros undefined; retained Entree packs may
// still declare them without conflicting with this options include.
#endif
