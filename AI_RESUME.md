# AUSM runtime handoff

- Current release work targets version 1.0.2 on Java 25.
- Shadered fluids remain visible with Celeritas in the normal Java 25 test instance, and the previously reported translucent entity/custom-model rendering is no longer present.
- GPOM double-slope emission and bloom now use the active per-quad vanilla lightmap emission when AUSM records bloom metadata. Both branches compile after this change; it still needs an in-game GPOM double-slope check.
- The normal test profile contains the single Java 25 1.0.2 release JAR. Do not replace or restart the running client without asking.
