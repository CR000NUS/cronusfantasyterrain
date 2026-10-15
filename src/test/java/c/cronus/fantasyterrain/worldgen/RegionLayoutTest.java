package c.cronus.fantasyterrain.worldgen;

import java.util.Random;

/** Standalone checks for the region geometry, including negative world coordinates. */
public final class RegionLayoutTest {
    public static void main(String[] args) {
        RegionLayout layout = new RegionLayout(12345L, 3800, 360);
        RegionLayout other = new RegionLayout(54321L, 3800, 360);
        Random random = new Random(9876L);
        boolean[] found = new boolean[5];
        int boundaries = 0, seedChanges = 0, interiors = 0;
        int peaks = 0, cliffs = 0, ramps = 0;
        int core = 0, mainBand = 0, summits = 0, valleys = 0;
        int narrowSpines = 0;
        int foothills = 0, stoneOverBasin = 0, basinInteriors = 0;

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
            // Deepslate basin: a material channel that never rises above the terrain it classifies.
            float basin = layout.basinFloor(x, z, sample);
            require(basin >= 0 && basin <= height, "Basin floor leaves the column");
            if (sample.border() > RegionLayout.BORDER_THRESHOLD + 1e-4f) {
                require(basin == height, "Border mountain column contains region stone");
            } else if (sample.border() > 0) {
                foothills++;
                if (basin > 0 && basin < height) stoneOverBasin++;
            } else if (basin > 0) {
                basinInteriors++;
            }
            if (!sample.equals(other.sample(x, z))) seedChanges++;
            RegionLayout.Sample neighbor = layout.sample(x + 4, z);
            float neighborHeight = layout.surfaceHeight(x + 4, z, neighbor);
            if (sample.border() > 0) {
                // Raised massif: nothing in the belt dips to or below region ground, so no lakes or canyons.
                require(height >= 75, "Border belt contains a hole, lake or canyon below region ground");
                double depth = 1 - sample.edgeDistance() / 360.0;
                if (depth >= RegionLayout.RAMP_END) {
                    require(height >= RegionLayout.MASSIF_FLOOR, "Massif floor sinks toward ground level");
                    core++;
                    if (height >= 175 && height <= 250) mainBand++;
                    if (height > 270) summits++;
                    if (height < 170) valleys++;
                }
                if (depth <= RegionLayout.RAMP_START) require(height <= 86, "Climb starts before the ramp");
                // Near ground level the climb is a steep slope, never a sheer wall (>4 blocks between adjacent columns).
                float adjacentHeight = layout.surfaceHeight(x + 1, z, layout.sample(x + 1, z));
                if (Math.min(height, adjacentHeight) < 100) {
                    require(Math.abs(height - adjacentHeight) <= 4, "Ground-level cliff in the border belt");
                    if (depth > RegionLayout.RAMP_START && depth < RegionLayout.RAMP_END) ramps++;
                }
            }
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
                if (Math.abs(height - neighborHeight) > 8) cliffs++;
            }
            if (neighbor.region() != sample.region()) {
                boundaries++;
                // Ownership changes halfway between two mountain feet, where both ramps have finished.
                for (RegionLayout.Sample side : new RegionLayout.Sample[] {sample, neighbor}) {
                    require(1 - side.edgeDistance() / 360.0 >= RegionLayout.RAMP_END, "Region transition lacks mountains");
                }
                require(Math.abs(height - neighborHeight) < 100, "Terrain seam at border");
                require(height == layout.surfaceHeight(x, z, new RegionLayout.Sample(neighbor.region(), sample.border(), sample.edgeDistance())),
                        "Changing region ownership changes border terrain");
            }
        }
        for (boolean region : found) require(region, "A region theme never appears");
        require(boundaries > 10, "No region transitions tested");
        require(seedChanges > 15000, "World seed does not affect layout");
        // Rounded-hexagon regions cover about 60% of the world; the rest is mountain belts.
        require(interiors > 11000, "Mountain belts occupy too much interior");
        require(peaks > 100, "Border lacks tall peaks");
        // One broad range: most of the massif in the main band, rare taller summits and rare deep valleys.
        require(mainBand > core * 0.75, "Most of the massif must lie between Y=175 and Y=250");
        require(summits > core * 0.005 && summits < core * 0.08, "Tall summits must exist but stay rare");
        require(valleys > core * 0.002 && valleys < core * 0.06, "Deep valleys must exist but stay rare");
        require(cliffs < core * 0.03, "Mountains must have broad flanks, not thin steep spikes");
        require(ramps > 50, "No region-to-massif ramps sampled");
        require(narrowSpines == 0, "Border contains sampled 1–4 block spines with abrupt drops on both sides");
        // Columns exactly on the rim have a zero-thickness stone layer; allow that rounding sliver.
        require(foothills > 100 && foothills - stoneOverBasin <= foothills / 200, "Foothills must keep stone above the deepslate basin");
        require(basinInteriors > 100 && basinInteriors < interiors / 2, "Basin wall must curve into, but not under, whole regions");
        System.out.println("Region geometry checks passed; " + boundaries + " transitions, " + interiors + " interiors.");
        System.out.printf("Border range checks: %d massif samples, %.0f%% in Y=175-250, %.1f%% summits above Y=270, "
                + "%.1f%% valleys below Y=170, %d peaks above Y=230, %d steep spots, %d ramp samples; no holes, lakes or ground-level cliffs.%n",
                core, 100.0 * mainBand / core, 100.0 * summits / core, 100.0 * valleys / core, peaks, cliffs, ramps);
        System.out.println("Grounded ridge checks passed; " + narrowSpines + " narrow spines.");
        System.out.println("Basin checks passed; " + foothills + " foothill columns over deepslate, "
                + basinInteriors + " interior columns on the basin wall.");
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
