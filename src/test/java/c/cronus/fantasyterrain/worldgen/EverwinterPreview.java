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
        writeFringe(output);
    }

    /**
     * Everwinter growing onto the border mountains (decision 24), with the real region layout: the stretch of
     * border in front of an Everwinter district, looking from the region towards the mountains.
     */
    private static void writeFringe(Path output) throws Exception {
        RegionLayout layout = new RegionLayout(12345L, 3800, 360);
        BiomeDistricts districts = new BiomeDistricts(31L, 3800);
        EverwinterTerrain terrain = new EverwinterTerrain(777L);
        EverwinterColumns columns = new EverwinterColumns(layout, districts, terrain,
                new EverwinterGroups(layout, districts, terrain, 360, 777L), 360);
        EverwinterPalette palette = new EverwinterPalette(99L);
        // A strongly covered spot at the start of the mountain ramp, with the mountains to the north (-z).
        int spotX = 0, spotZ = 0;
        search:
        for (int z = -12000; z < 12000; z += 32) {
            for (int x = -12000; x < 12000; x += 32) {
                EverwinterColumns.Column column = columns.column(x, z);
                if (!column.border() || !column.member() || column.rockTop() > 100) continue;
                // Mountains to the north, Everwinter to the south.
                if (!columns.column(x, z + 150).member() || columns.column(x, z - 150).rockTop() < 140) continue;
                spotX = x;
                spotZ = z;
                break search;
            }
        }
        int sizeX = 500, sizeZ = 420, originX = spotX - sizeX / 2, originZ = spotZ - 260;
        EverwinterColumns.Column[][] grid = new EverwinterColumns.Column[sizeZ][sizeX];
        float[][] heights = new float[sizeZ][sizeX];
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                grid[z][x] = columns.column(originX + x, originZ + z);
                heights[z][x] = grid[z][x].terrain().height();
            }
        }
        Area area = new Area(originX, originZ, sizeX, sizeZ, heights, (x, z, y, top, gentle) -> {
            EverwinterColumns.Column column = grid[z][x];
            int bx = originX + x, bz = originZ + z;
            if (column.border() && !(column.member() && y >= column.terrain().iceBase())) {
                int floor = top;
                for (int k = 0; k < 4; k++) {
                    int nx = Math.clamp(x + (k == 0 ? 1 : k == 1 ? -1 : 0), 0, sizeX - 1);
                    int nz = Math.clamp(z + (k == 2 ? 1 : k == 3 ? -1 : 0), 0, sizeZ - 1);
                    floor = Math.min(floor, (int) Math.ceil(grid[nz][nx].terrain().height()));
                }
                return borderColor(BorderPalette.selectFringe(12345L, bx, y, bz, top, floor, gentle, column.fringe()));
            }
            if (!column.member()) return new Color(126, 126, 126);
            return color(palette.select(bx, y, bz, top, column.terrain().iceBase(), column.terrain().feature(), gentle));
        });
        writeOblique(area, 2, 1, 2, output.resolveSibling("everwinter-fringe.png"),
                "Everwinter growing onto the border mountains (real layout), looking north, true vertical scale");
        writeTopDown(area, 1, output.resolveSibling("everwinter-fringe-map.png"),
                "Everwinter border fringe from above: " + sizeX + " x " + sizeZ + " blocks, mountains to the north");
    }

    private static Color borderColor(BorderPalette.Material material) {
        return switch (material) {
            case SNOW -> new Color(238, 243, 250);
            case PACKED_ICE -> new Color(150, 184, 232);
            case BLUE_ICE -> new Color(88, 140, 222);
            case DEEPSLATE -> new Color(72, 72, 80);
            case TUFF -> new Color(96, 98, 90);
        };
    }

    /** Colour of a block in an area (local coordinates), given the column's top block and whether it is gentle. */
    private interface Painter {
        Color at(int x, int z, int y, int top, boolean gentle);
    }

    /** Block-quantized surface of an area: top block and colours. */
    private static final class Area {
        final int originX, originZ, sizeX, sizeZ;
        final float[][] surfaces;
        final int[][] tops;
        final Color[][] colors;
        final Painter painter;

        /** Everwinter at full strength with grid serac fields. */
        Area(EverwinterTerrain terrain, EverwinterPalette palette, int originX, int originZ, int sizeX, int sizeZ) {
            this(originX, originZ, sizeX, sizeZ, terrain, palette, new EverwinterTerrain.Column[sizeZ][sizeX]);
        }

        private Area(int originX, int originZ, int sizeX, int sizeZ, EverwinterTerrain terrain, EverwinterPalette palette,
                     EverwinterTerrain.Column[][] columns) {
            this(originX, originZ, sizeX, sizeZ, heights(terrain, originX, originZ, columns), (x, z, y, top, gentle) ->
                    color(palette.select(originX + x, y, originZ + z, top, columns[z][x].iceBase(), columns[z][x].feature(), gentle)));
        }

        Area(int originX, int originZ, int sizeX, int sizeZ, float[][] heights, Painter painter) {
            this.originX = originX;
            this.originZ = originZ;
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
            this.painter = painter;
            surfaces = new float[sizeZ][sizeX];
            tops = new int[sizeZ][sizeX];
            colors = new Color[sizeZ][sizeX];
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) tops[z][x] = (int) Math.ceil(heights[z][x]) - 1;
            }
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    // Same gradient rule as the material rule (heightmap differences over two blocks).
                    int gx = tops[z][Math.min(sizeX - 1, x + 1)] - tops[z][Math.max(0, x - 1)];
                    int gz = tops[Math.min(sizeZ - 1, z + 1)][x] - tops[Math.max(0, z - 1)][x];
                    boolean gentle = Math.abs(gx) < 3 && Math.abs(gz) < 3;
                    colors[z][x] = painter.at(x, z, tops[z][x], tops[z][x], gentle);
                    surfaces[z][x] = tops[z][x] + 1;
                }
            }
        }

        private static float[][] heights(EverwinterTerrain terrain, int originX, int originZ, EverwinterTerrain.Column[][] columns) {
            float[][] heights = new float[columns.length][columns[0].length];
            for (int z = 0; z < columns.length; z++) {
                for (int x = 0; x < columns[0].length; x++) {
                    columns[z][x] = terrain.column(originX + x, originZ + z, 80, 1);
                    heights[z][x] = columns[z][x].height();
                }
            }
            return heights;
        }

        Color side(int x, int z, int y) {
            return painter.at(x, z, y, tops[z][x], false);
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
