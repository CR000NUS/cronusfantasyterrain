package c.cronus.fantasyterrain.worldgen;

import c.cronus.fantasyterrain.CronusFantasyTerrain;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Keeps Everwinter free of natural water (decision 22): the world has no aquifers, so every carved cave below sea
 * level fills with water, in every biome. Run once per chunk after carving and the other features, this drains
 * the water in the chunk's Everwinter columns (caves become dry air, waterlogged blocks dry out). Where water of
 * a neighbouring biome touches a drained block, packed ice seals the cave instead, so that water cannot flow in
 * later. Player-placed water is unaffected (this only runs during world generation).
 */
public record EverwinterDryFeature() implements Feature {
    public static final MapCodec<EverwinterDryFeature> CODEC = MapCodec.unit(EverwinterDryFeature::new);
    private static final ResourceKey<Biome> EVERWINTER =
            ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(CronusFantasyTerrain.MODID, "everwinter"));
    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    @Override
    public MapCodec<EverwinterDryFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        ChunkAccess chunk = level.getChunk(origin);
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        // Per column: 0 unknown, 1 Everwinter, 2 other biome.
        byte[] columns = new byte[256];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(), side = new BlockPos.MutableBlockPos();
        boolean changed = false;
        for (int index = 0; index < chunk.getSectionsCount(); index++) {
            LevelChunkSection section = chunk.getSection(index);
            if (!section.hasFluid()) continue;
            int baseY = chunk.getSectionYFromSectionIndex(index) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (!section.getFluidState(x, y, z).is(FluidTags.WATER)) continue;
                        if (!everwinter(level, columns, minX, minZ, x, z)) continue;
                        pos.set(minX + x, baseY + y, minZ + z);
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
                            level.setBlock(pos, state.setValue(BlockStateProperties.WATERLOGGED, false), 2);
                        } else {
                            level.setBlock(pos, foreignWaterNextTo(level, columns, minX, minZ, pos, side)
                                    ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                        }
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    /** Whether water of another biome touches this block from the side (it would flow into the drained cave). */
    private static boolean foreignWaterNextTo(WorldGenLevel level, byte[] columns, int minX, int minZ, BlockPos pos,
                                              BlockPos.MutableBlockPos side) {
        for (Direction direction : SIDES) {
            side.setWithOffset(pos, direction);
            if (!level.getBlockState(side).getFluidState().is(FluidTags.WATER)) continue;
            int x = side.getX() - minX, z = side.getZ() - minZ;
            boolean inside = x >= 0 && x < 16 && z >= 0 && z < 16;
            if (inside ? !everwinter(level, columns, minX, minZ, x, z) : !level.getBiome(side.atY(64)).is(EVERWINTER)) return true;
        }
        return false;
    }

    private static boolean everwinter(WorldGenLevel level, byte[] columns, int minX, int minZ, int x, int z) {
        int slot = x | z << 4;
        if (columns[slot] == 0) {
            columns[slot] = (byte) (level.getBiome(new BlockPos(minX + x, 64, minZ + z)).is(EVERWINTER) ? 1 : 2);
        }
        return columns[slot] == 1;
    }
}
