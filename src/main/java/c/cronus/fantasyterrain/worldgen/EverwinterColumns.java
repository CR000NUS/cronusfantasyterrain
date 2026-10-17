package c.cronus.fantasyterrain.worldgen;

import c.cronus.fantasyterrain.FrozenLand;

/**
 * Terrain column of the Frozen Wastes biome terrain: region terrain plus Everwinter's relief inside the
 * district, running up onto the border mountains on its side with glacier tongues on top (decisions 24-25).
 * Pure Java (no Minecraft dependencies) so tests and previews use exactly what {@link BiomeTerrainFunction}
 * generates.
 */
public final class EverwinterColumns {
    private final RegionLayout layout;
    private final BiomeDistricts districts;
    private final EverwinterTerrain everwinter;
    private final EverwinterGroups groups;
    private final int borderWidth;

    /**
     * @param terrain terrain column (on Everwinter's side of the border: its relief on top of the mountain rock)
     * @param member  whether Everwinter's materials apply: the biome is Everwinter (same test as the biome
     *                source), or a border column Everwinter has taken over
     * @param border  whether the column belongs to the border biome
     * @param rockTop region terrain height (the unchanged rock or ground)
     * @param fringe  patchy frost of the border palette, on border columns just beyond Everwinter's (0-1)
     */
    public record Column(EverwinterTerrain.Column terrain, boolean member, boolean border, float rockTop, float fringe) {}

    public EverwinterColumns(RegionLayout layout, BiomeDistricts districts, EverwinterTerrain everwinter,
                             EverwinterGroups groups, int borderWidth) {
        this.layout = layout;
        this.districts = districts;
        this.everwinter = everwinter;
        this.groups = groups;
        this.borderWidth = borderWidth;
    }

    public Column column(int x, int z) {
        RegionLayout.Sample sample = layout.sample(x, z);
        float base = layout.surfaceHeight(x, z, sample);
        boolean border = sample.border() > RegionLayout.BORDER_THRESHOLD;
        EverwinterTerrain.Column plain = new EverwinterTerrain.Column(base, base, EverwinterTerrain.PLAIN);
        // Same tests as RegionBiomeSource: region from the layout, no border biome, pool slot from the district.
        if (sample.region() != FrozenLand.REGION_INDEX) return new Column(plain, false, border, base, 0);
        BiomeDistricts.District district = districts.district(sample, FrozenLand.POOL_SIZE);
        if (district.slot() != FrozenLand.EVERWINTER_SLOT) return new Column(plain, false, border, base, 0);
        // Everwinter's full relief runs up to the mountain foot and fades out up the mountain ramp (decision 25);
        // on the border belt it lies on top of the unchanged mountain rock.
        double depth = borderWidth - sample.edgeDistance();
        double weight = EverwinterTerrain.districtWeight(district.edge()) * everwinter.borderReach(x, z, depth, base);
        EverwinterTerrain.Column column = everwinter.column(x, z, base, weight, groups.groups(sample));
        double glacier = everwinter.edgeGlacier(x, z, depth) * weight;
        int feature = column.feature();
        if (glacier >= 1 && (feature == EverwinterTerrain.PLAIN || feature == EverwinterTerrain.DRIFT || feature == EverwinterTerrain.HILL)) {
            // Glacier tongue: ice on top of the ground (and of the ice sheet beneath it).
            column = new EverwinterTerrain.Column((float) (column.height() + glacier), column.iceBase(), EverwinterTerrain.GLACIER);
        }
        if (!border) {
            // Near the mountain foot the basin's deepslate reaches almost to the surface and is painted first:
            // keep Everwinter's ground (and crevasse floors) a few blocks above it.
            float floor = layout.basinFloor(x, z, sample) + 5;
            if (column.height() < floor) column = new EverwinterTerrain.Column(floor, Math.min(column.iceBase(), floor - 1), column.feature());
            return new Column(column, true, false, base, 0);
        }
        // On the belt, Everwinter's materials take over where its relief is strong; higher up the border palette
        // returns with patchy frost (fringe 0-1).
        boolean owned = everwinter.ownsBorder(x, z, weight);
        double frost = EverwinterTerrain.districtWeight(district.edge()) * everwinter.borderFrost(x, z, depth, base);
        return new Column(column, owned, true, base, owned ? 0 : (float) Math.min(1, frost * 1.6));
    }
}
