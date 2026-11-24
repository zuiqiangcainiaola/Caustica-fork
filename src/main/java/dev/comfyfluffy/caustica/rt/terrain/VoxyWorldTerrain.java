package dev.comfyfluffy.caustica.rt.terrain;

import dev.comfyfluffy.caustica.CausticaMod;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import me.cortex.voxy.client.VoxyClientInstance;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.other.Mapper;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.cortex.voxy.commonImpl.VoxyInstance;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.material.MapColor;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

final class VoxyWorldTerrain extends VoxyTerrain {
    private static FileLock exclusiveLock;
    private static final int[] AIR = {0};
    private record Tile(int lod, int x, int y, int z) {}
    private record Snapshot(int[] colors, long timestamp) {}

    private final WorldIdentifier id;
    private final VoxyInstance instance;
    private final LinkedHashMap<Tile, Snapshot> cache = new LinkedHashMap<>(128, 0.75f, true);
    private final Int2IntOpenHashMap palette = new Int2IntOpenHashMap();

    private final Registry<Biome> biomes;
    private final Int2IntOpenHashMap waterTints = new Int2IntOpenHashMap();
    private Mapper.BiomeEntry[] biomeEntries = {};

    private VoxyWorldTerrain(WorldIdentifier id, VoxyInstance instance, Registry<Biome> biomes) {
        this.biomes = biomes;
        this.id = id;
        this.instance = instance;
    }

    static void initialize() {
        // Honour the same optional process-wide database lock as VoxyClient.initVoxyClient.
        if (Boolean.getBoolean("voxy.exclusiveLock")) {
            try {
                var dir = Minecraft.getInstance().gameDirectory.toPath().resolve(".voxy");
                Files.createDirectories(dir);
                var channel = FileChannel.open(dir.resolve("voxy.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                try {
                    exclusiveLock = channel.lock();
                } catch (IOException | RuntimeException e) {
                    channel.close();
                    throw e;
                }
            } catch (IOException | RuntimeException e) {
                CausticaMod.LOGGER.error("Could not lock Voxy storage", e);
                return;
            }
        }
        VoxyCommon.setInstanceFactory(VoxyClientInstance::new);
        CausticaMod.LOGGER.info("Voxy 0.2.19-beta storage enabled for Vulkan terrain");
    }

    static VoxyTerrain currentWorld(ClientLevel level, VoxyTerrain previous) {
        var config = VoxyConfig.CONFIG;
        var instance = VoxyCommon.getInstance();
        if (!config.enabled || !config.enableRendering || instance == null || !instance.isRunning()) return null;
        var id = WorldIdentifier.of(level);
        if (id == null) return null;
        if (previous instanceof VoxyWorldTerrain old && old.instance == instance && old.id.equals(id)) return old;
        return new VoxyWorldTerrain(id, instance, level.registryAccess().lookupOrThrow(Registries.BIOME));
    }

    private record Roots(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {}
    private Roots requested;
    private CompletableFuture<LongSet> discovery;
    private LongSet recorded = LongSet.of();
    private long lastDiscovery;

    @Override LongSet recorded(int cx, int cz, int radius, int loY, int hiY) {
        var roots = new Roots((cx - radius) >> 5, (cx + radius) >> 5, loY >> 5, hiY >> 5,
                (cz - radius) >> 5, (cz + radius) >> 5);
        if (discovery != null && discovery.isDone()) {
            recorded = discovery.join();
            discovery = null;
        }
        if (discovery == null && (!roots.equals(requested) || System.nanoTime() - lastDiscovery > 30_000_000_000L)) {
            requested = roots;
            lastDiscovery = System.nanoTime();
            discovery = CompletableFuture.supplyAsync(() -> discover(roots), RtWorkerPool.INSTANCE::submit);
        }
        return recorded;
    }

    private LongSet discover(Roots roots) {
        var keys = new LongOpenHashSet();
        var engine = instance.getOrCreate(id, true);
        if (engine == null) return keys;
        try {
            for (int x = roots.minX; x <= roots.maxX && instance.isRunning(); x++) {
                for (int z = roots.minZ; z <= roots.maxZ; z++) {
                    for (int y = roots.minY; y <= roots.maxY; y++) {
                        collect(engine, WorldEngine.MAX_LOD_LAYER, x, y, z, keys);
                    }
                }
            }
        } finally {
            engine.releaseRef();
        }
        return keys;
    }

    private static void collect(WorldEngine engine, int lod, int x, int y, int z, LongSet keys) {
        if (lod == 1) {
            keys.add(VoxyLodTile.key(x << 2, y << 2, z << 2));
            return;
        }
        var section = engine.acquireIfExists(lod, x, y, z);
        if (section == null) return;
        int children;
        try {
            children = Byte.toUnsignedInt(section.getNonEmptyChildren());
        } finally {
            section.release();
        }
        for (int i = 0; i < 8; i++) {
            if ((children & (1 << i)) != 0) {
                collect(engine, lod - 1, x * 2 + (i & 1), y * 2 + (i >> 2), z * 2 + ((i >> 1) & 1), keys);
            }
        }
    }

    @Override RtTerrainMesher.CpuSection meshTile(int sx, int sy, int sz, VoxyLodTile plan, int waterMaterial) {
        var engine = instance.getOrCreate(id, true);
        if (engine == null) return MISSING;
        try {
            int grid = 1 << plan.gridLod();
            var pages = new TilePages(engine);
            var packed = VoxySurface.mesh(64 / grid, grid, true, waterMaterial, (x, y, z) -> {
                int dx = x * grid, dz = z * grid;
                int bx = (sx << 4) + dx, by = (sy << 4) + y * grid, bz = (sz << 4) + dz;
                if (!plan.contains(bx + grid / 2, bz + grid / 2)) return 0;
                int preferred = plan.sampleLod(dx, dz);
                // Resolve the nearest available stored level. Keep missing pages in the per-job map.
                for (int n = 0; n <= WorldEngine.MAX_LOD_LAYER; n++) {
                    int lod = preferred + n <= WorldEngine.MAX_LOD_LAYER ? preferred + n : WorldEngine.MAX_LOD_LAYER - n;
                    int cell = pages.cell(lod, bx, by, bz);
                    if (cell == TilePages.ABSENT) continue;
                    return VoxySurface.isWater(cell) ? pages.waterCell(lod, bx, by, bz, grid, cell) : cell;
                }
                return 0;
            });
            return new RtTerrainMesher.CpuSection(packed, null);
        } finally {
            engine.releaseRef();
        }
    }

    /** Per-mesh page cache, including misses. Refine only the elevation of recorded water. */
    private final class TilePages {
        // Solid colors always have alpha FF, water heights use 01..90, and air is zero.
        static final int ABSENT = 0x010000;
        private final WorldEngine engine;
        private final HashMap<Tile, int[]> pages = new HashMap<>();

        TilePages(WorldEngine engine) {
            this.engine = engine;
        }

        int cell(int lod, int x, int y, int z) {
            int shift = 5 + lod;
            var key = new Tile(lod, x >> shift, y >> shift, z >> shift);
            if (!pages.containsKey(key)) pages.put(key, read(engine, key));
            var page = pages.get(key);
            return page == null ? ABSENT : sample(page, x >> lod, y >> lod, z >> lod);
        }

        int waterCell(int lod, int x, int y, int z, int grid, int cell) {
            int width = 1 << lod;
            int bottom = y & -width;
            int surface = (bottom + width - 1) * 9 + (cell >>> 24);
            int above = cell(lod, x, bottom + width, z);
            if (above != ABSENT && above != 0) return VoxySurface.water(cell, grid * 9);

            // Horizontal shape stays at the selected LOD. At a free surface the representative
            // coarse cell can span both air and water. Recover its recorded level at the column centre.
            int cx = (x & -width) + width / 2, cz = (z & -width) + width / 2;
            for (int dy = width - 1; dy >= 0 && lod > 0; dy--) {
                int fine = cell(0, cx, bottom + dy, cz);
                if (fine == ABSENT) break;
                if (VoxySurface.isWater(fine)) {
                    surface = (bottom + dy) * 9 + (fine >>> 24);
                    break;
                }
            }
            int height = Math.min(grid * 9, surface - y * 9);
            return height <= 0 ? 0 : VoxySurface.water(cell, height);
        }
    }

    @Override int radiusChunks() {
        return Math.max(0, Math.round(VoxyConfig.CONFIG.sectionRenderDistance * 32));
    }

    @Override RtTerrainMesher.CpuSection mesh(int sx, int sy, int sz, int waterMaterial) {
        var engine = instance.getOrCreate(id, true);
        if (engine == null) return MISSING;
        int[][] fine;
        int[][] coarse;
        try {
            var fineCenter = read(engine, new Tile(0, sx >> 1, sy >> 1, sz >> 1));
            var coarseCenter = read(engine, new Tile(1, sx >> 2, sy >> 2, sz >> 2));
            if (fineCenter == null && coarseCenter == null) return MISSING;
            if ((fineCenter == null || fineCenter == AIR) && (coarseCenter == null || coarseCenter == AIR))
                return new RtTerrainMesher.CpuSection(null, null);
            fine = neighborhood(engine, 0, sx, sy, sz);
            coarse = neighborhood(engine, 1, sx, sy, sz);
        } finally {
            engine.releaseRef();
        }
        var packed = VoxySurface.mesh(16, 1, false, waterMaterial, (x,y,z) -> {
            int bx = (sx << 4) + x, by = (sy << 4) + y, bz = (sz << 4) + z;
            int[] block = fine[slot(bx >> 5, by >> 5, bz >> 5)];
            int[] lod = coarse[slot(bx >> 6, by >> 6, bz >> 6)];
            // Keep the outermost block layer at recorded block resolution on both sides of each
            // section boundary. A fine near mesh and its coarse replacement then meet on the same grid.
            int detailed = sample(block, bx, by, bz);
            int reduced = sample(lod, bx >> 1, by >> 1, bz >> 1);
            if (block != null && (lod == null || boundary(bx) || boundary(by) || boundary(bz)
                    || VoxySurface.isWater(detailed) || VoxySurface.isWater(reduced))) return detailed;
            return reduced;
        });
        return new RtTerrainMesher.CpuSection(packed, null);
    }

    private static boolean boundary(int coordinate) {
        int local = coordinate & 15;
        return local == 0 || local == 15;
    }

    private static int sample(int[] colors, int x, int y, int z) {
        if (colors == null) return 0;
        return colors.length == 1 ? colors[0] : colors[(x & 31) | ((z & 31) << 5) | ((y & 31) << 10)];
    }

    private static int slot(int x, int y, int z) {
        return (x & 1) | ((z & 1) << 1) | ((y & 1) << 2);
    }

    private int[][] neighborhood(WorldEngine engine, int lod, int sx, int sy, int sz) {
        int shift = 5 + lod;
        int ox = sx << 4, oy = sy << 4, oz = sz << 4;
        int[][] result = new int[8][];
        for (int y = (oy - 1) >> shift; y <= (oy + 16) >> shift; y++)
            for (int z = (oz - 1) >> shift; z <= (oz + 16) >> shift; z++)
                for (int x = (ox - 1) >> shift; x <= (ox + 16) >> shift; x++)
                    result[slot(x,y,z)] = read(engine, new Tile(lod, x, y, z));
        return result;
    }

    private synchronized int[] read(WorldEngine engine, Tile key) {
        long now = System.nanoTime();
        var cached = cache.get(key);
        if (cached != null && now - cached.timestamp < 10_000_000_000L) return cached.colors;
        var section = engine.acquireIfExists(key.lod, key.x, key.y, key.z);
        int[] colors = null;
        if (section != null) {
            long[] data;
            try {
                data = section.copyData();
            } finally {
                section.release();
            }
            colors = new int[data.length];
            boolean uniform = true;
            for (int i = 0; i < data.length; i++) {
                int block = Mapper.getBlockId(data[i]);
                colors[i] = block == 0 ? 0 : palette.computeIfAbsent(block, b -> color(engine.getMapper(), b));
                if (VoxySurface.isWater(colors[i])) {
                    int biome = Mapper.getBiomeId(data[i]);
                    int tint = waterTints.computeIfAbsent(biome, b -> waterTint(engine.getMapper(), b));
                    colors[i] = VoxySurface.water(tint, colors[i] >>> 24);
                }
                uniform &= colors[i] == colors[0];
            }
            if (uniform) colors = colors[0] == 0 ? AIR : new int[]{colors[0]};
        }
        cache.put(key, new Snapshot(colors, now));
        if (cache.size() > 192) cache.pollFirstEntry();
        return colors;
    }

    private int waterTint(Mapper mapper, int id) {
        if (id >= biomeEntries.length) biomeEntries = mapper.getBiomeEntries();
        if (id < biomeEntries.length && biomeEntries[id] != null) {
            var key = Identifier.tryParse(biomeEntries[id].biome);
            var biome = key == null ? null : biomes.getValue(key);
            if (biome != null) return biome.getWaterColor();
        }
        return 0x3f76e4;
    }

    private static int color(Mapper mapper, int block) {
        var state = mapper.getBlockStateFromBlockId(block);
        if (state.isAir() || (state.getRenderShape() == net.minecraft.world.level.block.RenderShape.INVISIBLE
                && state.getFluidState().isEmpty())) return 0;
        var fluid = state.getFluidState();
        if (fluid.is(FluidTags.WATER) && state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty())
            return VoxySurface.water(0, Math.round(fluid.getOwnHeight() * 9));
        var color = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        return 0xff000000 | (color == MapColor.NONE ? 0xa5a7a4 : color.col);
    }
}
