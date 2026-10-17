package c.cronus.fantasyterrain.worldgen;

import c.cronus.fantasyterrain.FrozenLand;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Places the serac fields (plate groups, {@link EverwinterTerrain.Group}) of every Everwinter district: two or
 * three per district, each lying well inside the biome and apart from the others. Pure Java (no Minecraft
 * dependencies). Placement depends only on the seeds and the region, so every chunk sees the same fields; the
 * result is cached per region.
 */
public final class EverwinterGroups {
    /** Serac fields per district: at least FIELDS_MIN, at most FIELDS_MAX (random per region). */
    public static final int FIELDS_MIN = 2, FIELDS_MAX = 3;
    /** Open ground kept between two serac fields, beyond both fields' reach. */
    public static final double FIELD_GAP = 50;
    /** Candidate positions tried per region and search pass, within SEARCH_RADIUS blocks of the region centre. */
    private static final int ATTEMPTS = 1000;
    /** Random picking orders tried to fit as many fields as possible. */
    private static final int ORDERS = 64;
    private static final double SEARCH_RADIUS = 1800;
    /**
     * Points on a circle around a candidate that must lie in the biome with at least RIM_WEIGHT relief, at
     * RIM_SHARE of the field's reach: strict in the first pass, relaxed in the second (narrow districts).
     */
    private static final int RIM_POINTS = 16;
    private static final double[] RIM_SHARE = {0.8, 0.55}, RIM_WEIGHT = {0.3, 0.1};
    private final RegionLayout layout;
    private final BiomeDistricts districts;
    private final EverwinterTerrain terrain;
    private final int borderWidth;
    private final long seed;
    private final Map<Long, EverwinterTerrain.Group[]> cache = new ConcurrentHashMap<>();

    public EverwinterGroups(RegionLayout layout, BiomeDistricts districts, EverwinterTerrain terrain, int borderWidth, long seed) {
        this.layout = layout;
        this.districts = districts;
        this.terrain = terrain;
        this.borderWidth = borderWidth;
        this.seed = seed;
    }

    /** Serac fields of the region a column belongs to. */
    public EverwinterTerrain.Group[] groups(RegionLayout.Sample sample) {
        int cellX = sample.cellX(), cellZ = sample.cellZ();
        return cache.computeIfAbsent(((long) cellX << 32) ^ (cellZ & 0xFFFFFFFFL), key -> place(cellX, cellZ));
    }

    /** Relief weight at a column if it is Everwinter in the given region, otherwise -1. */
    public double weight(int x, int z, int cellX, int cellZ) {
        RegionLayout.Sample sample = layout.sample(x, z);
        if (sample.cellX() != cellX || sample.cellZ() != cellZ || sample.region() != FrozenLand.REGION_INDEX
                || sample.border() > RegionLayout.BORDER_THRESHOLD) return -1;
        BiomeDistricts.District district = districts.district(sample, FrozenLand.POOL_SIZE);
        if (district.slot() != FrozenLand.EVERWINTER_SLOT) return -1;
        return EverwinterTerrain.districtWeight(district.edge()) * EverwinterTerrain.edgeFade(sample.edgeDistance(), borderWidth);
    }

    /**
     * Collects fields that fit inside the district (strict pass first, then relaxed), then tries several random
     * orders of picking them greedily, keeping the order that places the most fields apart from each other.
     */
    private EverwinterTerrain.Group[] place(int cellX, int cellZ) {
        SplittableRandom random = new SplittableRandom(seed ^ (cellX * 0x9E3779B97F4A7C15L) ^ (cellZ * 0xC2B2AE3D27D4EB4FL));
        int target = FIELDS_MIN + random.nextInt(FIELDS_MAX - FIELDS_MIN + 1);
        double centreX = layout.siteX(cellX, cellZ), centreZ = layout.siteZ(cellX, cellZ);
        List<List<EverwinterTerrain.Group>> candidates = new ArrayList<>();
        for (int pass = 0; pass < RIM_SHARE.length; pass++) {
            List<EverwinterTerrain.Group> fitting = new ArrayList<>();
            for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                double distance = SEARCH_RADIUS * Math.sqrt(random.nextDouble()), angle = 2 * Math.PI * random.nextDouble();
                double x = centreX + distance * Math.cos(angle), z = centreZ + distance * Math.sin(angle);
                EverwinterTerrain.Group group = terrain.group(random.nextLong(), x, z);
                if (inside(group, cellX, cellZ, pass)) fitting.add(group);
            }
            candidates.add(fitting);
        }
        List<EverwinterTerrain.Group> best = List.of();
        for (int trial = 0; trial < ORDERS && best.size() < target; trial++) {
            List<EverwinterTerrain.Group> placed = new ArrayList<>(target);
            for (List<EverwinterTerrain.Group> fitting : candidates) {
                List<EverwinterTerrain.Group> order = new ArrayList<>(fitting);
                for (int k = order.size() - 1; k > 0; k--) java.util.Collections.swap(order, k, random.nextInt(k + 1));
                for (EverwinterTerrain.Group group : order) {
                    if (placed.size() < target && apart(group, placed)) placed.add(group);
                }
            }
            if (placed.size() > best.size()) best = placed;
        }
        return best.toArray(new EverwinterTerrain.Group[0]);
    }

    private static boolean apart(EverwinterTerrain.Group group, List<EverwinterTerrain.Group> placed) {
        for (EverwinterTerrain.Group other : placed) {
            if (Math.hypot(group.x() - other.x(), group.z() - other.z()) < group.reach() + other.reach() + FIELD_GAP) return false;
        }
        return true;
    }

    /** Whether a field lies inside the district: full relief at its centre and enough around it. */
    private boolean inside(EverwinterTerrain.Group group, int cellX, int cellZ, int pass) {
        if (weight((int) group.x(), (int) group.z(), cellX, cellZ) < 1) return false;
        for (int k = 0; k < RIM_POINTS; k++) {
            double angle = 2 * Math.PI * k / RIM_POINTS, distance = RIM_SHARE[pass] * group.reach();
            int x = (int) (group.x() + distance * Math.cos(angle)), z = (int) (group.z() + distance * Math.sin(angle));
            if (weight(x, z, cellX, cellZ) < RIM_WEIGHT[pass]) return false;
        }
        return true;
    }
}
