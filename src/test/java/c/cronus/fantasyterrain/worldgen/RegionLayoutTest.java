package c.cronus.fantasyterrain.worldgen;

import java.util.Random;

/** Standalone checks for the region geometry, including negative world coordinates. */
public final class RegionLayoutTest {
    public static void main(String[] args) {
        RegionLayout layout = new RegionLayout(12345L, 3072, 360);
        RegionLayout other = new RegionLayout(54321L, 3072, 360);
        Random random = new Random(9876L);
        boolean[] found = new boolean[5];
        int boundaries = 0, seedChanges = 0, interiors = 0;
        int peaks = 0, canyons = 0, cliffs = 0, deepDrops = 0, wideCanyons = 0;
        int narrowSpines = 0;

        for (int i = 0; i < 30000; i++) {
            int x = random.nextInt(100000) - 50000, z = random.nextInt(100000) - 50000;
            RegionLayout.Sample sample = layout.sample(x, z);
            require(sample.equals(layout.sample(x, z)), "Layout must be deterministic");
            require(sample.region() >= 0 && sample.region() < 5, "Invalid region");
            require(sample.border() >= 0 && sample.border() <= 1, "Invalid border mask");
            found[sample.region()] = true;
            float height = layout.surfaceHeight(x, z, sample);
            RegionLayout.TerrainColumn column = layout.terrainColumn(x, z, sample);
            require(column.density(-64) > 0 && column.density(320) < 0, "Invalid world floor or ceiling");
            require(column.density((int) Math.ceil(column.upperBound()) + 1) < 0,
                    "Surface search bound misses rock above it");
            require(column.density(100) == layout.terrainColumn(x, z, sample).density(100), "Density is not deterministic");
            require(height >= 8 && height <= 312, "Height exceeds declared density range");
            if (sample.border() == 0) {
                interiors++;
                require(height >= 75, "Region interior contains canyon cuts");
                require(height <= 85, "Region interior contains mountains");
                for (int y = 0; y <= 320; y += 8) {
                    require((column.density(y) > 0) == (height > y), "Overhang shaping leaks into interiors");
                }
            }
            if (!sample.equals(other.sample(x, z))) seedChanges++;
            RegionLayout.Sample neighbor = layout.sample(x + 4, z);
            float neighborHeight = layout.surfaceHeight(x + 4, z, neighbor);
            if (sample.border() > 0.8f) {
                boolean aboveSurface = false;
                for (int y = -64; y <= 320; y += 2) {
                    boolean solid = column.density(y) > 0;
                    require(!aboveSurface || !solid, "Detached rock or an artificial undercut interrupts a grounded ridge");
                    if (!solid) aboveSurface = true;
                }
                if (height > 160) {
                    int top = actualTop(column);
                    int left = actualTop(layout.terrainColumn(x - 4, z, layout.sample(x - 4, z)));
                    int right = actualTop(layout.terrainColumn(x + 4, z, layout.sample(x + 4, z)));
                    int north = actualTop(layout.terrainColumn(x, z - 4, layout.sample(x, z - 4)));
                    int south = actualTop(layout.terrainColumn(x, z + 4, layout.sample(x, z + 4)));
                    if (top - Math.max(left, right) > 28 || top - Math.max(north, south) > 28) narrowSpines++;
                }
                if (height > 230) peaks++;
                if (height < 50) canyons++;
                if (Math.abs(height - neighborHeight) > 8) cliffs++;
                RegionLayout.Sample distant = layout.sample(x + 80, z);
                if (distant.border() > 0.6f && Math.abs(height - layout.surfaceHeight(x + 80, z, distant)) > 80) deepDrops++;
                if (height < 50) {
                    boolean wide = true;
                    for (int offset = -16; offset <= 16; offset += 4) {
                        RegionLayout.Sample floor = layout.sample(x + offset, z);
                        if (floor.border() < 0.65f || layout.surfaceHeight(x + offset, z, floor) > 63) wide = false;
                    }
                    if (wide) wideCanyons++;
                }
            }
            if (neighbor.region() != sample.region()) {
                boundaries++;
                require(sample.border() > 0.99f && neighbor.border() > 0.99f, "Region transition lacks mountains");
                // Either high peaks or canyon floors can occupy the actual ownership boundary.
                require(Math.abs(height - neighborHeight) < 100, "Terrain seam at border");
                require(height == layout.surfaceHeight(x, z, new RegionLayout.Sample(neighbor.region(), sample.border())),
                        "Changing region ownership changes border terrain");
            }
        }
        for (boolean region : found) require(region, "A region theme never appears");
        require(boundaries > 10, "No region transitions tested");
        require(seedChanges > 15000, "World seed does not affect layout");
        require(interiors > 15000, "Mountain belts occupy too much interior");
        require(peaks > 100, "Border lacks tall peaks");
        require(canyons > 100, "Border lacks deep canyons");
        require(cliffs > 20, "Border terrain is too gentle");
        require(deepDrops > 20, "Border lacks deep drops between nearby positions");
        require(wideCanyons > 20, "Canyons are too narrow");
        require(narrowSpines == 0, "Border contains sampled 1–4 block spines with abrupt drops on both sides");
        System.out.println("Region geometry checks passed; " + boundaries + " transitions, " + interiors + " interiors.");
        System.out.println("Rugged border checks: " + peaks + " peaks, " + canyons + " canyon floors, "
                + cliffs + " cliffs, " + deepDrops + " deep drops, " + wideCanyons + " wide canyon floors.");
        System.out.println("Grounded ridge checks passed; " + narrowSpines + " narrow spines.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static int actualTop(RegionLayout.TerrainColumn column) {
        for (int y = (int) Math.ceil(column.upperBound()); y >= 0; y--) {
            if (column.density(y) > 0) return y;
        }
        return 0;
    }

}
