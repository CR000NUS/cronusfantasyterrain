package c.cronus.fantasyterrain.worldgen;

/**
 * Everwinter materials (pure Java): a snow cover with packed-ice patches over a glacier ice sheet (packed ice
 * with blue-ice layers) that sits on a stone/deepslate bed. Snow piles are snow throughout; big hills, serac
 * blocks, cliffs and crevasse walls show bare ice on their steep faces. Deepslate outcrops break through.
 * Covers Y >= 0 only; below that vanilla deepslate, ores and veins stay in charge.
 */
public final class EverwinterPalette {
    public enum Material { SNOW, PACKED_ICE, BLUE_ICE, DEEPSLATE, STONE }
    /** Depth of the snow cover on gentle open ground before the ice sheet begins. */
    public static final int COVER_DEPTH = 4;
    /** Snow piles are snow down to this depth (pile plus the snow cover beneath it). */
    public static final int DRIFT_DEPTH = 16;
    private final EverwinterTerrain noise;

    public EverwinterPalette(long seed) {
        this.noise = new EverwinterTerrain(seed);
    }

    /**
     * @param top     highest solid block of the column
     * @param iceBase bed of the ice sheet (rock below); for outcrops, the snow surface around the knob
     * @param gentle  whether the surface here is flat enough to hold snow
     */
    public Material select(int x, int y, int z, int top, float iceBase, int feature, boolean gentle) {
        int depth = top - y;
        if (feature == EverwinterTerrain.ROCK && y > iceBase - 4) {
            // Bare rock with a little snow caught on its flat tops.
            return depth == 0 && gentle && noise.noise(x / 14.0, z / 14.0, 62) > 0.2 ? Material.SNOW : Material.DEEPSLATE;
        }
        int cover = switch (feature) {
            case EverwinterTerrain.DRIFT -> DRIFT_DEPTH;
            case EverwinterTerrain.HILL -> gentle ? 3 : 0;
            case EverwinterTerrain.GLACIER, EverwinterTerrain.CREVASSE -> gentle ? 1 : 0;
            case EverwinterTerrain.SPIRE -> 0;
            default -> gentle ? COVER_DEPTH : 1;
        };
        if (depth < cover) {
            // Snow, with patches of wind-polished ice on the open ground.
            boolean ice = feature == EverwinterTerrain.PLAIN && depth < 2
                    && noise.noise(x / 36.0, z / 36.0, 63) + 0.3 * noise.noise(x / 9.0, z / 9.0, 64) > 0.38;
            return ice ? Material.PACKED_ICE : Material.SNOW;
        }
        if (y < iceBase) return underground(x, y, z);
        // Glacier ice: packed ice with thin, gently tilted blue-ice layers and streaks.
        double layer = noise.noise(x / 40.0, z / 40.0, 60) * 2 + y / 3.0;
        return Math.abs(layer - Math.round(layer)) < 0.12 || noise.noise(x / 7.0, (y + z) / 7.0, 61) > 0.62
                ? Material.BLUE_ICE : Material.PACKED_ICE;
    }

    /** Stone and deepslate in large blobs, with deepslate growing more common toward Y=0. */
    public Material underground(int x, int y, int z) {
        double blob = noise.noise(x / 28.0 + y / 61.0, z / 28.0 - y / 47.0, 65)
                + 0.5 * noise.noise((x + y) / 11.0, (z - y) / 11.0, 66);
        return blob + (24 - y) / 70.0 > -0.1 ? Material.DEEPSLATE : Material.STONE;
    }
}
