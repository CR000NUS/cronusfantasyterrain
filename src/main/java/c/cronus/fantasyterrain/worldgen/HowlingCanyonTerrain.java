package c.cronus.fantasyterrain.worldgen;

/**
 * Howling Canyon relief on top of the shared region interior (Frozen Wastes). Pure Java, no Minecraft
 * dependencies. Open snow ground with a frozen river winding through it, between separate glacier massifs
 * (decision 29): blobby, lobed masses of glacier ice that rise in tiers towards their middle, like mountains, each
 * tier a sheer, fluted ice cliff above a snowy bench. Gaps of open ground run between neighbouring massifs; some
 * of them are higher saddles where two massifs join at their first or second tier. The river keeps a strip of
 * ground on both sides, so the massifs line it like broken canyon walls instead of two continuous walls.
 * <p>
 * Natural, never geometric (decision 31): massifs of very different sizes, scattered irregularly (one site per cell
 * of a coarse grid, jittered over almost the whole cell, some cells empty, bigger massifs claiming more room so the
 * dividing lines between them curve). Each outline varies with direction at several scales (egg-shaped, elongated,
 * lobed). The height climbs from the outline towards an off-centre summit, on some massifs also towards a lower
 * second summit, so one side is steep with narrow benches and the other gentle with wide ones, and the tier rings
 * are neither evenly spaced nor parallel to the outline or the river. A terrace function turns that into benches and
 * sheer risers (flat walls in 2-3 even steps, decision 30). The relief is scaled by a weight that is zero outside
 * the biome and in every column the region border touches, so borders, basin and neighbouring biomes stay
 * untouched; towards the biome edge the ground rises smoothly to the neighbouring ground and the massifs step down.
 */
public final class HowlingCanyonTerrain {
    /** Column feature codes; also used by the material rule. */
    public static final int PLATEAU = 0, FLOOR = 1, RIVER = 2;
    /** Number of feature codes. */
    public static final int FEATURES = 3;
    /** Ground level between the massifs. The river ice lies a block lower, still above sea level (63). */
    public static final double FLOOR_Y = 66;
    /** Lowest surface: the river ice tops out above sea level (63), so the world's fluid never fills the canyon. */
    public static final double LOWEST_SURFACE = 64;
    /** Distance (blocks) over which the relief fades in from the biome's district boundary. */
    public static final double DISTRICT_FADE = 220;
    /** Distance (blocks) over which the relief fades in, measured inward from where the border mask reaches 0. */
    public static final double EDGE_FADE = 160;
    /**
     * Glaciers from the border mountains (decision 33): from GLACIER_START (depth into the border belt, negative inside
     * the region) a massif reaches out to the mountains (its outline grows by up to GLACIER_REACH blocks towards them),
     * and its surface rises towards them by GLACIER_RISE (field per block) from its top tier, so it comes down from the
     * mountains in benches and stepped walls; its sides climb like a massif's (WALL_RISE).
     */
    static final double GLACIER_START = -150, GLACIER_REACH = 260, GLACIER_RISE = 0.16, WALL_RISE = 0.6;
    /** On the mountain the glacier is a sheet of ice on the rock, SHEET_MIN-SHEET_MAX thick, gone CLIMB_MIN-CLIMB_MAX into the belt. */
    static final double SHEET_MIN = 10, SHEET_MAX = 22, CLIMB_MIN = 110, CLIMB_MAX = 190;
    /** Cliff tiers up to the high glacier level; one more (the summit tier) rises above it. */
    public static final int TIERS = 3;
    /** Height of a tier (before its slow variation of +/-20%), and of the summit tier. */
    private static final double TIER_MIN = 18, TIER_MAX = 28, SUMMIT_TIER = 18;
    /**
     * Each tier band: a bench rising gently (BENCH_RISE of the tier) towards the next cliff, snow banked at the
     * cliff's foot (TALUS), then the cliff, which takes CLIFF of the band. A cliff is a flat wall split into 2 or 3
     * even steps (decision 30): each a sheer piece of wall with a narrow ledge (LEDGE of the step) in front of it.
     */
    private static final double CLIFF = 0.25, BENCH_RISE = 0.06, TALUS = 0.12, LEDGE = 0.6;
    /**
     * Massifs: one site per cell of a grid this size (blocks), jittered over almost the whole cell; EMPTY of the
     * cells stay open ground.
     */
    static final int MASSIF_CELL = 300;
    private static final double EMPTY = 0.15, JITTER = 0.05;
    /** Massif radius as a share of the cell size, per massif (before its shape and lobes); small ones are more common. */
    private static final double RADIUS_MIN = 0.42, RADIUS_MAX = 0.92;
    /**
     * How much bigger massifs claim more room: a column belongs to the massif with the smallest distance minus
     * OWNERSHIP times its radius, so the dividing lines between massifs of different sizes curve.
     */
    private static final double OWNERSHIP = 0.5;
    /**
     * Steepest average climb (field per block) from a summit's outline to its top. A massif squeezed by its
     * neighbours or the river gets fewer tiers instead of steeper, taller walls.
     */
    private static final double RATE = 1.2;
    /**
     * Gentlest climb (field per block) from the outline: on a massif's far side the climb towards its summit would
     * otherwise be so slow that the cliffs smear into wide ramps and their lines fray.
     */
    private static final double MIN_RISE = 0.6;
    /** Room (share of the radius inside the outline) a summit needs before it stops moving back towards the site. */
    private static final double SUMMIT_ROOM = 0.5;
    /** Rounding of massif corners where the outline meets a gap or the river's strip (blocks). */
    private static final double ROUNDING = 22;
    /** Rounding of a massif's corners where its dividing lines meet (blocks), per massif. */
    private static final double CORNER_MIN = 22, CORNER_MAX = 42;
    /**
     * Narrow parts of a massif or saddle pull back: by PULL of how much their half width (between two of their
     * edges) falls short of NARROW, so pieces narrower than about 25 blocks vanish and spurs end in rounded noses,
     * while no wall gets more than a quarter steeper.
     */
    private static final double NARROW = 36, PULL = 0.5;
    /** How fast a saddle rises from its edge (field per block), varied per saddle. */
    private static final double SADDLE_RISE = 0.45;
    /** Sites of massifs farther than this from a column cannot reach it (blocks). */
    private static final double NEAR = 2 * MASSIF_CELL;
    /** Width (blocks beyond both outlines, and beyond the gap from their dividing line) of a saddle pass. */
    private static final double PASS = 30;
    /** Gap kept between two neighbouring massifs where their outlines would meet: half width per pair. */
    static final double GAP_MIN = 10, GAP_MAX = 30;
    /** Share of the gaps that go down to the ground; the rest are saddles at the first or second tier, evenly. */
    private static final double GROUND_GAPS = 0.6;
    /** Strip of ground on each side of the river centre line (half width, varying along it at two scales). */
    static final double FLOOR_HALF = 40, FLOOR_SWING = 14, FLOOR_WOBBLE = 6;
    /** Farthest the river's ground strip reaches from its course. */
    static final double RIVER_REACH = FLOOR_HALF + FLOOR_SWING + FLOOR_WOBBLE + 10;
    /** Frozen river: half width RIVER_MIN to RIVER_MAX. */
    private static final double RIVER_MIN = 6, RIVER_MAX = 12;
    /** Rock below the glacier: a few blocks under the ground; under ground that is not lowered, this deep. */
    private static final double BED_BELOW_FLOOR = 5, BED_DEPTH = 8;
    /** Spacing of course points along the course's axis. */
    public static final double STEP = 4;
    private final long seed;

    public HowlingCanyonTerrain(long seed) {
        this.seed = seed;
    }

    /**
     * @param height  surface height
     * @param bed     top of the rock beneath the glacier ice
     * @param feature feature code
     */
    public record Column(float height, float bed, int feature) {}

    /** The river's course: points {@link #STEP} blocks apart along its axis. */
    public record Course(double[] x, double[] z, double minX, double minZ, double maxX, double maxZ) {
        public static Course of(double[] x, double[] z) {
            double minX = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < x.length; i++) {
                minX = Math.min(minX, x[i]);
                maxX = Math.max(maxX, x[i]);
                minZ = Math.min(minZ, z[i]);
                maxZ = Math.max(maxZ, z[i]);
            }
            return new Course(x, z, minX, minZ, maxX, maxZ);
        }

        public int size() { return x.length; }
    }

    /**
     * The massif a column belongs to: how far it lies inside the massif's outline (negative outside), the massif's
     * base radius, how far the column lies inside a saddle pass at the first or second tier joining it to a
     * neighbour (negative outside; negative infinity where there is none, scaled by that saddle's steepness), and the
     * massif's summits ({@code second} null on massifs with a single top).
     */
    record Massif(int cellX, int cellZ, boolean empty, double inside, double radius, double pass1, double pass2,
                  double px, double pz, Summits summits) {}

    /**
     * A summit: where the massif's height climbs to (in the warped plane of {@link #massif}), how far it lies inside
     * the massif's outline (before the river's strip), the share of the climb after which the top is reached (the
     * rest is the flat top around the summit), the profile's curve (positive: steep foot and broad shoulders,
     * negative: gentle foot and steeper top) and, for the second summit, how many tiers it lies below the main one.
     */
    record Summit(double x, double z, double inside, double topShare, double curve, int drop) {}

    /** The summits of a massif ({@code second} null on massifs with a single top), cached per massif. */
    record Summits(int cellX, int cellZ, Summit main, Summit second) {}

    /** Massif summits by cell (direct-mapped): they depend only on the massif and its neighbours. */
    private final java.util.concurrent.atomic.AtomicReferenceArray<Summits> summits = new java.util.concurrent.atomic.AtomicReferenceArray<>(4096);

    /** Relief weight 0-1 from the distance to the biome's district boundary: 0 on it, 1 from DISTRICT_FADE inside. */
    public static double districtWeight(double edge) {
        return smoothstep(0, DISTRICT_FADE, edge);
    }

    /** 0 wherever the border mask is above 0 (edge distance below the border width), 1 from EDGE_FADE further in. */
    public static double edgeFade(double edgeDistance, int borderWidth) {
        return smoothstep(borderWidth, borderWidth + EDGE_FADE, edgeDistance);
    }

    /** @param courses the river's course(s) that may reach this column (in the world: the region's, {@link HowlingCanyonCourses}) */
    public Column column(int x, int z, double base, double weight, Course[] courses) {
        if (weight <= 0) return new Column((float) base, (float) (base - BED_DEPTH), PLATEAU);
        double[] levels = levels(x, z);

        // Ground strip along the river: the walls wander independently of the river, which stays on the ground.
        double river = Double.POSITIVE_INFINITY, strip = Double.POSITIVE_INFINITY;
        for (Course course : courses) {
            double[] near = nearest(course, x, z, RIVER_REACH + 200);
            if (near == null) continue;
            river = Math.min(river, Math.abs(near[0]));
            strip = Math.min(strip, strip(x, z, near[0]));
        }

        // Massif height: the first cliff at the massif's outline, then tier after tier up to its off-centre summit
        // (and a lower second summit on some massifs), steeper on the side nearer the summit.
        Massif massif = massif(x, z);
        double foot = levels[1] * (1 - CLIFF), smooth = 0;
        long key = 0;
        if (!massif.empty()) {
            int cellX = massif.cellX(), cellZ = massif.cellZ(), top = top(cellX, cellZ);
            key = (long) cellX * 7919 + cellZ;
            // Rounded where the outline meets the river's strip: no sharp wedges or thin fins of ice.
            double inside = stripCut(massif.inside(), strip);
            if (inside > 0) {
                // From the outline in: the first cliff's steps, then the benches and cliffs of the higher tiers.
                Summit main = massif.summits().main(), second = massif.summits().second();
                int mainTier = summitTier(main, top, courses);
                smooth = foot + climb(inside, main, massif, cap(levels, mainTier) - foot);
                int secondTier = second == null ? 0 : Math.min(mainTier - second.drop(), summitTier(second, top, courses));
                if (secondTier >= 1) smooth = Math.max(smooth, foot + climb(inside, second, massif, cap(levels, secondTier) - foot));
            }
        }
        // Saddles: passes at the first or second tier joining two massifs across their gap. They rise from their
        // edge (and from the river's strip) like a massif, in the same stepped cliffs, never as one tall sheer wall.
        for (int tier = 1; tier <= 2; tier++) {
            double pass = stripCut(tier == 1 ? massif.pass1() : massif.pass2(), strip);
            if (pass > 0) smooth = Math.max(smooth, Math.min(cap(levels, tier), foot + SADDLE_RISE * pass));
        }

        // Towards the biome edge the ground rises smoothly to the neighbouring ground (open, never stepped, so the
        // river runs out into the next biome) while the massifs above it get lower: the tiers are cut first and
        // then scaled, so their cliffs stay sheer instead of smearing into wide, ragged slopes. The last stretch
        // blends onto the ground exactly.
        double floorRise = (base - FLOOR_Y) * (1 - weight);
        double terraced = weight * terrace(smooth, levels, key);
        double relief = floorRise + terraced + weight * (2.5 * noise(x / 350.0, z / 350.0, 28) + noise(x / 30.0, z / 30.0, 31));
        double height = base + smoothstep(0, 0.3, weight) * (FLOOR_Y + relief - base);

        int feature = terraced < 3 ? FLOOR : PLATEAU;
        double riverHalf = RIVER_MIN + (RIVER_MAX - RIVER_MIN) * (0.5 + 0.5 * noise(x / 150.0, z / 150.0, 29));
        if (feature == FLOOR && river + 0.8 * noise(x / 3.0, z / 3.0, 32) < riverHalf) {
            // Frozen river: its ice a block below the snowy ground.
            height -= 1;
            feature = RIVER;
        }
        height = Math.max(LOWEST_SURFACE, height);
        double bed = base - BED_DEPTH + weight * (FLOOR_Y - BED_BELOW_FLOOR + 2 * noise(x / 90.0, z / 90.0, 30) - (base - BED_DEPTH));
        return new Column((float) height, (float) Math.min(bed, height - 4), feature);
    }

    /**
     * The massif (site of an irregular scatter) a column lies in. Each massif is an irregular blob around its site
     * whose radius varies with direction; {@code inside} is how far the column lies inside its outline, which is
     * also cut back GAP_MIN-GAP_MAX blocks from the curved dividing line with every neighbour, so two massifs never
     * merge. Where the gap between two massifs is a saddle, {@code pass1}/{@code pass2} are positive in the pass.
     */
    Massif massif(int x, int z) {
        return massif(x, z, 0);
    }

    /** The massif a column lies in, its outline reaching {@code outward} blocks further out (not into its neighbours). */
    Massif massif(int x, int z, double outward) {
        double px = warpX(x, z), pz = warpZ(x, z);
        int baseX = Math.floorDiv((int) Math.floor(px), MASSIF_CELL), baseZ = Math.floorDiv((int) Math.floor(pz), MASSIF_CELL);
        // Nearby massifs (sites within reach); the one with the smallest distance minus a share of its radius owns
        // the column, so bigger massifs claim more room and the dividing lines curve.
        Sites sites = new Sites();
        int owner = 0;
        for (int cz = baseZ - 2; cz <= baseZ + 2; cz++) {
            for (int cx = baseX - 2; cx <= baseX + 2; cx++) {
                double sx = siteX(cx, cz), sz = siteZ(cx, cz), d = Math.hypot(px - sx, pz - sz);
                if (d > NEAR) continue;
                int k = sites.add(cx, cz, sx, sz, radius(cx, cz));
                if (sites.claim(k, d) < sites.claim(owner, Math.hypot(px - sites.x[owner], pz - sites.z[owner]))) owner = k;
            }
        }
        double lobes = lobes(x, z);
        double[] distance = new double[sites.count], blob = new double[sites.count];
        for (int k = 0; k < sites.count; k++) {
            distance[k] = Math.hypot(px - sites.x[k], pz - sites.z[k]);
            blob[k] = blob(sites.cellX[k], sites.cellZ[k], sites.x[k], sites.z[k], px, pz, lobes);
        }
        // Saddle passes depend only on the two massifs they join (never on which massif owns the column), so they
        // are continuous everywhere: where both massifs come close, near their dividing line.
        double pass1 = Double.NEGATIVE_INFINITY, pass2 = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < sites.count; i++) {
            if (empty(sites.cellX[i], sites.cellZ[i])) continue;
            for (int j = i + 1; j < sites.count; j++) {
                int tier = saddle(sites, i, j);
                if (tier == 0 || empty(sites.cellX[j], sites.cellZ[j])) continue;
                double near = gap(sites, i, j, x, z) + PASS - Math.abs(sites.divide(px, pz, distance, i, j));
                double reach = Math.min(blob[i], blob[j]) + PASS;
                // Rounded where its sides meet, and pulled back where it narrows, like a massif's spur.
                double pass = cornerMin(reach, near, 2 * CORNER_MIN) - pullBack(reach, near);
                if (pass > 0) {
                    // Only between the two massifs it joins: a gap away from every other massif's (or empty cell's)
                    // ground, never as a free-standing ridge across someone else's ground.
                    double within = Double.POSITIVE_INFINITY;
                    for (int k = 0; k < sites.count; k++) {
                        if (k == i || k == j) continue;
                        within = Math.min(within, Math.max(sites.divide(px, pz, distance, i, k) - gap(sites, i, k, x, z),
                                sites.divide(px, pz, distance, j, k) - gap(sites, j, k, x, z)));
                    }
                    pass = cornerMin(pass, within, 2 * CORNER_MIN) - pullBack(pass, within);
                }
                // Each saddle climbs at its own pace.
                pass *= 1 + 0.4 * unit(hash(sites.pair(i, j), 0, 230));
                // Never above either massif's own top.
                int low = Math.min(top(sites.cellX[i], sites.cellZ[i]), top(sites.cellX[j], sites.cellZ[j]));
                if (low < tier) tier = low;
                if (tier == 1) pass1 = Math.max(pass1, pass);
                else if (tier == 2) pass2 = Math.max(pass2, pass);
            }
        }
        int ax = sites.cellX[owner], az = sites.cellZ[owner];
        double inside = sites.inside(owner, px, pz, distance, blob[owner] + outward, this, x, z);
        return new Massif(ax, az, empty(ax, az), inside, radius(ax, az), pass1, pass2, px, pz, empty(ax, az) ? null : summits(ax, az));
    }

    /** The summits of a massif (cached). */
    Summits summits(int cellX, int cellZ) {
        int slot = Math.floorMod(cellX * 7919 + cellZ * 104729, 4096);
        Summits cached = summits.get(slot);
        if (cached != null && cached.cellX() == cellX && cached.cellZ() == cellZ) return cached;
        // The massif and its neighbours, as a column of the massif would see them.
        Sites sites = new Sites();
        int owner = -1;
        double siteX = siteX(cellX, cellZ), siteZ = siteZ(cellX, cellZ);
        for (int cz = cellZ - 2; cz <= cellZ + 2; cz++) {
            for (int cx = cellX - 2; cx <= cellX + 2; cx++) {
                double sx = siteX(cx, cz), sz = siteZ(cx, cz);
                if (Math.hypot(siteX - sx, siteZ - sz) > NEAR + MASSIF_CELL) continue;
                int k = sites.add(cx, cz, sx, sz, radius(cx, cz));
                if (cx == cellX && cz == cellZ) owner = k;
            }
        }
        double angle = 2 * Math.PI * unit(hash(cellX, cellZ, 214));
        Summit main = summit(sites, owner, angle, 0.2 + 0.3 * unit(hash(cellX, cellZ, 215)),
                0.45 + 0.25 * unit(hash(cellX, cellZ, 220)), -0.2 + 0.7 * unit(hash(cellX, cellZ, 221)), 0);
        Summit second = null;
        // About half the massifs have a lower second top somewhere across from the main one.
        if (unit(hash(cellX, cellZ, 216)) < 0.45) {
            second = summit(sites, owner, angle + Math.PI + 2 * (unit(hash(cellX, cellZ, 217)) - 0.5), 0.3 + 0.3 * unit(hash(cellX, cellZ, 218)),
                    0.5 + 0.25 * unit(hash(cellX, cellZ, 231)), -0.2 + 0.7 * unit(hash(cellX, cellZ, 232)), unit(hash(cellX, cellZ, 219)) < 0.6 ? 1 : 2);
        }
        Summits result = new Summits(cellX, cellZ, main, second);
        summits.set(slot, result);
        return result;
    }

    /**
     * A summit of a massif, {@code reach} of the way from its site to its outline in direction {@code angle}. Where
     * a neighbour or the massif's own shape leaves too little room there, it tries other directions first (staying
     * off-centre), then moves in towards the site, so a squeezed massif is not reduced to one low tier.
     */
    private Summit summit(Sites sites, int owner, double angle, double reach, double topShare, double curve, int drop) {
        int cellX = sites.cellX[owner], cellZ = sites.cellZ[owner];
        Summit best = null;
        for (int attempt = 0; attempt < 19; attempt++) {
            // Six directions (the chosen one first, then turning further away from it) at full, then 70%, then 45%
            // of the reach; the site itself last.
            double share = attempt == 18 ? 0 : attempt < 6 ? 1 : attempt < 12 ? 0.7 : 0.45;
            int turn = attempt % 6;
            double direction = angle + (turn % 2 == 1 ? 1 : -1) * ((turn + 1) / 2) * Math.PI / 3;
            double out = share * reach * sites.radius[owner] * (1 + shape(cellX, cellZ, direction));
            double sx = sites.x[owner] + out * Math.cos(direction), sz = sites.z[owner] + out * Math.sin(direction);
            // How far the summit lies inside the outline, cut back from the neighbours like a column there.
            double[] distance = new double[sites.count];
            for (int k = 0; k < sites.count; k++) distance[k] = Math.hypot(sx - sites.x[k], sz - sites.z[k]);
            int ix = (int) Math.floor(sx), iz = (int) Math.floor(sz);
            double inside = sites.inside(owner, sx, sz, distance, blob(cellX, cellZ, sites.x[owner], sites.z[owner], sx, sz, lobes(ix, iz)),
                    this, ix, iz);
            if (best == null || inside > best.inside()) best = new Summit(sx, sz, inside, topShare, curve, drop);
            if (inside >= SUMMIT_ROOM * sites.radius[owner]) break;
        }
        return best;
    }

    /**
     * Highest tier a summit can reach (at most the massif's top tier) without its average climb exceeding RATE;
     * 0 if not even the first. Fixed per summit (levels and the river's strip are read at the summit itself).
     */
    private int summitTier(Summit summit, int top, Course[] courses) {
        double room = summit.inside();
        for (Course course : courses) {
            double[] near = nearest(course, summit.x(), summit.z(), RIVER_REACH + 200);
            if (near != null) room = stripCut(room, strip(summit.x(), summit.z(), near[0]));
        }
        double[] levels = levels((int) Math.floor(summit.x()), (int) Math.floor(summit.z()));
        double allowed = RATE * summit.topShare() * room, foot = levels[1] * (1 - CLIFF);
        int tier = top;
        while (tier >= 1 && cap(levels, tier) - foot > allowed) tier--;
        return summit.drop() == 0 ? Math.max(1, tier) : tier;
    }

    /**
     * Climb towards a summit: 0 at the outline, {@code height} on the flat top around the summit. The share of the
     * way is measured as inside / (inside + distance to the summit), so tier lines bunch on the side nearer the
     * summit and spread out on the far side instead of running parallel to the outline at even spacing. On the far
     * side the climb never gets gentler than MIN_RISE, so cliffs stay sheer and their lines clean.
     */
    private double climb(double inside, Summit summit, Massif massif, double height) {
        if (height <= 0) return 0;
        double distance = Math.hypot(massif.px() - summit.x(), massif.pz() - summit.z());
        double u = Math.min(1, inside / (inside + distance) / summit.topShare());
        // The gentlest climb varies along the outline too, so even there the benches are not evenly wide.
        double gentlest = MIN_RISE * (1 + 0.3 * noise(massif.px() / 140.0, massif.pz() / 140.0, 33));
        return Math.min(height, Math.max(height * (u + summit.curve() * u * (1 - u)), gentlest * inside));
    }

    /**
     * Surface of a glacier coming down from the border mountains (decision 33), or NaN where none: near the
     * mountains every massif reaches out to them and becomes the tongue of a glacier. Its surface rises from the
     * massif's top tier towards the mountains (benches and flat stepped walls, the canyon's own tiers), its sides and
     * front are the canyon walls, and on the mountain it lies as a sheet of ice on the unchanged rock, thinning out
     * unevenly up the slope. Gaps between massifs, empty cells and the river's strip stay open to the mountains.
     *
     * @param depth  how far the column lies into the border belt (negative inside the region)
     * @param rock   region terrain height (the ground, or the mountain rock)
     * @param weight relief weight at the biome's district edge (0 on it)
     */
    double glacier(int x, int z, double depth, double rock, double weight, Course[] courses) {
        if (depth < GLACIER_START || weight <= 0) return Double.NaN;
        Massif massif = massif(x, z, GLACIER_REACH * smoothstep(GLACIER_START, 30, depth));
        if (massif.empty()) return Double.NaN;
        double strip = Double.POSITIVE_INFINITY;
        for (Course course : courses) {
            double[] near = nearest(course, x, z, RIVER_REACH + 200);
            if (near != null) strip = Math.min(strip, strip(x, z, near[0]));
        }
        double inside = stripCut(massif.inside(), strip);
        if (inside <= 0) return Double.NaN;
        double[] levels = levels(x, z);
        int cellX = massif.cellX(), cellZ = massif.cellZ();
        long key = (long) cellX * 7919 + cellZ;
        // From the massif's top towards the mountains, the surface rises; the sides climb like a massif's walls.
        double rise = cap(levels, top(cellX, cellZ)) + GLACIER_RISE * (depth - GLACIER_START) * (1 + 0.3 * noise(x / 300.0, z / 300.0, 34));
        double field = Math.min(levels[1] * (1 - CLIFF) + WALL_RISE * inside, rise);
        double height = FLOOR_Y + weight * terrace(field, levels, key);
        if (depth > 0) {
            // On the mountain: a sheet of ice on the rock, thinning out unevenly up the slope; the glacier's body above
            // it thins out the same way (so it never fills a mountain valley beyond the sheet).
            double climb = CLIMB_MIN + (CLIMB_MAX - CLIMB_MIN) * (0.5 + 0.5 * noise(x / 260.0, z / 260.0, 35));
            double thinning = 1 - smoothstep(40, climb, depth);
            double sheet = (SHEET_MIN + (SHEET_MAX - SHEET_MIN) * (0.5 + 0.5 * noise(x / 110.0, z / 110.0, 36)))
                    * thinning * smoothstep(0, 16, inside) * weight;
            height = Math.max(rock + sheet, Math.min(height, rock + 90 * thinning));
        }
        return height;
    }

    /**
     * A massif or saddle cut back from the river's ground strip: rounded where they meet, and pulled back where only
     * a narrow piece would be left between its own edge and the strip, so no sliver or lone knob stands by the river.
     */
    private static double stripCut(double inside, double strip) {
        if (strip == Double.POSITIVE_INFINITY) return inside;
        return softMin(inside, strip) - pullBack(inside, strip);
    }

    /** How far a piece between two edges (distances a and b inside them) pulls back because it is narrow. */
    private static double pullBack(double a, double b) {
        return PULL * Math.max(0, NARROW - (a + b) / 2);
    }

    /** Signed distance (blocks) outside the river's ground strip (negative on the strip), from a signed distance to its course. */
    private double strip(double x, double z, double fromCourse) {
        double floorHalf = FLOOR_HALF + FLOOR_SWING * noise(x / 330.0, z / 330.0, 20) + FLOOR_WOBBLE * noise(x / 120.0, z / 120.0, 23);
        double shift = 0.5 * Math.max(0, floorHalf - RIVER_MAX - 4) * noise(x / 260.0, z / 260.0, 22);
        return Math.abs(fromCourse + shift) - floorHalf;
    }

    /** Warped plane the massifs are laid out in: broad bends plus smaller ones, so no outline or gap runs straight. */
    private double warpX(int x, int z) {
        return x + 55 * fbm(x, z, 320, 2, 50) + 12 * noise(x / 110.0, z / 110.0, 54);
    }

    private double warpZ(int x, int z) {
        return z + 55 * fbm(x, z, 320, 2, 52) + 12 * noise(x / 110.0, z / 110.0, 55);
    }

    /**
     * Lobes and bays of all outlines, in blocks (the same for big and small massifs, and never as steep as the
     * outline itself, so no outline flattens into a broad, frayed edge). No fine ripples: on the narrow crest of a
     * ridge they would fray the cliff lines.
     */
    private double lobes(int x, int z) {
        return 30 * fbm(x, z, 170, 2, 57);
    }

    /** Massif sites near a column, with the pair-wise measures of the gaps between them. */
    private static final class Sites {
        final int[] cellX = new int[25], cellZ = new int[25];
        final double[] x = new double[25], z = new double[25], radius = new double[25];
        int count;

        int add(int cellX, int cellZ, double x, double z, double radius) {
            this.cellX[count] = cellX;
            this.cellZ[count] = cellZ;
            this.x[count] = x;
            this.z[count] = z;
            this.radius[count] = radius;
            return count++;
        }

        /** Ownership measure: distance minus a share of the radius (smallest wins). */
        double claim(int k, double distance) {
            return distance - OWNERSHIP * radius[k];
        }

        /**
         * Signed distance (about one per block, also far out along the line) from the curved dividing line between
         * massifs i and j, positive on i's side: the difference of their claims over the length of its gradient.
         */
        double divide(double px, double pz, double[] distance, int i, int j) {
            double di = Math.max(1e-6, distance[i]), dj = Math.max(1e-6, distance[j]);
            double gx = (px - x[j]) / dj - (px - x[i]) / di, gz = (pz - z[j]) / dj - (pz - z[i]) / di;
            return (claim(j, distance[j]) - claim(i, distance[i])) / Math.max(0.2, Math.hypot(gx, gz));
        }

        /**
         * How far (px, pz) lies inside the owner's outline: its own blob, cut back from the dividing line with every
         * neighbour by that pair's gap (all neighbours, not only the nearest, whose switch made the field jump into
         * tall sheer walls). Corners are rounded, and narrow parts pull back ({@link #pullBack}), so spurs end in
         * rounded noses instead of needle points or thin fins.
         */
        double inside(int owner, double px, double pz, double[] distance, double blob, HowlingCanyonTerrain terrain, double atX, double atZ) {
            // The two nearest dividing lines (independent of the order of the neighbours).
            double first = Double.POSITIVE_INFINITY, second = Double.POSITIVE_INFINITY;
            for (int k = 0; k < count; k++) {
                if (k == owner) continue;
                double cut = divide(px, pz, distance, owner, k) - terrain.gap(this, owner, k, atX, atZ);
                if (cut < first) {
                    second = first;
                    first = cut;
                } else if (cut < second) {
                    second = cut;
                }
            }
            double rounding = 2 * terrain.corner(cellX[owner], cellZ[owner]);
            double inside = cornerMin(cornerMin(first, second, rounding), blob, rounding);
            // Half the width between the two nearest edges (of the blob and the dividing lines).
            return inside - (blob >= second ? pullBack(first, second) : pullBack(first, blob));
        }

        /** Pair key, the same for both massifs of a pair. */
        int pair(int i, int j) {
            int a = cellX[i] * 7919 + cellZ[i], b = cellX[j] * 7919 + cellZ[j];
            return Math.min(a, b) * 31 + Math.max(a, b);
        }
    }

    /** Half width of the gap between two massifs, widening and narrowing along it. */
    private double gap(Sites sites, int i, int j, double x, double z) {
        return GAP_MIN + (GAP_MAX - GAP_MIN) * unit(hash(sites.pair(i, j), 0, 205))
                + 4 * noise(x / 80.0, z / 80.0, 56);
    }

    /** Whether the gap between two massifs is open ground (0) or a saddle at the first or second tier. */
    private int saddle(Sites sites, int i, int j) {
        double kind = unit(hash(sites.pair(i, j), 0, 206));
        return kind < GROUND_GAPS ? 0 : kind < GROUND_GAPS + (1 - GROUND_GAPS) / 2 ? 1 : 2;
    }

    /**
     * How far (px, pz) lies inside a cell's blob (negative outside): the distance from the site, measured against a
     * radius that varies with direction ({@link #shape}) and with the lobes of the land.
     */
    private double blob(int cellX, int cellZ, double siteX, double siteZ, double px, double pz, double lobes) {
        double dx = px - siteX, dz = pz - siteZ;
        return radius(cellX, cellZ) + lobes - Math.hypot(dx, dz) / (1 + shape(cellX, cellZ, Math.atan2(dz, dx)));
    }

    /**
     * Relative change of a massif's radius with direction, per massif: lopsided (egg-shaped), elongated, three- and
     * four-lobed at random strengths and orientations, so no two outlines are alike and none is a circle or ellipse.
     */
    double shape(int cellX, int cellZ, double angle) {
        double sum = 0;
        double[] strength = {0.12, 0.22, 0.1, 0.06};
        for (int k = 0; k < strength.length; k++) {
            double amplitude = strength[k] * (k == 1 ? 0.3 + 0.7 * unit(hash(cellX, cellZ, 222 + k)) : unit(hash(cellX, cellZ, 222 + k)));
            sum += amplitude * Math.cos((k + 1) * angle - 2 * Math.PI * unit(hash(cellX, cellZ, 226 + k)));
        }
        return sum;
    }

    /**
     * Smooth minimum: like the smaller value, but rounded over ROUNDING blocks where both are close, so outlines cut
     * by a gap or the river's strip get rounded corners and slivers narrower than about ROUNDING vanish.
     */
    static double softMin(double a, double b) {
        return softMin(a, b, ROUNDING);
    }

    /** Smooth minimum rounded over {@code rounding} blocks. */
    static double softMin(double a, double b, double rounding) {
        if (!(Math.abs(a - b) <= 8 * rounding)) return Math.min(a, b);
        double low = Math.min(a, b);
        return low - rounding * Math.log(Math.exp((low - a) / rounding) + Math.exp((low - b) / rounding));
    }

    /**
     * Smooth minimum that only rounds where both values are within {@code rounding} of each other (a corner) and is
     * exactly the smaller value elsewhere, so it rounds corners without shrinking the rest of an outline.
     */
    static double cornerMin(double a, double b, double rounding) {
        double difference = Math.abs(a - b);
        if (!(difference < rounding)) return Math.min(a, b);
        double h = (rounding - difference) / rounding;
        return Math.min(a, b) - h * h * rounding / 4;
    }

    /**
     * Rounding (blocks) of a massif's corners where its dividing lines meet each other or its own outline: broad and
     * different per massif, so no massif ends in a polygon corner.
     */
    private double corner(int cellX, int cellZ) {
        return CORNER_MIN + (CORNER_MAX - CORNER_MIN) * unit(hash(cellX, cellZ, 233));
    }

    /** Base radius of a massif: RADIUS_MIN-RADIUS_MAX of the cell size, small ones more common than big ones. */
    double radius(int cellX, int cellZ) {
        return MASSIF_CELL * (RADIUS_MIN + (RADIUS_MAX - RADIUS_MIN) * Math.pow(unit(hash(cellX, cellZ, 209)), 1.4));
    }

    /** Gap tier at a column (0: ground, 1-2: saddle pass), or -1 on a massif or away from the gaps between two (tests). */
    int gapTier(int x, int z) {
        Massif massif = massif(x, z);
        if (massif.empty() || massif.inside() > 0) return -1;
        if (massif.pass2() > 0) return 2;
        if (massif.pass1() > 0) return 1;
        return massif.inside() > -40 ? 0 : -1;
    }

    boolean empty(int cellX, int cellZ) {
        return unit(hash(cellX, cellZ, 200)) < EMPTY;
    }

    /** Top tier of a massif: 1 (one tier) to TIERS + 1 (summit tier), mostly two or three. */
    private int top(int cellX, int cellZ) {
        double u = unit(hash(cellX, cellZ, 201));
        return u < 0.1 ? 1 : u < 0.4 ? 2 : u < 0.8 ? 3 : 4;
    }

    /** Height of the flat top at a tier: on the bench, before the next riser. */
    private static double cap(double[] levels, int tier) {
        return tier >= levels.length - 1 ? levels[levels.length - 1] + 3 : levels[tier] + 0.35 * (levels[tier + 1] - levels[tier]);
    }

    double siteX(int cellX, int cellZ) {
        return (cellX + JITTER + (1 - 2 * JITTER) * unit(hash(cellX, cellZ, 203))) * MASSIF_CELL;
    }

    double siteZ(int cellX, int cellZ) {
        return (cellZ + JITTER + (1 - 2 * JITTER) * unit(hash(cellX, cellZ, 204))) * MASSIF_CELL;
    }

    /**
     * Tier levels above the ground: TIERS cliff tiers, then the summit tier. Tier heights differ per world and
     * drift across the land at two scales (about +/-25%), so neighbouring massifs have different tiers, benches
     * tilt and a cliff changes height along its length.
     */
    double[] levels(int x, int z) {
        double[] levels = new double[TIERS + 2];
        for (int k = 0; k <= TIERS; k++) {
            double tier = k == TIERS ? SUMMIT_TIER : TIER_MIN + (TIER_MAX - TIER_MIN) * unit(hash(k, 0, 40));
            double drift = 0.17 * noise(x / 650.0, z / 650.0, 41 + k) + 0.08 * noise(x / 230.0, z / 230.0, 45 + k);
            levels[k + 1] = levels[k] + tier * (1 + drift);
        }
        return levels;
    }

    /**
     * Terrace: within each tier band a gently rising bench with snow banked at the foot of the next cliff, then the
     * cliff: a flat wall in 2 or 3 even steps (per massif and tier, {@code key}), each a sheer piece of wall above a
     * narrow ledge. The ground below the first cliff stays flat. Below the ground and above the summit tier the
     * field is only flattened.
     */
    double terrace(double value, double[] levels, long key) {
        if (value <= 0) return 0.35 * value;
        int top = levels.length - 1;
        if (value >= levels[top]) return levels[top] + 0.35 * (value - levels[top]);
        int k = 0;
        while (value >= levels[k + 1]) k++;
        double height = levels[k + 1] - levels[k], f = (value - levels[k]) / height, bench = 1 - CLIFF;
        if (f < bench) {
            if (k == 0) return 0;
            double t = f / bench, talus = smoothstep(0.55, 1, t);
            return levels[k] + height * (BENCH_RISE * t + TALUS * talus * talus);
        }
        int steps = cliffSteps(key, k);
        double start = k == 0 ? 0 : BENCH_RISE + TALUS, along = (f - bench) / CLIFF * steps;
        int step = Math.min(steps - 1, (int) along);
        double rise = (step + smoothstep(LEDGE, 1, along - step)) / steps;
        return levels[k] + height * (start + (1 - start) * rise);
    }

    /** Steps in a massif's cliff up to tier k + 1: 2 or 3, the same all along that cliff. */
    int cliffSteps(long key, int k) {
        return unit(hash((int) key, (int) (key >>> 32) * 31 + k, 213)) < 0.5 ? 2 : 3;
    }

    /**
     * Nearest point of a course within {@code reach}: {signed distance (positive on the course's left), fractional
     * point index}, or null if the course is farther away.
     */
    static double[] nearest(Course course, double x, double z, double reach) {
        if (x < course.minX() - reach || x > course.maxX() + reach || z < course.minZ() - reach || z > course.maxZ() + reach) {
            return null;
        }
        double[] px = course.x(), pz = course.z();
        int best = -1;
        double bestSquare = reach * reach;
        for (int i = 0; i < px.length; i++) {
            double square = (x - px[i]) * (x - px[i]) + (z - pz[i]) * (z - pz[i]);
            if (square < bestSquare) {
                bestSquare = square;
                best = i;
            }
        }
        if (best < 0) return null;
        // Exact distance to the two segments around the nearest point.
        double distance = Double.POSITIVE_INFINITY, side = 1, index = best;
        for (int i = Math.max(0, best - 1); i <= Math.min(px.length - 2, best); i++) {
            double dx = px[i + 1] - px[i], dz = pz[i + 1] - pz[i];
            double t = Math.clamp(((x - px[i]) * dx + (z - pz[i]) * dz) / (dx * dx + dz * dz), 0, 1);
            double d = Math.hypot(x - px[i] - t * dx, z - pz[i] - t * dz);
            if (d < distance) {
                distance = d;
                side = dx * (z - pz[i]) - dz * (x - px[i]) >= 0 ? 1 : -1;
                index = i + t;
            }
        }
        return new double[] {distance * side, index};
    }

    double fbm(double x, double z, double scale, int octaves, int salt) {
        double sum = 0, amplitude = 1, normalization = 0;
        for (int octave = 0; octave < octaves; octave++) {
            sum += amplitude * noise(x / scale, z / scale, salt + octave);
            normalization += amplitude;
            amplitude *= 0.5;
            scale *= 0.5;
        }
        return sum / normalization;
    }

    /** 2D gradient noise, roughly -1..1. */
    double noise(double x, double z, int salt) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double dx = x - ix, dz = z - iz;
        double tx = fade(dx), tz = fade(dz);
        double a = lerp(tx, gradient(hash(ix, iz, salt), dx, dz), gradient(hash(ix + 1, iz, salt), dx - 1, dz));
        double b = lerp(tx, gradient(hash(ix, iz + 1, salt), dx, dz - 1), gradient(hash(ix + 1, iz + 1, salt), dx - 1, dz - 1));
        return Math.clamp(lerp(tz, a, b) * 1.4, -1, 1);
    }

    private static double gradient(long hash, double x, double z) {
        double angle = unit(hash) * Math.PI * 2;
        return Math.cos(angle) * x + Math.sin(angle) * z;
    }

    long hash(int x, int z, int salt) {
        long h = seed ^ ((long) x * 0x632BE59BD9B4E019L) ^ ((long) z * 0x9E3779B97F4A7C15L) ^ ((long) salt * 0xD1B54A32D192ED03L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    static double unit(long hash) { return (hash >>> 11) * 0x1.0p-53; }
    static double smoothstep(double low, double high, double value) {
        double t = Math.clamp((value - low) / (high - low), 0, 1);
        return t * t * (3 - 2 * t);
    }
    private static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    private static double lerp(double t, double a, double b) { return a + t * (b - a); }
}
