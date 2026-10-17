package c.cronus.fantasyterrain.worldgen;

import java.util.function.DoubleBinaryOperator;

/**
 * Everwinter relief on top of the shared region interior (Frozen Wastes). Pure Java, no Minecraft
 * dependencies. Harsh, broken glacier country whose features grow out of the ground instead of sitting on a
 * flat plain: rolling snow ground with medium rolls and wind ridges, merging snow piles and drift fields, big
 * snow/ice hills on broad feet with snow tails in their lee, a few ice cliffs, long deep crevasses, and a few
 * two or three serac fields per biome: groups of 4-7 huge pieces of the glacier ground itself, broken off and
 * tilted 5-30 degrees, sunk below the surrounding ground at one end and raised to a sheer face at the other.
 * <p>
 * A slowly varying shelter field splits the biome into sheltered stretches (long snow ramps, snow banked
 * against cliffs and outcrops) and wind-scoured stretches (short steep rises, sheer edges). Beneath the snow lies a glacier ice sheet whose bed drops below
 * every nearby cut, so cliffs, cracks and crevasses show ice walls. The relief is scaled by a weight that is
 * zero outside the biome and in every column the region border touches, so borders and basin stay untouched.
 */
public final class EverwinterTerrain {
    /** Column feature codes; also used by the material rule. */
    public static final int PLAIN = 0, GLACIER = 1, SPIRE = 2, ROCK = 3, HILL = 4, DRIFT = 5, CREVASSE = 6;
    /** Number of feature codes. */
    public static final int FEATURES = 7;
    /** Distance (blocks) over which the relief fades in, measured inward from where the border mask reaches 0. */
    public static final double EDGE_FADE = 120;
    /** Distance (blocks) over which the relief fades in, measured inward from the biome's district boundary. */
    public static final double DISTRICT_FADE = 96;
    /** Lowest Y a crevasse or crack floor reaches: a few blocks above sea level (63), so they stay dry. */
    public static final double LOWEST_FLOOR = 66;
    /** Average lift of the rolling ground above the plain interior, and its swing up and down. */
    private static final double SWELL_LIFT = 10, SWELL = 18;
    /** Ice cliffs: the contour level they follow. */
    private static final double CLIFF_LEVEL = 0.1;
    /** Plates per serac field (group): one in the middle, the rest in a ring around it. */
    public static final int GROUP_MIN = 4, GROUP_MAX = 7;
    /**
     * Plate radius: 19-28 blocks, scaled by 2.5-3.5 (decision 19) and by GROW (decision 20), so about 57-118
     * blocks.
     */
    private static final double PLATE_RADIUS_MIN = 19, PLATE_RADIUS_MAX = 28, PLATE_SCALE_MIN = 2.5, PLATE_SCALE_MAX = 3.5;
    private static final double GROW = 1.2;
    /** Ring plates sit RING_SPACING times the sum of their and the middle plate's radius from the middle. */
    private static final double RING_SPACING = 0.72;
    /** Serac fields without a region (tests, previews): at most one per GRID_CELL square, in GRID_CHANCE of them. */
    private static final int GRID_CELL = 1000;
    private static final double GRID_CHANCE = 0.8;
    /**
     * Between two neighbouring plates: in CONNECTED of the pairs a tear 1-2 blocks wide (half width
     * TEAR_HALF_MIN-MAX), otherwise a wider gap of snow ground (half width GAP_MIN-MAX, so 16-36 blocks) broken
     * with only gentle, broad rises and dips.
     */
    private static final double CONNECTED = 0.45, TEAR_HALF_MIN = 0.85, TEAR_HALF_MAX = 1.15;
    private static final double GAP_MIN = 8, GAP_MAX = 18;
    /** Depth of the tears between connected plates below the ground (the floor never below LOWEST_FLOOR). */
    private static final double TEAR_MIN = 26, TEAR_MAX = 36;
    /** Gentle rises and dips in the ground between plates: up to RISE blocks, reaching STEP_REACH beyond the outlines. */
    private static final double RISE = 5, STEP_REACH = 18;
    /** Deepest a plate's low end sinks below the ground, and the height its high end levels off towards. */
    private static final double SINK_MAX = 20, RISE_SOFT = 45;
    /** Deepest crack at the foot of a plate's high face, below the ground. */
    private static final double SERAC_CRACK = 22;
    /** Plate tilt: 5 to 30 degrees. */
    private static final double TILT_MIN = 5, TILT_MAX = 30;
    /** Extra ice kept below the deepest nearby cut, so its walls are ice rather than rock. */
    private static final double WALL_ICE = 6;
    private static final int DRIFT_CELL = 40, HILL_CELL = 230, SPIRE_CELL = 72;
    /** Wind direction (snow piles and tails stretch along it). */
    private static final double WIND_X = 0.8, WIND_Z = 0.6;
    private static final Serac NO_SERAC = new Serac(0, 0, 0, -1, false, false);
    private static final Group[] NO_GROUPS = new Group[0];
    /**
     * Everwinter on the border mountains (decisions 24-25): on its side of the border the biome's full relief
     * runs right up to the mountain foot (no flat fading strip) and fades out up the mountain ramp, between
     * REACH_START and REACH_MIN-MAX blocks into the border belt (the ramp starts about 68 blocks in), and below
     * about Y=TOP_LOW-TOP_HIGH. Glacier tongues lie on top of it, growing towards the mountains.
     */
    public static final double REACH_START = 50, REACH_MIN = 130, REACH_MAX = 200;
    private static final double TOP_LOW = 160, TOP_HIGH = 200;
    /** How much further up the mountain the frost reaches than Everwinter's ground. */
    public static final double FROST_EXTRA = 80;
    /** Edge glaciers: up to this far (blocks) out from the border belt, thickness GLACIER_MIN-MAX. */
    private static final double GLACIER_OUT = 220, GLACIER_MIN = 5, GLACIER_MAX = 14;
    private final long seed;
    /** Direction the crevasses run in (seeded). */
    private final double tearCos, tearSin;

    public EverwinterTerrain(long seed) {
        this.seed = seed;
        double angle = Math.PI * unit(hash(0, 0, 109));
        this.tearCos = Math.cos(angle);
        this.tearSin = Math.sin(angle);
    }

    /**
     * @param height  surface height
     * @param iceBase bed of the ice sheet: rock below it (for {@link #ROCK} columns, the snow surface around the knob)
     * @param feature feature code
     */
    public record Column(float height, float iceBase, int feature) {}

    /**
     * Relief weight 0-1 from the distance to the biome's district boundary ({@link BiomeDistricts.District#edge}):
     * 0 at the boundary, 1 from DISTRICT_FADE blocks inside, so neighbouring biomes keep the plain interior.
     */
    public static double districtWeight(double edge) {
        return smoothstep(0, DISTRICT_FADE, edge);
    }

    /** 0 wherever the border mask is above 0 (edge distance below the border width), 1 from EDGE_FADE further in. */
    public static double edgeFade(double edgeDistance, int borderWidth) {
        return smoothstep(borderWidth, borderWidth + EDGE_FADE, edgeDistance);
    }

    /**
     * Relief weight of Everwinter on its side of the border (decision 25): 1 in the region and at the mountain
     * foot, fading out up the ramp in uneven tongues and with height. Multiplied with {@link #districtWeight}.
     *
     * @param depth   blocks behind the start of the border belt (border width minus edge distance; negative
     *                inside the region)
     * @param rockTop surface of the region terrain (plain ground or mountain rock)
     */
    public double borderReach(int x, int z, double depth, double rockTop) {
        return reach(x, z, depth, rockTop, 0);
    }

    /**
     * Frost on the mountain rock above Everwinter's ground (0-1): the same tongues as {@link #borderReach}
     * reaching FROST_EXTRA blocks further up, so snow and ice patches climb on to meet the mountains' snowline.
     */
    public double borderFrost(int x, int z, double depth, double rockTop) {
        return reach(x, z, depth, rockTop, FROST_EXTRA);
    }

    private double reach(int x, int z, double depth, double rockTop, double extra) {
        if (depth <= REACH_START) return 1;
        double reach = REACH_MIN + extra + (REACH_MAX - REACH_MIN) * (0.5 + 0.5 * noise(x / 150.0, z / 150.0, 250))
                + 20 * noise(x / 40.0, z / 40.0, 251);
        return (1 - smoothstep(REACH_START, reach, depth))
                * (1 - smoothstep(TOP_LOW + extra / 2, TOP_HIGH + extra / 2, rockTop + 15 * noise(x / 60.0, z / 60.0, 252)));
    }

    /**
     * Glacier lying on Everwinter's ground near the border: broad, lobed tongues of ice GLACIER_MIN-MAX blocks
     * thick with sheer ice-cliff edges, covering about half of the ground at the mountain foot and thinning out
     * GLACIER_OUT blocks into the region. Thickness in blocks (0: none).
     */
    public double edgeGlacier(int x, int z, double depth) {
        double near = smoothstep(-GLACIER_OUT, 40, depth);
        if (near <= 0) return 0;
        double tongues = fbm(x, z, 160, 3, 253) + 0.12 * noise(x / 18.0, z / 18.0, 254), threshold = 0.5 - 0.5 * near;
        return (GLACIER_MIN + (GLACIER_MAX - GLACIER_MIN) * (0.5 + 0.5 * noise(x / 70.0, z / 70.0, 255)))
                * smoothstep(threshold, threshold + 0.02, tongues);
    }

    /** Where Everwinter's materials take over a border column from the border palette: above about half weight. */
    public boolean ownsBorder(int x, int z, double weight) {
        return weight > 0.45 + 0.2 * noise(x / 24.0, z / 24.0, 256);
    }

    /** Column with serac fields on a jittered grid ({@link #gridGroups}), for tests and previews. */
    public Column column(int x, int z, double base, double weight) {
        return column(x, z, base, weight, weight > 0 ? gridGroups(x, z) : NO_GROUPS);
    }

    /** @param groups serac fields that may reach this column (in the world: the region's, {@link EverwinterGroups}) */
    public Column column(int x, int z, double base, double weight, Group[] groups) {
        if (weight <= 0) return new Column((float) base, (float) base, PLAIN);
        double shelter = shelter(x, z);

        // Rolling ground: broad rises and hollows lifted above the plain interior, medium rolls, and wind ridges
        // that are strong in some stretches and calm in others.
        double u = x * WIND_X + z * WIND_Z, v = z * WIND_X - x * WIND_Z;
        double swell = SWELL_LIFT + SWELL * fbm(x, z, 420, 3, 5);
        double rolls = 5 * fbm(x + 40 * noise(x / 230.0, z / 230.0, 164), z + 40 * noise(x / 230.0, z / 230.0, 165), 150, 2, 166);
        double ridgeStrength = 1.2 + 3.3 * smoothstep(-0.2, 0.45, noise(x / 320.0, z / 320.0, 168));
        double ridges = ridgeStrength * (2 * square(1 - Math.abs(noise(u / 45, v / 170, 1))) - 1) + 1.6 * fbm(x, z, 190, 2, 3);

        Serac serac = serac(x, z, groups);
        double ground = base + weight * (swell + rolls + ridges);
        // Bed of the ice sheet: rock begins about 5-13 blocks below the snow surface.
        double iceBase = base + weight * (swell + rolls + Math.min(ridges, 0) - EverwinterPalette.COVER_DEPTH
                - 5 - 4 * noise(x / 90.0, z / 90.0, 8));

        double cliff = cliff(x, z, shelter);
        double[] hill = hill(x, z);
        double spire = spire(x, z, groups);
        // Deepslate outcrops: rough knobs breaking through the snow, kept off the ice fields and big hills; where
        // sheltered, snow banks up around them.
        double rockiness = fbm(x, z, 85, 2, 30) + 0.25 * noise(x / 13.0, z / 13.0, 32);
        double open = 1 - serac.field();
        // Serac fields stay clean: no hills on or between the plates (spires are kept away in spire()).
        for (int k = 0; k < 3; k++) hill[k] *= open;
        double rock = open * (1 - smoothstep(1, 6, hill[0])) * 9 * Math.clamp((rockiness - 0.42) / 0.3, 0, 1);
        double snow = open * (drift(x, z) + hill[2] + shelter * 3.5 * smoothstep(0.3, 0.44, rockiness));
        double hillHeight = Math.max(hill[0], hill[1]);

        boolean crevasse = serac.crack() > 0;
        double height = crevasse
                ? Math.max(LOWEST_FLOOR, ground + weight * (cliff - serac.crack()))
                : Math.max(LOWEST_FLOOR, ground + weight * (cliff + hillHeight + snow + serac.height() + Math.max(spire, rock)));
        // Deep ice under and around serac fields, so sunken plates and cracks have ice walls.
        if (serac.near()) {
            iceBase = Math.min(iceBase, Math.max(LOWEST_FLOOR, ground - weight * TEAR_MAX) - WALL_ICE);
        }

        // Crevasses: long, deep tears in the ground with near-vertical ice walls.
        double[] tear = tear(x, z);
        if (tear != null && tear[0] < tear[1] * weight + 16) {
            double bottom = Math.max(LOWEST_FLOOR, ground - weight * tear[2]);
            iceBase = Math.min(iceBase, bottom - WALL_ICE);
            double half = tear[1] * weight;
            if (tear[0] < half) {
                double cut = 1 - smoothstep(0.45 * half, half, tear[0]);
                height += cut * (Math.min(bottom, height) - height);
                crevasse |= cut > 0.5;
            }
        }

        int feature = crevasse ? CREVASSE
                : serac.plate() != -1 ? GLACIER
                : spire * weight > 1 && spire >= rock ? SPIRE
                : rock * weight > 0.5 ? ROCK
                : hill[0] * weight > 2 ? HILL
                : snow * weight > 1 ? DRIFT : PLAIN;
        if (feature == ROCK) iceBase = ground;
        return new Column((float) height, (float) iceBase, feature);
    }

    /** 0 where the wind scours the ground (harsh edges), 1 where it is sheltered (snow ramps, banked snow). */
    private double shelter(int x, int z) {
        return smoothstep(-0.12, 0.12, fbm(x, z, 300, 2, 160));
    }

    /** How busy the ground is: more snow piles and hills where high, calm snow fields where low. */
    private double activity(double x, double z) {
        return smoothstep(-0.4, 0.4, noise(x / 650, z / 650, 162));
    }

    /**
     * Ice cliffs: the ground steps up 10-20 blocks along a noise contour. In wind-scoured stretches the step is
     * mostly a sheer, ragged cliff; where sheltered it is mostly a slope, and the remaining cliffs have snow
     * banked against their lower half.
     */
    private double cliff(int x, int z, double shelter) {
        double field = terrace(x, z);
        double step = 10 + 10 * (0.5 + 0.5 * noise(x / 260.0, z / 260.0, 119));
        double soft = smoothstep(-0.06, 0.06, field);
        double sharp = smoothstep(-0.05, 0.25, fbm(x, z, 900, 2, 117)) * (1 - 0.7 * shelter);
        if (sharp <= 0) return step * soft;
        double hard = field > 0 ? 1 : 0;
        if (Math.abs(field) < 0.05) {
            // Sheer face: the distance to the contour (gradient only needed close to it) with a ragged jitter.
            double distance = field / slope(this::terrace, x, z, field) + 1.5 * noise(x / 4.0, z / 4.0, 116);
            hard = Math.max(smoothstep(-1.2, 1.2, distance), shelter * 0.55 * smoothstep(-16, 0, distance));
        }
        return step * (soft + sharp * (hard - soft));
    }

    private double terrace(double x, double z) {
        double wx = x + 50 * fbm(x, z, 260, 2, 110), wz = z + 50 * fbm(x, z, 260, 2, 112);
        return fbm(wx, wz, 480, 2, 114) - CLIFF_LEVEL;
    }

    /** Crevasses: {distance to the crack line, half width at full weight, depth}, or null away from them. */
    private double[] tear(int x, int z) {
        double gate = smoothstep(0.2, 0.4, fbm(x, z, 850, 2, 100));
        if (gate <= 0) return null;
        double field = tearField(x, z);
        // Far enough out to cover the ice halo (half width + 16 blocks) even where the field changes quickly.
        if (Math.abs(field) > 0.25) return null;
        double distance = Math.abs(field) / slope(this::tearField, x, z, field) + 0.7 * noise(x / 3.0, z / 3.0, 108);
        double half = gate * (1.5 + 4.5 * (0.5 + 0.5 * noise(x / 80.0, z / 80.0, 106)));
        double depth = gate * (18 + 16 * (0.5 + 0.5 * noise(x / 110.0, z / 110.0, 107)));
        return new double[] {distance, half, depth};
    }

    /** Long, roughly parallel fractures (like crevasses across a flowing glacier), gently bent and jagged. */
    private double tearField(double x, double z) {
        double along = x * tearCos + z * tearSin, across = z * tearCos - x * tearSin;
        across += 90 * noise(along / 1400, across / 1400, 102) + 5 * noise(x / 20.0, z / 20.0, 103);
        return noise(along / 700, across / 170, 104) + 0.2 * noise(along / 260, across / 70, 105);
    }

    /**
     * Big snow/ice hills, more common in busy ground: {hill, foot, snow tail}. Each is a mound 18-42 blocks high
     * on a broad, low foot (snow ground) that merges into the surrounding ground, with a long snow tail in its lee. Snow hills are
     * smooth with a slightly steeper lee side; icy hills (about half) have a sheer wind-cut face of bare ice and
     * a craggy top.
     */
    private double[] hill(int x, int z) {
        int cellX = Math.floorDiv(x, HILL_CELL), cellZ = Math.floorDiv(z, HILL_CELL);
        double best = 0, feet = 0, tail = 0;
        for (int cz = cellZ - 1; cz <= cellZ + 1; cz++) {
            for (int cx = cellX - 1; cx <= cellX + 1; cx++) {
                double px = (cx + 0.25 + 0.5 * unit(hash(cx, cz, 141))) * HILL_CELL;
                double pz = (cz + 0.25 + 0.5 * unit(hash(cx, cz, 142))) * HILL_CELL;
                if (unit(hash(cx, cz, 140)) > 0.2 + 0.35 * activity(px, pz)) continue;
                boolean icy = unit(hash(cx, cz, 147)) < 0.55;
                double peak = 18 + 24 * unit(hash(cx, cz, 144)), radius = 45 + 40 * unit(hash(cx, cz, 143));
                double angle = Math.PI * 2 * unit(hash(cx, cz, 145)), stretch = 1 + 0.6 * unit(hash(cx, cz, 146));
                double lee = icy ? 3 + 1.5 * unit(hash(cx, cz, 149)) : 1.3 + 0.5 * unit(hash(cx, cz, 149));
                double wobble = 1 + 0.22 * noise(x / 28.0, z / 28.0, 171);
                double dx = x - px, dz = z - pz;
                double height = bump(dx, dz, radius, peak, angle, stretch, lee, wobble);
                if (icy && height > 0) {
                    height = Math.max(0, height + height / peak * 7 * (1 - 2 * Math.abs(noise(x / 16.0, z / 16.0, 172))));
                }
                best = Math.max(best, height);
                feet = Math.max(feet, bump(dx, dz, radius * 2.3, peak * 0.28, angle, stretch, 1, wobble));
                // Snow tail: drifted snow stretching downwind from the hill.
                tail = Math.max(tail, bump(dx - radius * 1.3 * WIND_X, dz - radius * 1.3 * WIND_Z, radius * 1.2,
                        peak * (0.2 + 0.15 * unit(hash(cx, cz, 173))), Math.atan2(WIND_Z, WIND_X), 2.2, 1, wobble));
            }
        }
        return new double[] {best, feet, tail};
    }

    /**
     * Snow piles: wind-stretched heaps 2-9 blocks high and 6-22 blocks across, steeper in their lee. Busy ground
     * holds dense drift fields where neighbouring piles merge into ridges; calm ground has only a few.
     */
    private double drift(int x, int z) {
        int cellX = Math.floorDiv(x, DRIFT_CELL), cellZ = Math.floorDiv(z, DRIFT_CELL);
        double total = 0, wobble = 1 + 0.25 * noise(x / 7.0, z / 7.0, 152);
        for (int cz = cellZ - 1; cz <= cellZ + 1; cz++) {
            for (int cx = cellX - 1; cx <= cellX + 1; cx++) {
                double px = (cx + 0.1 + 0.8 * unit(hash(cx, cz, 151))) * DRIFT_CELL;
                double pz = (cz + 0.1 + 0.8 * unit(hash(cx, cz, 153))) * DRIFT_CELL;
                if (unit(hash(cx, cz, 150)) > 0.3 + 0.65 * activity(px, pz)) continue;
                double size = Math.pow(unit(hash(cx, cz, 154)), 1.5);
                double radius = 6 + 16 * size, height = 2 + 7 * (0.6 * size + 0.4 * unit(hash(cx, cz, 155)));
                total += bump(x - px, z - pz, radius, height, Math.atan2(WIND_Z, WIND_X) + 0.6 * (unit(hash(cx, cz, 156)) - 0.5),
                        1.4 + 0.8 * unit(hash(cx, cz, 157)), 1.5, wobble);
            }
        }
        return 12 * Math.tanh(total / 12);
    }

    /**
     * Rounded, stretched and wobbly mound at offset (dx, dz) from its centre; 0 outside. The side the angle
     * points to is {@code lee} times steeper.
     */
    private static double bump(double dx, double dz, double radius, double height, double angle, double stretch,
                               double lee, double wobble) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        double along = (dx * cos + dz * sin) / stretch, across = (dz * cos - dx * sin) * stretch;
        if (along > 0) along *= lee;
        double d = Math.sqrt(along * along + across * across) / radius * wobble;
        return d < 1 ? height * square(1 - d * d) : 0;
    }

    /**
     * Serac field column: plate surface relative to the ground (negative where the plate is sunk), crack depth
     * below the ground (0: none), clean-ground share 0-1, the plate it belongs to (-1: none), whether a serac
     * field is close (deep ice below), and whether this is stepped ground in a wider gap between plates.
     */
    private record Serac(double height, double crack, double field, long plate, boolean near, boolean gap) {}

    /**
     * A serac field: a group of {@code count} plates, plate 0 in the middle, the rest in a ring around it.
     * {@code reach} is the farthest any plate extends from the group's centre.
     */
    public record Group(double x, double z, long key, int count, double[] siteX, double[] siteZ, double[] radius,
                        double reach) {}

    /** Lays out a serac field of 4-7 plates around (x, z); {@code key} seeds its shape. */
    public Group group(long key, double x, double z) {
        int a = (int) key, b = (int) (key >>> 32);
        int count = GROUP_MIN + (int) ((GROUP_MAX - GROUP_MIN + 1) * unit(hash(a, b, 220)));
        double[] siteX = new double[count], siteZ = new double[count], radius = new double[count];
        for (int k = 0; k < count; k++) {
            radius[k] = GROW * (PLATE_RADIUS_MIN + (PLATE_RADIUS_MAX - PLATE_RADIUS_MIN) * unit(hash(a + k, b, 210)))
                    * (PLATE_SCALE_MIN + (PLATE_SCALE_MAX - PLATE_SCALE_MIN) * unit(hash(a + k, b, 215)));
        }
        siteX[0] = x;
        siteZ[0] = z;
        double reach = radius[0] * 1.25, start = 2 * Math.PI * unit(hash(a, b, 221)), step = 2 * Math.PI / (count - 1);
        for (int k = 1; k < count; k++) {
            double angle = start + step * (k + 0.6 * (unit(hash(a + k, b, 222)) - 0.5));
            double distance = RING_SPACING * (radius[0] + radius[k]) * (0.92 + 0.16 * unit(hash(a + k, b, 223)));
            siteX[k] = x + distance * Math.cos(angle);
            siteZ[k] = z + distance * Math.sin(angle);
            reach = Math.max(reach, distance + radius[k] * 1.25);
        }
        return new Group(x, z, key, count, siteX, siteZ, radius, reach + 15);
    }

    /** Serac fields on a jittered grid that may reach (x, z), at most one per GRID_CELL square. */
    public Group[] gridGroups(int x, int z) {
        int cellX = Math.floorDiv(x, GRID_CELL), cellZ = Math.floorDiv(z, GRID_CELL);
        java.util.List<Group> groups = new java.util.ArrayList<>(2);
        for (int cz = cellZ - 1; cz <= cellZ + 1; cz++) {
            for (int cx = cellX - 1; cx <= cellX + 1; cx++) {
                if (unit(hash(cx, cz, 12)) > GRID_CHANCE) continue;
                Group group = group(((long) cx << 32) ^ (cz & 0xFFFFFFFFL),
                        (cx + 0.35 + 0.3 * unit(hash(cx, cz, 13))) * GRID_CELL, (cz + 0.35 + 0.3 * unit(hash(cx, cz, 14))) * GRID_CELL);
                // Generous margin: spires near the field look the field up too (see nearField).
                if (Math.hypot(x - group.x(), z - group.z()) < group.reach() + 100) groups.add(group);
            }
        }
        return groups.toArray(NO_GROUPS);
    }

    /**
     * Serac fields: groups of 4-7 huge pieces of the glacier ground itself, broken off and tilted, lying next to
     * each other. Plates have irregular, wobbly outlines about 115-235 blocks across (cut back where they meet a
     * neighbour) and keep the ground's own rolls on top. Each is tilted 5-30 degrees in its own direction about
     * its middle: its low end sinks below the surrounding ground (which breaks off in a wall down to it, at most
     * SINK_MAX deep), its high end rises to a sheer ice face with a deep crack 1-2 blocks wide at its foot. Very
     * long, steep plates level off towards RISE_SOFT. Neighbouring plates either touch, split by a deep tear
     * 1-2 blocks wide, or stand 16-36 blocks apart over plain snow ground with gentle rises and dips.
     */
    private Serac serac(int x, int z, Group[] groups) {
        Group group = null;
        for (Group candidate : groups) {
            if (Math.hypot(x - candidate.x(), z - candidate.z()) < candidate.reach() + 30) group = candidate;
        }
        if (group == null) return NO_SERAC;
        // Broadly warped lookups (bent plate boundaries); the warp is gentle enough that a 2-block tear stays open.
        double px = x + 9 * noise(x / 45.0, z / 45.0, 120), pz = z + 9 * noise(x / 45.0, z / 45.0, 121);
        // Nearest plate centre and the boundary to the next one (Voronoi within the group).
        int best = 0, second = -1;
        double bestDistance = Double.POSITIVE_INFINITY, secondDistance = Double.POSITIVE_INFINITY;
        for (int k = 0; k < group.count(); k++) {
            double distance = square(px - group.siteX()[k]) + square(pz - group.siteZ()[k]);
            if (distance < bestDistance) {
                second = best;
                secondDistance = bestDistance;
                best = k;
                bestDistance = distance;
            } else if (distance < secondDistance) {
                second = k;
                secondDistance = distance;
            }
        }
        int a = (int) group.key() + best, b = (int) (group.key() >>> 32);
        double dx = px - group.siteX()[best], dz = pz - group.siteZ()[best], radius = group.radius()[best];
        // Wobbly, ragged outlines: blocks inside each plate's own radius.
        double wobble = 1 + 0.18 * noise(px / 35.0, pz / 35.0, 124) + 0.06 * noise(px / 9.0, pz / 9.0, 125);
        double ragged = 1.5 * noise(x / 5.0, z / 5.0, 126);
        double outline = radius * wobble - Math.sqrt(bestDistance) + ragged;
        double outlineSecond = Double.NEGATIVE_INFINITY, edge = Double.POSITIVE_INFINITY, gap = 0;
        boolean connected = false;
        int pairA = 0, pairB = 0;
        if (second >= 0) {
            outlineSecond = group.radius()[second] * wobble - Math.sqrt(secondDistance) + ragged;
            double separation = Math.hypot(group.siteX()[second] - group.siteX()[best], group.siteZ()[second] - group.siteZ()[best]);
            edge = (secondDistance - bestDistance) / (2 * separation);
            pairA = (int) group.key() * 31 + Math.min(best, second);
            pairB = b * 31 + Math.max(best, second);
            double u = unit(hash(pairA, pairB, 201));
            connected = unit(hash(pairA, pairB, 202)) < CONNECTED;
            if (connected) {
                gap = TEAR_HALF_MIN + (TEAR_HALF_MAX - TEAR_HALF_MIN) * u;
            } else {
                gap = GAP_MIN + (GAP_MAX - GAP_MIN) * u;
                edge += 2.5 * noise(x / 6.0, z / 6.0, 127);
            }
        }
        // Blocks inside the plate: within its outline and clear of the gap to the neighbouring plate.
        double inside = Math.min(edge - gap, outline);
        double tilt = Math.tan(Math.toRadians(TILT_MIN + (TILT_MAX - TILT_MIN) * unit(hash(a, b, 211))));
        double angle = 2 * Math.PI * unit(hash(a, b, 212)), u4 = unit(hash(a, b, 213));
        // Position along the tilt: -radius at the low end, +radius at the high face.
        double along = dx * Math.cos(angle) + dz * Math.sin(angle);
        // Tilted about the middle; long steep plates pivot nearer their low end so it sinks at most SINK_MAX.
        double offset = tilt * along + Math.max(0, tilt * radius - SINK_MAX);
        if (offset > 0) offset = RISE_SOFT * Math.tanh(offset / RISE_SOFT);
        // Clean snow ground (no piles, hills or outcrops) on and within about 40 blocks of a plate.
        double field = smoothstep(-40, -10, inside);
        if (inside >= 0) {
            return new Serac(offset + 0.8 * noise(x / 8.0, z / 8.0, 133), 0, field, group.key() * 8 + best, true, false);
        }
        double between = Math.min(outline, outlineSecond);
        if (second >= 0 && connected && edge < gap && between > 0) {
            // Deep tear between two touching plates.
            return new Serac(0, TEAR_MIN + (TEAR_MAX - TEAR_MIN) * unit(hash(pairA, pairB, 203)), 1, -1, true, false);
        }
        // Deep crack 1-2 blocks wide along the foot of a tall high face.
        if (inside > -(1 + u4) && along > 0.3 * radius && offset > 8) {
            return new Serac(0, 10 + (SERAC_CRACK - 10) * unit(hash(a, b, 214)), field, -1, true, false);
        }
        if (second >= 0 && between > -STEP_REACH && (!connected || edge >= gap)) {
            // Plates further apart (or not reaching each other): plain snow ground with gentle rises and dips.
            return new Serac(steps(x, z, between), 0, 1, -1, true, true);
        }
        return new Serac(0, 0, field, -1, true, false);
    }

    /**
     * Ground between two plates further apart: the plain snow ground plus gentle, broad rises and dips (up to
     * RISE blocks over about 50 blocks, never steeper than about one block in four), fading out towards
     * STEP_REACH beyond the plates. No steps, spikes or rubble (the user rejected both, decisions 21-22).
     */
    private double steps(int x, int z, double between) {
        return RISE * noise(x / 50.0, z / 50.0, 240) * smoothstep(-STEP_REACH, -STEP_REACH + 6, between);
    }

    /** Serac plate at a column (-1: none) with grid serac fields, for tests. */
    long plate(int x, int z) {
        return serac(x, z, gridGroups(x, z)).plate();
    }

    /** Whether a column is stepped ground in a wider gap between plates (grid serac fields), for tests. */
    boolean stepGap(int x, int z) {
        return serac(x, z, gridGroups(x, z)).gap();
    }

    /**
     * Ice spires: rare, thick, tapering shards 14-42 blocks tall with a 4-9 block base radius on a jittered grid.
     * Each leans slightly, and about 40% curve: their axis drifts further sideways the higher it gets. As a
     * heightmap, a column reaches the highest slice of the spire that still covers it.
     */
    private double spire(int x, int z, Group[] groups) {
        int cellX = Math.floorDiv(x, SPIRE_CELL), cellZ = Math.floorDiv(z, SPIRE_CELL);
        double best = 0;
        for (int cz = cellZ - 1; cz <= cellZ + 1; cz++) {
            for (int cx = cellX - 1; cx <= cellX + 1; cx++) {
                if (unit(hash(cx, cz, 40)) > 0.14) continue;
                double px = (cx + 0.2 + 0.6 * unit(hash(cx, cz, 41))) * SPIRE_CELL;
                double pz = (cz + 0.2 + 0.6 * unit(hash(cx, cz, 42))) * SPIRE_CELL;
                double radius = 4 + 5 * unit(hash(cx, cz, 43)), height = 14 + 28 * unit(hash(cx, cz, 44));
                double direction = 2 * Math.PI * unit(hash(cx, cz, 45));
                double lean = 0.08 * unit(hash(cx, cz, 46));
                double curve = unit(hash(cx, cz, 48)) < 0.4 ? 0.06 + 0.08 * unit(hash(cx, cz, 49)) : 0;
                double reach = radius + (lean + curve) * height + 2;
                double dx = x - px, dz = z - pz;
                if (dx * dx + dz * dz > reach * reach || nearField(px, pz, reach, groups)) continue;
                double rough = 1 + 0.2 * noise(x / 3.0, z / 3.0, 47);
                // Highest slice covering this column; slices shrink upwards while their centre drifts sideways.
                for (double h = height; h > best; h -= 0.5) {
                    double t = h / height, shift = lean * h + curve * h * t;
                    double r = radius * rough * Math.pow(1 - t, 1.1);
                    double sx = dx - shift * Math.cos(direction), sz = dz - shift * Math.sin(direction);
                    if (sx * sx + sz * sz <= r * r) {
                        best = h;
                        break;
                    }
                }
            }
        }
        return best;
    }

    /** Whether a spire at (x, z) would stand on or within 40 blocks of a serac field. */
    private static boolean nearField(double x, double z, double reach, Group[] groups) {
        for (Group group : groups) {
            if (Math.hypot(x - group.x(), z - group.z()) < group.reach() + reach + 40) return true;
        }
        return false;
    }

    /** Change of a field per block (forward differences), for distances to its zero contour. */
    private static double slope(DoubleBinaryOperator field, double x, double z, double here) {
        return Math.max(1e-4, Math.hypot(field.applyAsDouble(x + 1, z) - here, field.applyAsDouble(x, z + 1) - here));
    }

    private double fbm(double x, double z, double scale, int octaves, int salt) {
        double sum = 0, amplitude = 1, normalization = 0;
        for (int octave = 0; octave < octaves; octave++) {
            sum += amplitude * noise(x / scale, z / scale, salt + octave);
            normalization += amplitude;
            amplitude *= 0.5;
            scale *= 0.5;
        }
        return sum / normalization;
    }

    /** 2D gradient noise, roughly -1..1. */
    double noise(double x, double z, int salt) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double dx = x - ix, dz = z - iz;
        double tx = fade(dx), tz = fade(dz);
        double a = lerp(tx, gradient(hash(ix, iz, salt), dx, dz), gradient(hash(ix + 1, iz, salt), dx - 1, dz));
        double b = lerp(tx, gradient(hash(ix, iz + 1, salt), dx, dz - 1), gradient(hash(ix + 1, iz + 1, salt), dx - 1, dz - 1));
        return Math.clamp(lerp(tz, a, b) * 1.4, -1, 1);
    }

    private static double gradient(long hash, double x, double z) {
        double angle = unit(hash) * Math.PI * 2;
        return Math.cos(angle) * x + Math.sin(angle) * z;
    }

    long hash(int x, int z, int salt) {
        long h = seed ^ ((long) x * 0x632BE59BD9B4E019L) ^ ((long) z * 0x9E3779B97F4A7C15L) ^ ((long) salt * 0xD1B54A32D192ED03L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    static double unit(long hash) { return (hash >>> 11) * 0x1.0p-53; }
    static double smoothstep(double low, double high, double value) {
        double t = Math.clamp((value - low) / (high - low), 0, 1);
        return t * t * (3 - 2 * t);
    }
    private static double square(double value) { return value * value; }
    private static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    private static double lerp(double t, double a, double b) { return a + t * (b - a); }
}
