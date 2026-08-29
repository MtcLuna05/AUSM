# AUSM runtime handoff

- Current release work targets version 1.0.2 on Java 8.
- Java 8 includes the same Celeritas/GregTech liquid routing and Complementary cloud fixes as Java 25.
- GPOM double-slope emission and bloom now use the active per-quad vanilla lightmap emission when AUSM records bloom metadata. Both branches compile after this change; it still needs an in-game GPOM double-slope check.
- The normal test profile contains the single Java 8 1.0.2 release JAR. Do not replace or restart the running client without asking.
