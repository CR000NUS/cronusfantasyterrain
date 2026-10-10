package c.cronus.fantasyterrain.worldgen;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Software rendering of the actual density sign, including visible undercuts. */
public final class BorderVolumePreview {
    public static void write(RegionLayout layout, int centerX, int centerZ, Path output) throws Exception {
        int nx = 144, nz = 128, ny = 80;
        boolean[][][] rock = new boolean[nx][nz][ny];
        float[][] masks = new float[nx][nz];
        for (int x = 0; x < nx; x++) {
            for (int z = 0; z < nz; z++) {
                int bx = centerX + (x - nx / 2) * 6, bz = centerZ + (z - nz / 2) * 6;
                RegionLayout.TerrainColumn column = layout.terrainColumn(bx, bz, layout.sample(bx, bz));
                masks[x][z] = column.border();
                for (int y = 0; y < ny; y++) rock[x][z][y] = column.density(y * 4 + 2) > 0;
            }
        }
        BufferedImage image = new BufferedImage(1180, 940, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(207, 220, 231));
        g.fillRect(0, 0, 1180, 940);
        for (int diagonal = 0; diagonal < nx + nz - 1; diagonal++) {
            for (int x = 0; x < nx; x++) {
                int z = diagonal - x;
                if (z < 0 || z >= nz) continue;
                for (int y = 0; y < ny; y++) {
                    double sx = 565 + (x - z) * 3.5, sy = 480 + (x + z) * 1.3 - (y + 1) * 4.8;
                    if (!rock[x][z][y]) {
                        if (y == 15) {
                            g.setColor(new Color(62, 107, 132));
                            face(g, sx, sy, new double[] {0, 3.5, 0, -3.5}, new double[] {0, 1.3, 2.6, 1.3});
                        }
                        continue;
                    }
                    Color base = y > 63 ? new Color(185, 182, 177) : new Color(136, 131, 123);
                    if (x + 1 == nx || !rock[x + 1][z][y]) {
                        g.setColor(base.darker());
                        face(g, sx, sy, new double[] {3.5, 0, 0, 3.5}, new double[] {1.3, 2.6, 7.4, 6.1});
                    }
                    if (z + 1 == nz || !rock[x][z + 1][y]) {
                        g.setColor(base);
                        face(g, sx, sy, new double[] {-3.5, 0, 0, -3.5}, new double[] {1.3, 2.6, 7.4, 6.1});
                    }
                    if (y + 1 == ny || !rock[x][z][y + 1]) {
                        g.setColor(masks[x][z] < 0.1f ? new Color(112, 143, 98) : base.brighter());
                        face(g, sx, sy, new double[] {0, 3.5, 0, -3.5}, new double[] {0, 1.3, 2.6, 1.3});
                    }
                }
            }
        }
        g.setColor(new Color(246, 244, 238));
        g.fillRect(0, 875, 1180, 65);
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.drawString("Border rock density: 864 x 768 blocks | software voxel preview, not a Minecraft screenshot", 18, 902);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        g.drawString("Actual solid/air field sampled every 6 horizontal and 4 vertical blocks | layout seed 12345", 18, 926);
        g.dispose();
        ImageIO.write(image, "png", output.toFile());
        System.out.println("Border volume preview: " + output.toAbsolutePath());
    }

    private static void face(Graphics2D g, double x, double y, double[] dx, double[] dy) {
        int[] px = new int[4], py = new int[4];
        for (int i = 0; i < 4; i++) { px[i] = (int) Math.round(x + dx[i]); py[i] = (int) Math.round(y + dy[i]); }
        g.fillPolygon(px, py, 4);
    }
}
