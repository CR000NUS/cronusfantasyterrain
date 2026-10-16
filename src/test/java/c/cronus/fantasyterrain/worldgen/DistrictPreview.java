package c.cronus.fantasyterrain.worldgen;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/** Top-down map of the biome districts with the pools from the preset; a software preview, not in-game. */
public final class DistrictPreview {
    private static final Color[] SLOT_COLORS = {new Color(122, 170, 214), new Color(214, 160, 96), new Color(132, 186, 112),
            new Color(196, 128, 170), new Color(222, 206, 110), new Color(120, 196, 186)};

    public static void write(RegionLayout layout, Path output) throws Exception {
        String preset = Files.readString(BiomeDistrictTest.PRESET);
        List<List<String>> pools = BiomeDistrictTest.POOLS.stream().map(name -> BiomeDistrictTest.pool(preset, name)).toList();
        BiomeDistricts districts = new BiomeDistricts(999L, 3800);
        int width = 1000, mapHeight = 800, scale = 14;
        BufferedImage image = new BufferedImage(width, mapHeight + 60, BufferedImage.TYPE_INT_RGB);
        int[][] ids = new int[mapHeight][width];
        // Label positions: mean pixel of each region district.
        Map<Long, double[]> centroids = new HashMap<>();
        Map<Long, String> labels = new HashMap<>();
        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < width; x++) {
                int blockX = (x - width / 2) * scale, blockZ = (y - mapHeight / 2) * scale;
                RegionLayout.Sample sample = layout.sample(blockX, blockZ);
                if (sample.border() > RegionLayout.BORDER_THRESHOLD) {
                    ids[y][x] = -1;
                    image.setRGB(x, y, new Color(62, 64, 70).getRGB());
                    continue;
                }
                List<String> pool = pools.get(sample.region());
                int slot = districts.district(sample, pool.size()).slot();
                ids[y][x] = (sample.cellX() * 31 + sample.cellZ()) * 8 + slot;
                image.setRGB(x, y, SLOT_COLORS[slot].getRGB());
                long key = ((long) sample.cellX() << 40) ^ ((long) (sample.cellZ() & 0xFFFFF) << 8) ^ slot;
                double[] sum = centroids.computeIfAbsent(key, k -> new double[3]);
                sum[0] += x;
                sum[1] += y;
                sum[2]++;
                labels.putIfAbsent(key, pool.get(slot).replace("minecraft:", "").replace("cronusfantasyterrain:", ""));
            }
        }
        // Dark lines along district boundaries.
        for (int y = 1; y < mapHeight; y++) {
            for (int x = 1; x < width; x++) {
                if (ids[y][x] >= 0 && (ids[y][x] != ids[y][x - 1] || ids[y][x] != ids[y - 1][x]) && ids[y][x - 1] >= 0 && ids[y - 1][x] >= 0) {
                    image.setRGB(x, y, new Color(40, 40, 46).getRGB());
                }
            }
        }
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        FontMetrics metrics = g.getFontMetrics();
        centroids.forEach((key, sum) -> {
            if (sum[2] < 400) return;
            String label = labels.get(key);
            int lx = (int) (sum[0] / sum[2]) - metrics.stringWidth(label) / 2, ly = (int) (sum[1] / sum[2]) + 4;
            g.setColor(new Color(255, 255, 255, 170));
            g.fillRect(lx - 2, ly - 11, metrics.stringWidth(label) + 4, 14);
            g.setColor(Color.BLACK);
            g.drawString(label, lx, ly);
        });
        g.setColor(new Color(245, 245, 240));
        g.fillRect(0, mapHeight, width, 60);
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        g.drawString("Biome districts | " + width * scale + " x " + mapHeight * scale + " blocks | layout seed 12345 | software preview", 14, mapHeight + 24);
        g.drawString("Each region: every pool biome exactly once, one district each | dark: border mountains | north is up", 14, mapHeight + 48);
        g.dispose();
        ImageIO.write(image, "png", output.toFile());
        System.out.println("Biome district preview: " + output.toAbsolutePath());
    }
}
