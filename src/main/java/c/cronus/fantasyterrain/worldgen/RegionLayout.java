package c.cronus.fantasyterrain.worldgen;

/** Seeded, warped Voronoi cells. This layer owns regions, not biome terrain. */
public final class RegionLayout {
    public static final int REGION_COUNT = 5;
    private final long seed;
    private final int size;
    private final int borderWidth;

    public RegionLayout(long seed, int size, int borderWidth) {
        this.seed = seed;
        this.size = size;
        this.borderWidth = borderWidth;
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
        return new Sample(region, border);
    }

    public float surfaceHeight(int x, int z, Sample sample) {
        return terrainColumn(x, z, sample).surfaceHeight();
    }

    public TerrainColumn terrainColumn(int x, int z, Sample sample) {
        // Region interiors keep their neutral relief. All ruggedness is gated by the border mask.
        double interior = 80 + 5 * noise(x / 300.0, z / 300.0);
        if (sample.border() == 0) return new TerrainColumn((float) interior, 0);

        // Alpine ridge hierarchy: tributary detail is strongest on the main ridges, not in valleys.
        double u = x * 0.86 + z * 0.51, v = z * 0.86 - x * 0.51;
        u += 70 * noise(x / 430.0, z / 430.0, 51);
        v += 70 * noise(x / 430.0, z / 430.0, 52);
        double weight = 1, sum = 0, amplitude = 1, normalization = 0;
        double scale = 280;
        for (int octave = 0; octave < 4; octave++) {
            double field = noise3d(u / scale, 0.371 + octave * 0.173, v / scale, 80 + octave);
            double signal = square(1 - Math.abs(field));
            signal *= weight;
            sum += signal * amplitude;
            normalization += amplitude;
            weight = Math.clamp(signal * 2.2, 0, 1);
            scale *= 0.5;
            // Slightly soften fine ridge detail while retaining the main crest network.
            amplitude *= 0.43;
        }
        double ridge = sum / normalization;
        double peakVariation = 12 * noise(u / 340, v / 340, 90);
        double alpineHeight = 28 + 265 * ridge + peakVariation * ridge;
        // Broad bases, connected crests, and erosion-like gullies all follow the same ridge network.
        double belt = Math.pow(sample.border(), 0.8);
        float height = (float) (interior + belt * (alpineHeight - interior));
        return new TerrainColumn(height, sample.border());
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

    public record Sample(int region, float border) {}

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
