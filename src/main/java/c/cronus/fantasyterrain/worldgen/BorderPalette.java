package c.cronus.fantasyterrain.worldgen;

/**
 * Deepslate exterior with a natural snowline; buried rock has its own palette. The snowline wanders with
 * the terrain and fades out through a band of snow patches instead of a cut at one fixed Y.
 */
public final class BorderPalette {
    public enum Material { DEEPSLATE, SNOW, TUFF, PACKED_ICE, BLUE_ICE }
    /** Average snowline (middle of the patchy band). The local line wanders by up to about ±17 blocks around it. */
    public static final int SNOW_LINE = 214;
    /** Height of the patchy transition band around the local snowline. */
    public static final int SNOW_BAND = 36;
    /** Extra height (blocks) steep faces need before they hold snow. */
    private static final double STEEP_SHED = 14.4;
    /** Outdoor faces this far above the local snowline are always snow, even when steep. */
    public static final int FULL_SNOW = 33;

    public static Material select(long seed, int x, int y, int z, int top, int exteriorFloor, boolean gentle) {
        if (y >= exteriorFloor) {
            return snow(seed, x, y, z, top - exteriorFloor) ? Material.SNOW : Material.DEEPSLATE;
        }
        // Underground host rock is deepslate, with a small amount of tuff. Ores run later.
        return chance(seed, Math.floorDiv(x, 4), Math.floorDiv(y, 4), Math.floorDiv(z, 4), 4) < 0.04
                ? Material.TUFF : Material.DEEPSLATE;
    }

    /**
     * Border palette just beyond where Everwinter's materials take over the mountain side (decision 25,
     * {@link EverwinterColumns}): patchy frost on the outdoor rock, snow on gentle and moderate slopes and
     * ledges, glacier ice on some steep faces, deepslate elsewhere. Without growth this is exactly {@link #select}.
     */
    public static Material selectFringe(long seed, int x, int y, int z, int top, int exteriorFloor, boolean gentle, double growth) {
        Material rock = select(seed, x, y, z, top, exteriorFloor, gentle);
        if (growth <= 0 || y < exteriorFloor || rock == Material.SNOW) return rock;
        double frost = growth + 0.35 * smooth(seed, x / 16.0, z / 16.0, 20) + 0.1 * chance(seed, x, 0, z, 21) - 0.6;
        if (frost <= 0) return rock;
        if (top - y < 2 && (gentle || top - exteriorFloor <= 4)) return Material.SNOW;
        return frost > 0.25 ? glacierIce(seed, x, y, z) : rock;
    }

    /** Glacier ice: packed ice with thin, gently tilted blue-ice layers. */
    private static Material glacierIce(long seed, int x, int y, int z) {
        double layer = smooth(seed, x / 40.0, z / 40.0, 22) * 2 + y / 3.0;
        return Math.abs(layer - Math.round(layer)) < 0.12 ? Material.BLUE_ICE : Material.PACKED_ICE;
    }

    /** Lowest Y that can ever receive snow; everything below is plain deepslate outdoors. */
    public static int lowestSnow() {
        return SNOW_LINE - 17 - SNOW_BAND / 2;
    }

    /** Lowest Y from which every outdoor face is snow. */
    public static int fullSnow() {
        return SNOW_LINE + 17 + FULL_SNOW;
    }

    /**
     * Snow cover of one outdoor block. Near the local snowline, snow forms patches (soft noise plus a little
     * per-column scatter); steep faces shed snow, so ledges and gentle slopes whiten first.
     */
    private static boolean snow(long seed, int x, int y, int z, int drop) {
        double line = SNOW_LINE + 12 * smooth(seed, x / 96.0, z / 96.0, 5) + 5 * smooth(seed, x / 24.0, z / 24.0, 6);
        double level = (y - line) / SNOW_BAND;
        if (level < -0.5) return false;
        // 0 on ledges and gentle slopes, up to STEEP_SHED blocks on cliff faces.
        double steep = STEEP_SHED / SNOW_BAND * Math.clamp((drop - 2) / 8.0, 0, 1);
        double patch = 0.75 * (smooth(seed, x / 9.0, z / 9.0, 7) * 0.5 + 0.5) + 0.25 * chance(seed, x, 0, z, 8);
        return level - steep > patch - 0.5;
    }

    /** Smooth value noise in -1..1. */
    private static double smooth(long seed, double x, double z, int salt) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double tx = x - ix, tz = z - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = chance(seed, ix, 0, iz, salt), b = chance(seed, ix + 1, 0, iz, salt);
        double c = chance(seed, ix, 0, iz + 1, salt), d = chance(seed, ix + 1, 0, iz + 1, salt);
        return 2 * ((a + (b - a) * tx) * (1 - tz) + (c + (d - c) * tx) * tz) - 1;
    }

    private static double chance(long seed, int x, int y, int z, int salt) {
        long hash = seed ^ (x * 0x632BE59BD9B4E019L) ^ (y * 0x9E3779B97F4A7C15L)
                ^ (z * 0x94D049BB133111EBL) ^ (salt * 0xD1B54A32D192ED03L);
        hash = (hash ^ (hash >>> 30)) * 0xBF58476D1CE4E5B9L;
        hash = (hash ^ (hash >>> 27)) * 0x94D049BB133111EBL;
        hash ^= hash >>> 31;
        return (hash >>> 11) * 0x1.0p-53;
    }

    private BorderPalette() {}
}
