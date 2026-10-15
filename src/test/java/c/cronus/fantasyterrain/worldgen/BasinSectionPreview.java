package c.cronus.fantasyterrain.worldgen;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Vertical slice across a region edge: region stone sits in a deepslate basin that rises into the mountains. */
final class BasinSectionPreview {
    private static final int SPAN = 2400, STEP = 2, MIN_Y = -64, MAX_Y = 320, SEA_LEVEL = 63;
    private static final Color SKY = new Color(196, 222, 242), WATER = new Color(62, 104, 196);
    private static final Color GRASS = new Color(98, 158, 58), DIRT = new Color(134, 96, 67), STONE = new Color(128, 128, 128);
    private static final Color DEEPSLATE = new Color(72, 72, 80), TUFF = new Color(110, 111, 102), SNOW = new Color(242, 246, 250);

    static void write(RegionLayout layout, Path output) throws Exception {
        // Find a region edge near the origin along a west-east line.
        int edgeX = 0, lineZ = 0;
        search:
        for (int z = 0; z <= 12000; z += 512) {
            for (int x = -6000; x < 6000; x += 4) {
                if (layout.sample(x, z).region() != layout.sample(x + 4, z).region()) {
                    edgeX = x;
                    lineZ = z;
                    break search;
                }
            }
        }
        int width = SPAN / STEP, height = MAX_Y - MIN_Y, footer = 60;
        BufferedImage image = new BufferedImage(width, height + footer, BufferedImage.TYPE_INT_RGB);
        for (int px = 0; px < width; px++) {
            int x = edgeX - SPAN / 2 + px * STEP;
            RegionLayout.Sample sample = layout.sample(x, lineZ);
            float surface = layout.surfaceHeight(x, lineZ, sample);
            int top = (int) Math.ceil(surface) - 1;
            boolean border = sample.border() > RegionLayout.BORDER_THRESHOLD;
            float basin = layout.basinFloor(x, lineZ, sample);
            int exteriorFloor = Math.min(top, exteriorFloor(layout, x, lineZ, surface));
            for (int y = MIN_Y; y < MAX_Y; y++) {
                Color color;
                if (y > top) color = y < SEA_LEVEL ? WATER : SKY;
                else if (border) color = switch (BorderPalette.select(12345L, x, y, lineZ, top, exteriorFloor, false)) {
                    case DEEPSLATE -> DEEPSLATE;
                    case TUFF -> TUFF;
                    case SNOW -> SNOW;
                };
                else if (y < 0 || y < basin) color = DEEPSLATE;
                else if (y == top) color = GRASS;
                else if (y >= top - 3) color = DIRT;
                else color = STONE;
                image.setRGB(px, MAX_Y - 1 - y, color.getRGB());
            }
        }
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(245, 245, 240));
        g.fillRect(0, height, width, footer);
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        g.drawString("Basin cross-section | " + SPAN + " blocks west-east at z=" + lineZ + ", centred on a region edge | Y "
                + MIN_Y + " to " + MAX_Y + " | vertical scale x" + STEP, 10, height + 22);
        g.drawString("Grey: region stone | dark: deepslate basin and border rock | white: snow cap", 10, height + 44);
        g.dispose();
        ImageIO.write(image, "png", output.toFile());
        System.out.println("Basin cross-section: " + output.toAbsolutePath());
    }

    private static int exteriorFloor(RegionLayout layout, int x, int z, float surface) {
        float lowest = surface;
        lowest = Math.min(lowest, layout.surfaceHeight(x - 1, z, layout.sample(x - 1, z)));
        lowest = Math.min(lowest, layout.surfaceHeight(x + 1, z, layout.sample(x + 1, z)));
        lowest = Math.min(lowest, layout.surfaceHeight(x, z - 1, layout.sample(x, z - 1)));
        lowest = Math.min(lowest, layout.surfaceHeight(x, z + 1, layout.sample(x, z + 1)));
        return (int) Math.ceil(lowest);
    }

    private BasinSectionPreview() {}
}
