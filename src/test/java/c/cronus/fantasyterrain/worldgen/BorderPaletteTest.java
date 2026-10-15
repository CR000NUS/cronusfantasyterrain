package c.cronus.fantasyterrain.worldgen;

import java.util.Random;

/** Checks the deepslate faces, the gradual snowline and separation from underground rock. */
public final class BorderPaletteTest {
    public static void main(String[] args) {
        Random random = new Random(872145);
        int samples = 200000, tuff = 0;
        int low = BorderPalette.lowestSnow(), high = BorderPalette.fullSnow();
        int[] bandYs = {200, 210, 220, 230, 240, 250};
        int[] gentleSnow = new int[bandYs.length], steepSnow = new int[bandYs.length];
        for (int i = 0; i < samples; i++) {
            int x = random.nextInt(200000) - 100000, z = random.nextInt(200000) - 100000;
            long seed = random.nextLong();
            for (int y : new int[] {80, 150, low - 1}) {
                require(BorderPalette.select(seed, x, y, z, 260, y, false) == BorderPalette.Material.DEEPSLATE,
                        "Every outdoor face below the snow band must be deepslate, without tuff");
                require(BorderPalette.select(seed, x, y, z, y, y, true) == BorderPalette.Material.DEEPSLATE,
                        "Lower ledges must have no light patches");
            }
            for (int y : new int[] {high, high + 20, 300}) {
                require(BorderPalette.select(seed, x, y, z, 310, y, false) == BorderPalette.Material.SNOW,
                        "The summit cap must cover steep outdoor faces too");
                require(BorderPalette.select(seed, x, y, z, y, y, true) == BorderPalette.Material.SNOW,
                        "The summit cap must be continuous above the snow band");
            }
            for (int b = 0; b < bandYs.length; b++) {
                int y = bandYs[b];
                if (BorderPalette.select(seed, x, y, z, y, y, true) == BorderPalette.Material.SNOW) gentleSnow[b]++;
                if (BorderPalette.select(seed, x, y, z, y + 12, y, false) == BorderPalette.Material.SNOW) steepSnow[b]++;
            }
            var buried = BorderPalette.select(seed, x, 150, z, 260, 250, true);
            require(buried == BorderPalette.Material.DEEPSLATE || buried == BorderPalette.Material.TUFF,
                    "Buried rock must retain the previously specified interior palette");
            if (buried == BorderPalette.Material.TUFF) tuff++;
            require(BorderPalette.select(seed, x, 280, z, 300, 290, true) != BorderPalette.Material.SNOW,
                    "High-altitude cave walls must not receive the outdoor snow cap");
        }
        // No hard cut: snow cover grows gradually through the band, and ledges whiten before cliff faces.
        double previous = 0;
        StringBuilder profile = new StringBuilder();
        for (int b = 0; b < bandYs.length; b++) {
            double cover = gentleSnow[b] / (double) samples;
            profile.append(String.format(" Y%d %.0f%%/%.0f%%", bandYs[b], 100 * cover, 100.0 * steepSnow[b] / samples));
            require(cover >= previous, "Snow cover must not shrink with altitude");
            require(steepSnow[b] <= gentleSnow[b], "Steep faces must not hold more snow than ledges");
            previous = cover;
        }
        require(gentleSnow[1] > samples * 0.03 && gentleSnow[1] < samples * 0.5, "Lower snow band must be patchy, not empty or solid");
        require(gentleSnow[3] > samples * 0.5 && gentleSnow[3] < samples * 0.97, "Upper snow band must be patchy, not empty or solid");
        require(Math.abs(tuff / (double) samples - 0.04) < 0.005, "Underground tuff must remain a small minority");
        System.out.println("Snowline checks passed across 200,000 positions; snow cover ledge/cliff:" + profile
                + "; no snow below Y=" + low + ", full cap from Y=" + high + "; underground palette preserved.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
