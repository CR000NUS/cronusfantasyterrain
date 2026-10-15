package c.cronus.fantasyterrain.worldgen;

import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Final palette enforcement after neighboring feature passes have finished. Only new chunks are processed. */
public final class BorderChunkMaterials {
    private static final ConcurrentLinkedQueue<LevelChunk> PENDING = new ConcurrentLinkedQueue<>();
    private static final ResourceKey<NoiseGeneratorSettings> SETTINGS = ResourceKey.create(Registries.NOISE_SETTINGS,
            Identifier.fromNamespaceAndPath("cronusfantasyterrain", "regions"));
    private static final Identifier BIOME = Identifier.fromNamespaceAndPath("cronusfantasyterrain", "region_border");

    public static void onChunkLoad(ChunkEvent.Load event) {
        // Load fires before FULL promotion; never query the level/chunk neighbors in this callback.
        if (event.isNewChunk() && event.getLevel() instanceof ServerLevel) PENDING.add(event.getChunk());
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        for (int i = 0; i < 8; i++) {
            LevelChunk chunk = PENDING.poll();
            if (chunk == null) break;
            finish(chunk);
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        LevelChunk chunk;
        while ((chunk = PENDING.poll()) != null) finish(chunk);
    }

    private static void finish(LevelChunk chunk) {
        if (!(chunk.getLevel() instanceof ServerLevel level)
                || !(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)
                || !generator.generatorSettings().is(SETTINGS)) return;
        var random = level.getChunkSource().randomState();
        var router = generator.generatorSettings().value().noiseRouter();
        long seed = random.getOrCreateRandomFactory(Identifier.fromNamespaceAndPath("cronusfantasyterrain", "border_palette"))
                .fromHashOf("patches").nextLong();
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        int[] tops = new int[256];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // Ignore vegetation and thin snow when finding the existing terrain surface.
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int top = chunk.getMinY();
            for (int y = chunk.getMaxY(); y >= chunk.getMinY(); y--) {
                BlockState state = chunk.getBlockState(pos.set(minX + x, y, minZ + z));
                if (!state.isAir() && state.getFluidState().isEmpty() && !decoration(state)) { top = y; break; }
            }
            tops[z * 16 + x] = top;
        }
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int bx = minX + x, bz = minZ + z;
            boolean border = random.sampleBlockValueUncached(router.erosion(), bx, 0, bz) > 0.12f
                    || chunk.getNoiseBiome(QuartPos.fromBlock(bx), 0, QuartPos.fromBlock(bz)).is(BIOME);
            if (!border) continue;
            int top = tops[z * 16 + x];
            int left = x > 0 ? tops[z * 16 + x - 1] : neighborTop(level, bx - 1, bz, surface(random.sampleBlockValueUncached(router.chunkSurfaceLevel(), bx - 1, 0, bz)));
            int right = x < 15 ? tops[z * 16 + x + 1] : neighborTop(level, bx + 1, bz, surface(random.sampleBlockValueUncached(router.chunkSurfaceLevel(), bx + 1, 0, bz)));
            int north = z > 0 ? tops[(z - 1) * 16 + x] : neighborTop(level, bx, bz - 1, surface(random.sampleBlockValueUncached(router.chunkSurfaceLevel(), bx, 0, bz - 1)));
            int south = z < 15 ? tops[(z + 1) * 16 + x] : neighborTop(level, bx, bz + 1, surface(random.sampleBlockValueUncached(router.chunkSurfaceLevel(), bx, 0, bz + 1)));
            int floor = Math.min(top, Math.min(Math.min(left, right), Math.min(north, south)) + 1);
            boolean gentle = Math.abs(right - left) < 4 && Math.abs(south - north) < 4;
            for (int y = chunk.getMaxY(); y >= chunk.getMinY(); y--) {
                pos.set(bx, y, bz);
                BlockState old = chunk.getBlockState(pos);
                if (old.isAir() || old.is(Blocks.WATER) || old.is(Blocks.LAVA) || old.is(Blocks.BEDROCK)) continue;
                if (decoration(old)) {
                    BlockState cleared = old.getFluidState().isEmpty() ? Blocks.AIR.defaultBlockState() : old.getFluidState().createLegacyBlock();
                    chunk.setBlockState(pos, cleared, 0);
                    level.getChunkSource().getLightEngine().checkBlock(pos);
                    continue;
                }
                // Keep the approved underground ore variants; remove ordinary stone ores and foreign blobs.
                String name = BuiltInRegistries.BLOCK.getKey(old.getBlock()).getPath();
                if (y < floor && name.startsWith("deepslate_") && name.endsWith("_ore")) continue;
                BlockState replacement = BorderMaterialRule.state(BorderPalette.select(seed, bx, y, bz, top, floor, gentle));
                if (old != replacement) chunk.setBlockState(pos, replacement, 0);
            }
        }
    }

    private static int surface(float height) { return (int) Math.ceil(height) - 1; }

    private static int neighborTop(ServerLevel level, int x, int z, int fallback) {
        // Nonblocking read of terrain already built for lighting; never force a neighbor to generate/load.
        var neighbor = level.getChunkSource().getChunkForLighting(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        if (neighbor == null) return fallback;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = neighbor.getMaxY(); y >= neighbor.getMinY(); y--) {
            BlockState state = neighbor.getBlockState(pos.set(x, y, z));
            if (!state.isAir() && state.getFluidState().isEmpty() && !decoration(state)) return y;
        }
        return fallback;
    }

    private static boolean decoration(BlockState state) {
        return state.is(Blocks.SNOW) || state.canBeReplaced() || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES);
    }

    private BorderChunkMaterials() {}
}
