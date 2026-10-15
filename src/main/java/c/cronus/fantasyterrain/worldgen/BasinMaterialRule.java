package c.cronus.fantasyterrain.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;

/**
 * Deepslate "cauldron" under region foothills: rock from Y=0 up to the basin floor is deepslate.
 * Everything above it, and everything below Y=0 (vanilla deepslate and iron veins), passes on to the next rule.
 */
public record BasinMaterialRule(DensityFunction basinFloor) implements MaterialRule {
    public static final MapCodec<BasinMaterialRule> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.CODEC.fieldOf("basin_floor").forGetter(BasinMaterialRule::basinFloor)
    ).apply(i, BasinMaterialRule::new));
    private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();

    @Override
    public RuleEvaluator compile(MaterialRuleContext context) {
        var floor = context.getDensitiesInChunk(basinFloor, true);
        return (x, y, z) -> y >= 0 && y < floor.get() ? DEEPSLATE : null;
    }

    @Override public MapCodec<BasinMaterialRule> codec() { return CODEC; }
}
