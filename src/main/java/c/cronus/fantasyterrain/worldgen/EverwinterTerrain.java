package c.cronus.fantasyterrain.worldgen;

import java.util.function.DoubleBinaryOperator;

/**
 * Everwinter relief on top of the shared region interior (Frozen Wastes). Pure Java, no Minecraft
 * dependencies. Harsh, broken glacier country: rolling snow ground lifted above the plain interior, frequent
 * small snow piles and rarer big snow/ice hills, a few ice cliffs, long deep crevasses torn into the ground,
 * fields of fractured, jumbled ice blocks (seracs) split by cracks, rare ice spires and deepslate outcrops.
 * Beneath the snow lies a glacier ice sheet whose bed drops below every nearby cut, so cliffs, cracks and
 * crevasses show ice walls. The relief is scaled by a weight that is zero outside the biome and in every
 * column the region border touches, so borders and basin stay untouched.
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
    /** Serac fields: mask level where they begin, typical block height above the ground, tallest block. */
    private static final double SERAC_LEVEL = 0.16, SERAC_HEIGHT = 16, SERAC_MAX = 34;
    /** Deepest crack between serac blocks, below the ground. */
    private static final double SERAC_CRACK = 12;
    /** Extra ice kept below the deepest nearby cut, so its walls are ice rather than rock. */
    private static final double WALL_ICE = 6;
    private static final int DRIFT_CELL = 40, HILL_CELL = 230, SERAC_CELL = 14, SPIRE_CELL = 72;
    private static final double DRIFT_CHANCE = 0.7, HILL_CHANCE = 0.4;
    private static final Serac NO_SERAC = new Serac(0, 0, 0);
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

    public Column column(int x, int z, double base, double weight) {
        if (weight <= 0) return new Column((float) base, (float) base, PLAIN);

        // Rolling ground: broad rises and hollows lifted above the plain interior, plus long wind-shaped dunes.
        double u = x * 0.80 + z * 0.60, v = z * 0.80 - x * 0.60;
        double swell = SWELL_LIFT + SWELL * fbm(x, z, 420, 3, 5);
        double dunes = 3.0 * square(1 - Math.abs(noise(u / 55, v / 210, 1))) - 1.5 + 2.0 * fbm(x, z, 190, 2, 3);
        double ground = base + weight * (swell + dunes);
        // Bed of the ice sheet: rock begins about 5-13 blocks below the snow surface.
        double iceBase = base + weight * (swell - EverwinterPalette.COVER_DEPTH - 5 - 4 * noise(x / 90.0, z / 90.0, 8));

        double cliff = cliff(x, z);
        double hill = hill(x, z);
        Serac serac = serac(x, z);
        double drift = (1 - serac.field()) * drift(x, z);
        double spire = spire(x, z);
        // Deepslate outcrops: rough knobs breaking through the snow, kept off the ice fields and big hills.
        double rockiness = fbm(x, z, 85, 2, 30) + 0.25 * noise(x / 13.0, z / 13.0, 32);
        double rock = (1 - serac.field()) * (1 - smoothstep(1, 6, hill)) * 9 * Math.clamp((rockiness - 0.42) / 0.3, 0, 1);

        boolean crevasse = serac.crack() > 0;
        double height = crevasse
                ? Math.max(LOWEST_FLOOR, ground + weight * (cliff - serac.crack()))
                : ground + weight * (cliff + hill + drift + serac.height() + Math.max(spire, rock));
        if (serac.field() > 0) iceBase = Math.min(iceBase, ground - weight * (SERAC_CRACK + WALL_ICE));

        // Crevasses: long, deep tears in the ground with near-vertical ice walls.
        double[] tear = tear(x, z);
        if (tear != null && tear[0] < tear[1] * weight + 10) {
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
                : serac.height() * weight > 2 ? GLACIER
                : spire * weight > 1 && spire >= rock ? SPIRE
                : rock * weight > 0.5 ? ROCK
                : hill * weight > 2 ? HILL
                : drift * weight > 1 ? DRIFT : PLAIN;
        if (feature == ROCK) iceBase = ground;
        return new Column((float) height, (float) iceBase, feature);
    }

    /**
     * Ice cliffs: the ground steps up 9-18 blocks along a noise contour. In a few stretches the step is a sheer,
     * ragged cliff; elsewhere it is a slope a few dozen blocks wide, so there are ways around.
     */
    private double cliff(int x, int z) {
        double field = terrace(x, z);
        double step = 10 + 10 * (0.5 + 0.5 * noise(x / 260.0, z / 260.0, 119));
        double soft = smoothstep(-0.06, 0.06, field);
        double sharp = smoothstep(-0.05, 0.25, fbm(x, z, 900, 2, 117));
        if (sharp <= 0) return step * soft;
        // Sheer face: the distance to the contour (gradient only needed close to it) with a ragged jitter.
        double hard = field > 0 ? 1 : 0;
        if (Math.abs(field) < 0.03) {
            double distance = field / slope(this::terrace, x, z, field) + 1.5 * noise(x / 4.0, z / 4.0, 116);
            hard = smoothstep(-1.2, 1.2, distance);
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
        if (Math.abs(field) > 0.1) return null;
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
     * Big snow/ice hills: rare broad mounds 18-42 blocks high. Snow hills are smooth with a slightly steeper
     * lee side; icy hills (about half) have a sheer wind-cut face of bare ice and a craggy top.
     */
    private double hill(int x, int z) {
        int cellX = Math.floorDiv(x, HILL_CELL), cellZ = Math.floorDiv(z, HILL_CELL);
        double best = 0;
        for (int cz = cellZ - 1; cz <= cellZ + 1; cz++) {
            for (int cx = cellX - 1; cx <= cellX + 1; cx++) {
                if (unit(hash(cx, cz, 140)) > HILL_CHANCE) continue;
                boolean icy = unit(hash(cx, cz, 147)) < 0.55;
                double peak = 18 + 24 * unit(hash(cx, cz, 144));
                double height = mound(x, z, cx, cz, HILL_CELL, 141, 45 + 40 * unit(hash(cx, cz, 143)), peak,
                        Math.PI * 2 * unit(hash(cx, cz, 145)), 1 + 0.6 * unit(hash(cx, cz, 146)),
                        icy ? 3 + 1.5 * unit(hash(cx, cz, 149)) : 1.3 + 0.5 * unit(hash(cx, cz, 149)), 0.22, 28);
                if (icy && height > 0) {
                    height = Math.max(0, height + height / peak * 7 * (1 - 2 * Math.abs(noise(x / 16.0, z / 16.0, 142))));
                }
                best = Math.max(best, height);
            }
        }
        return best;
    }

    /** Small snow piles: frequent wind-stretched heaps 2-7 blocks high. */
    private double drift(int x, int z) {
        int cellX = Math.floorDiv(x, DRIFT_CELL), cellZ = Math.floorDiv(z, DRIFT_CELL);
        double best = 0;
        for (int cz = cellZ - 1; cz <= cellZ + 1; cz++) {
            for (int cx = cellX - 1; cx <= cellX + 1; cx++) {
                if (unit(hash(cx, cz, 150)) > DRIFT_CHANCE) continue;
                best = Math.max(best, mound(x, z, cx, cz, DRIFT_CELL, 151, 6 + 8 * unit(hash(cx, cz, 153)),
                        2.5 + 4.5 * unit(hash(cx, cz, 154)), 0.64 + 0.6 * (unit(hash(cx, cz, 155)) - 0.5),
                        1.4 + 0.8 * unit(hash(cx, cz, 156)), 1.5, 0.25, 7));
            }
        }
        return best;
    }

    /**
     * Rounded, stretched and wobbly mound around a jittered point in a grid cell; 0 outside. The side the
     * angle points to is {@code lee} times steeper.
     */
    private double mound(int x, int z, int cx, int cz, int cell, int salt, double radius, double height, double angle,
                         double stretch, double lee, double wobble, double wobbleScale) {
        double px = (cx + 0.25 + 0.5 * unit(hash(cx, cz, salt))) * cell;
        double pz = (cz + 0.25 + 0.5 * unit(hash(cx, cz, salt + 1))) * cell;
        double dx = x - px, dz = z - pz, cos = Math.cos(angle), sin = Math.sin(angle);
        double along = (dx * cos + dz * sin) / stretch, across = (dz * cos - dx * sin) * stretch;
        if (along > 0) along *= lee;
        double d = Math.sqrt(along * along + across * across) / radius
                * (1 + wobble * noise(x / wobbleScale, z / wobbleScale, salt + 2));
        return d < 1 ? height * square(1 - d * d) : 0;
    }

    /** Serac field column: block height above the ground, crack depth below it (0: no crack), field membership 0-1. */
    private record Serac(double height, double crack, double field) {}

    /**
     * Serac fields: broad warped patches broken into jumbled ice blocks (Voronoi cells about 14 blocks across),
     * each with its own height and tilted top, separated by narrow deep cracks. Blocks step down towards the
     * field's margin, which ends in sheer, fractured walls.
     */
    private Serac serac(int x, int z) {
        double mask = seracMask(x, z);
        if (mask < SERAC_LEVEL - 0.08) return NO_SERAC;
        double field = smoothstep(SERAC_LEVEL - 0.06, SERAC_LEVEL, mask);
        // Rough fracture faces: the cell lookup is jittered slightly.
        double px = x + 1.6 * noise(x / 5.0, z / 5.0, 120), pz = z + 1.6 * noise(x / 5.0, z / 5.0, 121);
        int cellX = (int) Math.floor(px / SERAC_CELL), cellZ = (int) Math.floor(pz / SERAC_CELL);
        double best = Double.POSITIVE_INFINITY, second = Double.POSITIVE_INFINITY;
        int bestX = 0, bestZ = 0, secondX = 0, secondZ = 0;
        for (int cz = cellZ - 1; cz <= cellZ + 1; cz++) {
            for (int cx = cellX - 1; cx <= cellX + 1; cx++) {
                double distance = square(px - siteX(cx, cz)) + square(pz - siteZ(cx, cz));
                if (distance < best) {
                    second = best;
                    secondX = bestX;
                    secondZ = bestZ;
                    best = distance;
                    bestX = cx;
                    bestZ = cz;
                } else if (distance < second) {
                    second = distance;
                    secondX = cx;
                    secondZ = cz;
                }
            }
        }
        double siteX = siteX(bestX, bestZ), siteZ = siteZ(bestX, bestZ);
        double strength = smoothstep(SERAC_LEVEL, SERAC_LEVEL + 0.08, seracMask(siteX, siteZ));
        if (strength <= 0) return new Serac(0, 0, field);

        double top = SERAC_HEIGHT + 9 * fbm(siteX, siteZ, 400, 2, 122);
        double height = 6 + strength * (top - 6) + 10 * (unit(hash(bestX, bestZ, 124)) - 0.5);
        double roll = unit(hash(bestX, bestZ, 125));
        if (roll < 0.08) height += 8 + 8 * unit(hash(bestX, bestZ, 126));
        else if (roll > 0.9) height *= 0.35;
        // Tilted slab tops with a little roughness.
        height += 0.7 * (unit(hash(bestX, bestZ, 127)) - 0.5) * (px - siteX)
                + 0.7 * (unit(hash(bestX, bestZ, 128)) - 0.5) * (pz - siteZ) + 1.2 * noise(x / 4.0, z / 4.0, 133);
        height = Math.clamp(height, 3, SERAC_MAX);

        // Cracks along the cell boundaries; some neighbouring blocks stay fused.
        double separation = Math.hypot(siteX(secondX, secondZ) - siteX, siteZ(secondX, secondZ) - siteZ);
        double edge = (second - best) / (2 * separation);
        int keyA = bestX * 7919 + bestZ, keyB = secondX * 7919 + secondZ;
        int low = Math.min(keyA, keyB), high = Math.max(keyA, keyB);
        double crackHalf = unit(hash(low, high, 130)) < 0.25 ? 0 : 0.5 + 1.3 * unit(hash(low, high, 131));
        double crack = edge < crackHalf ? 2 + (SERAC_CRACK - 2) * unit(hash(low, high, 132)) : 0;
        return new Serac(height, crack, 1);
    }

    private double siteX(int cx, int cz) { return (cx + 0.15 + 0.7 * unit(hash(cx, cz, 134))) * SERAC_CELL; }
    private double siteZ(int cx, int cz) { return (cz + 0.15 + 0.7 * unit(hash(cx, cz, 135))) * SERAC_CELL; }

    private double seracMask(double x, double z) {
        double wx = x + 60 * fbm(x, z, 240, 2, 10), wz = z + 60 * fbm(x, z, 240, 2, 12);
        return fbm(wx, wz, 330, 3, 14);
    }

    /** Ice spires: rare sharp, slightly leaning shards on a jittered grid. */
    private double spire(int x, int z) {
        int cellX = Math.floorDiv(x, SPIRE_CELL), cellZ = Math.floorDiv(z, SPIRE_CELL);
        double best = 0;
        for (int cz = cellZ - 1; cz <= cellZ + 1; cz++) {
            for (int cx = cellX - 1; cx <= cellX + 1; cx++) {
                if (unit(hash(cx, cz, 40)) > 0.14) continue;
                double px = (cx + 0.2 + 0.6 * unit(hash(cx, cz, 41))) * SPIRE_CELL;
                double pz = (cz + 0.2 + 0.6 * unit(hash(cx, cz, 42))) * SPIRE_CELL;
                double radius = 3 + 4 * unit(hash(cx, cz, 43)), height = 8 + 20 * unit(hash(cx, cz, 44));
                double dx = x - px - 0.25 * (height - 8) * (unit(hash(cx, cz, 45)) - 0.5);
                double dz = z - pz - 0.25 * (height - 8) * (unit(hash(cx, cz, 46)) - 0.5);
                double d = Math.sqrt(dx * dx + dz * dz) / (radius * (1 + 0.25 * noise(x / 3.0, z / 3.0, 47)));
                if (d < 1) best = Math.max(best, height * Math.pow(1 - d, 1.4));
            }
        }
        return best;
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
