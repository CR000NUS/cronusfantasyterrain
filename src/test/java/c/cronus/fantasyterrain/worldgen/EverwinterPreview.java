package c.cronus.fantasyterrain.worldgen;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Software previews of Everwinter at full strength: top-down map, an oblique overview and an oblique close-up of
 * a serac field, all with block steps as they would generate. Not in-game screenshots.
 */
public final class EverwinterPreview {
    private static final int SIZE_X = 900, SIZE_Z = 700;
    private static final Color SKY = new Color(64, 104, 168);

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        EverwinterTerrain terrain = new EverwinterTerrain(777L);
        EverwinterPalette palette = new EverwinterPalette(99L);
        Area overview = new Area(terrain, palette, 400, -300, SIZE_X, SIZE_Z);
        writeTopDown(overview, 1, output, "Everwinter top-down: 900 x 700 blocks, 1 px per block, full biome strength");
        writeOblique(overview, 1, 0.55, 2.2, output.resolveSibling("everwinter-view.png"),
                "Everwinter oblique view looking north (vertical scale x2.2)");
        // Close-up of a whole serac field: the grid field nearest the overview's centre.
        EverwinterTerrain.Group field = null;
        for (int z = -2000; z <= 2000 && field == null; z += 200) {
            for (int x = -2000; x <= 2000 && field == null; x += 200) {
                EverwinterTerrain.Group[] groups = terrain.gridGroups(850 + x, 50 + z);
                if (groups.length > 0) field = groups[0];
            }
        }
        int fieldX = field == null ? 850 : (int) field.x(), fieldZ = field == null ? 50 : (int) field.z();
        Area closeUp = new Area(terrain, palette, fieldX - 380, fieldZ - 300, 760, 600);
        writeOblique(closeUp, 1, 0.6, 1.2, output.resolveSibling("everwinter-closeup.png"),
                "Everwinter serac field looking north, true vertical scale");
        writeTopDown(closeUp, 1, output.resolveSibling("everwinter-seracs.png"),
                "Everwinter serac field from above: 760 x 600 blocks, 1 px per block");
    }

    /** Block-quantized surface of an area: top block and colours. */
    private static final class Area {
        final int originX, originZ, sizeX, sizeZ;
        final float[][] surfaces;
        final int[][] tops, features;
        final Color[][] colors;
        final EverwinterTerrain.Column[][] columns;
        final EverwinterPalette palette;

        Area(EverwinterTerrain terrain, EverwinterPalette palette, int originX, int originZ, int sizeX, int sizeZ) {
            this.originX = originX;
            this.originZ = originZ;
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
            this.palette = palette;
            surfaces = new float[sizeZ][sizeX];
            tops = new int[sizeZ][sizeX];
            features = new int[sizeZ][sizeX];
            colors = new Color[sizeZ][sizeX];
            columns = new EverwinterTerrain.Column[sizeZ][sizeX];
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    columns[z][x] = terrain.column(originX + x, originZ + z, 80, 1);
                    tops[z][x] = (int) Math.ceil(columns[z][x].height()) - 1;
                    features[z][x] = columns[z][x].feature();
                }
            }
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    // Same gradient rule as the material rule (heightmap differences over two blocks).
                    int gx = tops[z][Math.min(sizeX - 1, x + 1)] - tops[z][Math.max(0, x - 1)];
                    int gz = tops[Math.min(sizeZ - 1, z + 1)][x] - tops[Math.max(0, z - 1)][x];
                    boolean gentle = Math.abs(gx) < 3 && Math.abs(gz) < 3;
                    colors[z][x] = color(palette.select(originX + x, tops[z][x], originZ + z, tops[z][x],
                            columns[z][x].iceBase(), features[z][x], gentle));
                    surfaces[z][x] = tops[z][x] + 1;
                }
            }
        }

        Color side(int x, int z, int y) {
            return color(palette.select(originX + x, y, originZ + z, tops[z][x], columns[z][x].iceBase(), features[z][x], false));
        }
    }

    private static void writeTopDown(Area area, int scale, Path output, String title) throws Exception {
        BufferedImage image = new BufferedImage(area.sizeX * scale, area.sizeZ * scale + 50, BufferedImage.TYPE_INT_RGB);
        float[][] s = area.surfaces;
        for (int z = 0; z < area.sizeZ; z++) {
            for (int x = 0; x < area.sizeX; x++) {
                double slope = s[z][Math.max(0, x - 1)] - s[z][x] + s[Math.max(0, z - 1)][x] - s[z][x];
                int rgb = shade(area.colors[z][x], Math.clamp(0.9 + slope * 0.06, 0.45, 1.15)).getRGB();
                for (int pz = 0; pz < scale; pz++) {
                    for (int px = 0; px < scale; px++) image.setRGB(x * scale + px, z * scale + pz, rgb);
                }
            }
        }
        caption(image, area.sizeZ * scale, title + " | software preview");
        ImageIO.write(image, "png", output.toFile());
        System.out.println("Everwinter preview: " + output.toAbsolutePath());
    }

    /**
     * Oblique view looking north with painter's ordering; each block column is drawn as a vertical strip of
     * {@code scale} pixels, rows {@code rowStep} pixels apart, {@code lift} pixels per block of height.
     */
    private static void writeOblique(Area area, int scale, double rowStep, double lift, Path output, String title) throws Exception {
        int width = area.sizeX * scale;
        float highest = 0, lowest = 999;
        for (float[] row : area.surfaces) {
            for (float value : row) {
                highest = Math.max(highest, value);
                lowest = Math.min(lowest, value);
            }
        }
        int height = (int) ((area.sizeZ - 1) * rowStep + (highest - lowest) * lift) + 80;
        BufferedImage image = new BufferedImage(width, height + 50, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(SKY);
        g.fillRect(0, 0, width, height);
        g.dispose();
        int[] lowestDrawn = new int[width];
        java.util.Arrays.fill(lowestDrawn, height);
        // Nearest rows first; a column is only drawn above what nearer rows already cover.
        for (int z = area.sizeZ - 1; z >= 0; z--) {
            for (int x = 0; x < area.sizeX; x++) {
                float surface = area.surfaces[z][x];
                int screenTop = (int) (height - 40 - (area.sizeZ - 1 - z) * rowStep - (surface - lowest) * lift);
                double slope = area.surfaces[z][Math.max(0, x - 1)] - surface;
                Color top = shade(area.colors[z][x], Math.clamp(0.95 + slope * 0.08, 0.5, 1.15));
                for (int px = x * scale; px < (x + 1) * scale; px++) {
                    if (screenTop >= lowestDrawn[px]) continue;
                    for (int y = Math.max(0, screenTop); y < lowestDrawn[px] && y < height; y++) {
                        int depth = (int) ((y - screenTop) / lift);
                        Color c = y - screenTop < Math.max(1, rowStep) ? top
                                : shade(area.side(x, z, area.tops[z][x] - depth), 0.72);
                        image.setRGB(px, y, c.getRGB());
                    }
                    lowestDrawn[px] = Math.max(0, screenTop);
                }
            }
        }
        caption(image, height, title + " | software preview, not in-game");
        ImageIO.write(image, "png", output.toFile());
        System.out.println("Everwinter view: " + output.toAbsolutePath());
    }

    private static void caption(BufferedImage image, int y, String text) {
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(244, 242, 236));
        g.fillRect(0, y, image.getWidth(), 50);
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        g.drawString(text, 14, y + 22);
        g.drawString("White: snow | pale blue: packed ice | deep blue: blue ice | dark grey: deepslate", 14, y + 42);
        g.dispose();
    }

    private static Color color(EverwinterPalette.Material material) {
        return switch (material) {
            case SNOW -> new Color(238, 243, 250);
            case PACKED_ICE -> new Color(150, 184, 232);
            case BLUE_ICE -> new Color(88, 140, 222);
            case DEEPSLATE -> new Color(72, 72, 80);
            case STONE -> new Color(126, 126, 126);
        };
    }

    private static Color shade(Color color, double light) {
        return new Color((int) Math.min(255, color.getRed() * light), (int) Math.min(255, color.getGreen() * light),
                (int) Math.min(255, color.getBlue() * light));
    }
}
