package c.cronus.fantasyterrain.worldgen;

/** Seeded, warped Voronoi cells. This layer owns regions, not biome terrain. */
public final class RegionLayout {
    public static final int REGION_COUNT = 5;
    /** Border mask above which a column belongs to the border mountains (biome and materials). */
    public static final float BORDER_THRESHOLD = 0.12f;
    /** Horizontal reach of the deepslate basin wall, measured inward from the mountain foot. */
    public static final double BASIN_REACH = 384;
    /** Lowest point of the massif (floor of the rare deep valleys); always far above region ground (Y=75–85). */
    public static final double MASSIF_FLOOR = 150;
    /** Typical massif height; most of the belt lies between about Y=175 and Y=250. */
    public static final double RANGE_CENTER = 212;
    /** Highest summits (rare). */
    public static final double PEAK_LIMIT = 302;
    /** Fractions of the border width (from the belt's outer edge) where the climb onto the massif starts and ends. */
    public static final double RAMP_START = 0.19, RAMP_END = 0.47;
    private final long seed;
    private final int size;
    private final int borderWidth;
    private final double rimDistance;

    public RegionLayout(long seed, int size, int borderWidth) {
        this.seed = seed;
        this.size = size;
        this.borderWidth = borderWidth;
        // Inverse smoothstep: the edge distance at which the border mask equals BORDER_THRESHOLD.
        this.rimDistance = borderWidth * (0.5 + Math.sin(Math.asin(1 - 2 * BORDER_THRESHOLD) / 3));
    }

    public Sample sample(int blockX, int blockZ) {
        // Bend the boundaries smoothly so mountain belts do not form straight polygon edges.
        double sampleX = blockX + size * 0.12 * noise(blockX / (size * 0.4), blockZ / (size * 0.4));
        double sampleZ = blockZ + size * 0.12 * noise(blockX / (size * 0.4) + 103.7, blockZ / (size * 0.4) - 67.2);
        int cellX = (int) Math.floor(sampleX / size);
        int cellZ = (int) Math.floor(sampleZ / size);
        double best = Double.POSITIVE_INFINITY;
        double winnerX = 0, winnerZ = 0;
        int winnerCellX = 0, winnerCellZ = 0;
        // Jitter stays within the middle half of each cell. Two rings cover all candidates.
        for (int z = cellZ - 2; z <= cellZ + 2; z++) {
            for (int x = cellX - 2; x <= cellX + 2; x++) {
                double px = siteX(x, z), pz = siteZ(x, z);
                double d = square(sampleX - px) + square(sampleZ - pz);
                if (d < best) {
                    best = d;
                    winnerX = px;
                    winnerZ = pz;
                    winnerCellX = x;
                    winnerCellZ = z;
                }
            }
        }
        double edgeDistance = Double.POSITIVE_INFINITY;
        for (int z = cellZ - 2; z <= cellZ + 2; z++) {
            for (int x = cellX - 2; x <= cellX + 2; x++) {
                if (x == winnerCellX && z == winnerCellZ) continue;
                double px = siteX(x, z), pz = siteZ(x, z);
                double d = square(sampleX - px) + square(sampleZ - pz);
                // Distance to the bisector, rather than a difference of radial distances.
                double separation = Math.sqrt(square(px - winnerX) + square(pz - winnerZ));
                edgeDistance = Math.min(edgeDistance, (d - best) / (2 * separation));
            }
        }
        int region = Math.floorMod(winnerCellX + 2 * winnerCellZ + (int) seed, REGION_COUNT);
        double t = Math.max(0, 1 - edgeDistance / borderWidth);
        float border = (float) (t * t * (3 - 2 * t));
        return new Sample(region, border, (float) edgeDistance);
    }

    public float surfaceHeight(int x, int z, Sample sample) {
        return terrainColumn(x, z, sample).surfaceHeight();
    }

    public TerrainColumn terrainColumn(int x, int z, Sample sample) {
        // Region interiors keep their neutral relief. All ruggedness is gated by the border mask.
        double interior = 80 + 5 * noise(x / 300.0, z / 300.0);
        if (sample.border() == 0) return new TerrainColumn((float) interior, 0);

        // One broad mountain range: a high massif body with many rounded peaks on top, rare taller
        // summits and rare deep valleys. Smooth (non-ridged) noise keeps every mountain broad-based.
        double u = x * 0.86 + z * 0.51, v = z * 0.86 - x * 0.51;
        u += 70 * noise(x / 430.0, z / 430.0, 51);
        v += 70 * noise(x / 430.0, z / 430.0, 52);
        double body = fbm(u, v, 520, 3, 80);
        double peaks = fbm(u, v, 150, 3, 84);
        double summit = smoothstep(0.12, 0.5, fbm(u, v, 340, 2, 88));
        double alpineHeight = RANGE_CENTER + 75 * body + 45 * peaks + 50 * summit * (0.7 + 0.6 * Math.max(0, peaks));
        // Rare valleys: thin noise contours, switched on only in a few stretches of the belt.
        double path = Math.abs(fbm(u, v, 640, 2, 92));
        double gate = smoothstep(0.12, 0.4, fbm(x, z, 900, 1, 94));
        double cut = gate * (1 - smoothstep(0, 0.16, path));
        double valleyFloor = MASSIF_FLOOR + 6 + 6 * noise(u / 120, v / 120, 96);
        alpineHeight += cut * (Math.min(valleyFloor, alpineHeight) - alpineHeight);
        alpineHeight = Math.clamp(alpineHeight, MASSIF_FLOOR, PEAK_LIMIT);
        // Short, steep ramp from region ground onto the massif instead of a belt-wide slope.
        double t = Math.clamp(1 - sample.edgeDistance() / borderWidth, 0, 1);
        double belt = smoothstep(RAMP_START, RAMP_END, t);
        float height = (float) (interior + belt * (alpineHeight - interior));
        return new TerrainColumn(height, sample.border());
    }

    /**
     * Material channel only: rock below this Y is the deepslate "cauldron" holding a region's stone.
     * It meets the surface at the mountain foot, curves down like a quarter ellipse and settles at Y=0
     * (the vanilla deepslate line) BASIN_REACH blocks inward. Terrain height and density are unaffected.
     */
    public float basinFloor(int x, int z, Sample sample) {
        double inward = sample.edgeDistance() - rimDistance;
        if (inward >= BASIN_REACH) return 0;
        float surface = surfaceHeight(x, z, sample);
        if (inward <= 0) return surface;
        double t = 1 - inward / BASIN_REACH;
        return (float) (surface * (1 - Math.sqrt(1 - t * t)));
    }

    private double siteX(int x, int z) {
        return (x + 0.25 + unit(hash(x, z, 0)) * 0.5) * size;
    }

    private double siteZ(int x, int z) {
        return (z + 0.25 + unit(hash(x, z, 1)) * 0.5) * size;
    }

    private double noise(double x, double z) {
        return noise(x, z, 2);
    }

    private double noise(double x, double z, int salt) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double tx = x - ix, tz = z - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = unit(hash(ix, iz, salt)), b = unit(hash(ix + 1, iz, salt));
        double c = unit(hash(ix, iz + 1, salt)), d = unit(hash(ix + 1, iz + 1, salt));
        return 2 * ((a + (b - a) * tx) * (1 - tz) + (c + (d - c) * tx) * tz) - 1;
    }

    /** Smooth gradient-noise fractal, roughly -1..1 with most values near 0. */
    private double fbm(double x, double z, double scale, int octaves, int salt) {
        double sum = 0, amplitude = 1, normalization = 0;
        for (int octave = 0; octave < octaves; octave++) {
            sum += amplitude * noise3d(x / scale, 0.371 + octave * 0.173, z / scale, salt + octave);
            normalization += amplitude;
            amplitude *= 0.5;
            scale *= 0.5;
        }
        return sum / normalization;
    }

    private static double smoothstep(double low, double high, double value) {
        double t = Math.clamp((value - low) / (high - low), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private long hash(int x, int z, int salt) {
        long h = seed ^ ((long) x * 0x632BE59BD9B4E019L) ^ ((long) z * 0x9E3779B97F4A7C15L) ^ salt;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    private static double unit(long hash) { return (hash >>> 11) * 0x1.0p-53; }
    private static double square(double value) { return value * value; }

    public record Sample(int region, float border, float edgeDistance) {}

    /** Every ridge is grounded: a column contains no floating slabs, holes, or carved pocket templates. */
    public record TerrainColumn(float surfaceHeight, float border) {
        public float upperBound() { return surfaceHeight; }
        public float density(int y) {
            return (float) Math.clamp((surfaceHeight - y) * 0.04, -16, 16);
        }
    }

    private double noise3d(double x, double y, double z, int salt) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
        double dx = x - ix, dy = y - iy, dz = z - iz;
        double tx = fade(dx), ty = fade(dy), tz = fade(dz);
        double a = lerp(tx, gradient(hash(ix, iz, salt ^ iy * 0x632BE5AB), dx, dy, dz),
                gradient(hash(ix + 1, iz, salt ^ iy * 0x632BE5AB), dx - 1, dy, dz));
        double b = lerp(tx, gradient(hash(ix, iz, salt ^ (iy + 1) * 0x632BE5AB), dx, dy - 1, dz),
                gradient(hash(ix + 1, iz, salt ^ (iy + 1) * 0x632BE5AB), dx - 1, dy - 1, dz));
        double c = lerp(tx, gradient(hash(ix, iz + 1, salt ^ iy * 0x632BE5AB), dx, dy, dz - 1),
                gradient(hash(ix + 1, iz + 1, salt ^ iy * 0x632BE5AB), dx - 1, dy, dz - 1));
        double d = lerp(tx, gradient(hash(ix, iz + 1, salt ^ (iy + 1) * 0x632BE5AB), dx, dy - 1, dz - 1),
                gradient(hash(ix + 1, iz + 1, salt ^ (iy + 1) * 0x632BE5AB), dx - 1, dy - 1, dz - 1));
        return Math.clamp(lerp(tz, lerp(ty, a, b), lerp(ty, c, d)) * 1.35, -1, 1);
    }

    private static double gradient(long hash, double x, double y, double z) {
        int h = (int) hash & 15;
        double a = h < 8 ? x : y, b = h < 4 ? y : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? a : -a) + ((h & 2) == 0 ? b : -b);
    }

    private static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    private static double lerp(double t, double a, double b) { return a + t * (b - a); }
}
