package c.cronus.fantasyterrain.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.*;

/** Shared world-seeded layout, including three-dimensional border rock density. */
public record RegionFunction(int regionSize, int borderWidth, Output output) implements DensityFunction {
    public enum Output { REGION, BORDER, HEIGHT, DENSITY }
    public static final MapCodec<RegionFunction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(1024, 32768).optionalFieldOf("region_size", 3072).forGetter(RegionFunction::regionSize),
            Codec.intRange(32, 512).optionalFieldOf("border_width", 360).forGetter(RegionFunction::borderWidth),
            Codec.STRING.comapFlatMap(s -> {
                        try {
                            return DataResult.success(Output.valueOf(s.toUpperCase(java.util.Locale.ROOT)));
                        } catch (IllegalArgumentException exception) {
                            return DataResult.error(() -> "Unknown region output '" + s + "'; expected region, border, height, or density");
                        }
                    },
                    o -> o.name().toLowerCase(java.util.Locale.ROOT)).fieldOf("output").forGetter(RegionFunction::output)
    ).apply(i, RegionFunction::new));

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        long seed = context.createRandom(Identifier.fromNamespaceAndPath("cronusfantasyterrain", "region_layout")).nextLong();
        RegionLayout layout = new RegionLayout(seed, regionSize, borderWidth);
        return new DensitySampler() {
            @Override
            public float sampleValue(SamplerContext context, int x, int y, int z) {
                RegionLayout.Sample sample = layout.sample(x, z);
                return switch (output) {
                    case REGION -> sample.region();
                    case BORDER -> sample.border();
                    case HEIGHT -> {
                        RegionLayout.TerrainColumn column = layout.terrainColumn(x, z, sample);
                        yield column.upperBound();
                    }
                    case DENSITY -> layout.terrainColumn(x, z, sample).density(y);
                };
            }

            @Override
            public void sampleVolume(SamplerContext context, DensityBuffer buffer, DensityVolume volume) {
                int index = 0;
                for (int z = 0; z < volume.sizeZ(); z++) {
                    for (int x = 0; x < volume.sizeX(); x++) {
                        int blockX = volume.blockX(x), blockZ = volume.blockZ(z);
                        if (output == Output.DENSITY) {
                            RegionLayout.TerrainColumn column = layout.terrainColumn(blockX, blockZ, layout.sample(blockX, blockZ));
                            for (int y = 0; y < volume.sizeY(); y++) buffer.set(index++, column.density(volume.blockY(y)));
                        } else {
                            float value = sampleValue(context, blockX, 0, blockZ);
                            for (int y = 0; y < volume.sizeY(); y++) buffer.set(index++, value);
                        }
                    }
                }
            }
        };
    }

    @Override public DensityFunction rewriteChildren(DfRewriteRule rule) { return this; }
    @Override public int domainAxes() { return output == Output.DENSITY ? ALL_AXES : AXIS_X | AXIS_Z; }
    @Override public Interval range() {
        return switch (output) {
            case REGION -> Interval.of(0, 4);
            case BORDER -> Interval.of(0, 1);
            case HEIGHT -> Interval.of(8, 310);
            case DENSITY -> Interval.of(-16, 16);
        };
    }
    @Override public MapCodec<RegionFunction> codec() { return CODEC; }
}
