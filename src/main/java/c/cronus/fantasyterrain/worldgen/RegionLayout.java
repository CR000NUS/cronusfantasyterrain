package c.cronus.fantasyterrain.worldgen;

import java.util.concurrent.atomic.AtomicReferenceArray;

/** Seeded round regions on a jittered hexagonal grid. This layer owns regions, not biome terrain. */
public final class RegionLayout {
    public static final int REGION_COUNT = 5;
    /** Border mask above which a column belongs to the border mountains (biome and materials). */
    public static final float BORDER_THRESHOLD = 0.12f;
    /**
     * Wanted base radius range (centre to mountain foot, before the shape), in blocks. Most regions are larger
     * than their space allows and are trimmed so their rim runs MIN_GAP / 2 from the halfway line to each
     * neighbour; the few smaller ones leave somewhat wider belts.
     */
    public static final double MIN_RADIUS = 1650, MAX_RADIUS = 1900;
    /**
     * Narrowest mountain belt between two facing regions, foot to foot (before rim noise). Must stay above
     * 2 * (rim distance - massif start) = about 180 blocks so both ramps finish before the belt's middle.
     */
    public static final double MIN_GAP = 400;
    /**
     * Rounded-hexagon sharpness: 2 = circle, higher = flatter sides and tighter corners (a true hexagon at
     * infinity). The corners fill the mountain areas where three regions meet, so belts stay narrow there.
     */
    public static final double HEX = 12;
    /** Per-region three-lobed bulges and oval stretch, as fractions of the radius. */
    public static final double LOBES = 0.02, OVAL = 0.02;
    /** Largest inward/outward wobble of the jagged region rim, in blocks. */
    public static final double RIM_NOISE = 60;
    /**
     * Furthest the whole mountain front (ramp and massif) pushes into a region, in blocks. Reach is one-sided
     * (never thins a belt) and varies smoothly along the rim in broad lobes and bays.
     */
    public static final double RIM_REACH = 180;
    /** Random offset of region centres, as a fraction of the region spacing. */
    private static final double JITTER = 0.06;
    /** Horizontal reach of the deepslate basin wall, measured inward from the mountain foot. */
    public static final double BASIN_REACH = 384;
    /** Lowest point of the massif (floor of the rare deep valleys); always far above region ground (Y=75–85). */
    public static final double MASSIF_FLOOR = 150;
    /** Typical massif height; most of the belt lies between about Y=175 and Y=250. */
    public static final double RANGE_CENTER = 212;
    /** Height of the sharp ridged crests on top of the rounded peaks (0 = smooth, the original spires were ~175). */
    public static final double RIDGE_STRENGTH = 55;
    /** Highest summits (rare). */
    public static final double PEAK_LIMIT = 310;
    /** Fractions of the border width (from the belt's outer edge) where the climb onto the massif starts and ends. */
    public static final double RAMP_START = 0.19, RAMP_END = 0.47;
    private final long seed;
    private final int size;
    private final int borderWidth;
    private final double rimDistance;
    /** Depth behind the rim where the ramp reaches the massif. */
    private final double footSpan;
    private final AtomicReferenceArray<CachedRadius> radii = new AtomicReferenceArray<>(4096);

    public RegionLayout(long seed, int size, int borderWidth) {
        this.seed = seed;
        this.size = size;
        this.borderWidth = borderWidth;
        // Inverse smoothstep: the edge distance at which the border mask equals BORDER_THRESHOLD.
        this.rimDistance = borderWidth * (0.5 + Math.sin(Math.asin(1 - 2 * BORDER_THRESHOLD) / 3));
        this.footSpan = rimDistance - borderWidth * (1 - RAMP_END);
    }

    public Sample sample(int blockX, int blockZ) {
        // A mild bend turns the round regions into slightly irregular blobs.
        double sampleX = blockX + size * 0.06 * noise(blockX / (size * 0.45), blockZ / (size * 0.45));
        double sampleZ = blockZ + size * 0.06 * noise(blockX / (size * 0.45) + 103.7, blockZ / (size * 0.45) - 67.2);
        int cellZ = (int) Math.floor(sampleZ / rowHeight());
        int cellX = (int) Math.floor(sampleX / size - 0.5 * (cellZ & 1));
        // Region centres sit on a jittered hexagonal grid. Two rings cover all candidates. The owner is the
        // region whose rim is nearest, so ownership changes halfway between two mountain feet, inside the
        // mountains, and the edge distance stays continuous across that change.
        double outside = Double.POSITIVE_INFINITY;
        int winnerCellX = 0, winnerCellZ = 0;
        for (int z = cellZ - 2; z <= cellZ + 2; z++) {
            for (int x = cellX - 2; x <= cellX + 2; x++) {
                double dx = sampleX - siteX(x, z), dz = sampleZ - siteZ(x, z);
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance - MAX_RADIUS * 1.16 * (1 + LOBES + OVAL) >= outside) continue;
                double rim = distance - radius(x, z) * shape(x, z, Math.atan2(dz, dx));
                if (rim < outside) {
                    outside = rim;
                    winnerCellX = x;
                    winnerCellZ = z;
                }
            }
        }
        // Round region with a jagged rim: the mountain foot lies `radius` blocks from the centre.
        double edgeDistance = rimDistance - outside - rimNoise(sampleX, sampleZ);
        // Lobes: the whole mountain front moves up to RIM_REACH blocks into the region. Past the ramp the
        // terrain no longer depends on depth, so the shift fades out there, well before the ownership change.
        double depth = rimDistance - edgeDistance;
        double fadeEnd = footSpan + 40;
        if (depth < fadeEnd) {
            double reach = rimReach(winnerCellX, winnerCellZ, sampleX, sampleZ);
            edgeDistance -= reach * (1 - smoothstep(footSpan, fadeEnd, depth));
        }
        int region = Math.floorMod(winnerCellX + 2 * winnerCellZ + (int) seed, REGION_COUNT);
        double t = Math.clamp(1 - edgeDistance / borderWidth, 0, 1);
        float border = (float) (t * t * (3 - 2 * t));
        return new Sample(region, border, (float) edgeDistance, winnerCellX, winnerCellZ,
                (float) (sampleX - siteX(winnerCellX, winnerCellZ)), (float) (sampleZ - siteZ(winnerCellX, winnerCellZ)));
    }

    /**
     * Base radius of a region (multiplied by its shape): MIN_RADIUS–MAX_RADIUS blocks, trimmed so the whole
     * rim stays at least MIN_GAP / 2 behind the halfway line to every neighbour, in every direction. The rim
     * noise then narrows or widens a belt by at most 2 * RIM_NOISE, which still leaves both ramps finished
     * before the belt's middle.
     */
    private double radius(int cellX, int cellZ) {
        int slot = Math.floorMod(cellX * 7919 + cellZ * 104729, 4096);
        CachedRadius cached = radii.get(slot);
        if (cached != null && cached.x() == cellX && cached.z() == cellZ) return cached.radius();
        double cx = siteX(cellX, cellZ), cz = siteZ(cellX, cellZ);
        double radius = MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * unit(hash(cellX, cellZ, 3));
        for (int z = cellZ - 1; z <= cellZ + 1; z++) {
            for (int x = cellX - 1; x <= cellX + 1; x++) {
                if (x == cellX && z == cellZ) continue;
                double dx = siteX(x, z) - cx, dz = siteZ(x, z) - cz;
                double separation = Math.sqrt(dx * dx + dz * dz);
                double limit = (separation - MIN_GAP) / 2;
                double nx = dx / separation, nz = dz / separation;
                // Check the rim in 72 directions against the line parallel to the halfway line.
                for (int k = 0; k < 72; k++) {
                    double angle = k * Math.PI / 36;
                    double reach = Math.cos(angle) * nx + Math.sin(angle) * nz;
                    if (reach > 0) radius = Math.min(radius, limit / (shape(cellX, cellZ, angle) * reach));
                }
            }
        }
        radii.set(slot, new CachedRadius(cellX, cellZ, radius));
        return radius;
    }

    private record CachedRadius(int x, int z, double radius) {}

    /**
     * Radius multiplier by direction, so regions are not circles: rounded-hexagon corners pointing into the
     * mountain areas where three regions meet (so belts there are not much wider than between two regions),
     * a soft three-lobed bulge and a slight oval stretch. Lobe and oval strength and orientation vary per region.
     */
    private double shape(int cellX, int cellZ, double angle) {
        double lobes = LOBES * (2 * unit(hash(cellX, cellZ, 4)) - 1);
        double oval = OVAL * (0.4 + 0.6 * unit(hash(cellX, cellZ, 5)));
        double ovalAngle = Math.PI * unit(hash(cellX, cellZ, 6));
        return hexagon(angle) * (1 + lobes * Math.cos(3 * (angle - Math.PI / 6)) + oval * Math.cos(2 * (angle - ovalAngle)));
    }

    /**
     * Rounded hexagon with flat sides facing the six grid neighbours: the inverse of a smooth maximum
     * (HEX-norm) of the three side-normal projections, scaled to 1 towards the neighbours.
     */
    private static double hexagon(double angle) {
        double sum = 0;
        for (int k = 0; k < 3; k++) sum += Math.pow(Math.abs(Math.cos(angle - k * Math.PI / 3)), HEX);
        return Math.pow((1 + 2 * Math.pow(0.5, HEX)) / sum, 1 / HEX);
    }

    /** Jagged rim: bays and headlands of a few hundred blocks with smaller notches; at most ±RIM_NOISE. */
    private double rimNoise(double x, double z) {
        return 0.55 * RIM_NOISE * noise3d(x / 300, 0.29, z / 300, 60)
                + 0.30 * RIM_NOISE * noise3d(x / 110, 0.53, z / 110, 61)
                + 0.15 * RIM_NOISE * noise3d(x / 40, 0.71, z / 40, 62);
    }

    /**
     * Inward reach of the mountain front, read from the point on the rim straight out from the region centre,
     * so it is constant along each inward line: the front keeps its normal ramp profile and never breaks off
     * into islands. Broad lobes and bays a few hundred blocks across.
     */
    private double rimReach(int cellX, int cellZ, double x, double z) {
        double cx = siteX(cellX, cellZ), cz = siteZ(cellX, cellZ);
        double dx = x - cx, dz = z - cz;
        double distance = Math.max(1, Math.sqrt(dx * dx + dz * dz));
        double rimX = cx + dx / distance * radius(cellX, cellZ), rimZ = cz + dz / distance * radius(cellX, cellZ);
        return RIM_REACH * smoothstep(-0.4, 0.4, fbm(rimX, rimZ, 400, 2, 68));
    }

    private double rowHeight() {
        return size * Math.sqrt(3) / 2;
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
        // Moderate ridged detail gives the peaks sharper crests and points without returning to thin spires.
        double crests = ridged(u, v, 170, 3, 100);
        double alpineHeight = RANGE_CENTER + 75 * body + 32 * peaks + RIDGE_STRENGTH * (crests - 0.4)
                + 50 * summit * (0.7 + 0.6 * Math.max(0, peaks));
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

    // Hexagonal rows (odd rows shifted half a spacing) with a small jitter, so neighbours stay evenly spaced.
    // Region centre in the bent plane; package-private so EverwinterGroups can search a region (read-only).
    double siteX(int x, int z) {
        return (x + 0.5 * (z & 1) + 0.5 + (unit(hash(x, z, 0)) - 0.5) * 2 * JITTER) * size;
    }

    double siteZ(int x, int z) {
        return (z + 0.5 + (unit(hash(x, z, 1)) - 0.5) * 2 * JITTER) * rowHeight();
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

    /** Ridged fractal in 0..1: sharp crests where the noise crosses zero, detail strongest along the crests. */
    private double ridged(double x, double z, double scale, int octaves, int salt) {
        double sum = 0, amplitude = 1, normalization = 0, weight = 1;
        for (int octave = 0; octave < octaves; octave++) {
            double signal = square(1 - Math.abs(noise3d(x / scale, 0.413 + octave * 0.191, z / scale, salt + octave)));
            sum += amplitude * signal * weight;
            normalization += amplitude;
            weight = Math.clamp(signal * 1.6, 0, 1);
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

    /**
     * {@code cellX}/{@code cellZ} identify the owning region's grid cell and {@code localX}/{@code localZ} give the
     * position relative to its centre in the bent plane the region shape is built in (for biome districts).
     */
    public record Sample(int region, float border, float edgeDistance, int cellX, int cellZ, float localX, float localZ) {}

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
