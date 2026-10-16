package c.cronus.fantasyterrain.worldgen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Standalone checks: every region holds each pool biome exactly once, as one large connected district. */
public final class BiomeDistrictTest {
    static final Path PRESET = Path.of("src/main/resources/data/cronusfantasyterrain/worldgen/world_preset/regions.json");
    /** Pool names by region index, as in the RegionBiomeSource codec. */
    static final List<String> POOLS = List.of("frozen_wastes", "cold_meadows", "ember_lands", "verdant_valley", "crystal_peaks");
    public static void main(String[] args) throws Exception {
        checkPools();
        checkCodes();
        RegionLayout layout = new RegionLayout(12345L, 3800, 360);
        BiomeDistricts districts = new BiomeDistricts(999L, 3800);
        // Many regions on a coarse grid, then a few at fine resolution to catch slivers along the mountain foot.
        int coarse = check(layout, districts, 24000, 32, 15);
        int fine = check(layout, districts, 5200, 8, 1);
        System.out.printf("Biome district checks passed: %d region layouts at 32-block and %d at 8-block resolution"
                        + " (pool sizes %d-%d), every biome once and in one piece.%n",
                coarse, fine, BiomeDistricts.MIN_BIOMES, BiomeDistricts.MAX_BIOMES);
    }

    /** Checks every pool size in every region lying wholly inside the square; returns the region layouts checked. */
    private static int check(RegionLayout layout, BiomeDistricts districts, int range, int step, int minRegions) {
        int n = 2 * range / step + 1;
        long[] cells = new long[n * n];
        boolean[] interior = new boolean[n * n];
        Map<Long, double[]> centres = new HashMap<>();
        RegionLayout.Sample[] samples = new RegionLayout.Sample[n * n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int x = -range + j * step, z = -range + i * step;
                RegionLayout.Sample sample = layout.sample(x, z);
                int index = i * n + j;
                samples[index] = sample;
                cells[index] = key(sample.cellX(), sample.cellZ());
                interior[index] = sample.border() <= RegionLayout.BORDER_THRESHOLD;
                // Approximate centre (the bend moves it by at most a few hundred blocks).
                centres.putIfAbsent(cells[index], new double[] {x - sample.localX(), z - sample.localZ()});
            }
        }
        Set<Long> inside = new HashSet<>();
        centres.forEach((cell, centre) -> {
            if (Math.abs(centre[0]) < range - 2600 && Math.abs(centre[1]) < range - 2600) inside.add(cell);
        });
        require(inside.size() >= minRegions, "Too few regions scanned: " + inside.size());

        int checked = 0, rejoined = 0;
        double smallest = 1, largest = 0;
        for (int count = BiomeDistricts.MIN_BIOMES; count <= BiomeDistricts.MAX_BIOMES; count++) {
            int[] slots = new int[n * n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    int index = i * n + j;
                    if (!interior[index] || !inside.contains(cells[index])) continue;
                    BiomeDistricts.District district = districts.district(samples[index], count);
                    require(district.slot() >= 0 && district.slot() < count, "Slot out of range");
                    require(district.edge() >= 0, "Negative district edge distance");
                    require(BiomeDistricts.slot(districts.code(samples[index]), count) == district.slot(),
                            "Router code disagrees with the district");
                    slots[index] = district.slot();
                    // A boundary between two grid neighbours must lie close to both, so biome terrain fades there.
                    if (i > 0 && interior[index - n] && cells[index - n] == cells[index] && slots[index - n] != slots[index]) {
                        require(district.edge() < step * 3, "District edge distance too large at a biome boundary: " + district.edge());
                    }
                }
            }
            // Connected pieces of each district on the grid (8-neighbour).
            int[] pieceOf = new int[n * n];
            java.util.Arrays.fill(pieceOf, -1);
            List<int[]> pieces = new ArrayList<>(); // {start index, area}
            for (int start = 0; start < n * n; start++) {
                if (pieceOf[start] >= 0 || !interior[start] || !inside.contains(cells[start])) continue;
                int id = pieces.size(), area = 0;
                ArrayDeque<Integer> queue = new ArrayDeque<>();
                queue.add(start);
                pieceOf[start] = id;
                while (!queue.isEmpty()) {
                    int index = queue.poll();
                    area++;
                    int i = index / n, j = index % n;
                    for (int ni = Math.max(0, i - 1); ni <= Math.min(n - 1, i + 1); ni++) {
                        for (int nj = Math.max(0, j - 1); nj <= Math.min(n - 1, j + 1); nj++) {
                            int next = ni * n + nj;
                            if (pieceOf[next] >= 0 || !interior[next] || cells[next] != cells[start] || slots[next] != slots[start]) continue;
                            pieceOf[next] = id;
                            queue.add(next);
                        }
                    }
                }
                pieces.add(new int[] {start, area});
            }
            // Per region and slot: the largest piece is the district; any other piece is a grid artefact (a narrow wedge
            // tip between grid points) only if it joins that district block by block.
            Map<Long, int[]> main = new HashMap<>();
            Map<Long, int[]> areas = new HashMap<>();
            for (int id = 0; id < pieces.size(); id++) {
                int start = pieces.get(id)[0];
                int[] best = main.computeIfAbsent(cells[start], c -> filled(BiomeDistricts.MAX_BIOMES));
                if (best[slots[start]] < 0 || pieces.get(best[slots[start]])[1] < pieces.get(id)[1]) best[slots[start]] = id;
                areas.computeIfAbsent(cells[start], c -> new int[BiomeDistricts.MAX_BIOMES])[slots[start]] += pieces.get(id)[1];
            }
            for (int id = 0; id < pieces.size(); id++) {
                int start = pieces.get(id)[0], mainId = main.get(cells[start])[slots[start]];
                if (id == mainId) continue;
                int x = -range + start % n * step, z = -range + start / n * step;
                require(joins(layout, districts, count, x, z, step, range, n, pieceOf, mainId),
                        "Biome slot " + slots[start] + " has a separate fragment of about " + pieces.get(id)[1] * step * step
                                + " blocks² at " + x + "," + z + " (pool size " + count + ")");
                rejoined++;
            }
            for (long cell : inside) {
                int[] regionAreas = areas.get(cell);
                require(regionAreas != null, "A region has no interior");
                int total = 0;
                for (int slot = 0; slot < count; slot++) total += regionAreas[slot];
                for (int slot = 0; slot < count; slot++) {
                    require(regionAreas[slot] > 0, "A region is missing a biome district (pool size " + count + ")");
                    double share = (double) regionAreas[slot] / total;
                    smallest = Math.min(smallest, share * count);
                    largest = Math.max(largest, share * count);
                    require(share > 0.45 / count, "Biome district too small: " + Math.round(share * 100) + "% of a region with " + count + " biomes");
                }
                checked++;
            }
        }
        System.out.printf("  %d-block grid: %d region layouts, districts %.0f%%-%.0f%% of an even share, %d wedge tips re-joined block by block.%n",
                step, checked, smallest * 100, largest * 100, rejoined);
        return checked;
    }

    /**
     * Block-level flood fill (4-neighbour) from a grid point through its own region and district, within a few grid
     * steps; true once it reaches a grid point of the main piece.
     */
    private static boolean joins(RegionLayout layout, BiomeDistricts districts, int count, int x0, int z0, int step,
                                 int range, int n, int[] pieceOf, int mainId) {
        RegionLayout.Sample origin = layout.sample(x0, z0);
        int slot = districts.district(origin, count).slot(), reach = 3 * step, side = 2 * reach + 1;
        boolean[] visited = new boolean[side * side];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] {x0, z0});
        visited[reach * side + reach] = true;
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            int gx = at[0] + range, gz = at[1] + range;
            if (gx % step == 0 && gz % step == 0 && gx / step < n && gz / step < n && pieceOf[gz / step * n + gx / step] == mainId) return true;
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = at[0] + d[0], z = at[1] + d[1];
                int wx = x - x0 + reach, wz = z - z0 + reach;
                if (wx < 0 || wz < 0 || wx >= side || wz >= side || visited[wz * side + wx]) continue;
                visited[wz * side + wx] = true;
                RegionLayout.Sample sample = layout.sample(x, z);
                if (sample.border() > RegionLayout.BORDER_THRESHOLD || sample.cellX() != origin.cellX() || sample.cellZ() != origin.cellZ()
                        || districts.district(sample, count).slot() != slot) continue;
                queue.add(new int[] {x, z});
            }
        }
        return false;
    }

    private static int[] filled(int length) {
        int[] values = new int[length];
        java.util.Arrays.fill(values, -1);
        return values;
    }

    /** Every preset pool has 4-6 entries and lists each biome once. */
    private static void checkPools() throws Exception {
        String preset = Files.readString(PRESET);
        for (String name : POOLS) {
            List<String> pool = pool(preset, name);
            require(pool.size() >= BiomeDistricts.MIN_BIOMES && pool.size() <= BiomeDistricts.MAX_BIOMES,
                    name + " must list " + BiomeDistricts.MIN_BIOMES + "-" + BiomeDistricts.MAX_BIOMES + " biomes");
            require(new HashSet<>(pool).size() == pool.size(), name + " lists a biome more than once");
        }
    }

    static List<String> pool(String preset, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*\\[([^]]*)]").matcher(preset);
        require(matcher.find(), "Pool " + name + " missing from the preset");
        return List.of(matcher.group(1).replaceAll("[\\s\"]", "").split(","));
    }

    /** Router codes must round-trip every slot combination exactly. */
    private static void checkCodes() {
        int combinations = 0;
        for (int code = 0; code < BiomeDistricts.CODES; code++) {
            int rebuilt = 0, base = 1;
            for (int count = BiomeDistricts.MIN_BIOMES; count <= BiomeDistricts.MAX_BIOMES; count++) {
                int slot = BiomeDistricts.slot(code, count);
                require(slot >= 0 && slot < count, "Decoded slot out of range");
                rebuilt += slot * base;
                base *= count;
            }
            require(rebuilt == code && Math.round((float) code) == code, "Router code does not round-trip");
            combinations++;
        }
        require(combinations == 4 * 5 * 6, "Router codes must cover every slot combination");
    }

    private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xFFFFFFFFL); }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
