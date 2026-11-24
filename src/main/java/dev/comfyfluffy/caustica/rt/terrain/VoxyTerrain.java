package dev.comfyfluffy.caustica.rt.terrain;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.multiplayer.ClientLevel;

/** Optional entry point. Only the Vulkan startup hook loads the version-specific Voxy adapter. */
public abstract class VoxyTerrain {
    static final RtTerrainMesher.CpuSection MISSING = new RtTerrainMesher.CpuSection(null, null);
    private static boolean dataOnly;

    public static void initializeStorage() {
        dataOnly = true;
        VoxyWorldTerrain.initialize();
    }

    public static boolean dataOnly() {
        return dataOnly;
    }

    static VoxyTerrain current(ClientLevel level, VoxyTerrain previous) {
        return dataOnly ? VoxyWorldTerrain.currentWorld(level, previous) : null;
    }

    abstract LongSet recorded(int cx, int cz, int radius, int loY, int hiY);
    abstract RtTerrainMesher.CpuSection meshTile(int sx, int sy, int sz, VoxyLodTile tile, int waterMaterial);
    abstract int radiusChunks();
    abstract RtTerrainMesher.CpuSection mesh(int sx, int sy, int sz, int waterMaterial);
}
