package dev.comfyfluffy.caustica.rt.terrain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VoxySurfaceTest {
    private static final int STONE = 0xff756a62;

    @Test void missingAndEnclosedSectionsHaveNoBlas() {
        assertNull(VoxySurface.mesh((x,y,z) -> 0));
        assertNull(VoxySurface.mesh((x,y,z) -> STONE));
    }

    @Test void mergesFacesAndPacksOutwardNormalsInSectionLocalSpace() {
        var p = VoxySurface.mesh((x,y,z) -> x >= 0 && x < 16 && y == 5 && z == 0 ? STONE : 0);
        assertNotNull(p);
        assertArrayEquals(new int[]{12, 0, 0, 0}, p.bucketTris());
        assertArrayEquals(new int[]{0, 12, 12, 12}, p.triBase());
        assertEquals(12 * 6, p.uvs().length);
        assertEquals(12 * 12, p.material().length);
        for (int i = 0; i < 12; i++) {
            int a = p.indices()[3*i] * 3, b = p.indices()[3*i+1] * 3, c = p.indices()[3*i+2] * 3;
            var pos = p.positions();
            var mat = p.material();
            for (int axis = 0; axis < 3; axis++) {
                int u = (axis + 1) % 3, v = (axis + 2) % 3;
                float cross = (pos[b+u]-pos[a+u])*(pos[c+v]-pos[a+v]) - (pos[b+v]-pos[a+v])*(pos[c+u]-pos[a+u]);
                assertEquals(mat[12*i+axis], Math.signum(cross), 0f);
            }
            assertTrue(pos[a] >= 0 && pos[a] <= 16);
            assertTrue(pos[a+1] >= 5 && pos[a+1] <= 6);
            assertTrue(pos[a+2] >= 0 && pos[a+2] <= 1);
            assertEquals(4, Float.floatToRawIntBits(mat[12*i+9]));
        }
    }

    @Test void neighborOccupancyCullsTheSharedPlaneOnBothSidesOfZero() {
        for (int origin : new int[]{-16, 0}) {
            var p = VoxySurface.mesh((x,y,z) -> origin+x >= -16 && origin+x < 16 && y == 0 && z == 0 ? STONE : 0);
            assertNotNull(p);
            assertEquals(10, p.bucketTris()[0]);
        }
    }

    @Test void waterKeepsItsSurfaceElevationMaterialAndSubmergedBed() {
        int water = VoxySurface.water(0x3f76e4, 26); // two full blocks and an 8/9 source
        var mesh = VoxySurface.mesh(2, 4, true, 37, (x,y,z) ->
                x == 0 && z == 0 ? (y == 1 ? water : y == 0 ? STONE : 0) : 0);
        int waterTops = 0, bedTops = 0;
        for (int tri = 0; tri < mesh.indices().length / 3; tri++) {
            int start = tri * 12;
            if (mesh.material()[start + 1] != 1) continue;
            boolean fluid = Float.floatToRawIntBits(mesh.material()[start + 9]) == (4 | 8 | 16);
            if (fluid) waterTops++; else bedTops++;
            assertEquals(fluid ? 37 : 0, Float.floatToRawIntBits(mesh.material()[start + 8]));
            for (int k = 0; k < 3; k++) {
                float y = mesh.positions()[mesh.indices()[tri * 3 + k] * 3 + 1];
                assertEquals(fluid ? 4 + 26 / 9f : 4, y, 0.00001);
            }
        }
        assertEquals(2, waterTops);
        assertEquals(2, bedTops);
        assertEquals(mesh.indices().length / 3, mesh.bucketTris()[1]);
    }

    @Test void adjacentWaterOnlyEmitsTheExposedStepAndLeavesSolidBanksOpaque() {
        var mesh = VoxySurface.mesh(3, 1, false, 19, (x,y,z) -> {
            if (y != 0 || z != 1) return 0;
            if (x == 0) return VoxySurface.water(0x3f76e4, 8);
            if (x == 1) return VoxySurface.water(0x3f76e4, 4);
            return x == 2 ? STONE : 0;
        });
        int steps = 0, banks = 0;
        for (int t = 0; t < mesh.indices().length / 3; t++) {
            int p = t * 12;
            int flags = Float.floatToRawIntBits(mesh.material()[p + 9]);
            int first = mesh.indices()[3*t] * 3;
            if (mesh.material()[p] == 1 && mesh.positions()[first] == 1) {
                steps++;
                for (int k = 0; k < 3; k++) {
                    float y = mesh.positions()[mesh.indices()[3*t+k]*3+1];
                    assertTrue(Math.abs(y-4/9f) < 1e-6 || Math.abs(y-8/9f) < 1e-6);
                }
                assertEquals(4 | 8, flags);
            }
            if (mesh.material()[p] == -1 && mesh.positions()[first] == 2) {
                banks++;
                assertEquals(4, flags);
            }
        }
        assertEquals(2, steps);
        assertEquals(2, banks);
    }

    @Test void keepsDistinctPaletteColorsOnTheSamePlane() {
        var p = VoxySurface.mesh((x,y,z) -> y == 1 && z == 1 && x >= 0 && x <= 1 ? STONE + x : 0);
        assertNotNull(p);
        assertEquals(20, p.bucketTris()[0]);
    }
}
