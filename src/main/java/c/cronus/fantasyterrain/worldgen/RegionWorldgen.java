package c.cronus.fantasyterrain.worldgen;

import c.cronus.fantasyterrain.CronusFantasyTerrain;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class RegionWorldgen {
    private static final DeferredRegister<MapCodec<? extends BiomeSource>> SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, CronusFantasyTerrain.MODID);
    private static final DeferredRegister<MapCodec<? extends DensityFunction>> FUNCTIONS =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, CronusFantasyTerrain.MODID);
    private static final DeferredRegister<MapCodec<? extends MaterialRule>> MATERIALS =
            DeferredRegister.create(Registries.MATERIAL_RULE_TYPE, CronusFantasyTerrain.MODID);
    static {
        SOURCES.register("regions", () -> RegionBiomeSource.CODEC);
        FUNCTIONS.register("region", () -> RegionFunction.CODEC);
        FUNCTIONS.register("biome_terrain", () -> BiomeTerrainFunction.CODEC);
        MATERIALS.register("border_palette", () -> BorderMaterialRule.CODEC);
        MATERIALS.register("region_basin", () -> BasinMaterialRule.CODEC);
        MATERIALS.register("everwinter_palette", () -> EverwinterMaterialRule.CODEC);
    }
    public static void register(IEventBus bus) {
        SOURCES.register(bus);
        FUNCTIONS.register(bus);
        MATERIALS.register(bus);
        NeoForge.EVENT_BUS.addListener(BorderChunkMaterials::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(BorderChunkMaterials::onServerTick);
        NeoForge.EVENT_BUS.addListener(BorderChunkMaterials::onServerStopping);
    }
    private RegionWorldgen() {}
}
