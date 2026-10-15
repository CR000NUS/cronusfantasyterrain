package c.cronus.fantasyterrain.worldgen;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Top-down, one pixel per block: surface material of the border massif around the snowline. */
final class BorderSnowPreview {
    static void write(RegionLayout layout, Path output) throws Exception {
        // Centre on a massif area whose surface sits near the average snowline.
        int centerX = 0, centerZ = 0;
        double best = Double.POSITIVE_INFINITY;
        for (int z = -8000; z <= 8000; z += 128) {
            for (int x = -8000; x <= 8000; x += 128) {
                RegionLayout.Sample sample = layout.sample(x, z);
                if (sample.border() < 0.99f) continue;
                double score = Math.abs(layout.surfaceHeight(x, z, sample) - BorderPalette.SNOW_LINE);
                if (score < best) {
                    best = score;
                    centerX = x;
                    centerZ = z;
                }
            }
        }
        int width = 900, height = 700;
        int[][] tops = new int[height + 2][width + 2];
        for (int y = 0; y < height + 2; y++) {
            for (int x = 0; x < width + 2; x++) {
                int bx = centerX + x - width / 2 - 1, bz = centerZ + y - height / 2 - 1;
                tops[y][x] = (int) Math.ceil(layout.surfaceHeight(bx, bz, layout.sample(bx, bz))) - 1;
            }
        }
        BufferedImage image = new BufferedImage(width, height + 70, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int bx = centerX + x - width / 2, bz = centerZ + y - height / 2;
                int top = tops[y + 1][x + 1];
                int floor = Math.min(top, Math.min(Math.min(tops[y + 1][x], tops[y + 1][x + 2]),
                        Math.min(tops[y][x + 1], tops[y + 2][x + 1])) + 1);
                RegionLayout.Sample sample = layout.sample(bx, bz);
                Color color;
                if (sample.border() <= RegionLayout.BORDER_THRESHOLD) color = new Color(110, 150, 90);
                else color = BorderPalette.select(12345L, bx, top, bz, top, floor, false) == BorderPalette.Material.SNOW
                        ? new Color(236, 240, 244) : new Color(78, 78, 84);
                double light = Math.clamp(0.9 + (tops[y + 1][x] - top + tops[y][x + 1] - top) * 0.08, 0.45, 1.2);
                image.setRGB(x, y, new Color((int) Math.min(255, color.getRed() * light),
                        (int) Math.min(255, color.getGreen() * light), (int) Math.min(255, color.getBlue() * light)).getRGB());
            }
        }
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(244, 242, 236));
        g.fillRect(0, height, width, 70);
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
        g.drawString("Border snowline: 900 x 700 blocks, top-down, 1 pixel per block | not an in-game screenshot", 14, height + 26);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        g.drawString("White: snow block | dark: deepslate | green: region | snow fades in from Y=" + BorderPalette.lowestSnow()
                + ", full cap from Y=" + BorderPalette.fullSnow(), 14, height + 52);
        g.dispose();
        ImageIO.write(image, "png", output.toFile());
        System.out.println("Border snow preview: " + output.toAbsolutePath());
    }

    private BorderSnowPreview() {}
}
