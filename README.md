# Realismns

A living world that remembers your steps — paths form where players run.

## Quick start

Requirements
- Java 17+
- Gradle (or use the included wrapper)
- A compatible Minecraft server (drop the built JAR into `mods/` or `plugins/` depending on your platform)

Build
```bash
./gradlew build
```

The plugin/mod JAR will be in `build/libs/` after a successful build.

Run
- Place the produced JAR into your server's `mods/` (Fabric/Forge) or `plugins/` (Spigot/Paper) folder and restart the server.

## Features / Modules

- Path creation (step-tracking): the world "remembers" where players run — frequent sprinting compacts grass and eventually forms paths.
  - Stages: GRASS → DIRT → ROOTS → FARMLAND → PATH
  - Works only when sprinting (sprint).
- Advanced Backpacks
  - Configurable storage with modular add-ons for automation.
  - Modules: Base (slot pages), Pickup / Advanced Pickup (auto-collect items from ground), Magnet / Advanced Magnet (pull items from distance).
- Clay from heat
  - Mud turns into clay when exposed to a heat source (lava/magma) or in Nether-like climates. A source of heat is required.
- Irrigation
  - If no infinite water source is available, you can hydrate farmland manually.
  - Use water splash bottles to instantly hydrate nearby farmland.
- Crop care
  - Crops require regular access to water; without it they may wither and die.

## Configuration / Feature Flags

Feature toggles are defined in `src/main/kotlin/org/kotofey/realismns/FeatureFlags.kt`.
You can enable/disable modules or adjust behavior by editing the feature flags or via your server configuration where supported.

Examples of toggles:
- Enable/disable path tracking
- Adjust number of runs required to compact a block
- Turn on/off backpack automation modules

(Refer to the `FeatureFlags.kt` file for exact flag names and defaults.)

## Usage examples

- Make a path: sprint repeatedly over the same blocks — after a few dozen runs grass compacts and turns into a path over time.
- Hydrate fields: throw a water splash bottle towards your crops to instantly hydrate farmland in front of you.
- Obtain clay: put mud near lava/magma or go to Nether-like biomes where mud turns into clay under heat.
- Backpacks: attach modules to a backpack to enable auto-pickup or magnet behavior.

## Contributing / Contacts

Contributions are welcome! Please:
1. Open an issue to discuss big changes.
2. Create a branch for your feature/fix.
3. Make a PR with a clear title and description.

Coding style: Kotlin idiomatic code, use KDoc for public APIs.

Contact: open issues or reach out via the repository discussions/issues.

## License

This project is licensed under the MIT License — see the `LICENSE` file for details.
