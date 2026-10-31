package c.cronus.fantasyterrain.worldgen;

import c.cronus.fantasyterrain.FrozenLand;

/**
 * Terrain column of Howling Canyon (Frozen Wastes): region terrain plus the canyon relief inside the district.
 * The relief is zero in the columns the border touches, so the border mountains stay as they are, except for the
 * glaciers coming down from them (decision 33, {@link HowlingCanyonTerrain#glacier}): their ice lies on the
 * unchanged rock.
 * Pure Java (no Minecraft dependencies) so tests and previews use exactly what {@link BiomeTerrainFunction}
 * generates.
 */
public final class HowlingCanyonColumns {
    private static final HowlingCanyonTerrain.Course[] NONE = new HowlingCanyonTerrain.Course[0];
    private final RegionLayout layout;
    private final BiomeDistricts districts;
    private final HowlingCanyonTerrain terrain;
    private final HowlingCanyonCourses courses;
    private final int borderWidth;

    /**
     * @param member  whether Howling Canyon's materials apply: the biome is Howling Canyon (same test as the biome
     *                source), or a border column a glacier lies on (its ice above {@code bed}, the rock)
     * @param border  whether the column belongs to the border biome
     * @param rockTop region terrain height (the plain ground or the unchanged mountain rock)
     */
    public record Column(float height, float bed, int feature, boolean member, boolean border, float rockTop) {}

    public HowlingCanyonColumns(RegionLayout layout, BiomeDistricts districts, HowlingCanyonTerrain terrain,
                                HowlingCanyonCourses courses, int borderWidth) {
        this.layout = layout;
        this.districts = districts;
        this.terrain = terrain;
        this.courses = courses;
        this.borderWidth = borderWidth;
    }

    /** The column if it lies in a Howling Canyon district (border columns included), otherwise null. */
    public Column column(int x, int z) {
        RegionLayout.Sample sample = layout.sample(x, z);
        // Same tests as RegionBiomeSource: region from the layout, no border biome, pool slot from the district.
        if (sample.region() != FrozenLand.REGION_INDEX) return null;
        BiomeDistricts.District district = districts.district(sample, FrozenLand.POOL_SIZE);
        if (district.slot() != FrozenLand.HOWLING_CANYON_SLOT) return null;
        float base = layout.surfaceHeight(x, z, sample);
        boolean border = sample.border() > RegionLayout.BORDER_THRESHOLD;
        double weight = HowlingCanyonTerrain.districtWeight(district.edge()) * HowlingCanyonTerrain.edgeFade(sample.edgeDistance(), borderWidth);
        HowlingCanyonTerrain.Column column = terrain.column(x, z, base, weight, weight > 0 ? courses.courses(sample) : NONE);
        // A glacier coming down from the mountains (where a massif lies near them).
        double glacier = terrain.glacier(x, z, borderWidth - sample.edgeDistance(), base, HowlingCanyonTerrain.districtWeight(district.edge()),
                courses.courses(sample));
        if (border && glacier > base + 0.5) return new Column((float) glacier, base, HowlingCanyonTerrain.PLATEAU, true, true, base);
        if (!border && glacier > column.height()) {
            return new Column((float) glacier, column.bed(), HowlingCanyonTerrain.PLATEAU, true, false, base);
        }
        return new Column(column.height(), column.bed(), column.feature(), !border, border, base);
    }
}
