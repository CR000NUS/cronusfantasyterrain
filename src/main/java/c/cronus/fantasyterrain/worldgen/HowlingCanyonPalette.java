package c.cronus.fantasyterrain.worldgen;

/**
 * Howling Canyon materials (pure Java): the glacier massifs are glacier ice in horizontal strata (packed ice,
 * blue ice and thin white firn bands) streaked with vertical blue-ice columns, so cliff faces look fluted; the
 * ground, benches and massif tops carry a snow cover with wind-polished ice patches; the frozen river is bare
 * ice broken into snow-covered floes. Below the glacier lie stone and deepslate. Covers Y >= 0 only; below that
 * vanilla deepslate, ores and veins stay in charge.
 */
public final class HowlingCanyonPalette {
    public enum Material { SNOW, PACKED_ICE, BLUE_ICE, DEEPSLATE, STONE }
    /** Depth of the snow cover on gentle ground (ground, benches, massif tops) before the glacier ice begins. */
    public static final int COVER_DEPTH = 3;
    /** Thickness of a glacier stratum, in blocks. */
    private static final double STRATUM = 3.5;
    /** River ice: floe cells this many blocks across. */
    private static final double FLOE = 7;
    private final HowlingCanyonTerrain noise;

    public HowlingCanyonPalette(long seed) {
        this.noise = new HowlingCanyonTerrain(seed);
    }

    /**
     * @param top    highest solid block of the column
     * @param bed    top of the rock beneath the glacier
     * @param gentle whether the surface here is flat enough to hold snow
     */
    public Material select(int x, int y, int z, int top, float bed, int feature, boolean gentle) {
        if (y < bed) return underground(x, y, z);
        int depth = top - y;
        if (feature == HowlingCanyonTerrain.RIVER) {
            // Frozen river: bare ice leads between snow-covered floes, solid ice beneath.
            return depth == 0 && floe(x, z) ? Material.SNOW : depth < 3 ? Material.PACKED_ICE : glacier(x, y, z);
        }
        if (gentle && depth < COVER_DEPTH) {
            // Snow, with patches of wind-polished ice on the open ground and massif tops.
            boolean ice = depth == 0 && noise.noise(x / 30.0, z / 30.0, 70) + 0.3 * noise.noise(x / 7.0, z / 7.0, 71) > 0.62;
            return ice ? Material.PACKED_ICE : Material.SNOW;
        }
        return glacier(x, y, z);
    }

    /**
     * Glacier ice: horizontal strata (gently warped) of packed ice, blue ice and thin white firn bands, crossed by
     * vertical blue-ice columns that flute the cliff faces.
     */
    public Material glacier(int x, int y, int z) {
        if (noise.noise(x / 2.6, z / 2.6, 72) + 0.25 * noise.noise(x / 9.0, (z + y) / 20.0, 73) > 0.62) return Material.BLUE_ICE;
        double level = (y + 3 * noise.noise(x / 140.0, z / 140.0, 74) + noise.noise(x / 25.0, z / 25.0, 75)) / STRATUM;
        int stratum = (int) Math.floor(level);
        double kind = HowlingCanyonTerrain.unit(noise.hash(stratum, 0, 76));
        if (kind < 0.14 && level - stratum < 0.35) return Material.SNOW;
        return kind > 0.62 ? Material.BLUE_ICE : Material.PACKED_ICE;
    }

    /** Snow-covered floe (true) or bare ice lead between floes: jittered cells with open ice along their edges. */
    private boolean floe(int x, int z) {
        double fx = x / FLOE + 0.35 * noise.noise(x / 11.0, z / 11.0, 77), fz = z / FLOE + 0.35 * noise.noise(x / 11.0, z / 11.0, 78);
        int cellX = (int) Math.floor(fx), cellZ = (int) Math.floor(fz);
        double first = Double.POSITIVE_INFINITY, second = Double.POSITIVE_INFINITY;
        long owner = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                double px = cellX + dx + HowlingCanyonTerrain.unit(noise.hash(cellX + dx, cellZ + dz, 79));
                double pz = cellZ + dz + HowlingCanyonTerrain.unit(noise.hash(cellX + dx, cellZ + dz, 80));
                double distance = Math.hypot(fx - px, fz - pz);
                if (distance < first) {
                    second = first;
                    first = distance;
                    owner = noise.hash(cellX + dx, cellZ + dz, 81);
                } else if (distance < second) {
                    second = distance;
                }
            }
        }
        // Edges between cells stay open ice; a few whole cells are open water frozen clear.
        return (second - first) * FLOE > 1.2 && HowlingCanyonTerrain.unit(owner) < 0.8;
    }

    /** Stone and deepslate in large blobs, with deepslate growing more common toward Y=0. */
    public Material underground(int x, int y, int z) {
        double blob = noise.noise(x / 28.0 + y / 61.0, z / 28.0 - y / 47.0, 82)
                + 0.5 * noise.noise((x + y) / 11.0, (z - y) / 11.0, 83);
        return blob + (24 - y) / 70.0 > -0.1 ? Material.DEEPSLATE : Material.STONE;
    }
}
