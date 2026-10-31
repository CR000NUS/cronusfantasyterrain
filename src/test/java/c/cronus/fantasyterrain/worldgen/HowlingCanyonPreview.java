package c.cronus.fantasyterrain.worldgen;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Software previews of Howling Canyon with the real region layout (seed 12345): a perspective view down the main
 * canyon, a top-down map of the whole district and a cross-section through the canyon, all with block steps as
 * they would generate. Not in-game screenshots.
 */
public final class HowlingCanyonPreview {
    private static final Color SKY = new Color(150, 170, 205), FOG = new Color(196, 208, 228);
    private static final int AREA = 2400;

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        RegionLayout layout = new RegionLayout(12345L, 3800, 360);
        BiomeDistricts districts = new BiomeDistricts(31L, 3800);
        HowlingCanyonTerrain terrain = new HowlingCanyonTerrain(777L);
        HowlingCanyonCourses courses = new HowlingCanyonCourses(layout, districts, terrain, 360, 777L);
        HowlingCanyonColumns columns = new HowlingCanyonColumns(layout, districts, terrain, courses, 360);
        HowlingCanyonPalette palette = new HowlingCanyonPalette(99L);

        // The district with the longest main canyon near the origin.
        HowlingCanyonTerrain.Course main = null;
        for (int z = -12000; z <= 12000; z += 300) {
            for (int x = -12000; x <= 12000; x += 300) {
                HowlingCanyonColumns.Column column = columns.column(x, z);
                if (column == null || !column.member()) continue;
                HowlingCanyonTerrain.Course[] found = courses.courses(layout.sample(x, z));
                if (found.length > 0 && (main == null || found[0].size() > main.size())) main = found[0];
            }
        }
        if (main == null) throw new IllegalStateException("No Howling Canyon district with a canyon found");
        int middle = main.size() / 2;
        int originX = (int) main.x()[middle] - AREA / 2, originZ = (int) main.z()[middle] - AREA / 2;
        Grid grid = new Grid(layout, columns, originX, originZ, AREA);

        writeMap(grid, palette, output, "Howling Canyon district from above: " + AREA + " x " + AREA + " blocks, 1 px per 2 blocks");
        // Perspective view from above the rim near one end of the main canyon, looking down it.
        int from = (int) (main.size() * 0.12), to = (int) (main.size() * 0.55);
        double dirX = main.x()[to] - main.x()[from], dirZ = main.z()[to] - main.z()[from], norm = Math.hypot(dirX, dirZ);
        writeView(grid, palette, main.x()[from] - dirX / norm * 60, main.z()[from] - dirZ / norm * 60, dirX / norm, dirZ / norm, 160,
                output.resolveSibling("howling-canyon-view.png"), "Howling Canyon looking down the main canyon from Y=160");
        int sideFrom = (int) (main.size() * 0.4);
        double tx = main.x()[sideFrom + 2] - main.x()[sideFrom - 2], tz = main.z()[sideFrom + 2] - main.z()[sideFrom - 2], tn = Math.hypot(tx, tz);
        writeView(grid, palette, main.x()[sideFrom] + tz / tn * 170, main.z()[sideFrom] - tx / tn * 170, -tz / tn * 0.8 + tx / tn * 0.6,
                tx / tn * 0.8 + tz / tn * 0.6, 185, output.resolveSibling("howling-canyon-rim.png"),
                "Howling Canyon from above the rim, looking across and along the canyon from Y=185");
        writeSection(grid, palette, main, sideFrom, output.resolveSibling("howling-canyon-section.png"));
        // The canyon mouth: from inside the canyon, looking out of the biome along the river.
        int end = main.size() - 1, back = Math.max(0, end - 110);
        double mouthX = main.x()[end] - main.x()[back], mouthZ = main.z()[end] - main.z()[back], mouthNorm = Math.hypot(mouthX, mouthZ);
        writeView(grid, palette, main.x()[back], main.z()[back], mouthX / mouthNorm, mouthZ / mouthNorm, 112,
                output.resolveSibling("howling-canyon-mouth.png"), "Howling Canyon mouth: looking out of the biome along the frozen river from Y=112");
        // Close-up of a massif's stepped cliffs: from the far river bank, facing the tallest massif near the river.
        int bestCamX = -1, bestCamZ = -1, bestTop = 0;
        double bestDirX = 0, bestDirZ = 0;
        for (int i = 4; i < main.size() - 4; i += 6) {
            double ax = main.x()[i + 2] - main.x()[i - 2], az = main.z()[i + 2] - main.z()[i - 2], an = Math.hypot(ax, az);
            for (int side = -1; side <= 1; side += 2) {
                double nx = -az / an * side, nz = ax / an * side;
                int camX = (int) (main.x()[i] - nx * 30) - grid.originX, camZ = (int) (main.z()[i] - nz * 30) - grid.originZ;
                for (int reach = 70; reach <= 190; reach += 30) {
                    int aimX = (int) (main.x()[i] + nx * reach) - grid.originX, aimZ = (int) (main.z()[i] + nz * reach) - grid.originZ;
                    if (camX < 0 || camZ < 0 || aimX < 0 || aimZ < 0 || camX >= AREA || camZ >= AREA || aimX >= AREA || aimZ >= AREA) continue;
                    if (grid.top(camX, camZ) > 70 || grid.top(aimX, aimZ) <= bestTop) continue;
                    bestTop = grid.top(aimX, aimZ);
                    bestCamX = camX;
                    bestCamZ = camZ;
                    bestDirX = nx * 0.9 + ax / an * 0.4;
                    bestDirZ = nz * 0.9 + az / an * 0.4;
                }
            }
        }
        if (bestCamX >= 0) {
            writeView(grid, palette, bestCamX + grid.originX, bestCamZ + grid.originZ, bestDirX, bestDirZ, grid.top(bestCamX, bestCamZ) + 34,
                    output.resolveSibling("howling-canyon-cliffs.png"), "Howling Canyon cliffs seen from the far river bank: flat walls in 2-3 steps");
        }
        // A glacier coming down from the mountains: from inside the canyon, looking at the mountain column with the
        // thickest ice, plus a section through it towards the mountains.
        int iceX = -1, iceZ = -1;
        double thickest = 0;
        for (int z = 0; z < AREA; z += 2) {
            for (int x = 0; x < AREA; x += 2) {
                HowlingCanyonColumns.Column column = grid.columns[z][x];
                if (column == null || !column.border() || !column.member() || column.height() - column.rockTop() <= thickest) continue;
                thickest = column.height() - column.rockTop();
                iceX = x;
                iceZ = z;
            }
        }
        if (iceX >= 0) {
            RegionLayout.Sample sample = layout.sample(grid.originX + iceX, grid.originZ + iceZ);
            double radius = Math.hypot(sample.localX(), sample.localZ()), outX = sample.localX() / radius, outZ = sample.localZ() / radius;
            writeView(grid, palette, grid.originX + iceX - outX * 420 - outZ * 90, grid.originZ + iceZ - outZ * 420 + outX * 90,
                    outX + outZ * 0.2, outZ - outX * 0.2, 150, output.resolveSibling("howling-canyon-glacier.png"),
                    "Howling Canyon: a glacier coming down from the mountains into the canyon walls, from Y=150");
            writeSection(grid, palette, grid.originX + iceX - outX * 200, grid.originZ + iceZ - outZ * 200, outX, outZ, 330, 50, 270,
                    output.resolveSibling("howling-canyon-glacier-section.png"), "Howling Canyon glacier section towards the mountains");
        } else {
            System.out.println("Howling Canyon preview: no glacier on the mountains in this district");
        }
        // High over the river: the separate massifs and the gaps between them.
        writeView(grid, palette, main.x()[from] - dirX / norm * 200, main.z()[from] - dirZ / norm * 200, dirX / norm, dirZ / norm, 240,
                output.resolveSibling("howling-canyon-overview.png"), "Howling Canyon from high above the river (Y=240): massifs, gaps and the river");
    }

    /** Columns of an area around the canyon (null outside Howling Canyon: plain neighbouring ground). */
    private static final class Grid {
        final int originX, originZ, size;
        final HowlingCanyonColumns.Column[][] columns;
        final int[][] tops;
        final boolean[][] mountains;

        Grid(RegionLayout layout, HowlingCanyonColumns columns, int originX, int originZ, int size) {
            this.originX = originX;
            this.originZ = originZ;
            this.size = size;
            this.columns = new HowlingCanyonColumns.Column[size][size];
            this.tops = new int[size][size];
            this.mountains = new boolean[size][size];
            java.util.stream.IntStream.range(0, size).parallel().forEach(z -> {
                for (int x = 0; x < size; x++) {
                    HowlingCanyonColumns.Column column = columns.column(originX + x, originZ + z);
                    this.columns[z][x] = column;
                    if (column == null) {
                        // Other biomes and the far side of the mountains: the plain region terrain.
                        RegionLayout.Sample sample = layout.sample(originX + x, originZ + z);
                        tops[z][x] = (int) Math.ceil(layout.surfaceHeight(originX + x, originZ + z, sample)) - 1;
                        mountains[z][x] = sample.border() > RegionLayout.BORDER_THRESHOLD;
                    } else {
                        tops[z][x] = (int) Math.ceil(column.height()) - 1;
                    }
                }
            });
        }

        int top(int x, int z) {
            return tops[Math.clamp(z, 0, size - 1)][Math.clamp(x, 0, size - 1)];
        }

        boolean gentle(int x, int z) {
            return Math.abs(top(x + 1, z) - top(x - 1, z)) < 3 && Math.abs(top(x, z + 1) - top(x, z - 1)) < 3;
        }

        /** Colour of block (x, y, z) in grid coordinates. */
        Color color(HowlingCanyonPalette palette, int x, int y, int z) {
            HowlingCanyonColumns.Column column = columns[z][x];
            if (column == null || !column.member() || column.border() && y < column.bed()) {
                boolean mountain = column != null ? column.border() : mountains[z][x];
                return mountain ? new Color(72, 72, 80) : y == tops[z][x] ? new Color(226, 232, 240) : new Color(126, 126, 126);
            }
            return color(palette.select(originX + x, y, originZ + z, tops[z][x], column.bed(), column.feature(), gentle(x, z)));
        }

        static Color color(HowlingCanyonPalette.Material material) {
            return switch (material) {
                case SNOW -> new Color(238, 243, 250);
                case PACKED_ICE -> new Color(150, 184, 232);
                case BLUE_ICE -> new Color(88, 140, 222);
                case DEEPSLATE -> new Color(72, 72, 80);
                case STONE -> new Color(126, 126, 126);
            };
        }
    }

    private static void writeMap(Grid grid, HowlingCanyonPalette palette, Path output, String title) throws Exception {
        int scale = 2, size = grid.size / scale;
        BufferedImage image = new BufferedImage(size, size + 50, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int gx = x * scale, gz = z * scale, top = grid.top(gx, gz);
                double slope = grid.top(gx - 1, gz) - top + grid.top(gx, gz - 1) - top;
                image.setRGB(x, z, shade(grid.color(palette, gx, top, gz), Math.clamp(0.9 + slope * 0.06, 0.45, 1.15)).getRGB());
            }
        }
        caption(image, size, title + " | software preview");
        ImageIO.write(image, "png", output.toFile());
        System.out.println("Howling Canyon preview: " + output.toAbsolutePath());
    }

    /**
     * Perspective view (heightmap ray casting): camera at world (camX, camZ) and height camY, looking along
     * (dirX, dirZ) and slightly down; cliff faces show the blocks at the height each pixel sees.
     */
    private static void writeView(Grid grid, HowlingCanyonPalette palette, double camX, double camZ, double dirX, double dirZ,
                                  double camY, Path output, String title) throws Exception {
        int width = 1600, height = 900, horizon = 330;
        double focal = 900;
        BufferedImage image = new BufferedImage(width, height + 50, BufferedImage.TYPE_INT_RGB);
        for (int px = 0; px < width; px++) {
            double lateral = (px - width / 2.0) / focal;
            // Ray direction on the ground plane (not normalised: distance d is along the view axis).
            double rayX = dirX - dirZ * lateral, rayZ = dirZ + dirX * lateral;
            int lowest = height;
            for (double d = 4; d < 1700 && lowest > 0; d *= 1.004) {
                int gx = (int) Math.floor(camX + rayX * d) - grid.originX, gz = (int) Math.floor(camZ + rayZ * d) - grid.originZ;
                if (gx < 0 || gz < 0 || gx >= grid.size || gz >= grid.size) continue;
                int top = grid.tops[gz][gx];
                int screen = (int) (horizon - (top + 1 - camY) / d * focal);
                if (screen >= lowest) continue;
                double fog = Math.min(0.85, d / 1900);
                for (int py = Math.max(0, screen); py < lowest; py++) {
                    // World height seen by this pixel at this distance; the face below the top shows its blocks.
                    int y = (int) Math.floor(camY + (horizon - py) * d / focal);
                    Color color = py - screen < Math.max(1, focal / d) ? shade(grid.color(palette, gx, top, gz), 1.0)
                            : shade(grid.color(palette, gx, Math.min(top, Math.max(y, top - 200)), gz), 0.78);
                    image.setRGB(px, py, mix(color, FOG, fog).getRGB());
                }
                lowest = Math.max(0, screen);
            }
            for (int py = 0; py < lowest; py++) image.setRGB(px, py, mix(SKY, FOG, py / (double) horizon * 0.6).getRGB());
        }
        caption(image, height, title + " | software preview, not in-game");
        ImageIO.write(image, "png", output.toFile());
        System.out.println("Howling Canyon view: " + output.toAbsolutePath());
    }

    /** Cross-section through the canyon at a course point, perpendicular to it, at true scale (2 px per block). */
    private static void writeSection(Grid grid, HowlingCanyonPalette palette, HowlingCanyonTerrain.Course main, int index, Path output)
            throws Exception {
        double tx = main.x()[index + 2] - main.x()[index - 2], tz = main.z()[index + 2] - main.z()[index - 2], norm = Math.hypot(tx, tz);
        writeSection(grid, palette, main.x()[index], main.z()[index], -tz / norm, tx / norm, 380, 40, 160, output, "Howling Canyon cross-section");
    }

    /** Vertical section through (centreX, centreZ) along (nx, nz), {@code half} blocks each way, at true scale (2 px per block). */
    private static void writeSection(Grid grid, HowlingCanyonPalette palette, double centreX, double centreZ, double nx, double nz,
                                     int half, int low, int high, Path output, String title) throws Exception {
        int scale = 2;
        BufferedImage image = new BufferedImage(2 * half * scale, (high - low) * scale + 50, BufferedImage.TYPE_INT_RGB);
        for (int u = -half; u < half; u++) {
            int gx = (int) Math.floor(centreX + nx * u) - grid.originX, gz = (int) Math.floor(centreZ + nz * u) - grid.originZ;
            int top = grid.top(gx, gz);
            for (int y = low; y < high; y++) {
                Color color = y > top ? SKY : grid.color(palette, Math.clamp(gx, 0, grid.size - 1), y, Math.clamp(gz, 0, grid.size - 1));
                for (int p = 0; p < scale * scale; p++) {
                    image.setRGB((u + half) * scale + p % scale, (high - 1 - y) * scale + p / scale, color.getRGB());
                }
            }
        }
        caption(image, (high - low) * scale, title + ", " + 2 * half + " blocks wide, Y=" + low + "-" + high + ", true scale | software preview");
        ImageIO.write(image, "png", output.toFile());
        System.out.println("Howling Canyon section: " + output.toAbsolutePath());
    }

    private static void caption(BufferedImage image, int y, String text) {
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(244, 242, 236));
        g.fillRect(0, y, image.getWidth(), 50);
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        g.drawString(text, 14, y + 22);
        g.drawString("White: snow | pale blue: packed ice | deep blue: blue ice | grey: stone / other biomes | dark grey: deepslate", 14, y + 42);
        g.dispose();
    }

    private static Color mix(Color a, Color b, double t) {
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t), (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    private static Color shade(Color color, double light) {
        return new Color((int) Math.min(255, color.getRed() * light), (int) Math.min(255, color.getGreen() * light),
                (int) Math.min(255, color.getBlue() * light));
    }
}
