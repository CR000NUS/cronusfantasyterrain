package c.cronus.fantasyterrain.worldgen;

/**
 * Splits every region into one large district per pool biome, so each biome of a pool appears exactly once
 * per region. Pure Java (no Minecraft dependencies) so the tests and previews can use it directly.
 * <p>
 * Districts are the cells of a Voronoi diagram whose sites sit on a ring around the region centre (seeded
 * rotation, uneven spacing, off-centre ring), so they are pie-like slices that meet somewhere near the middle.
 * A gentle domain warp bends the straight dividing lines into organic biome boundaries. Everything works in
 * the bent plane the region shape is built in ({@link RegionLayout.Sample#localX}), compressed radially
 * towards the region centre: near the mountains the boundaries then follow the same radial lines as the rim
 * lobes and cross the mountain foot steeply, instead of running along it and leaving thin slivers or tails of a
 * biome in the rim's bays. Which biome gets which slice is a seeded shuffle per region.
 */
public final class BiomeDistricts {
    /** Smallest and largest number of biomes in a region pool. */
    public static final int MIN_BIOMES = 4, MAX_BIOMES = 6;
    /** Number of distinct router codes: one digit (slot) per possible pool size, see {@link #code}. */
    public static final int CODES = codeBase(MAX_BIOMES + 1);
    /** Ring radius of the district sites and largest offset of the ring centre, as fractions of the region spacing. */
    private static final double RING_RADIUS = 0.17, RING_SHIFT = 0.04;
    /**
     * Radial compression scale, as a fraction of the region spacing: a point r blocks from the region centre is
     * placed at COMPRESS * tanh(r / COMPRESS), nearly unchanged in the middle, strongly flattened near the rim.
     */
    private static final double COMPRESS = 0.29;
    /** Largest angular wobble of a site, as a fraction of the even spacing between sites. */
    private static final double ANGLE_JITTER = 0.15;
    /** Boundary warp in blocks: broad bends plus smaller wiggles. */
    private static final double WARP = 130, WARP_DETAIL = 35;
    private final long seed;
    private final int size;

    public BiomeDistricts(long seed, int size) {
        this.seed = seed;
        this.size = size;
    }

    /**
     * Pool slot of the district containing a column, and the distance from the column to the nearest other
     * district, for fading biome terrain out towards biome boundaries. The distance is measured in the
     * compressed, warped plane: exactly 0 on a boundary and roughly the distance in blocks nearby.
     */
    public record District(int slot, double edge) {}

    public District district(RegionLayout.Sample sample, int count) {
        return district(sample, local(sample), count);
    }

    /**
     * The district slots for every pool size at once, packed into one integer for the router's vegetation
     * channel (mixed radix: the slot for pool size n is digit n). {@link #slot} unpacks it.
     */
    public int code(RegionLayout.Sample sample) {
        double[] local = local(sample);
        int code = 0;
        for (int count = MIN_BIOMES; count <= MAX_BIOMES; count++) {
            code += district(sample, local, count).slot() * codeBase(count);
        }
        return code;
    }

    /** Pool slot for a pool of {@code count} biomes from a router code made by {@link #code}. */
    public static int slot(int code, int count) {
        return Math.floorMod(code / codeBase(count), count);
    }

    /** Product of all pool sizes below {@code count}. */
    private static int codeBase(int count) {
        int base = 1;
        for (int n = MIN_BIOMES; n < count; n++) base *= n;
        return base;
    }

    /** Compressed, warped position relative to the region centre (see the class comment). */
    private double[] local(RegionLayout.Sample sample) {
        double dx = sample.localX(), dz = sample.localZ(), radius = Math.sqrt(dx * dx + dz * dz);
        double scale = COMPRESS * size;
        double factor = radius > 0 ? scale * Math.tanh(radius / scale) / radius : 1;
        double px = dx * factor, pz = dz * factor;
        // The warp is sampled in the compressed plane too, so near the rim it barely changes outward. The cell
        // offset gives every region its own warp.
        double nx = px + sample.cellX() * (double) size, nz = pz + sample.cellZ() * (double) size;
        return new double[] {
                px + WARP * noise(nx / 900, nz / 900, 1) + WARP_DETAIL * noise(nx / 230, nz / 230, 2),
                pz + WARP * noise(nx / 900, nz / 900, 3) + WARP_DETAIL * noise(nx / 230, nz / 230, 4)};
    }

    private District district(RegionLayout.Sample sample, double[] local, int count) {
        int cellX = sample.cellX(), cellZ = sample.cellZ();
        int salt = count * 64;
        double x = local[0], z = local[1];
        double shiftAngle = 2 * Math.PI * unit(hash(cellX, cellZ, salt));
        double shift = RING_SHIFT * size * unit(hash(cellX, cellZ, salt + 1));
        double ringX = shift * Math.cos(shiftAngle), ringZ = shift * Math.sin(shiftAngle);
        double rotation = 2 * Math.PI * unit(hash(cellX, cellZ, salt + 2));
        double[] siteX = new double[count], siteZ = new double[count];
        int nearest = 0;
        double best = Double.POSITIVE_INFINITY;
        for (int k = 0; k < count; k++) {
            double angle = rotation + 2 * Math.PI * (k + ANGLE_JITTER * (2 * unit(hash(cellX, cellZ, salt + 8 + k)) - 1)) / count;
            double radius = RING_RADIUS * size * (0.9 + 0.2 * unit(hash(cellX, cellZ, salt + 16 + k)));
            siteX[k] = ringX + radius * Math.cos(angle);
            siteZ[k] = ringZ + radius * Math.sin(angle);
            double distance = square(x - siteX[k]) + square(z - siteZ[k]);
            if (distance < best) {
                best = distance;
                nearest = k;
            }
        }
        // Distance to the closest dividing line (perpendicular bisector to another site).
        double edge = Double.POSITIVE_INFINITY;
        for (int k = 0; k < count; k++) {
            if (k == nearest) continue;
            double other = square(x - siteX[k]) + square(z - siteZ[k]);
            double separation = Math.sqrt(square(siteX[k] - siteX[nearest]) + square(siteZ[k] - siteZ[nearest]));
            edge = Math.min(edge, (other - best) / (2 * separation));
        }
        return new District(shuffledSlot(cellX, cellZ, count, nearest), edge);
    }

    /** Seeded Fisher-Yates shuffle of the pool per region, so biome order around the ring varies. */
    private int shuffledSlot(int cellX, int cellZ, int count, int site) {
        int[] slots = new int[count];
        for (int k = 0; k < count; k++) slots[k] = k;
        for (int k = count - 1; k > 0; k--) {
            int swap = (int) (unit(hash(cellX, cellZ, count * 64 + 32 + k)) * (k + 1));
            int value = slots[k];
            slots[k] = slots[swap];
            slots[swap] = value;
        }
        return slots[site];
    }

    /** 2D gradient noise, roughly -1..1. */
    private double noise(double x, double z, int salt) {
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

    private long hash(int x, int z, int salt) {
        long h = seed ^ ((long) x * 0x632BE59BD9B4E019L) ^ ((long) z * 0x9E3779B97F4A7C15L) ^ ((long) salt * 0xD1B54A32D192ED03L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    private static double unit(long hash) { return (hash >>> 11) * 0x1.0p-53; }
    private static double square(double value) { return value * value; }
    private static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    private static double lerp(double t, double a, double b) { return a + t * (b - a); }
}
