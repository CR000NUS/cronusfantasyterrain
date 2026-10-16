package c.cronus.fantasyterrain.worldgen;

import c.cronus.fantasyterrain.FrozenLand;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Standalone checks for Everwinter terrain, materials and its wiring into the Frozen Wastes pool. */
public final class EverwinterTest {
    private static final Path WORLDGEN = Path.of("src/main/resources/data/cronusfantasyterrain/worldgen");

    public static void main(String[] args) throws Exception {
        checkWiring();
        // The relief fades to zero at the district boundary and is full further inside.
        require(EverwinterTerrain.districtWeight(0) == 0, "Relief must vanish at the biome edge");
        require(EverwinterTerrain.districtWeight(EverwinterTerrain.DISTRICT_FADE) == 1, "Relief must be full inside the biome");
        require(EverwinterTerrain.edgeFade(360, 360) == 0, "Relief must vanish where the border mask begins");

        // Borders and basin stay untouched: no relief in any column the border reaches.
        RegionLayout layout = new RegionLayout(12345L, 3800, 360);
        EverwinterTerrain terrain = new EverwinterTerrain(777L);
        Random random = new Random(4242L);
        int touched = 0;
        for (int i = 0; i < 30000; i++) {
            int x = random.nextInt(100000) - 50000, z = random.nextInt(100000) - 50000;
            RegionLayout.Sample sample = layout.sample(x, z);
            double weight = EverwinterTerrain.edgeFade(sample.edgeDistance(), 360);
            float base = layout.surfaceHeight(x, z, sample);
            EverwinterTerrain.Column column = terrain.column(x, z, base, weight);
            if (sample.border() > 0) {
                require(column.height() == base, "Everwinter relief reaches a border column");
            } else if (column.height() != base) {
                touched++;
            }
        }
        require(touched > 5000, "Everwinter relief never applies in region interiors");

        // Full-strength statistics over a large area.
        int samples = 0, walls = 0, steps = 0, crevasses = 0, deep = 0, wallChecks = 0, rockWalls = 0;
        int[] features = new int[EverwinterTerrain.FEATURES];
        int[] heights = new int[400];
        double lowest = 999, highest = -999;
        for (int z = -3000; z < 3000; z += 6) {
            for (int x = -3000; x < 3000; x += 6) {
                EverwinterTerrain.Column column = terrain.column(x, z, 80, 1);
                samples++;
                features[column.feature()]++;
                heights[(int) column.height()]++;
                lowest = Math.min(lowest, column.height());
                highest = Math.max(highest, column.height());
                EverwinterTerrain.Column next = terrain.column(x + 1, z, 80, 1);
                double drop = column.height() - next.height();
                if (column.feature() == EverwinterTerrain.GLACIER && drop > 6) walls++;
                if (open(column) && open(next) && Math.abs(drop) > 5) steps++;
                if (column.feature() == EverwinterTerrain.CREVASSE) {
                    // Depth below the surrounding surface; walls must be ice down to the floor, not rock.
                    crevasses++;
                    double rim = 0;
                    for (int k = -10; k <= 10; k += 2) {
                        for (EverwinterTerrain.Column side : new EverwinterTerrain.Column[] {
                                terrain.column(x + k, z, 80, 1), terrain.column(x, z + k, 80, 1)}) {
                            if (side.feature() == EverwinterTerrain.CREVASSE) continue;
                            rim = Math.max(rim, side.height());
                            if (side.feature() == EverwinterTerrain.ROCK) continue;
                            wallChecks++;
                            if (side.iceBase() > column.height()) rockWalls++;
                        }
                    }
                    if (rim - column.height() >= 15) deep++;
                }
            }
        }
        int p5 = percentile(heights, samples, 0.05), p95 = percentile(heights, samples, 0.95);
        double seracs = share(features, EverwinterTerrain.GLACIER, samples), hills = share(features, EverwinterTerrain.HILL, samples);
        double piles = share(features, EverwinterTerrain.DRIFT, samples), rocks = share(features, EverwinterTerrain.ROCK, samples);
        double spires = share(features, EverwinterTerrain.SPIRE, samples), cuts = share(features, EverwinterTerrain.CREVASSE, samples);
        require(lowest >= EverwinterTerrain.LOWEST_FLOOR, "Everwinter dips to sea level: " + lowest);
        require(highest <= 200, "Everwinter rises too high: " + highest);
        require(p95 - p5 >= 25, "Everwinter needs more elevation change: Y=" + p5 + "-" + p95 + " (5th-95th percentile)");
        require(seracs > 0.08 && seracs < 0.3, "Serac fields must be common but not dominant");
        require(piles > 0.02 && hills > 0.015 && hills < 0.15, "Snow piles must be common, big hills rarer");
        require(rocks > 0.01 && rocks < 0.08, "Deepslate outcrops must be occasional");
        require(spires > 0 && spires < 0.02, "Ice spires must exist but stay rare");
        require(cuts > 0.01 && cuts < 0.1, "Crevasses and cracks must exist without swallowing the biome");
        require(share(features, EverwinterTerrain.PLAIN, samples) > 0.4, "Open snow ground must remain the most common surface");
        require(walls > 500, "Serac blocks need sheer ice walls");
        require(steps > 100, "Open ground needs a few ice cliffs");
        require(deep > crevasses * 0.7, "Crevasses must be deep: " + deep + " of " + crevasses);
        require(rockWalls < wallChecks * 0.01, "Crevasse walls must be ice: " + rockWalls + " of " + wallChecks + " show rock");

        // Materials: snow and ice cover, ice glaciers, deepslate outcrops, stone/deepslate underground.
        EverwinterPalette palette = new EverwinterPalette(99L);
        int cover = 0, ice = 0, deepslate = 0, under = 0;
        for (int i = 0; i < 200000; i++) {
            int x = random.nextInt(20000) - 10000, z = random.nextInt(20000) - 10000;
            EverwinterPalette.Material top = palette.select(x, 80, z, 80, 80.5f, EverwinterTerrain.PLAIN, true);
            require(top == EverwinterPalette.Material.SNOW || top == EverwinterPalette.Material.PACKED_ICE, "Plain surface must be snow or ice");
            cover++;
            if (top == EverwinterPalette.Material.PACKED_ICE) ice++;
            var body = palette.select(x, 92, z, 100, 80.5f, EverwinterTerrain.GLACIER, false);
            require(body == EverwinterPalette.Material.PACKED_ICE || body == EverwinterPalette.Material.BLUE_ICE, "Glacier body must be ice");
            require(palette.select(x, 84, z, 86, 80.5f, EverwinterTerrain.ROCK, false) == EverwinterPalette.Material.DEEPSLATE,
                    "Outcrop faces must be deepslate");
            var sheet = palette.select(x, 74, z, 80, 70.5f, EverwinterTerrain.PLAIN, true);
            require(sheet == EverwinterPalette.Material.PACKED_ICE || sheet == EverwinterPalette.Material.BLUE_ICE,
                    "Below the snow cover lies glacier ice");
            require(palette.select(x, 84, z, 90, 70.5f, EverwinterTerrain.DRIFT, false) == EverwinterPalette.Material.SNOW,
                    "Snow piles must be snow");
            var face = palette.select(x, 95, z, 95, 70.5f, EverwinterTerrain.HILL, false);
            require(face == EverwinterPalette.Material.PACKED_ICE || face == EverwinterPalette.Material.BLUE_ICE,
                    "Steep hill faces must show bare ice");
            int y = random.nextInt(70);
            var rockMaterial = palette.select(x, y, z, 80, 80.5f, EverwinterTerrain.PLAIN, true);
            require(rockMaterial == EverwinterPalette.Material.STONE || rockMaterial == EverwinterPalette.Material.DEEPSLATE,
                    "Underground must be stone or deepslate");
            under++;
            if (rockMaterial == EverwinterPalette.Material.DEEPSLATE) deepslate++;
        }
        require(ice > cover * 0.1 && ice < cover * 0.45, "Packed-ice patches must cover part of the snow plain");
        require(deepslate > under * 0.3 && deepslate < under * 0.7, "Underground must mix stone and deepslate: " + deepslate * 100 / under + "% deepslate");

        System.out.printf("Everwinter checks passed; heights Y=%.0f-%.0f (Y=%d-%d for 90%%), serac fields %.1f%%, big hills %.1f%%,"
                        + " snow piles %.1f%%, crevasses and cracks %.1f%% (%d%% at least 15 deep), outcrops %.1f%%, spires %.2f%%,"
                        + " %d cliff steps; plain ice patches %.0f%%, underground deepslate %.0f%%; %d interior columns relieved,"
                        + " none in borders.%n",
                lowest, highest, p5, p95, 100 * seracs, 100 * hills, 100 * piles, 100 * cuts, deep * 100 / Math.max(1, crevasses),
                100 * rocks, 100 * spires, steps, 100.0 * ice / cover, 100.0 * deepslate / under, touched);
    }

    /** Pool slot, pool size and the shared region geometry parameters must agree across data files. */
    private static void checkWiring() throws Exception {
        String preset = Files.readString(WORLDGEN.resolve("world_preset/regions.json"));
        Matcher pool = Pattern.compile("\"frozen_wastes\"\\s*:\\s*\\[([^]]*)]").matcher(preset);
        require(pool.find(), "Frozen Wastes pool missing");
        String[] entries = pool.group(1).replaceAll("[\\s\"]", "").split(",");
        require(entries.length == FrozenLand.POOL_SIZE, "FrozenLand.POOL_SIZE does not match the preset");
        require(entries[FrozenLand.EVERWINTER_SLOT].equals("cronusfantasyterrain:everwinter"),
                "FrozenLand.EVERWINTER_SLOT does not point at Everwinter in the preset");
        try (var files = Files.list(WORLDGEN.resolve("density_function"))) {
            for (Path file : files.toList()) {
                String json = Files.readString(file);
                if (!json.contains("cronusfantasyterrain:region\"") && !json.contains("cronusfantasyterrain:biome_terrain\"")) continue;
                require(json.matches("(?s).*\"region_size\"\\s*:\\s*3800.*") && json.matches("(?s).*\"border_width\"\\s*:\\s*360.*"),
                        file.getFileName() + " must use region_size 3800 and border_width 360");
            }
        }
    }

    /** Open ground (no serac block, crack, crevasse, spire or outcrop), for counting cliff steps. */
    private static boolean open(EverwinterTerrain.Column column) {
        int feature = column.feature();
        return feature != EverwinterTerrain.GLACIER && feature != EverwinterTerrain.CREVASSE
                && feature != EverwinterTerrain.SPIRE && feature != EverwinterTerrain.ROCK;
    }

    private static double share(int[] features, int feature, int samples) {
        return (double) features[feature] / samples;
    }

    private static int percentile(int[] histogram, int total, double fraction) {
        int seen = 0;
        for (int height = 0; height < histogram.length; height++) {
            seen += histogram[height];
            if (seen >= total * fraction) return height;
        }
        return histogram.length - 1;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
