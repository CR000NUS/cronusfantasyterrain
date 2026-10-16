package c.cronus.fantasyterrain.worldgen;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Software previews of Everwinter at full strength: top-down map and an oblique view. Not in-game screenshots. */
public final class EverwinterPreview {
    private static final int SIZE_X = 900, SIZE_Z = 700;

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        EverwinterTerrain terrain = new EverwinterTerrain(777L);
        EverwinterPalette palette = new EverwinterPalette(99L);
        int originX = 400, originZ = -300;
        float[][] heights = new float[SIZE_Z][SIZE_X];
        Color[][] colors = new Color[SIZE_Z][SIZE_X];
        for (int z = 0; z < SIZE_Z; z++) {
            for (int x = 0; x < SIZE_X; x++) {
                heights[z][x] = terrain.column(originX + x, originZ + z, 80, 1).height();
            }
        }
        for (int z = 0; z < SIZE_Z; z++) {
            for (int x = 0; x < SIZE_X; x++) {
                int bx = originX + x, bz = originZ + z;
                EverwinterTerrain.Column column = terrain.column(bx, bz, 80, 1);
                int top = (int) Math.ceil(column.height()) - 1;
                float gx = heights[z][Math.min(SIZE_X - 1, x + 1)] - heights[z][Math.max(0, x - 1)];
                float gz = heights[Math.min(SIZE_Z - 1, z + 1)][x] - heights[Math.max(0, z - 1)][x];
                boolean gentle = Math.abs(gx) < 3 && Math.abs(gz) < 3;
                colors[z][x] = color(palette.select(bx, top, bz, top, column.iceBase(), column.feature(), gentle));
            }
        }
        writeTopDown(heights, colors, output);
        writeOblique(heights, colors, palette, terrain, originX, originZ, output.resolveSibling("everwinter-view.png"));
    }

    private static void writeTopDown(float[][] heights, Color[][] colors, Path output) throws Exception {
        BufferedImage image = new BufferedImage(SIZE_X, SIZE_Z + 50, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < SIZE_Z; z++) {
            for (int x = 0; x < SIZE_X; x++) {
                double slope = heights[z][Math.max(0, x - 1)] - heights[z][x] + heights[Math.max(0, z - 1)][x] - heights[z][x];
                image.setRGB(x, z, shade(colors[z][x], Math.clamp(0.9 + slope * 0.06, 0.45, 1.15)).getRGB());
            }
        }
        caption(image, SIZE_Z, "Everwinter top-down: 900 x 700 blocks, 1 px per block, full biome strength | software preview");
        ImageIO.write(image, "png", output.toFile());
        System.out.println("Everwinter preview: " + output.toAbsolutePath());
    }

    /** Oblique view looking north with painter's ordering; each block column is drawn as a vertical strip. */
    private static void writeOblique(float[][] heights, Color[][] colors, EverwinterPalette palette, EverwinterTerrain terrain,
                                     int originX, int originZ, Path output) throws Exception {
        int width = SIZE_X, height = 760;
        double rowStep = 0.55, lift = 2.2;
        BufferedImage image = new BufferedImage(width, height + 50, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(64, 104, 168));
        g.fillRect(0, 0, width, height);
        int[] lowestDrawn = new int[width];
        java.util.Arrays.fill(lowestDrawn, height);
        // Nearest rows first; a column is only drawn above what nearer rows already cover.
        for (int z = SIZE_Z - 1; z >= 0; z -= 1) {
            for (int x = 0; x < width; x++) {
                int screenTop = (int) (height - 40 - (SIZE_Z - 1 - z) * rowStep - (heights[z][x] - 60) * lift);
                if (screenTop >= lowestDrawn[x]) continue;
                double slope = heights[z][Math.max(0, x - 1)] - heights[z][x];
                Color top = shade(colors[z][x], Math.clamp(0.95 + slope * 0.08, 0.5, 1.15));
                int bx = originX + x, bz = originZ + z;
                EverwinterTerrain.Column column = terrain.column(bx, bz, 80, 1);
                for (int y = Math.max(0, screenTop); y < lowestDrawn[x] && y < height; y++) {
                    int depth = (int) ((y - screenTop) / lift);
                    Color c = depth == 0 ? top : shade(color(palette.select(bx, (int) heights[z][x] - 1 - depth, bz,
                            (int) heights[z][x] - 1, column.iceBase(), column.feature(), false)), 0.72);
                    image.setRGB(x, y, c.getRGB());
                }
                lowestDrawn[x] = Math.max(0, screenTop);
            }
        }
        g.dispose();
        caption(image, height, "Everwinter oblique view looking north (vertical scale x2.2) | software preview, not in-game");
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
