package c.cronus.fantasyterrain.worldgen;

import java.util.Random;

/** Checks continuous blackstone/snow bands and separation from underground rock. */
public final class BorderPaletteTest {
    public static void main(String[] args) {
        Random random = new Random(872145);
        int samples = 200000, tuff = 0;
        for (int i = 0; i < samples; i++) {
            int x = random.nextInt(200000) - 100000, z = random.nextInt(200000) - 100000;
            long seed = random.nextLong();
            for (int y : new int[] {80, 150, 219}) {
                require(BorderPalette.select(seed, x, y, z, 260, y, false) == BorderPalette.Material.BLACKSTONE,
                        "Every outdoor face below the snowline must be blackstone");
                require(BorderPalette.select(seed, x, y, z, y, y, true) == BorderPalette.Material.BLACKSTONE,
                        "Lower ledges must have no light patches");
            }
            for (int y : new int[] {220, 240, 260}) {
                require(BorderPalette.select(seed, x, y, z, 260, y, false) == BorderPalette.Material.SNOW,
                        "The summit cap must cover steep outdoor faces too");
                require(BorderPalette.select(seed, x, y, z, y, y, true) == BorderPalette.Material.SNOW,
                        "Snow must form a continuous band, independent of coordinates and seed");
            }
            var buried = BorderPalette.select(seed, x, 150, z, 260, 250, true);
            require(buried == BorderPalette.Material.DEEPSLATE || buried == BorderPalette.Material.TUFF,
                    "Buried rock must retain the previously specified interior palette");
            if (buried == BorderPalette.Material.TUFF) tuff++;
            require(BorderPalette.select(seed, x, 240, z, 260, 250, true) != BorderPalette.Material.SNOW,
                    "High-altitude cave walls must not receive the outdoor snow cap");
        }
        require(Math.abs(tuff / (double) samples - 0.04) < 0.005, "Underground tuff must remain a small minority");
        System.out.println("Continuous blackstone/snowline checks passed across 200,000 positions; underground palette preserved.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
