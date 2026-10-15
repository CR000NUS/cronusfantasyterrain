package c.cronus.fantasyterrain.worldgen;

/** Blackstone exterior with a continuous summit snowline; buried rock has its own palette. */
public final class BorderPalette {
    public enum Material { BLACKSTONE, DEEPSLATE, SNOW, TUFF }
    public static final int SNOW_LINE = 220;

    public static Material select(long seed, int x, int y, int z, int top, int exteriorFloor, boolean gentle) {
        if (y >= exteriorFloor) {
            // No coordinate randomness, mixing or slope holes in the outdoor snow cap.
            return y >= SNOW_LINE ? Material.SNOW : Material.BLACKSTONE;
        }
        // Underground host rock is deepslate, with a small amount of tuff. Ores run later.
        return chance(seed, Math.floorDiv(x, 4), Math.floorDiv(y, 4), Math.floorDiv(z, 4), 4) < 0.04
                ? Material.TUFF : Material.DEEPSLATE;
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
