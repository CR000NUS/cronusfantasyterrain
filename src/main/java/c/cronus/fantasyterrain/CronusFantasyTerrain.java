package c.cronus.fantasyterrain;

import c.cronus.fantasyterrain.worldgen.RegionWorldgen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Mod entry point; the Fantasy Regions world generation lives in the worldgen package. */
@Mod(CronusFantasyTerrain.MODID)
public class CronusFantasyTerrain {
    /** Must match the mod id in META-INF/neoforge.mods.toml (gradle.properties mod_id). */
    public static final String MODID = "cronusfantasyterrain";

    public CronusFantasyTerrain(IEventBus modEventBus) {
        RegionWorldgen.register(modEventBus);
    }
}
