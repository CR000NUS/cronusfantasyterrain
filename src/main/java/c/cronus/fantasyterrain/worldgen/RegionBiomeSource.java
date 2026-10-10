package c.cronus.fantasyterrain.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.*;
import java.util.List;
import java.util.stream.Stream;

/** Router temperature carries region ID; erosion carries border strength; vegetation selects local biomes. */
public final class RegionBiomeSource extends BiomeSource {
    private static final Codec<List<Holder<Biome>>> POOL = Biome.CODEC.listOf(4, 5);
    public static final MapCodec<RegionBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            POOL.fieldOf("frozen_wastes").forGetter(s -> s.pools.get(c.cronus.fantasyterrain.FrozenLand.REGION_INDEX)),
            POOL.fieldOf("cold_meadows").forGetter(s -> s.pools.get(1)),
            POOL.fieldOf("ember_lands").forGetter(s -> s.pools.get(2)),
            POOL.fieldOf("verdant_valley").forGetter(s -> s.pools.get(3)),
            POOL.fieldOf("crystal_peaks").forGetter(s -> s.pools.get(4)),
            Biome.CODEC.fieldOf("mountain_biome").forGetter(s -> s.mountainBiome)
    ).apply(i, RegionBiomeSource::new));
    private final List<List<Holder<Biome>>> pools;
    private final Holder<Biome> mountainBiome;

    public RegionBiomeSource(List<Holder<Biome>> frozen, List<Holder<Biome>> cold, List<Holder<Biome>> ember,
                             List<Holder<Biome>> verdant, List<Holder<Biome>> crystal, Holder<Biome> mountain) {
        pools = List.of(List.copyOf(frozen), List.copyOf(cold), List.copyOf(ember), List.copyOf(verdant), List.copyOf(crystal));
        mountainBiome = mountain;
    }

    @Override protected MapCodec<RegionBiomeSource> codec() { return CODEC; }
    @Override protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(pools.stream().flatMap(List::stream), Stream.of(mountainBiome));
    }

    @Override public BiomeResolver createResolver(Climate.Sampler sampler) {
        return (qx, qy, qz) -> {
            int x = QuartPos.toBlock(qx), z = QuartPos.toBlock(qz);
            if (sampler.erosion().sampleValue(x, 0, z) > 0.12f) return mountainBiome;
            int region = Math.clamp(Math.round(sampler.temperature().sampleValue(x, 0, z)), 0, 4);
            List<Holder<Biome>> pool = pools.get(region);
            float noise = sampler.humidity().sampleValue(x, 0, z);
            int index = Math.clamp((int) ((noise + 1) * 0.5f * pool.size()), 0, pool.size() - 1);
            return pool.get(index);
        };
    }
}
