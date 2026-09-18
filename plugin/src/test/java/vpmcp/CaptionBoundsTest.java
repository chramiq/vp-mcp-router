package vpmcp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;
import vpmcp.write.BatchApplier;

/** Caption placement math. Recipe values, not magic: actor (126,179,30,60) captions at (116,239,50,15). */
class CaptionBoundsTest {

    @Test
    void recipeGeometryReproduced() {
        assertArrayEquals(new int[] {116, 239, 50, 15}, BatchApplier.actorCaptionBounds(126, 179, 30, 60));
    }

    @Test
    void captionCentersUnderWideShapes() {
        int[] bounds = BatchApplier.actorCaptionBounds(100, 100, 90, 60);

        assertArrayEquals(new int[] {120, 160, 50, 15}, bounds);
    }

    @Test
    void insideCaptionCentersInShape() {
        assertArrayEquals(new int[] {120, 122, 80, 16},
                BatchApplier.insideCaptionBounds(100, 100, 120, 60));
    }
}
