# Fantasy Regions technical guide

Targets **Minecraft 26.3**, **NeoForge 26.3.0.43-beta** and **Java 25** (see `gradle.properties`).

Create a **new world** and choose **Fantasy Regions** as the World Type. Installing the mod does not change existing saves. Dedicated servers can set `level-type=cronusfantasyterrain:regions` before the world is first generated. The earlier Test World preset is still available.

Standing decisions and their history are in [`extra/project_notes.txt`](extra/project_notes.txt). **The border mountain shape is frozen**: do not change it unless the user asks, and tell them before any change that seems necessary.

## Regions

The user confirmed eleven regions on 2026-10-12. Five are generated; six are planned and have names only.

| Region | Key | Index | Status |
| --- | --- | --- | --- |
| [Frozen Wastes](regions/frozen_wastes.txt) | `frozen_wastes` | 0 | Generated, vanilla placeholders; Frostscar planned |
| [Cold Meadows](regions/cold_meadows.txt) | `cold_meadows` | 1 | Generated, vanilla placeholders |
| [Scorched Lands](regions/scorched_lands.txt) | `ember_lands` | 2 | Generated, vanilla placeholders; formerly Ember Lands |
| [Verdant Valley](regions/verdant_valley.txt) | `verdant_valley` | 3 | Generated, vanilla placeholders |
| [Crystal Peaks](regions/crystal_peaks.txt) | `crystal_peaks` | 4 | Generated, vanilla placeholders |
| [Blooming Highlands](regions/blooming_highlands.txt) | – | – | Planned |
| [Dry Dunes](regions/dry_dunes.txt) | – | – | Planned |
| [Enchanted Groves](regions/enchanted_groves.txt) | – | – | Planned |
| [Land of the Giants](regions/land_of_the_giants.txt) | – | – | Planned |
| [Misty Marshes](regions/misty_marshes.txt) | – | – | Planned |
| [Shadow Wilds](regions/shadow_wilds.txt) | – | – | Planned |

The Scorched Lands rename is documentation only; code and data still use `ember_lands`.

## Layout and biomes

The infinite Overworld is split into large, seeded regions shaped like rounded hexagons with slightly jagged rims (round regions on request 2026-10-15, decision 8; made less circular so the borders are narrower, decisions 10 and 11). Centres sit on a jittered hexagonal grid 3,800 blocks apart (`region_size`). Each region is a rounded hexagon (`RegionLayout.HEX` = 12: 2 would be a circle, higher is sharper) with flat sides facing its six neighbours and rounded corners filling the spots where three regions meet, plus a small per-region three-lobed bulge and oval stretch (`LOBES`, `OVAL`, 2% each). Every region is as large as its space allows: the rim stays `MIN_GAP` / 2 = 200 blocks behind the halfway line to each neighbour in every direction (wanted base radius `MIN_RADIUS`–`MAX_RADIUS` = 1650–1900, almost always trimmed). That gives about 1,700 blocks from the centre to a flat side and about 1,850 to a corner. A mild coordinate bend adds irregularity, and the rim wobbles by up to ±60 blocks (`RIM_NOISE`). On top of that the whole mountain front pushes up to 180 blocks into the region (`RIM_REACH`, decision 12) in broad, smooth lobes and bays a few hundred blocks across. Mountains fill the space between regions in fairly even belts, about 400 blocks from foot to foot (±120 from rim noise and jitter); there are no wide mountain areas where three regions meet. Region interiors without any border influence cover about 53% of the world (about 60% before the spurs). The five generated themes repeat throughout the world; there is no fixed continent or compass position. (The original sketch is treated as a layout concept, not a fixed map.)

Each region has a pool of four vanilla placeholder biomes (the codec accepts four or five), and local biome noise picks only from that pool:

| Region | Placeholder pool |
| --- | --- |
| Frozen Wastes | Snowy Plains, Ice Spikes, Snowy Taiga, Frozen River |
| Cold Meadows | Meadow, Taiga, Old Growth Spruce Taiga, Grove |
| Scorched Lands | Desert, Badlands, Wooded Badlands, Savanna |
| Verdant Valley | Plains, Forest, Birch Forest, Swamp |
| Crystal Peaks | Stony Shore, Windswept Hills, Windswept Gravelly Hills, Windswept Forest |

Placeholders keep vanilla surfaces, vegetation, mobs, structures and carvers, but they do **not** shape terrain: Frozen River carves no river and Windswept Hills makes no hills. All region interiors share gentle terrain around Y=75–85.

Region edges use the custom [Region Border](biomes/region_border.txt) biome wherever the border mask exceeds 0.12. Internal biome boundaries never create mountains.

## Border mountains

### Terrain (frozen; last changed on request 2026-10-15)

- The mountain foot is the region rim; the border mask fades out about 80 blocks inside it (foothills). Belts are about 400 blocks from foot to foot (`MIN_GAP`; rim noise narrows or widens them by up to 120): a ramp of about 100 blocks on each side and a raised massif between them.
- Lobes (decision 12): the edge distance is shifted by up to `RIM_REACH` = 180 blocks, so the ramp and massif move into the region together with their normal profile (no low aprons). The reach is read from the point on the rim straight out from the region centre, so it is constant along each inward line and never forms islands. It fades out past the ramp, well before the ownership change. Reach is one-sided and never thins a belt. Where a lobe's flank runs obliquely the ramp is somewhat steeper sideways (rarely about 4–5 blocks per block near ground level).
- Each position belongs to the region whose rim is nearest, so ownership changes halfway between two mountain feet, always after both ramps have finished. The edge distance stays continuous across that change. Rims are curved, so the ramp has no polygon corners (no vertical creases) and no long straight walls.
- The belt is one giant mountain range: a high massif with many broad, rounded peaks and only rare deep cuts (user request 2026-10-15, decision 7). No thin, pointy spires between valleys.
- Built from warped gradient noise: a massif body (scale 520), many rounded peaks on top (scale 150), moderate ridged crests (scale 170, `RIDGE_STRENGTH` = 55) for sharper, slightly jagged summits (decision 9), rare taller summits (scale 340) and rare valleys that follow thin noise contours in a few gated stretches.
- Heights (massif past the ramp): about 83% between Y=175 and Y=250 around `RANGE_CENTER` = 212; a few percent of summits above Y=270, capped at `PEAK_LIMIT` = 302; a few percent of valleys below Y=170, never below `MASSIF_FLOOR` = 150.
- No holes, lakes, canyons or cliffs at or below region ground: belt terrain never dips below the region floor (Y=75), and the border biome has no ravine (`minecraft:canyon`) carver.
- Harsh transition: the climb from region ground onto the massif happens between 19% and 47% of the border width from the belt's outer edge (`RAMP_START`/`RAMP_END`, about blocks 68–169), starting where the foothills end so the whole climb is deepslate, instead of across the whole belt. Near ground level the slope is steep but never more than 4 blocks up per block, so there is no sheer wall.
- Columns are solid below their surface: no floating slabs, detached rock or overhangs.
- Border terrain and region ownership come from the same seeded geometry, independent of biome and chunk order, including at negative coordinates.

### Materials

`noise_settings/regions.json` selects `material_rule/regions.json`, a sequence of: the bedrock floor, `material_rule/region_border.json` (border columns), `material_rule/region_basin.json` (the deepslate basin) and vanilla Overworld materials.

A column belongs to the border when its border mask exceeds 0.12 (`RegionLayout.BORDER_THRESHOLD`), the same threshold that selects the border biome. Materials use the column's mask, not the per-block biome, because Minecraft's biome lookup is fuzzy near biome edges and previously left stone pockets inside the mountains.

Border columns are deepslate all the way through:

- Exposed faces: deepslate (plain, no tuff) below the snow; snow fades in through a patchy band around a wandering snowline (average Y=214, band 36 blocks tall, locally about ±17 blocks; `BorderPalette.SNOW_LINE`/`SNOW_BAND`; extended downward 2026-10-16). No snow below Y=179; from Y=264 every exposed face is snow. Ledges and gentle slopes catch snow first; steep faces stay bare deepslate longer. No hard cut at one fixed height (user request 2026-10-15).
- Buried rock and cave walls: deepslate with about 4% tuff patches, plus deepslate ores.
- No exterior stone, blackstone, tuff, ore or random patches.

#### Deepslate basin ("cauldron", user sketch 2026-10-15)

Each region's grass, dirt and stone sit in a deepslate bowl whose walls continue up into the border mountains:

- Digging into a mountain or exploring its caves reveals only deepslate (with the tuff patches and deepslate ores above).
- Foothills (border mask 0–0.12, which use the neighboring region's biome) keep vanilla surface and stone. Below them, deepslate starts at the basin floor.
- The basin floor meets the surface at the mountain foot and curves down as a quarter ellipse to Y=0 (the vanilla deepslate line) 384 blocks (`RegionLayout.BASIN_REACH`) further inward. Region interiors beyond that are vanilla.
- The basin rule only paints Y=0 and above, so vanilla deepslate, tuff and iron ore veins below Y=0 are unchanged. Copper/granite ore veins do not form inside the basin wall.
- `density_function/region_basin_floor.json` (`basin_floor` output) is a material channel only and does not change terrain.
- Ore and blob features from region biomes (granite, diorite, andesite, dirt, gravel) can still place small blobs into the basin wall under foothills. `BorderChunkMaterials` repaints border columns only.

`BorderMaterialRule` and `BorderPalette` choose the border material; `BasinMaterialRule` paints the basin. The `border_exterior_floor` density output (lowest neighboring surface) separates outdoor cliff faces from buried rock; it is a material channel only and does not change height or density.

### Features and cleanup

- The border biome keeps only custom ore placements: vanilla frequencies and heights, deepslate variants, replacing only deepslate/tuff, and discarded when exposed to air.
- No dirt, gravel, granite, diorite, andesite, sediment disks, geodes, dungeons, lakes, springs or vegetation.
- The biome is temperate (temperature 0.8, downfall 0.3) with no `freeze_top_layer`, so lower slopes are not blanketed in snow.
- `BorderChunkMaterials` repaints newly generated chunks after neighboring features spill in, keeping cave air, fluids and approved ores. It never rewrites old saved chunks.

## Code map

- `RegionLayout.java` – pure Java. Owns the region partition, the border mask and the terrain column (surface height and density). Knows nothing about biomes.
- `RegionFunction.java` – exposes region ID, border mask, surface height, density, exterior floor and basin floor to Minecraft. All outputs derive their seed from the same namespaced random source, so they agree regardless of chunk order.
- `RegionBiomeSource.java` – reads region ID from the router's temperature channel, border strength from erosion and local biome noise from vegetation. These channels carry region data, not vanilla climate values.
- `world_preset/regions.json` – each region's biome pool. Replace placeholder IDs with custom biomes as they are built.
- `density_function/region_id.json`, `region_border.json`, `region_height.json`, `region_terrain.json`, `border_exterior_floor.json`, `region_basin_floor.json` – all contain `region_size` (3800, the spacing between region centres) and `border_width` (360). **Keep them identical** or terrain and ownership desynchronize.
- `density_function/region_terrain.json` – selects the `density` output (interior relief, alpine ridges and valleys). Future biome terrain can feed into this layer.
- `noise/region_biomes.json` – local biome patch scale, independent of region size.
- `FrozenLand.java` – identifies Frozen Wastes (`REGION_INDEX = 0`); its pool is data-driven in the preset.

Generation parameters are saved into each world. Changing them can cause seams in existing worlds, so always test in a **fresh** Fantasy Regions world.

## Verification

- `gradlew.bat build` compiles, packages and runs all checks:
  - `regionLayoutTest` – 30,000 sampled positions: all themes, seed variation, negative coordinates, neutral interiors, floor/ceiling bounds, continuity across ownership changes, tall crests, high-altitude cliffs and drops, no belt terrain below region ground, the raised massif floor, the short ramp with no ground-level walls, thin spines, no solid rock above air gaps, and the deepslate basin (border columns fully deepslate, foothills keep stone, basin floor within the column).
  - `borderPaletteTest` – the deepslate/snow-line/tuff palette.
  - `borderFeatureTest` – the border biome's ore-only feature whitelist.
- `gradlew.bat runGameTestServer` loads the worldgen data and runs game tests; its flat world does not show Fantasy Regions terrain. Use an isolated game directory if mods in `run/mods` interfere.
- `gradlew.bat regionPreview` writes software previews (layout seed 12345; Minecraft derives its layout seed from the world seed):
  - `build/reports/regions/preview.png` – region themes and border belts.
  - `build/reports/regions/border-terrain.png` – shaded ridge surface with a vertical section.
  - `build/reports/regions/border-volume.png` – isometric solid/air voxels (6 blocks horizontal, 4 vertical).
  - `build/reports/regions/basin-section.png` – vertical material slice across a region edge showing the stone basin, deepslate walls and border rock.
  - `build/reports/regions/border-snow.png` – top-down surface materials (1 pixel per block) around the snowline.

The tests sample; they do not guarantee visual quality everywhere, and previews omit materials, decorations and lighting. Always check in a fresh Minecraft world.

## Not yet implemented

- Custom biomes for region interiors (Frostscar is the first design).
- Region-specific or biome-specific terrain, rock formations and constrained overhangs.
- The six planned regions.
- Custom cave biomes, mountain passes, oceans and coastlines.
- Custom Nether or End generation.
