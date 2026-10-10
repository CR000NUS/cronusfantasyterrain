# Region foundation

This project targets **Minecraft 26.3**, **NeoForge 26.3.0.43-beta**, and **Java 25**, as configured in `gradle.properties` and `build.gradle`.

Create a **new world**, choose **Fantasy Regions** in the World Type selector, and generate the world. Installing the mod does not switch the world type of existing saves. Dedicated servers can select `level-type=cronusfantasyterrain:regions` before generating a new world. The earlier Test World preset remains available.

## Current behavior

The infinite Overworld contains large, seeded, irregular regions, each roughly 3,072 blocks across. Five themes repeat throughout the world. This interprets the sketch as a layout concept rather than one fixed continent with exact compass positions.

Each region owns a pool of four vanilla placeholder biomes. The local biome noise selects only from that pool. Rugged mountain and canyon belts occupy region boundaries and use jagged peaks as a shared border biome. Border terrain and region ownership come from the same seeded geometry, including at negative coordinates. Internal biome boundaries never create region mountains.

| Region | Temporary biome pool |
| --- | --- |
| Frozen Wastes | Snowy Plains, Ice Spikes, Snowy Taiga, Frozen River |
| Cold Meadows | Meadow, Taiga, Old Growth Spruce Taiga, Grove |
| Ember Lands | Desert, Badlands, Wooded Badlands, Savanna |
| Verdant Valley | Plains, Forest, Birch Forest, Swamp |
| Crystal Peaks | Stony Shore, Windswept Hills, Windswept Gravelly Hills, Windswept Forest |

These are placeholders, not finished fantasy biomes. A Frozen River placeholder does not carve a river; a Windswept Hills placeholder does not shape hills. Biome surfaces, vegetation, mobs, structures, and carvers still use vanilla biome behavior. All interiors currently share gentle terrain around Y=75–85. Border belts extend up to 360 blocks on either side of a cell edge (roughly 720 blocks wide before coordinate warping).

Borders follow the alpine references: connected main crests, smaller branching ridges, steep triangular flanks, and erosion-like gullies between them. A warped ridged multifractal uses four scales (280, 140, 70, and 35 blocks). Each smaller scale is weighted by the preceding ridge, so detail follows the mountain network rather than appearing independently throughout the terrain. Broad bases support sharp crests, while the shared region mask confines the range to the border belt. Successive detail scales retain 43% of the previous amplitude, slightly softening the finest ridges.

This replaces the previous strong 3D displacement and geometric cavity experiments. Mountain columns are solid below their surface, so they cannot produce detached rock slabs, floating islands, or cutout-shaped cave mouths. The emphasis is now the simple, grounded, vein-like mountain structure shown in the reference photographs. Dedicated overhang generation is absent in this version.

The mountain profile ranges approximately from Y=28 to Y=305, with the neutral region interiors remaining around Y=75?85. Low gullies form deep valleys between ridges; portions below Y=63 fill with water. Border shape remains seeded and independent of biome ownership and chunk order.

There is no fixed continent, ocean coastline, custom biome terrain, custom cave biome selection, or deliberate mountain pass system yet. The Nether and End use vanilla generation. Biome-specific rock formations and carefully constrained overhangs remain future work.

## Where to extend it

- `RegionLayout.java` owns the horizontal region partition and a continuous mountain mask. It knows nothing about biome types.
- `RegionFunction.java` exposes region ID, border mask, surface height, and three-dimensional density to Minecraft's sampler. Each view derives its seed using the same namespaced random source, independently of chunk generation order. Density volumes compute one terrain column per X/Z position, then sample its rock geometry at each Y.
- `RegionBiomeSource.java` reads region ID from the router's temperature channel, border strength from erosion, and local biome noise from vegetation. These are dedicated data channels for this preset, not vanilla climate values.
- `world_preset/regions.json` defines each region's allowed biomes. Replace placeholder IDs with registered custom biomes as they are developed. Each pool accepts four or five entries.
- `density_function/region_id.json`, `region_border.json`, `region_height.json`, and `region_terrain.json` contain `region_size` and `border_width`. Keep those values identical across all four files so terrain and ownership stay aligned. Parameters are saved in world generation data; changing them can create seams in previously generated worlds, so test changes in a fresh world.
- `density_function/region_terrain.json` selects the custom `density` output, which combines interior relief, the grounded alpine ridge network and intervening valleys. Later biome terrain can feed into this layer without turning regions themselves into biome terrain definitions.
- `noise/region_biomes.json` controls local biome patch scale, independently of region size.

The original `FrozenLand.java` now identifies the Frozen Wastes region. Its actual allowed biome pool is data driven in the world preset.

## Verification

Run `gradlew.bat build` to compile, package, and check seeded region geometry. The standalone `regionLayoutTest` checks 30,000 positions, all themes, seed variation, negative coordinates, neutral interiors, floor/ceiling bounds, and terrain continuity where ownership changes. It checks tall crests, steep flanks, nearby elevation drops, broad low valleys, and grounded geometry without solid rock above air gaps. The thin-spine check samples actual topmost solid voxels and nearby columns. These are sampled checks, not exhaustive guarantees of visual quality across an infinite world.

`gradlew.bat runGameTestServer` loads the worldgen datapacks and runs available game tests; its default flat world does not visually verify Fantasy Regions terrain. Use an isolated game directory if mods in `run/mods` interfere.

Run `gradlew.bat regionPreview` to export the following algorithm illustrations:

- `build/reports/regions/preview.png`: region themes and border belts.
- `build/reports/regions/border-terrain.png`: shaded density surface showing branching ridge crests, with a vertical section.
- `build/reports/regions/border-volume.png`: an isometric rendering of actual solid/air geometry, sampled at six blocks horizontally and four vertically.

These are software previews, not Minecraft screenshots. They use layout seed 12345; Minecraft derives the layout seed from the chosen world seed. The voxel rendering is coarse and omits Minecraft materials, decorations, and lighting. Visual evaluation in a fresh Minecraft world remains necessary.
