package dev.comfyfluffy.caustica.rt.terrain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VoxyLodTileTest {
    @Test void settingControlsTheCircleIncludingItsPartialEdgeTiles() {
        assertNull(VoxyLodTile.select(384,0,0,0,320));
        var edge = VoxyLodTile.select(256,192,0,0,336);
        assertNotNull(edge);
        assertTrue(edge.contains(258,194));
        assertFalse(edge.contains(318,254));
        assertNotNull(VoxyLodTile.select(32000,0,0,0,32768));
        assertNull(VoxyLodTile.select(24576,24576,0,0,32768));
    }

    @Test void distantTilesChangeLevelAndShareTheirNeighborSamplingRate() {
        assertEquals(1,VoxyLodTile.select(0,0,0,0,32768).lod());
        var inner = VoxyLodTile.select(320,0,0,0,32768);
        var outer = VoxyLodTile.select(384,0,0,0,32768);
        assertEquals(2,inner.lod());
        assertEquals(3,outer.lod());
        assertEquals(inner.lod(),outer.sampleLod(-1,0));
        assertEquals(outer.lod(),inner.sampleLod(64,0));
        assertEquals(2,outer.gridLod());
        assertEquals(4,VoxyLodTile.select(-1024,0,0,0,32768).lod());
    }

    @Test void macroKeysAndCoverageDistinguishAll64FineSectionsBelowZero() {
        long all = 0;
        for (int y=-4; y<0; y++)
            for (int z=-4; z<0; z++)
                for (int x=-4; x<0; x++) {
                    assertTrue(RtTerrain.sectionKey(x,y,z) >= 0);
                    assertEquals(VoxyLodTile.key(-4,-4,-4),VoxyLodTile.key(x,y,z));
                    all |= 1L << VoxyLodTile.bit(x,y,z);
                }
        assertEquals(-1L,all);
    }

    @Test void coarseFacesUseBlockSpaceAndTheCoverageAwareBucket() {
        var mesh=VoxySurface.mesh(4,16,true,0,(x,y,z)->x==3 && y==0 && z==0 ? 0xffabcdef : 0);
        assertArrayEquals(new int[]{0,12,0,0},mesh.bucketTris());
        assertArrayEquals(new int[]{0,0,12,12},mesh.triBase());
        for (int i=0;i<mesh.positions().length;i+=3)
            assertTrue(mesh.positions()[i]==48 || mesh.positions()[i]==64);
    }
}
