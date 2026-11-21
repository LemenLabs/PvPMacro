# PvP Macro

A client-side Fabric mod providing configurable PvP macro keybinds, combos, utility features and an in-game menu.

## Project details

| Setting | Value |
|---|---|
| Mod ID | `pvpmacro` |
| Mod version | `1.1.0` |
| Minecraft | `26.2` |
| Mod loader | Fabric |
| Fabric Loader | `0.20.1` |
| Fabric API | `0.161.0+26.2` |
| Java source target | 25 |

> The uploaded JAR filenames say `1.0.0`, but their embedded `fabric.mod.json` identifies the actual mod version as `1.1.0`. This project uses the embedded version.

## Build

This project uses Gradle and Fabric Loom.

1. Install JDK 25 and Gradle 9.8.2 (the compiled JAR reports Java class-file version 69, which corresponds to Java 25).
2. From this directory, run:

   ```bash
   gradle build
   ```

3. The built mod JAR will be in `build/libs/`.

Use Java 25 to build and run this Minecraft 26.2 project.

## Source recovery note

This repository was reconstructed from the supplied `pvpmacro-1.0.0-sources.jar` and the matching compiled mod JAR. The original Gradle build files and wrapper were not included in those archives, so the Gradle configuration here has been recreated from the compiled JAR metadata. It has not yet been compiled in this environment, so the first local build is the final compatibility check.

## License

The supplied compiled JAR contained `LICENSE_pvpmacro` (CC0 1.0 Universal); it has been retained as `LICENSE_pvpmacro`.
