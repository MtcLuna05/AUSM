bool coloredFluidMaterial = true;
// The fluid renderer has already combined the fluid's registered tint with
// its still/flowing sprite in color.  Preserve that authoritative mod color;
// material IDs select only the generic optical response and emission class.
color.a = min(color.a, 0.82);
translucentMultCalculated = true;
translucentMult = vec4(1.0, 1.0, 1.0, 0.0);
reflectMult = 0.72 * (0.45 + 0.55 * NdotUmax0);
smoothnessG = 0.62;
highlightMult = 0.72 * (16.0 - 15.0 * pow2(fresnel));
if (mat >= 32640 && mat <= 32644) emission = max(emission, 0.55);
