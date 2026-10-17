package c.cronus.fantasyterrain.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.*;

/**
 * Region terrain plus biome relief (currently Everwinter in the Frozen Wastes). Wherever no biome relief
 * applies, including every column the border touches, the output equals the plain region terrain.
 * Output {@code district} is the router's vegetation channel: the packed biome-district slots
 * ({@link BiomeDistricts#code}) the biome source picks pool entries from, so terrain and biomes always agree.
 */
public record BiomeTerrainFunction(int regionSize, int borderWidth, Output output) implements DensityFunction {
    public enum Output { DENSITY, HEIGHT, EVERWINTER, FEATURE, ICE_BASE, DISTRICT, FRINGE }
    /**
     * Recent columns per worldgen thread, shared by all outputs: each output compiles its own sampler, but they
     * all need the same (fairly expensive) Everwinter column.
     */
    private static final ThreadLocal<ColumnCache> CACHE = ThreadLocal.withInitial(ColumnCache::new);
    /** Serac field placement per generator setup (see {@link EverwinterGroups}). */
    private static final java.util.Map<Long, EverwinterGroups> GROUPS = new java.util.concurrent.ConcurrentHashMap<>();
    public static final MapCodec<BiomeTerrainFunction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(1024, 32768).optionalFieldOf("region_size", 3800).forGetter(BiomeTerrainFunction::regionSize),
            Codec.intRange(32, 512).optionalFieldOf("border_width", 360).forGetter(BiomeTerrainFunction::borderWidth),
            Codec.STRING.comapFlatMap(s -> {
                        try {
                            return DataResult.success(Output.valueOf(s.toUpperCase(java.util.Locale.ROOT)));
                        } catch (IllegalArgumentException exception) {
                            return DataResult.error(() -> "Unknown biome terrain output '" + s + "'; expected density, height, everwinter, feature, ice_base, district, or fringe");
                        }
                    },
                    o -> o.name().toLowerCase(java.util.Locale.ROOT)).fieldOf("output").forGetter(BiomeTerrainFunction::output)
    ).apply(i, BiomeTerrainFunction::new));

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        // Same seed derivation as RegionFunction, so the base terrain is identical.
        long layoutSeed = context.createRandom(Identifier.fromNamespaceAndPath("cronusfantasyterrain", "region_layout")).nextLong();
        RegionLayout layout = new RegionLayout(layoutSeed, regionSize, borderWidth);
        long everwinterSeed = context.createRandom(Identifier.fromNamespaceAndPath("cronusfantasyterrain", "everwinter")).nextLong();
        long districtSeed = context.createRandom(Identifier.fromNamespaceAndPath("cronusfantasyterrain", "biome_districts")).nextLong();
        EverwinterTerrain everwinter = new EverwinterTerrain(everwinterSeed);
        BiomeDistricts districts = new BiomeDistricts(districtSeed, regionSize);
        // Identifies this generator setup in the shared cache (same seeds and sizes give the same columns).
        long setup = ((layoutSeed * 31 + everwinterSeed) * 31 + districtSeed) * 31 + regionSize * 1031L + borderWidth;
        // Serac field placement is searched once per region, so it is shared by all samplers of a setup.
        EverwinterGroups groups = GROUPS.computeIfAbsent(setup,
                key -> new EverwinterGroups(layout, districts, everwinter, borderWidth, everwinterSeed));
        EverwinterColumns columns = new EverwinterColumns(layout, districts, everwinter, groups, borderWidth);
        return new DensitySampler() {
            private EverwinterColumns.Column column(int x, int z) {
                return CACHE.get().get(setup, x, z, columns::column);
            }

            @Override
            public float sampleValue(SamplerContext context, int x, int y, int z) {
                if (output == Output.DISTRICT) return districts.code(layout.sample(x, z));
                EverwinterColumns.Column column = column(x, z);
                return switch (output) {
                    case DENSITY -> density(column.terrain().height(), y);
                    case HEIGHT -> column.terrain().height();
                    case EVERWINTER -> column.member() ? 1 : 0;
                    case FEATURE -> column.terrain().feature();
                    case ICE_BASE -> column.terrain().iceBase();
                    case FRINGE -> column.fringe();
                    case DISTRICT -> throw new IllegalStateException();
                };
            }

            @Override
            public void sampleVolume(SamplerContext context, DensityBuffer buffer, DensityVolume volume) {
                int index = 0;
                for (int z = 0; z < volume.sizeZ(); z++) {
                    for (int x = 0; x < volume.sizeX(); x++) {
                        int blockX = volume.blockX(x), blockZ = volume.blockZ(z);
                        if (output == Output.DENSITY) {
                            float height = column(blockX, blockZ).terrain().height();
                            for (int y = 0; y < volume.sizeY(); y++) buffer.set(index++, density(height, volume.blockY(y)));
                        } else {
                            float value = sampleValue(context, blockX, 0, blockZ);
                            for (int y = 0; y < volume.sizeY(); y++) buffer.set(index++, value);
                        }
                    }
                }
            }
        };
    }

    private interface ColumnSource { EverwinterColumns.Column column(int x, int z); }

    /** Direct-mapped cache of the last few thousand columns (a 64 x 64 block window). */
    private static final class ColumnCache {
        private static final int SIZE = 4096;
        private final long[] setups = new long[SIZE], positions = new long[SIZE];
        private final EverwinterColumns.Column[] columns = new EverwinterColumns.Column[SIZE];

        EverwinterColumns.Column get(long setup, int x, int z, ColumnSource compute) {
            long position = (long) x << 32 | (z & 0xFFFFFFFFL);
            int slot = (x & 63) | (z & 63) << 6;
            EverwinterColumns.Column column = columns[slot];
            if (column == null || setups[slot] != setup || positions[slot] != position) {
                column = compute.column(x, z);
                columns[slot] = column;
                setups[slot] = setup;
                positions[slot] = position;
            }
            return column;
        }
    }

    /** Same grounded column density as RegionLayout.TerrainColumn. */
    private static float density(float surfaceHeight, int y) {
        return new RegionLayout.TerrainColumn(surfaceHeight, 0).density(y);
    }

    @Override public DensityFunction rewriteChildren(DfRewriteRule rule) { return this; }

    @Override public int domainAxes() { return output == Output.DENSITY ? ALL_AXES : AXIS_X | AXIS_Z; }
    @Override public Interval range() {
        return switch (output) {
            case HEIGHT, ICE_BASE -> Interval.of(8, 310);
            case EVERWINTER, FRINGE -> Interval.of(0, 1);
            case FEATURE -> Interval.of(0, EverwinterTerrain.FEATURES - 1);
            case DENSITY -> Interval.of(-16, 16);
            case DISTRICT -> Interval.of(0, BiomeDistricts.CODES - 1);
        };
    }
    @Override public MapCodec<BiomeTerrainFunction> codec() { return CODEC; }
}
