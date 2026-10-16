package c.cronus.fantasyterrain.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;

/**
 * Everwinter blocks from Y=0 up, in columns whose biome is Everwinter (same test as the biome source).
 * Runs after the border and basin rules; returns null elsewhere and below Y=0.
 */
public record EverwinterMaterialRule(DensityFunction everwinter, DensityFunction surface, DensityFunction feature,
                                     DensityFunction iceBase) implements MaterialRule {
    public static final MapCodec<EverwinterMaterialRule> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.CODEC.fieldOf("everwinter").forGetter(EverwinterMaterialRule::everwinter),
            DensityFunction.CODEC.fieldOf("surface").forGetter(EverwinterMaterialRule::surface),
            DensityFunction.CODEC.fieldOf("feature").forGetter(EverwinterMaterialRule::feature),
            DensityFunction.CODEC.fieldOf("ice_base").forGetter(EverwinterMaterialRule::iceBase)
    ).apply(i, EverwinterMaterialRule::new));

    @Override
    public RuleEvaluator compile(MaterialRuleContext context) {
        var member = context.getDensitiesInChunk(everwinter, true);
        var surfaceHeight = context.getDensitiesInChunk(surface, true);
        var featureCode = context.getDensitiesInChunk(feature, true);
        var plain = context.getDensitiesInChunk(iceBase, true);
        long seed = context.getOrCreateRandomFactory(
                Identifier.fromNamespaceAndPath("cronusfantasyterrain", "everwinter_palette")).fromHashOf("palette").nextLong();
        EverwinterPalette palette = new EverwinterPalette(seed);
        return (x, y, z) -> {
            if (y < 0 || member.get() < 0.5f) return null;
            int top = (int) Math.ceil(surfaceHeight.get()) - 1;
            boolean gentle = Math.abs(context.surfaceGradientX()) < 3 && Math.abs(context.surfaceGradientZ()) < 3;
            return state(palette.select(x, y, z, top, plain.get(), Math.round(featureCode.get()), gentle));
        };
    }

    static BlockState state(EverwinterPalette.Material material) {
        return switch (material) {
            case SNOW -> Blocks.SNOW_BLOCK.defaultBlockState();
            case PACKED_ICE -> Blocks.PACKED_ICE.defaultBlockState();
            case BLUE_ICE -> Blocks.BLUE_ICE.defaultBlockState();
            case DEEPSLATE -> Blocks.DEEPSLATE.defaultBlockState();
            case STONE -> Blocks.STONE.defaultBlockState();
        };
    }

    @Override public MapCodec<EverwinterMaterialRule> codec() { return CODEC; }
}
