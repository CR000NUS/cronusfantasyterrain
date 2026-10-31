package c.cronus.fantasyterrain.worldgen;

import c.cronus.fantasyterrain.FrozenLand;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Standalone checks for Howling Canyon terrain, materials and its wiring into the Frozen Wastes pool. */
public final class HowlingCanyonTest {
    private static final Path WORLDGEN = Path.of("src/main/resources/data/cronusfantasyterrain/worldgen");

    public static void main(String[] args) throws Exception {
        checkWiring();
        require(HowlingCanyonTerrain.districtWeight(0) == 0, "Relief must vanish at the biome edge");
        require(HowlingCanyonTerrain.districtWeight(HowlingCanyonTerrain.DISTRICT_FADE) == 1, "Relief must be full inside the biome");
        require(HowlingCanyonTerrain.edgeFade(360, 360) == 0, "Relief must vanish where the border mask begins");
        checkCourses();
        checkMassifs();
        checkNaturalMassifs();
        checkCliffSteps();
        checkTerrain();
        checkGlaciers();
        checkEverwinterUnchanged();
        checkMaterials();
    }

    /**
     * Every large Howling Canyon district gets a river that runs out of the district at both ends (open, never a
     * closed end), mostly into neighbouring biomes, with its ice reaching the district edge and open ground on both
     * sides. Beside it the glacier is broken into separate massifs: walking along the river at some distance, the
     * ground or a saddle keeps coming back between the massifs (decision 29). Its bends differ in length (decision
     * 31: no sine wave with evenly spaced bends).
     */
    private static void checkCourses() {
        int districts = 0, openEnds = 0, riverPoints = 0, riverIce = 0, banks = 0, groundBanks = 0;
        int besideHigh = 0, breaks = 0;
        double longest = 0, shortest = Double.POSITIVE_INFINITY, besideLength = 0;
        java.util.List<Double> bends = new java.util.ArrayList<>();
        for (long seed = 1; seed <= 4; seed++) {
            Setup setup = new Setup(seed);
            Map<Long, Integer> areas = new HashMap<>();
            Map<Long, RegionLayout.Sample> samples = new HashMap<>();
            for (int z = -12000; z < 12000; z += 64) {
                for (int x = -12000; x < 12000; x += 64) {
                    RegionLayout.Sample sample = setup.layout.sample(x, z);
                    if (setup.courses.weight(x, z, sample.cellX(), sample.cellZ()) < 0) continue;
                    long key = ((long) sample.cellX() << 32) ^ (sample.cellZ() & 0xFFFFFFFFL);
                    areas.merge(key, 64 * 64, Integer::sum);
                    samples.putIfAbsent(key, sample);
                }
            }
            for (var entry : areas.entrySet()) {
                if (entry.getValue() < 900_000) continue;
                RegionLayout.Sample sample = samples.get(entry.getKey());
                int cellX = sample.cellX(), cellZ = sample.cellZ();
                HowlingCanyonTerrain.Course[] courses = setup.courses.courses(sample);
                districts++;
                require(courses.length == 1, "A Howling Canyon district of " + entry.getValue() + " blocks has no river");
                HowlingCanyonTerrain.Course river = courses[0];
                double length = (river.size() - 1) * HowlingCanyonTerrain.STEP;
                longest = Math.max(longest, length);
                shortest = Math.min(shortest, length);
                bends.addAll(bends(river));
                // Open at both ends: the river leaves the district.
                for (int end : new int[] {0, river.size() - 1}) {
                    require(setup.courses.weight(river.x()[end], river.z()[end], cellX, cellZ) < 0, "The river ends inside its biome");
                    if (setup.courses.opensToBiome(river.x()[end], river.z()[end])) openEnds++;
                }
                boolean high = false;
                for (int i = 2; i < river.size() - 2; i += 2) {
                    double weight = setup.courses.weight(river.x()[i], river.z()[i], cellX, cellZ);
                    if (weight <= 0) continue;
                    // The frozen river runs along the whole course inside the biome, up to its edge.
                    HowlingCanyonColumns.Column column = setup.columns.column((int) Math.floor(river.x()[i]), (int) Math.floor(river.z()[i]));
                    riverPoints++;
                    if (column != null && column.feature() == HowlingCanyonTerrain.RIVER) riverIce++;
                    if (weight < 1) continue;
                    double tx = river.x()[i + 2] - river.x()[i - 2], tz = river.z()[i + 2] - river.z()[i - 2], norm = Math.hypot(tx, tz);
                    for (int side = -1; side <= 1; side += 2) {
                        // Open ground beside the river (not a wall rising straight from its bank).
                        int bankX = (int) Math.floor(river.x()[i] - side * tz / norm * 16), bankZ = (int) Math.floor(river.z()[i] + side * tx / norm * 16);
                        banks++;
                        if (setup.columns.column(bankX, bankZ).height() < HowlingCanyonTerrain.FLOOR_Y + 4) groundBanks++;
                    }
                    // Walking along the river 150 blocks to its left: massifs, broken by ground or saddles between them.
                    int besideX = (int) Math.floor(river.x()[i] + tz / norm * 150), besideZ = (int) Math.floor(river.z()[i] - tx / norm * 150);
                    HowlingCanyonColumns.Column beside = setup.columns.column(besideX, besideZ);
                    if (beside == null || setup.courses.weight(besideX, besideZ, cellX, cellZ) < 1) continue;
                    double[] levels = setup.terrain.levels(besideX, besideZ);
                    boolean tall = beside.height() > HowlingCanyonTerrain.FLOOR_Y + levels[1] - 2;
                    besideLength += 2 * HowlingCanyonTerrain.STEP;
                    if (tall) besideHigh++;
                    if (!tall && high) breaks++;
                    high = tall;
                }
            }
        }
        require(districts > 6, "Too few Howling Canyon districts scanned: " + districts);
        require(openEnds >= districts * 2 * 0.75, "Rivers must mostly open into neighbouring biomes: " + openEnds + " of " + 2 * districts + " ends");
        require(riverIce >= riverPoints * 0.9, "The frozen river must run up to the biome edge: " + riverIce + " of " + riverPoints);
        require(groundBanks >= banks * 0.9, "The river needs open ground on both banks: " + groundBanks + " of " + banks);
        // High massif ground beside the river, but broken up: on average a break at least every 600 blocks.
        require(besideHigh > 0 && breaks * 600 >= besideLength, "The massifs beside the river must be broken up by gaps: "
                + breaks + " breaks over " + (int) besideLength + " blocks");
        // Bends of uneven length: a sine wave would make them all the same.
        double mean = bends.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double spread = Math.sqrt(bends.stream().mapToDouble(b -> (b - mean) * (b - mean)).average().orElse(0)) / mean;
        double shortestBend = bends.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double longestBend = bends.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        require(bends.size() > 3 * districts && spread > 0.3 && longestBend > 2.5 * shortestBend,
                "River bends must differ in length: " + bends.size() + " bends, spread " + spread);
        System.out.printf("River checks passed: %d districts, rivers %.0f-%.0f blocks long, %d of %d ends open into neighbouring"
                        + " biomes, river ice on %d%% of the course, open ground on %d%% of the banks; beside the river a gap between"
                        + " massifs every %.0f blocks; bends %.0f-%.0f blocks long (spread %.0f%%).%n", districts, shortest, longest, openEnds,
                2 * districts, riverIce * 100 / Math.max(1, riverPoints), groundBanks * 100 / Math.max(1, banks),
                besideLength / Math.max(1, breaks), shortestBend, longestBend, 100 * spread);
    }

    /**
     * Lengths (blocks) of a river's bends: stretches turning the same way, measured between the points where it
     * starts turning the other way (the turn over 24 blocks; wiggles under 40 blocks and the partial bends at both
     * ends are left out).
     */
    private static java.util.List<Double> bends(HowlingCanyonTerrain.Course river) {
        java.util.List<Double> lengths = new java.util.ArrayList<>();
        int span = 6, start = -1, sign = 0;
        for (int i = span; i < river.size() - span; i++) {
            double ax = river.x()[i] - river.x()[i - span], az = river.z()[i] - river.z()[i - span];
            double bx = river.x()[i + span] - river.x()[i], bz = river.z()[i + span] - river.z()[i];
            int turn = ax * bz - az * bx >= 0 ? 1 : -1;
            if (turn == sign) continue;
            if (start >= 0 && i - start >= 10) lengths.add(arc(river, start, i));
            if (i - start >= 10 || start < 0) start = i;
            sign = turn;
        }
        return lengths;
    }

    private static double arc(HowlingCanyonTerrain.Course river, int from, int to) {
        double length = 0;
        for (int i = from; i < to; i++) length += Math.hypot(river.x()[i + 1] - river.x()[i], river.z()[i + 1] - river.z()[i]);
        return length;
    }

    /**
     * Natural, never mathematical (decision 31): massif tops lie off-centre, outlines vary with direction (no circles
     * or ellipses), sizes vary, and neighbouring sites are not evenly spaced.
     */
    private static void checkNaturalMassifs() {
        HowlingCanyonTerrain terrain = new HowlingCanyonTerrain(5);
        int massifs = 0, offCentre = 0, irregular = 0, twinTops = 0;
        double smallest = Double.POSITIVE_INFINITY, largest = 0;
        java.util.List<Double> spacing = new java.util.ArrayList<>();
        for (int cellZ = -12; cellZ <= 12; cellZ++) {
            for (int cellX = -12; cellX <= 12; cellX++) {
                spacing.add(Math.hypot(terrain.siteX(cellX + 1, cellZ) - terrain.siteX(cellX, cellZ), terrain.siteZ(cellX + 1, cellZ) - terrain.siteZ(cellX, cellZ)));
                if (terrain.empty(cellX, cellZ)) continue;
                massifs++;
                double radius = terrain.radius(cellX, cellZ);
                smallest = Math.min(smallest, radius);
                largest = Math.max(largest, radius);
                HowlingCanyonTerrain.Summits summits = terrain.summits(cellX, cellZ);
                double offset = Math.hypot(summits.main().x() - terrain.siteX(cellX, cellZ), summits.main().z() - terrain.siteZ(cellX, cellZ));
                if (offset > 0.15 * radius) offCentre++;
                if (summits.second() != null) twinTops++;
                double low = Double.POSITIVE_INFINITY, high = 0;
                for (int k = 0; k < 72; k++) {
                    double reach = 1 + terrain.shape(cellX, cellZ, k * Math.PI / 36);
                    low = Math.min(low, reach);
                    high = Math.max(high, reach);
                }
                if (high > 1.25 * low) irregular++;
            }
        }
        double mean = spacing.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double spread = Math.sqrt(spacing.stream().mapToDouble(s -> (s - mean) * (s - mean)).average().orElse(0)) / mean;
        require(offCentre > massifs * 0.6, "Massif tops must lie off-centre: " + offCentre + " of " + massifs);
        require(irregular > massifs * 0.8, "Massif outlines must vary with direction: " + irregular + " of " + massifs);
        require(twinTops > massifs * 0.3, "Some massifs need a second top: " + twinTops + " of " + massifs);
        require(largest > 2 * smallest, "Massif sizes must vary: radius " + smallest + "-" + largest);
        require(spread > 0.25, "Massifs must not sit on an even grid: spacing spread " + spread);
        System.out.printf("Natural shape checks passed: %d massifs, %d%% with off-centre tops, %d%% with a second top, %d%% with"
                        + " outlines varying over 25%% with direction, radius %.0f-%.0f, site spacing spread %.0f%%.%n", massifs,
                offCentre * 100 / massifs, twinTops * 100 / massifs, irregular * 100 / massifs, smallest, largest, 100 * spread);
    }

    /**
     * Every cliff is a flat wall in 2 or 3 even steps (decision 30): walking up the field through a cliff, the
     * height climbs in 2-3 sheer pieces with a flat ledge between them, and never goes back down. No random columns.
     */
    private static void checkCliffSteps() {
        HowlingCanyonTerrain terrain = new HowlingCanyonTerrain(9);
        double[] levels = terrain.levels(0, 0);
        int[] counts = new int[4];
        for (long key = 0; key < 400; key++) {
            for (int k = 0; k < HowlingCanyonTerrain.TIERS; k++) {
                double band = levels[k + 1] - levels[k], previous = terrain.terrace(levels[k] + 0.01, levels, key);
                int walls = 0;
                boolean climbing = false;
                for (double f = 0.01; f < 1; f += 0.0005) {
                    double height = terrain.terrace(levels[k] + f * band, levels, key);
                    require(height >= previous - 1e-9, "A cliff must never go back down");
                    // A sheer piece of wall: climbing fast; a ledge or bench: (nearly) flat.
                    boolean steep = (height - previous) / (0.0005 * band) > 2;
                    if (steep && !climbing) walls++;
                    climbing = steep;
                    previous = height;
                }
                require(walls == terrain.cliffSteps(key, k), "A cliff must have exactly its 2 or 3 steps: " + walls);
                counts[walls]++;
            }
        }
        require(counts[2] > 0 && counts[3] > 0, "Cliffs must come with both 2 and 3 steps");
        System.out.printf("Cliff checks passed: flat walls in 2 steps (%d cliffs) or 3 steps (%d cliffs), no random columns.%n", counts[2], counts[3]);
    }

    /**
     * Separate glacier massifs rising in tiers, with gaps between them down to the ground, the first tier and the
     * second tier.
     */
    private static void checkMassifs() {
        HowlingCanyonTerrain terrain = new HowlingCanyonTerrain(5);
        HowlingCanyonTerrain.Course[] none = new HowlingCanyonTerrain.Course[0];
        int samples = 0, ground = 0, tiered = 0, summits = 0;
        int[] gapTiers = new int[HowlingCanyonTerrain.TIERS];
        for (int z = -4000; z < 4000; z += 8) {
            for (int x = -4000; x < 4000; x += 8) {
                double height = terrain.column(x, z, 80, 1, none).height() - HowlingCanyonTerrain.FLOOR_Y;
                double[] levels = terrain.levels(x, z);
                samples++;
                if (height < 4) ground++;
                if (height > levels[2] - 4) tiered++;
                if (height > levels[3] - 4) summits++;
                int gap = terrain.gapTier(x, z);
                if (gap >= 0) gapTiers[gap]++;
            }
        }
        int gaps = gapTiers[0] + gapTiers[1] + gapTiers[2];
        require(ground > samples * 0.15 && ground < samples * 0.55, "Open ground must surround the massifs: " + ground * 100 / samples + "%");
        require(tiered > samples * 0.1, "Massifs must rise at least two tiers: " + tiered * 100 / samples + "%");
        require(summits > samples * 0.03, "Some massifs must reach the high tiers: " + summits * 100 / samples + "%");
        for (int tier = 0; tier < HowlingCanyonTerrain.TIERS; tier++) {
            require(gapTiers[tier] > gaps * 0.05, "Gaps between massifs must reach the ground and both lower tiers: " + java.util.Arrays.toString(gapTiers));
        }
        System.out.printf("Massif checks passed: open ground %d%%, two tiers or more %d%%, three or more %d%%; gaps down to the"
                        + " ground / first tier / second tier %s.%n", ground * 100 / samples, tiered * 100 / samples, summits * 100 / samples,
                java.util.Arrays.toString(gapTiers));
    }

    /**
     * Borders, the district boundary and neighbouring biomes stay untouched; the canyon is deep, with sheer tiers,
     * benches, a wide floor and a frozen river, and never reaches sea level.
     */
    private static void checkTerrain() {
        int samples = 0, full = 0, floor = 0, river = 0, sheer = 0, benches = 0, borderColumns = 0, tallWalls = 0, neighbours = 0, glacierBorder = 0;
        float lowest = 999, highest = -999;
        int[] heights = new int[400];
        for (long seed = 1; seed <= 2; seed++) {
            Setup setup = new Setup(seed);
            Random random = new Random(seed);
            for (int i = 0; i < 300000; i++) {
                int x = random.nextInt(24000) - 12000, z = random.nextInt(24000) - 12000;
                HowlingCanyonColumns.Column column = setup.columns.column(x, z);
                RegionLayout.Sample sample = setup.layout.sample(x, z);
                float rock = setup.layout.surfaceHeight(x, z, sample);
                if (column == null) {
                    boolean canyon = sample.region() == FrozenLand.REGION_INDEX
                            && setup.districts.district(sample, FrozenLand.POOL_SIZE).slot() == FrozenLand.HOWLING_CANYON_SLOT;
                    require(!canyon, "A Howling Canyon column was not recognised");
                    continue;
                }
                require(column.rockTop() == rock, "The region and border rock must stay unchanged");
                double edge = setup.districts.district(sample, FrozenLand.POOL_SIZE).edge();
                if (sample.border() > 0) {
                    borderColumns++;
                    // Only a glacier coming down from the mountains touches them (decision 33): its ice lies on the
                    // unchanged rock, within its reach; beyond it the mountain is untouched.
                    double depth = 360 - sample.edgeDistance();
                    if (depth > HowlingCanyonTerrain.CLIMB_MAX) require(column.height() == rock, "Howling Canyon relief reaches beyond its glaciers");
                    require(column.height() >= rock, "A glacier must lie on the rock, never cut into it");
                    require(!column.member() || sample.border() <= RegionLayout.BORDER_THRESHOLD || column.height() > rock,
                            "Border biome columns are only Howling Canyon's where a glacier lies on them");
                    if (column.height() != rock) glacierBorder++;
                    continue;
                }
                if (edge < 0.5) require(Math.abs(column.height() - rock) < 0.6, "Howling Canyon relief must vanish at its biome edge");
                samples++;
                lowest = Math.min(lowest, column.height());
                highest = Math.max(highest, column.height());
                require(column.height() >= setup.layout.basinFloor(x, z, sample) + 3, "The deepslate basin must stay below the canyon");
                require(column.bed() <= column.height() - 3.99, "The glacier must keep a few blocks of ice above the rock");
                double weight = HowlingCanyonTerrain.districtWeight(edge) * HowlingCanyonTerrain.edgeFade(sample.edgeDistance(), 360);
                if (weight < 1) continue;
                full++;
                heights[(int) column.height()]++;
                if (column.feature() == HowlingCanyonTerrain.FLOOR) floor++;
                if (column.feature() == HowlingCanyonTerrain.RIVER) river++;
                HowlingCanyonColumns.Column east = setup.columns.column(x + 1, z), west = setup.columns.column(x - 1, z);
                HowlingCanyonColumns.Column south = setup.columns.column(x, z + 1), north = setup.columns.column(x, z - 1);
                if (east == null || west == null || south == null || north == null) continue;
                double drop = Math.max(Math.abs(east.height() - west.height()), Math.abs(south.height() - north.height()));
                // A step of a cliff: a sheer piece of wall several blocks high.
                if (drop >= 6 && drop <= 20) sheer++;
                // No tall sheer walls: every cliff climbs in steps of at most about 17 blocks (decision 30).
                neighbours++;
                if (drop > 20) tallWalls++;
                if (drop < 2 && column.height() > HowlingCanyonTerrain.FLOOR_Y + 10 && column.height() < HowlingCanyonTerrain.FLOOR_Y + 55) benches++;
            }
        }
        int p5 = percentile(heights, full, 0.05), p95 = percentile(heights, full, 0.95);
        require(lowest > 62, "Howling Canyon reaches sea level (water would fill it): " + lowest);
        require(highest < 200, "Howling Canyon rises too high: " + highest);
        require(full > 2000, "Too few full-strength Howling Canyon columns: " + full);
        require(p95 - p5 >= 45, "The canyon must be deep: Y=" + p5 + "-" + p95 + " (5th-95th percentile)");
        require(floor > full * 0.04, "The canyon needs a wide floor: " + floor + " of " + full);
        require(river > full * 0.005 && river < floor, "The canyon floor needs a frozen river: " + river);
        require(tallWalls == 0, "Walls must climb in steps, never as one tall sheer wall: " + tallWalls + " drops over 20 blocks");
        require(sheer > full * 0.005, "The massifs need stepped cliffs: " + sheer + " of " + full);
        require(benches > full * 0.05, "The canyon walls need benches between the cliffs: " + benches + " of " + full);
        System.out.printf("Terrain checks passed: Y=%.0f-%.0f (Y=%d-%d for 90%% at full strength), floor %.1f%%, river %.1f%%,"
                        + " cliff steps %.1f%%, benches %.1f%%; %d of %d border columns carry a glacier on the rock, the rest untouched.%n",
                lowest, highest, p5, p95, 100.0 * floor / full, 100.0 * river / full, 100.0 * sheer / full, 100.0 * benches / full,
                glacierBorder, borderColumns);
    }

    /**
     * Glaciers come down from the border mountains and turn into the canyon walls (decision 33): near the mountains
     * the massifs reach out to them, high and in flat stepped walls (no drop over 20 blocks), and their ice lies on
     * the mountain slope; gaps, empty ground and the river keep their way to the mountains.
     */
    private static void checkGlaciers() {
        int zone = 0, glacier = 0, tallWalls = 0, onMountain = 0;
        double height = 0, reach = 0;
        for (long seed = 1; seed <= 2; seed++) {
            Setup setup = new Setup(seed);
            Random random = new Random(seed + 40);
            for (int i = 0; i < 1_500_000; i++) {
                int x = random.nextInt(24000) - 12000, z = random.nextInt(24000) - 12000;
                RegionLayout.Sample sample = setup.layout.sample(x, z);
                double depth = 360 - sample.edgeDistance();
                if (depth < -60 || depth > HowlingCanyonTerrain.CLIMB_MAX) continue;
                HowlingCanyonColumns.Column column = setup.columns.column(x, z);
                if (column == null) continue;
                BiomeDistricts.District district = setup.districts.district(sample, FrozenLand.POOL_SIZE);
                if (HowlingCanyonTerrain.districtWeight(district.edge()) < 1) continue;
                if (column.border()) {
                    if (column.member()) {
                        onMountain++;
                        reach = Math.max(reach, depth);
                    }
                    continue;
                }
                if (depth > 60) continue;
                zone++;
                if (column.height() < HowlingCanyonTerrain.FLOOR_Y + 20) continue;
                glacier++;
                height += column.height() - HowlingCanyonTerrain.FLOOR_Y;
                HowlingCanyonColumns.Column east = setup.columns.column(x + 1, z), west = setup.columns.column(x - 1, z);
                HowlingCanyonColumns.Column south = setup.columns.column(x, z + 1), north = setup.columns.column(x, z - 1);
                if (east == null || west == null || south == null || north == null) continue;
                double drop = Math.max(Math.abs(east.height() - west.height()), Math.abs(south.height() - north.height()));
                if (drop > 20 && !east.border() && !west.border() && !south.border() && !north.border()) tallWalls++;
            }
        }
        require(glacier > zone * 0.3, "Glaciers must come down from the mountains: " + glacier + " of " + zone + " columns before them");
        require(glacier < zone * 0.9, "Gaps and open ground must keep their way to the mountains: " + glacier + " of " + zone);
        require(height / glacier > 30, "The glaciers before the mountains must stand high: " + height / glacier + " blocks");
        require(tallWalls == 0, "Glacier walls must climb in steps, never as one tall sheer wall: " + tallWalls);
        require(onMountain > 0 && reach > 100, "The glaciers must come down from the mountain slope: " + onMountain + " columns, up to " + reach);
        System.out.printf("Glacier checks passed: %d%% of the ground before the mountains under glaciers (on average %.0f blocks high),"
                        + " stepped walls; ice on %d mountain columns up to %.0f blocks into the belt.%n", glacier * 100 / zone,
                height / glacier, onMountain, reach);
    }

    /** Everwinter is frozen: its columns come from EverwinterColumns alone, and Howling Canyon never claims one. */
    private static void checkEverwinterUnchanged() {
        Setup setup = new Setup(3);
        EverwinterTerrain everwinter = new EverwinterTerrain(3);
        EverwinterColumns columns = new EverwinterColumns(setup.layout, setup.districts, everwinter,
                new EverwinterGroups(setup.layout, setup.districts, everwinter, 360, 3), 360);
        Random random = new Random(11);
        int checked = 0;
        for (int i = 0; i < 200000; i++) {
            int x = random.nextInt(24000) - 12000, z = random.nextInt(24000) - 12000;
            EverwinterColumns.Column column = columns.column(x, z);
            if (!column.member() && column.fringe() == 0) continue;
            require(setup.columns.column(x, z) == null, "Howling Canyon claims an Everwinter column");
            checked++;
        }
        require(checked > 1000, "Too few Everwinter columns checked: " + checked);
        System.out.printf("Everwinter checks passed: %d Everwinter columns left to Everwinter.%n", checked);
    }

    private static void checkMaterials() {
        HowlingCanyonPalette palette = new HowlingCanyonPalette(99L);
        Random random = new Random(5);
        int cover = 0, ice = 0, under = 0, deepslate = 0, walls = 0, blue = 0, floes = 0, river = 0;
        for (int i = 0; i < 200000; i++) {
            int x = random.nextInt(20000) - 10000, z = random.nextInt(20000) - 10000;
            var top = palette.select(x, 130, z, 130, 60, HowlingCanyonTerrain.PLATEAU, true);
            require(top == HowlingCanyonPalette.Material.SNOW || top == HowlingCanyonPalette.Material.PACKED_ICE, "Massif tops must be snow");
            cover++;
            if (top == HowlingCanyonPalette.Material.PACKED_ICE) ice++;
            int y = 64 + random.nextInt(60);
            var wall = palette.select(x, y, z, 130, 60, HowlingCanyonTerrain.PLATEAU, false);
            require(wall != HowlingCanyonPalette.Material.STONE && wall != HowlingCanyonPalette.Material.DEEPSLATE, "Canyon walls must be ice and snow");
            walls++;
            if (wall == HowlingCanyonPalette.Material.BLUE_ICE) blue++;
            var surface = palette.select(x, 65, z, 65, 60, HowlingCanyonTerrain.RIVER, true);
            require(surface == HowlingCanyonPalette.Material.SNOW || surface == HowlingCanyonPalette.Material.PACKED_ICE, "River must be ice and floes");
            river++;
            if (surface == HowlingCanyonPalette.Material.SNOW) floes++;
            int rock = random.nextInt(60);
            var underground = palette.select(x, rock, z, 66, 61, HowlingCanyonTerrain.FLOOR, true);
            require(underground == HowlingCanyonPalette.Material.STONE || underground == HowlingCanyonPalette.Material.DEEPSLATE,
                    "Underground must be stone or deepslate");
            under++;
            if (underground == HowlingCanyonPalette.Material.DEEPSLATE) deepslate++;
        }
        require(ice < cover * 0.15, "Ice patches must stay a minor part of the snow: " + ice * 100 / cover + "%");
        require(blue > walls * 0.15 && blue < walls * 0.7, "Walls must mix packed and blue ice: " + blue * 100 / walls + "% blue");
        require(floes > river * 0.3 && floes < river * 0.85, "The river needs floes and open ice leads: " + floes * 100 / river + "% floes");
        require(deepslate > under * 0.3 && deepslate < under * 0.7, "Underground must mix stone and deepslate: " + deepslate * 100 / under + "%");
        System.out.printf("Material checks passed: massif-top ice patches %d%%, walls %d%% blue ice, river %d%% floes, underground %d%% deepslate.%n",
                ice * 100 / cover, blue * 100 / walls, floes * 100 / river, deepslate * 100 / under);
    }

    private static void checkWiring() throws Exception {
        String preset = Files.readString(WORLDGEN.resolve("world_preset/regions.json"));
        Matcher pool = Pattern.compile("\"frozen_wastes\"\\s*:\\s*\\[([^]]*)]").matcher(preset);
        require(pool.find(), "Frozen Wastes pool missing");
        String[] entries = pool.group(1).replaceAll("[\\s\"]", "").split(",");
        require(entries.length == FrozenLand.POOL_SIZE, "FrozenLand.POOL_SIZE does not match the preset");
        require(entries[FrozenLand.HOWLING_CANYON_SLOT].equals("cronusfantasyterrain:howling_canyon"),
                "FrozenLand.HOWLING_CANYON_SLOT does not point at Howling Canyon in the preset");
        String rules = Files.readString(WORLDGEN.resolve("material_rule/regions.json"));
        require(rules.matches("(?s).*\"cronusfantasyterrain:everwinter\",\\s*\"cronusfantasyterrain:howling_canyon\",\\s*\"minecraft:overworld\".*"),
                "The Howling Canyon material rule must run after Everwinter's and before vanilla's");
        String biome = Files.readString(WORLDGEN.resolve("biome/howling_canyon.json"));
        require(!biome.contains("minecraft:spring_water") && !biome.contains("lake_water"), "Howling Canyon must not spawn water");
        require(!biome.contains("\"minecraft:canyon\""), "Ravines would cut slots through the ice walls");
        require(biome.contains("minecraft:freeze_top_layer"), "Howling Canyon needs the vanilla snow layer");
    }

    /** Layout, districts, canyon layout and columns for one test seed. */
    private static final class Setup {
        final RegionLayout layout;
        final BiomeDistricts districts;
        final HowlingCanyonCourses courses;
        final HowlingCanyonColumns columns;
        final HowlingCanyonTerrain terrain;

        Setup(long seed) {
            layout = new RegionLayout(seed * 977, 3800, 360);
            districts = new BiomeDistricts(seed * 31, 3800);
            terrain = new HowlingCanyonTerrain(seed);
            courses = new HowlingCanyonCourses(layout, districts, terrain, 360, seed);
            columns = new HowlingCanyonColumns(layout, districts, terrain, courses, 360);
        }
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
