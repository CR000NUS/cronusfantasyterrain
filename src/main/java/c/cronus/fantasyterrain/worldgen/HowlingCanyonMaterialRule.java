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
 * Howling Canyon blocks from Y=0 up, in columns whose biome is Howling Canyon (same test as the biome source).
 * Runs after the border and basin rules; returns null elsewhere and below Y=0.
 */
public record HowlingCanyonMaterialRule(DensityFunction canyon, DensityFunction surface, DensityFunction feature,
                                        DensityFunction bed) implements MaterialRule {
    public static final MapCodec<HowlingCanyonMaterialRule> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.CODEC.fieldOf("canyon").forGetter(HowlingCanyonMaterialRule::canyon),
            DensityFunction.CODEC.fieldOf("surface").forGetter(HowlingCanyonMaterialRule::surface),
            DensityFunction.CODEC.fieldOf("feature").forGetter(HowlingCanyonMaterialRule::feature),
            DensityFunction.CODEC.fieldOf("bed").forGetter(HowlingCanyonMaterialRule::bed)
    ).apply(i, HowlingCanyonMaterialRule::new));

    @Override
    public RuleEvaluator compile(MaterialRuleContext context) {
        var member = context.getDensitiesInChunk(canyon, true);
        var surfaceHeight = context.getDensitiesInChunk(surface, true);
        var featureCode = context.getDensitiesInChunk(feature, true);
        var rock = context.getDensitiesInChunk(bed, true);
        long seed = context.getOrCreateRandomFactory(
                Identifier.fromNamespaceAndPath("cronusfantasyterrain", "howling_canyon_palette")).fromHashOf("palette").nextLong();
        HowlingCanyonPalette palette = new HowlingCanyonPalette(seed);
        return (x, y, z) -> {
            if (y < 0 || member.get() < 0.5f) return null;
            int top = (int) Math.ceil(surfaceHeight.get()) - 1;
            boolean gentle = Math.abs(context.surfaceGradientX()) < 3 && Math.abs(context.surfaceGradientZ()) < 3;
            return state(palette.select(x, y, z, top, rock.get(), Math.round(featureCode.get()), gentle));
        };
    }

    static BlockState state(HowlingCanyonPalette.Material material) {
        return switch (material) {
            case SNOW -> Blocks.SNOW_BLOCK.defaultBlockState();
            case PACKED_ICE -> Blocks.PACKED_ICE.defaultBlockState();
            case BLUE_ICE -> Blocks.BLUE_ICE.defaultBlockState();
            case DEEPSLATE -> Blocks.DEEPSLATE.defaultBlockState();
            case STONE -> Blocks.STONE.defaultBlockState();
        };
    }

    @Override public MapCodec<HowlingCanyonMaterialRule> codec() { return CODEC; }
}
