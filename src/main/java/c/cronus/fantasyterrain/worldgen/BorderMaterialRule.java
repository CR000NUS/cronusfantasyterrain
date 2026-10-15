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

/** Gives outdoor cliff faces a different palette from buried rock and cave walls. */
public record BorderMaterialRule(DensityFunction surface, DensityFunction exteriorFloor) implements MaterialRule {
    public static final MapCodec<BorderMaterialRule> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.CODEC.fieldOf("surface").forGetter(BorderMaterialRule::surface),
            DensityFunction.CODEC.fieldOf("exterior_floor").forGetter(BorderMaterialRule::exteriorFloor)
    ).apply(i, BorderMaterialRule::new));

    @Override
    public RuleEvaluator compile(MaterialRuleContext context) {
        var surfaceHeight = context.getDensitiesInChunk(surface, true);
        var exposedFloor = context.getDensitiesInChunk(exteriorFloor, true);
        long seed = context.getOrCreateRandomFactory(
                Identifier.fromNamespaceAndPath("cronusfantasyterrain", "border_palette")).fromHashOf("patches").nextLong();
        return (x, y, z) -> {
            int top = (int) Math.ceil(surfaceHeight.get()) - 1;
            int floor = Math.min(top, (int) exposedFloor.get());
            boolean gentle = Math.abs(context.surfaceGradientX()) < 4 && Math.abs(context.surfaceGradientZ()) < 4;
            BorderPalette.Material material = BorderPalette.select(seed, x, y, z, top, floor, gentle);
            // Submerged columns receive rock rather than bright accents.
            if (context.waterHeight() > y && material == BorderPalette.Material.SNOW) {
                material = BorderPalette.Material.BLACKSTONE;
            }
            return state(material);
        };
    }

    static BlockState state(BorderPalette.Material material) {
        return switch (material) {
            case BLACKSTONE -> Blocks.BLACKSTONE.defaultBlockState();
            case DEEPSLATE -> Blocks.DEEPSLATE.defaultBlockState();
            case SNOW -> Blocks.SNOW_BLOCK.defaultBlockState();
            case TUFF -> Blocks.TUFF.defaultBlockState();
        };
    }

    @Override public MapCodec<BorderMaterialRule> codec() { return CODEC; }
}
