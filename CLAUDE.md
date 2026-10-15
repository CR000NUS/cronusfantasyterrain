# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

NeoForge mod (`mod_id` `cronusfantasyterrain`, package `c.cronus.fantasyterrain`) that adds a **Fantasy Regions** world preset: an infinite Overworld split into large seeded regions (~3072 blocks) separated by alpine mountain border belts. Targets Minecraft **26.3**, NeoForge **26.3.0.43-beta**, Java **25** (versions in `gradle.properties`). The Nether/End are vanilla. The example block/item/tab in `CronusFantasyTerrain.java` are leftover MDK template code.

## Commands

Windows shell; use `gradlew.bat` (or `./gradlew` from Git Bash).

- `gradlew.bat build` – compile, package, and run all checks (`check` depends on the three tasks below). CI (`.github/workflows/build.yml`) runs `./gradlew build` on JDK 25.
- `gradlew.bat regionLayoutTest` – standalone geometry checks (`RegionLayoutTest.main`, 30,000 sampled positions).
- `gradlew.bat borderPaletteTest` – border material palette checks (`BorderPaletteTest.main`).
- `gradlew.bat borderFeatureTest` – Groovy task in `build.gradle` that enforces the border biome feature whitelist (only `cronusfantasyterrain:border/ore_*`, deepslate ores replacing only deepslate/tuff, `discard_chance_on_air_exposure == 1.0`).
- `gradlew.bat regionPreview` – renders `build/reports/regions/preview.png`, `border-terrain.png`, `border-volume.png` (layout seed 12345; software previews, not in-game).
- `gradlew.bat runClient` / `runServer` / `runGameTestServer` / `runData` – NeoForge run configs (`run/` is the game dir).

Tests are **not JUnit**: each is a `public static void main` under `src/test/java` wired up as a `JavaExec` task, failing via thrown exceptions. `test` has `failOnNoDiscoveredTests = false`. To add a test, write a `main` class and register a `JavaExec` task + `check` dependency in `build.gradle` following the existing pattern.

## Architecture

World generation is mostly data-driven JSON wired to a few custom registered codecs (`worldgen/RegionWorldgen.java` registers them plus the chunk event listeners):

- **`RegionLayout`** – pure Java, no Minecraft dependencies. Owns the seeded horizontal region partition (`sample(x,z)` → region id 0–4 + border mask 0–1) and the terrain column (`surfaceHeight`, `terrainColumn(...).density(y)`) including the ridged-multifractal mountain network. Because it is MC-free, the standalone tests and previews exercise it directly.
- **`RegionFunction`** – a `DensityFunction` (`cronusfantasyterrain:region`) wrapping `RegionLayout`, with `output` = `region | border | height | density | exterior_floor`. Seed derived from `context.createRandom("cronusfantasyterrain:region_layout")` so all outputs share identical geometry independent of chunk order.
- **Noise router channel repurposing** (`noise_settings/regions.json`): `temperature` = region id, `erosion` = border mask, `vegetation` = local biome noise, `final_density` = terrain, `chunk_surface_level` = height. These are *not* climate values.
- **`RegionBiomeSource`** (`cronusfantasyterrain:regions`) reads those channels: erosion > 0.12 → `mountain_biome`; otherwise temperature picks the region pool and vegetation picks a biome from it. Pools (4–5 biomes each, currently vanilla placeholders) are defined in `world_preset/regions.json`. The codec has five hard-coded pool fields; adding a region requires changing the codec, `RegionLayout.REGION_COUNT`, `RegionFunction.range()`, the clamp in the resolver, and the preset.
- **Border materials**: `material_rule/regions.json` → bedrock floor, then `material_rule/region_border.json` inside the `region_border` biome, else vanilla overworld. `BorderMaterialRule` (`cronusfantasyterrain:border_palette`) uses the `exterior_floor` output (lowest neighboring surface) to distinguish outdoor faces from buried rock; `BorderPalette` picks blackstone below / snow at and above `SNOW_LINE = 220` outdoors, deepslate with ~4% tuff underground.
- **`BorderChunkMaterials`** – queues newly generated chunks on `ChunkEvent.Load`, processes them on server tick (8 per tick, drained on shutdown) to re-apply the palette after neighbor feature spill-over. Only acts when the generator uses `cronusfantasyterrain:regions` noise settings; never rewrites old chunks. Do not query the level/neighbors inside the load callback.

### Invariants

- `region_size` and `border_width` must be **identical** in `density_function/region_id.json`, `region_border.json`, `region_height.json`, and `region_terrain.json`, or terrain and region ownership desynchronize.
- Generation parameters are saved into worlds; verify changes in a **fresh** Fantasy Regions world.
- The code/data key for Scorched Lands is still `ember_lands`; the rename is documentation-only.
- `FrozenLand.REGION_INDEX` identifies the Frozen Wastes pool index.

## Standing user decisions

- **Border terrain geometry is frozen.** Do not change the border mountain shape (`RegionLayout` terrain, border width, partition, terrain density outputs) unless the user explicitly asks. If a change seems necessary, tell the user first and wait. Material-only changes have been separately authorized in the past (see `docs/extra/project_notes.txt`).
- Border biome features are restricted to the custom deepslate ore placements; no decorative blocks, vegetation, or other features (enforced by `borderFeatureTest`).

## Documentation

`docs/regions.md` is the technical guide; `docs/regions/*.txt` (11 regions, 5 implemented, 6 planned), `docs/biomes/*.txt` (per placeholder/custom biome), and `docs/extra/project_notes.txt` (dated user decisions). Update these when the user provides or changes region/biome designs, and keep planned ideas, vanilla placeholders, and implemented features clearly distinguished.
