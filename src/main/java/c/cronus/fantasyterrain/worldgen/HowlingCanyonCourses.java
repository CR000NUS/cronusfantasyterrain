package c.cronus.fantasyterrain.worldgen;

import c.cronus.fantasyterrain.FrozenLand;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lays out the frozen river of every Howling Canyon district: it meanders right through the district and out of
 * it at both ends, so it opens into the neighbouring biomes (no closed ends, decision 28); exits into other
 * districts are preferred over exits towards the border mountains. The glacier massifs between which it runs come
 * from {@link HowlingCanyonTerrain} itself. Pure Java (no Minecraft dependencies). The layout depends only on the
 * seeds and the region, so every chunk sees the same river; the result is cached per region.
 */
public final class HowlingCanyonCourses {
    /** Shortest stretch of river with full relief worth placing; smaller districts keep only the massifs. */
    public static final double MIN_LENGTH = 400;
    /** How far the river continues past the district edge, so it stays open to it. */
    private static final double EXTEND = 60;
    /** Room checked on both sides of the river: its ground strip stays within the biome. */
    private static final double LATERAL = 110;
    /** District search around the region centre, and the river lines tried through the district centre. */
    private static final double SCAN = 2100, SCAN_STEP = 48, SPAN = 3000, FIT_STEP = 16;
    private static final double[] OFFSETS = {0, -150, 150, -300, 300};
    private static final int ANGLES = 16;
    private static final HowlingCanyonTerrain.Course[] NONE = new HowlingCanyonTerrain.Course[0];
    private final RegionLayout layout;
    private final BiomeDistricts districts;
    private final HowlingCanyonTerrain terrain;
    private final int borderWidth;
    private final long seed;
    private final Map<Long, HowlingCanyonTerrain.Course[]> cache = new ConcurrentHashMap<>();

    public HowlingCanyonCourses(RegionLayout layout, BiomeDistricts districts, HowlingCanyonTerrain terrain, int borderWidth, long seed) {
        this.layout = layout;
        this.districts = districts;
        this.terrain = terrain;
        this.borderWidth = borderWidth;
        this.seed = seed;
    }

    /** The river of the region a column belongs to (empty if the district is too small). */
    public HowlingCanyonTerrain.Course[] courses(RegionLayout.Sample sample) {
        int cellX = sample.cellX(), cellZ = sample.cellZ();
        return cache.computeIfAbsent(((long) cellX << 32) ^ (cellZ & 0xFFFFFFFFL), key -> place(cellX, cellZ));
    }

    /** Relief weight at a column if it is Howling Canyon in the given region, otherwise -1. */
    public double weight(double x, double z, int cellX, int cellZ) {
        RegionLayout.Sample sample = layout.sample((int) Math.floor(x), (int) Math.floor(z));
        if (sample.cellX() != cellX || sample.cellZ() != cellZ || sample.region() != FrozenLand.REGION_INDEX
                || sample.border() > RegionLayout.BORDER_THRESHOLD) return -1;
        BiomeDistricts.District district = districts.district(sample, FrozenLand.POOL_SIZE);
        if (district.slot() != FrozenLand.HOWLING_CANYON_SLOT) return -1;
        return HowlingCanyonTerrain.districtWeight(district.edge()) * HowlingCanyonTerrain.edgeFade(sample.edgeDistance(), borderWidth);
    }

    /** Whether a point just outside the district opens into a neighbouring biome (not the border mountains). */
    public boolean opensToBiome(double x, double z) {
        return layout.sample((int) Math.floor(x), (int) Math.floor(z)).border() <= RegionLayout.BORDER_THRESHOLD;
    }

    private HowlingCanyonTerrain.Course[] place(int cellX, int cellZ) {
        SplittableRandom random = new SplittableRandom(seed ^ (cellX * 0x9E3779B97F4A7C15L) ^ (cellZ * 0xC2B2AE3D27D4EB4FL));
        // Centre of the district (in the world, around the region centre).
        double centreX = layout.siteX(cellX, cellZ), centreZ = layout.siteZ(cellX, cellZ), sumX = 0, sumZ = 0;
        int count = 0;
        for (double dz = -SCAN; dz <= SCAN; dz += SCAN_STEP) {
            for (double dx = -SCAN; dx <= SCAN; dx += SCAN_STEP) {
                if (dx * dx + dz * dz > SCAN * SCAN || weight(centreX + dx, centreZ + dz, cellX, cellZ) < 0) continue;
                sumX += centreX + dx;
                sumZ += centreZ + dz;
                count++;
            }
        }
        if (count == 0) return NONE;
        Meander meander = new Meander(120 + 60 * random.nextDouble(), 280 + 140 * random.nextDouble(),
                80 + 80 * random.nextDouble(), 1000 * random.nextDouble());
        double originX = sumX / count, originZ = sumZ / count, rotation = Math.PI * random.nextDouble();

        // The river: the line through the district that leaves it at both ends, preferably into neighbouring
        // biomes at both, with the most room beside it.
        Line best = null;
        for (int a = 0; a < ANGLES; a++) {
            double angle = rotation + Math.PI * a / ANGLES;
            for (double offset : OFFSETS) {
                Line line = trace(originX - Math.sin(angle) * offset, originZ + Math.cos(angle) * offset, angle, meander, cellX, cellZ);
                if (line != null && (best == null || line.score() > best.score())) best = line;
            }
        }
        if (best == null || best.room() < MIN_LENGTH) return NONE;
        int points = (int) ((best.end() - best.start() + 2 * EXTEND) / HowlingCanyonTerrain.STEP) + 1;
        double[] mainX = new double[points], mainZ = new double[points];
        for (int i = 0; i < points; i++) {
            double s = best.start() - EXTEND + i * HowlingCanyonTerrain.STEP;
            double[] point = best.point(s, meander, terrain);
            mainX[i] = point[0];
            mainZ[i] = point[1];
        }
        return new HowlingCanyonTerrain.Course[] {HowlingCanyonTerrain.Course.of(mainX, mainZ)};
    }

    /**
     * Traces a river line through (x, z) both ways until it leaves the district; null if it starts outside
     * full relief. Its score prefers lines opening into neighbouring biomes at both ends, then the most room.
     */
    private Line trace(double x, double z, double angle, Meander meander, int cellX, int cellZ) {
        Line line = new Line(x, z, Math.cos(angle), Math.sin(angle), 0, 0, 0, 0);
        double[] centre = line.point(0, meander, terrain);
        if (weight(centre[0], centre[1], cellX, cellZ) < 0.9) return null;
        double[] ends = new double[2];
        boolean[] open = new boolean[2];
        double room = 0;
        for (int direction = 0; direction < 2; direction++) {
            double sign = direction == 0 ? -1 : 1, s = 0;
            while (true) {
                s += sign * FIT_STEP;
                double[] point = line.point(s, meander, terrain);
                if (Math.abs(s) > SPAN || weight(point[0], point[1], cellX, cellZ) < 0) {
                    open[direction] = Math.abs(s) <= SPAN && opensToBiome(point[0], point[1]);
                    break;
                }
                if (fits(line, s, meander, cellX, cellZ)) room += FIT_STEP;
            }
            ends[direction] = s;
        }
        int opened = (open[0] ? 1 : 0) + (open[1] ? 1 : 0);
        return new Line(x, z, line.ux(), line.uz(), ends[0], ends[1], room, opened * 100_000 + room);
    }

    /** Whether the river has full relief at {@code s} along its line and room on both sides. */
    private boolean fits(Line line, double s, Meander meander, int cellX, int cellZ) {
        double[] point = line.point(s, meander, terrain);
        double x = point[0], z = point[1], ux = line.ux(), uz = line.uz();
        return weight(x, z, cellX, cellZ) >= 0.9
                && weight(x - uz * LATERAL, z + ux * LATERAL, cellX, cellZ) >= 0.15
                && weight(x + uz * LATERAL, z - ux * LATERAL, cellX, cellZ) >= 0.15;
    }

    /**
     * A river line: through (x, z) along (ux, uz) with the meander sideways, from {@code start} to
     * {@code end} along its axis (where it leaves the district), {@code room} blocks of it with full relief and
     * room on both sides.
     */
    private record Line(double x, double z, double ux, double uz, double start, double end, double room, double score) {
        double[] point(double s, Meander meander, HowlingCanyonTerrain terrain) {
            double lateral = meander.offset(s, terrain);
            return new double[] {x + ux * s - uz * lateral, z + uz * s + ux * lateral};
        }
    }

    /**
     * Meanders of the river, sideways to its axis: bends of uneven length and swing (two octaves of noise, no sine
     * wave, decision 31) plus a slow drift of the whole course. The noise is read along a slanted line, so its
     * lattice never sets an even rhythm of bends.
     */
    private record Meander(double amplitude, double scale, double drift, double offset) {
        double offset(double s, HowlingCanyonTerrain terrain) {
            double t = s / scale, slow = s / 1500;
            return amplitude * (0.8 * terrain.noise(t, 0.61 * t + offset, 60) + 0.2 * terrain.noise(2.3 * t, 0.43 * t + offset + 17.3, 61))
                    + drift * terrain.noise(slow, 0.37 * slow + offset + 41.9, 62);
        }
    }
}
