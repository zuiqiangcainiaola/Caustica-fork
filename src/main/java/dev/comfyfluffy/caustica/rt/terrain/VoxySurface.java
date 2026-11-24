package dev.comfyfluffy.caustica.rt.terrain;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.Arrays;

/** Greedy quads in section-local coordinates. Zero is air, solids have an opaque alpha byte, and water carries its height in ninths of a block. */
final class VoxySurface {
    interface Voxels { int color(int x, int y, int z); }
    private final int scale;
    private final boolean coverageMask;
    private final int waterMaterial;
    private boolean emitsWater;

    private VoxySurface(int scale, boolean coverageMask, int waterMaterial) {
        this.scale = scale;
        this.coverageMask = coverageMask;
        this.waterMaterial = waterMaterial;
    }

    static boolean isWater(int cell) {
        return cell != 0 && (cell >>> 24) != 255;
    }

    static int water(int rgb, int height) {
        return (height << 24) | (rgb & 0xffffff);
    }

    private final FloatArrayList vertices = new FloatArrayList();
    private final FloatArrayList material = new FloatArrayList();
    private final IntArrayList indices = new IntArrayList();

    static RtTerrainMesher.PackedSection mesh(Voxels voxels) {
        return mesh(16, 1, false, 0, voxels);
    }

    static RtTerrainMesher.PackedSection mesh(int size, int scale, boolean coverageMask, int waterMaterial, Voxels voxels) {
        int side = size + 2, plane = side * side;
        // Include the six neighbour planes to cull faces shared with adjacent sections.
        int[] cells = new int[side * plane];
        boolean occupied = false;
        for (int y = -1; y <= size; y++) {
            for (int z = -1; z <= size; z++) {
                for (int x = -1; x <= size; x++) {
                    int color = voxels.color(x, y, z);
                    cells[(x + 1) + side * (z + 1) + plane * (y + 1)] = color;
                    occupied |= color != 0 && x >= 0 && x < size && y >= 0 && y < size && z >= 0 && z < size;
                }
            }
        }
        if (!occupied) return null;
        var result = new VoxySurface(scale, coverageMask, waterMaterial);
        long[] mask = new long[size * size];
        int[] stride = {1, plane, side};
        for (int axis = 0; axis < 3; axis++) {
            int du = stride[(axis + 1) % 3], dv = stride[(axis + 2) % 3];
            for (int sign = -1; sign <= 1; sign += 2) {
                for (int slice = 0; slice < size; slice++) {
                    int origin = 1 + side + plane + stride[axis] * slice;
                    for (int v = 0; v < size; v++) {
                        for (int u = 0; u < size; u++) {
                            int i = origin + du * u + dv * v;
                            int cell = cells[i], adjacent = cells[i + sign * stride[axis]];
                            // A submerged solid still has a visible bed/shore face. Emit only the fluid-air interface.
                            if (isWater(cell) && isWater(cells[i + plane])) cell = water(cell, scale * 9);
                            if (axis != 1 && isWater(adjacent) && isWater(cells[i + sign * stride[axis] + plane]))
                                adjacent = water(adjacent, scale * 9);
                            long face = 0;
                            if (adjacent == 0 || (cell != 0 && !isWater(cell) && isWater(adjacent)))
                                face = Integer.toUnsignedLong(cell);
                            else if (axis != 1 && isWater(cell) && isWater(adjacent) && (cell >>> 24) > (adjacent >>> 24))
                                face = Integer.toUnsignedLong(cell) | ((long)(adjacent >>> 24) << 32);
                            mask[size * v + u] = face;
                        }
                    }
                    result.emitPlane(mask, size, axis, slice, sign);
                }
            }
        }
        int count = result.indices.size() / 3;
        if (count == 0) return null;
        // Cutout invokes any-hit for both coverage and water shadow transmission.
        boolean anyHit = coverageMask || result.emitsWater;
        return new RtTerrainMesher.PackedSection(result.vertices.toFloatArray(), result.indices.toIntArray(),
                new float[count * 6], result.material.toFloatArray(),
                anyHit ? new int[]{0, count, 0, 0} : new int[]{count, 0, 0, 0},
                new int[]{0, anyHit ? 0 : count, count, count}, new float[0]);
    }

    private void emitPlane(long[] mask, int size, int axis, int slice, int sign) {
        for (int i = 0; i < mask.length; i++) {
            long face = mask[i];
            int color = (int) face;
            if (color == 0) continue;
            int u = i % size, v = i / size;
            int width = 1;
            while (u + width < size && mask[i + width] == face) width++;
            int height = 1;
            nextRow: while (v + height < size) {
                for (int dx = 0; dx < width; dx++) {
                    if (mask[i + size * height + dx] != face) break nextRow;
                }
                height++;
            }
            for (int row = 0; row < height; row++)
                Arrays.fill(mask, i + row * size, i + row * size + width, 0);
            int base = vertices.size() / 3;
            int d = sign > 0 ? slice + 1 : slice;
            boolean water = isWater(color);
            emitsWater |= water;
            int bottomY = axis == 0 ? u : axis == 1 ? slice : v;
            int topY = bottomY + (axis == 0 ? width : axis == 1 ? 1 : height);
            float drop = water ? scale - (color >>> 24) / 9f : 0;
            float lift = (face >>> 32) / 9f;
            vertex(axis, d, u, v, bottomY, topY, lift, drop);
            vertex(axis, d, u + width, v, bottomY, topY, lift, drop);
            vertex(axis, d, u + width, v + height, bottomY, topY, lift, drop);
            vertex(axis, d, u, v + height, bottomY, topY, lift, drop);
            int b = sign > 0 ? 1 : 3, c = 4 - b;
            indices.add(base); indices.add(base + b); indices.add(base + 2);
            indices.add(base); indices.add(base + 2); indices.add(base + c);
            for (int tri = 0; tri < 2; tri++) {
                for (int n = 0; n < 3; n++) material.add(n == axis ? sign : 0);
                material.add(0);
                material.add(((color >>> 16) & 255) / 255f);
                material.add(((color >>> 8) & 255) / 255f);
                material.add((color & 255) / 255f);
                material.add(0);
                material.add(Float.intBitsToFloat(water ? waterMaterial : 0));
                // TERRAIN_PRIM_VOXY, TERRAIN_PRIM_VOXY_WATER and TERRAIN_PRIM_VOXY_MASKED
                material.add(Float.intBitsToFloat(4 | (water ? 8 : 0) | (coverageMask ? 16 : 0)));
                material.add(0);
                material.add(0);
            }
        }
    }

    private void vertex(int axis, int d, int u, int v, int bottomY, int topY, float lift, float drop) {
        int y = axis == 0 ? u : axis == 1 ? d : v;
        vertices.add((axis == 0 ? d : axis == 1 ? v : u) * scale);
        vertices.add(y * scale + (y == bottomY ? lift : y == topY ? -drop : 0));
        vertices.add((axis == 0 ? v : axis == 1 ? u : d) * scale);
    }
}
