package c.cronus.fantasyterrain.worldgen;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Algorithm preview; the seed is the layout seed, not Minecraft's derived world seed. */
public final class RegionPreview {
    public static void main(String[] args) throws Exception {
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 12345L;
        RegionLayout layout = new RegionLayout(seed, 3800, 360);
        int width = 1000, mapHeight = 800;
        BufferedImage image = new BufferedImage(width, 920, BufferedImage.TYPE_INT_RGB);
        Color[] colors = {new Color(159, 210, 236), new Color(153, 188, 178), new Color(207, 134, 98),
                new Color(126, 177, 111), new Color(181, 155, 204)};
        String[] names = {"Frozen Wastes", "Cold Meadows", "Ember Lands", "Verdant Valley", "Crystal Peaks"};
        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < width; x++) {
                int blockX = (x - width / 2) * 24, blockZ = (y - mapHeight / 2) * 24;
                RegionLayout.Sample sample = layout.sample(blockX, blockZ);
                Color color = colors[sample.region()];
                float mask = sample.border();
                int red = (int) (color.getRed() * (1 - mask) + 55 * mask);
                int green = (int) (color.getGreen() * (1 - mask) + 60 * mask);
                int blue = (int) (color.getBlue() * (1 - mask) + 65 * mask);
                image.setRGB(x, y, new Color(red, green, blue).getRGB());
            }
        }
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(245, 245, 240));
        g.fillRect(0, mapHeight, width, 120);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 19));
        for (int i = 0; i < colors.length; i++) {
            int x = 20 + i * 196;
            g.setColor(colors[i]);
            g.fillRect(x, 828, 18, 18);
            g.setColor(Color.DARK_GRAY);
            g.drawString(names[i], x + 24, 844);
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 17));
        g.drawString("Region layout preview | 24,000 x 19,200 blocks | layout seed " + seed, 20, 877);
        g.drawString("Dark belts: mountain boundaries | neutral interiors | north is up", 20, 901);
        g.dispose();
        Path output = Path.of(args[0]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        ImageIO.write(image, "png", output.toFile());
        System.out.println("Region preview: " + output.toAbsolutePath());
        writeTerrainPreview(layout, output.resolveSibling("border-terrain.png"));
        writeRimPreview(layout, output.resolveSibling("border-rim.png"));
        BasinSectionPreview.write(layout, output.resolveSibling("basin-section.png"));
        BorderSnowPreview.write(layout, output.resolveSibling("border-snow.png"));
        DistrictPreview.write(layout, output.resolveSibling("biome-districts.png"));
    }

    private static void writeTerrainPreview(RegionLayout layout, Path output) throws Exception {
        int centerX = 0, centerZ = 0;
        float bestDrop = 0;
        for (int z = -7000; z <= 7000; z += 64) {
            for (int x = -7000; x <= 7000; x += 64) {
                RegionLayout.Sample a = layout.sample(x, z), b = layout.sample(x + 80, z);
                if (a.border() < 0.9f || b.border() < 0.9f) continue;
                float drop = Math.abs(layout.surfaceHeight(x, z, a) - layout.surfaceHeight(x + 80, z, b));
                if (drop > bestDrop) {
                    bestDrop = drop;
                    centerX = x + 40;
                    centerZ = z;
                }
            }
        }
        int width = 1000, mapHeight = 600, scale = 3;
        float[][] heights = new float[mapHeight][width];
        for (int y = 0; y < mapHeight; y += 2) {
            for (int x = 0; x < width; x += 2) {
                int bx = centerX + (x - width / 2) * scale, bz = centerZ + (y - mapHeight / 2) * scale;
                RegionLayout.TerrainColumn column = layout.terrainColumn(bx, bz, layout.sample(bx, bz));
                int top = (int) Math.ceil(column.upperBound());
                while (top > 0 && column.density(top) <= 0) top -= 2;
                for (int dy = 0; dy < 2 && y + dy < mapHeight; dy++) {
                    for (int dx = 0; dx < 2 && x + dx < width; dx++) heights[y + dy][x + dx] = top;
                }
            }
        }
        BufferedImage image = new BufferedImage(width, 950, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < width; x++) {
                float h = heights[y][x];
                double slopeX = heights[y][Math.max(0, x - 1)] - h;
                double slopeZ = heights[Math.max(0, y - 1)][x] - h;
                double light = Math.clamp(0.85 + (slopeX + slopeZ) * 0.035, 0.35, 1.15);
                Color color = h < 63 ? new Color(48, 95, 122) : h < 90 ? new Color(122, 150, 101)
                        : h < 200 ? new Color(139, 127, 113) : new Color(183, 183, 180);
                image.setRGB(x, y, new Color((int) Math.min(255, color.getRed() * light),
                        (int) Math.min(255, color.getGreen() * light), (int) Math.min(255, color.getBlue() * light)).getRGB());
            }
        }
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(244, 242, 236));
        g.fillRect(0, mapHeight, width, 350);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.setColor(Color.DARK_GRAY);
        g.drawString("Border terrain: 3,000 x 1,800 blocks | shaded density surface, not an in-game screenshot", 18, 628);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        g.drawString("Grey: raised massif and ridges | green: neutral interiors | no belt terrain below region ground", 18, 653);
        int[] polygonX = new int[width + 2], polygonY = new int[width + 2];
        for (int x = 0; x < width; x++) {
            polygonX[x] = x;
            polygonY[x] = 920 - (int) (heights[mapHeight / 2][x] * 0.7);
        }
        polygonX[width] = width - 1;
        polygonY[width] = 920;
        polygonX[width + 1] = 0;
        polygonY[width + 1] = 920;
        g.setColor(new Color(104, 99, 92));
        g.fillPolygon(polygonX, polygonY, width + 2);
        g.setColor(new Color(48, 95, 122));
        int seaY = 920 - (int) (63 * 0.7);
        for (int x = 0; x < width; x++) {
            if (polygonY[x] > seaY) g.drawLine(x, seaY, x, polygonY[x]);
        }
        // Render the actual density slice so the cross-section exposes rock overhangs.
        for (int x = 0; x < width; x++) {
            int bx = centerX + (x - width / 2) * scale;
            RegionLayout.TerrainColumn column = layout.terrainColumn(bx, centerZ, layout.sample(bx, centerZ));
            for (int y = 0; y <= 312; y++) {
                Color color = column.density(y) > 0 ? new Color(104, 99, 92)
                        : y < 63 ? new Color(48, 95, 122) : new Color(244, 242, 236);
                image.setRGB(x, 920 - (int) (y * 0.7), color.getRGB());
            }
        }
        g.setColor(Color.DARK_GRAY);
        g.drawString("West–east density cross-section: grounded alpine ridges and valleys; Y=0 at baseline", 18, 680);
        g.setColor(new Color(239, 207, 95));
        for (int x = 0; x < width; x += 12) g.drawLine(x, mapHeight / 2, x + 6, mapHeight / 2);
        g.dispose();
        ImageIO.write(image, "png", output.toFile());
        System.out.println("Border terrain preview: " + output.toAbsolutePath());
        BorderVolumePreview.write(layout, centerX, centerZ, output.resolveSibling("border-volume.png"));
    }

    /** Close-up of a mountain foot from above: deepslate border columns against region ground, hill-shaded. */
    private static void writeRimPreview(RegionLayout layout, Path output) throws Exception {
        // Walk west from the origin off the massif (whose edge the rim spurs do not move), then centre on the foot.
        int footX = 0;
        while (layout.sample(footX, 0).border() < 0.99f) footX -= 16;
        while (layout.sample(footX, 0).border() >= 0.99f) footX -= 16;
        footX -= 150;
        int width = 1000, mapHeight = 700, scale = 2;
        float[][] heights = new float[mapHeight][width];
        boolean[][] border = new boolean[mapHeight][width];
        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < width; x++) {
                int bx = footX + (x - width / 2) * scale, bz = (y - mapHeight / 2) * scale;
                RegionLayout.Sample sample = layout.sample(bx, bz);
                heights[y][x] = layout.surfaceHeight(bx, bz, sample);
                border[y][x] = sample.border() > RegionLayout.BORDER_THRESHOLD;
            }
        }
        BufferedImage image = new BufferedImage(width, mapHeight + 50, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < width; x++) {
                float h = heights[y][x];
                double slope = heights[y][Math.max(0, x - 1)] - h + heights[Math.max(0, y - 1)][x] - h;
                double light = Math.clamp(0.85 + slope * 0.08, 0.35, 1.2);
                Color color = !border[y][x] ? new Color(84, 128, 62) : h >= 220 ? new Color(225, 228, 232) : new Color(78, 78, 84);
                image.setRGB(x, y, new Color((int) Math.min(255, color.getRed() * light),
                        (int) Math.min(255, color.getGreen() * light), (int) Math.min(255, color.getBlue() * light)).getRGB());
            }
        }
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(244, 242, 236));
        g.fillRect(0, mapHeight, width, 50);
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        g.drawString("Mountain foot close-up: 2,000 x 1,400 blocks around x=" + footX
                + ", z=0 | green: region ground | grey: border columns", 18, mapHeight + 30);
        g.dispose();
        ImageIO.write(image, "png", output.toFile());
        System.out.println("Border rim preview: " + output.toAbsolutePath());
    }
}
