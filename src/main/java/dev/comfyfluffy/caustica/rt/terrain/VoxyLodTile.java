package dev.comfyfluffy.caustica.rt.terrain;

/** A stable 64³ block tile. Distances are horizontal and expressed in world blocks. */
record VoxyLodTile(int lod, int west, int east, int north, int south, int clipX, int clipZ, int radius) {
    static final long MARKER = Long.MIN_VALUE;
    static long key(int sx, int sy, int sz) {
        return MARKER | RtTerrain.sectionKey(sx & ~3, sy & ~3, sz & ~3);
    }
    static int bit(int sx, int sy, int sz) {
        return (sx & 3) | ((sz & 3) << 2) | ((sy & 3) << 4);
    }
    static VoxyLodTile select(int x, int z, int cx, int cz, int radius) {
        int dx = Math.max(Math.max(x - cx, cx - (x + 64)), 0);
        int dz = Math.max(Math.max(z - cz, cz - (z + 64)), 0);
        long r2 = (long) radius * radius;
        if ((long)dx*dx + (long)dz*dz >= r2) return null;
        int mx = x + 32 - cx, mz = z + 32 - cz;
        long farX = Math.abs(mx) + 32L, farZ = Math.abs(mz) + 32L;
        boolean edge = farX*farX + farZ*farZ > r2;
        return new VoxyLodTile(level(mx,mz),level(mx-64,mz),level(mx+64,mz),
                level(mx,mz-64),level(mx,mz+64),edge ? cx : 0, edge ? cz : 0, edge ? radius : 0);
    }
    private static int level(int dx, int dz) {
        long squared = (long) dx * dx + (long) dz * dz;
        if (squared < 192L*192) return 1;
        if (squared < 384L*384) return 2;
        if (squared < 768L*768) return 3;
        return 4;
    }
    int sampleLod(int x, int z) {
        if (x < 0) return west;
        if (x >= 64) return east;
        if (z < 0) return north;
        if (z >= 64) return south;
        return lod;
    }
    boolean contains(int x, int z) {
        return radius == 0 || withinRadius(x, z, clipX, clipZ, radius);
    }
    static boolean withinRadius(int x, int z, int cx, int cz, int radius) {
        long dx = (long)x - cx, dz = (long)z - cz;
        return dx * dx + dz * dz <= (long)radius * radius;
    }
    int gridLod() {
        return Math.min(lod, Math.min(Math.min(west,east), Math.min(north,south)));
    }
}
