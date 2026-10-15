package c.cronus.fantasyterrain.worldgen;

/**
 * Deepslate exterior with a natural snowline; buried rock has its own palette. The snowline wanders with
 * the terrain and fades out through a band of snow patches instead of a cut at one fixed Y.
 */
public final class BorderPalette {
    public enum Material { DEEPSLATE, SNOW, TUFF }
    /** Average snowline. The local line wanders by up to about ±17 blocks around it. */
    public static final int SNOW_LINE = 220;
    /** Height of the patchy transition band around the local snowline. */
    public static final int SNOW_BAND = 24;
    /** Outdoor faces this far above the local snowline are always snow, even when steep. */
    public static final int FULL_SNOW = 27;

    public static Material select(long seed, int x, int y, int z, int top, int exteriorFloor, boolean gentle) {
        if (y >= exteriorFloor) {
            return snow(seed, x, y, z, top - exteriorFloor) ? Material.SNOW : Material.DEEPSLATE;
        }
        // Underground host rock is deepslate, with a small amount of tuff. Ores run later.
        return chance(seed, Math.floorDiv(x, 4), Math.floorDiv(y, 4), Math.floorDiv(z, 4), 4) < 0.04
                ? Material.TUFF : Material.DEEPSLATE;
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
        // 0 on ledges and gentle slopes, up to 0.6 band heights on cliff faces.
        double steep = 0.6 * Math.clamp((drop - 2) / 8.0, 0, 1);
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
