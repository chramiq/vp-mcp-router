package vpmcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.Rectangle;
import org.junit.jupiter.api.Test;
import vpmcp.graph.CropBox;

/** Crop-box math contracts: padding expands, clamping contains, abuse throws. */
class CropBoxTest {

    @Test
    void paddingExpandsSymmetrically() {
        Rectangle box = CropBox.around(100, 100, 80, 40, 20);

        assertEquals(new Rectangle(80, 80, 120, 80), box);
    }

    @Test
    void shiftMovesWithoutResizing() {
        Rectangle box = CropBox.shift(new Rectangle(633, 160, 80, 40), -222, -105);

        assertEquals(new Rectangle(411, 55, 80, 40), box);
    }

    @Test
    void insideBoxSurvivesClamp() {
        Rectangle box = CropBox.clamp(new Rectangle(10, 10, 50, 50), 500, 500);

        assertEquals(new Rectangle(10, 10, 50, 50), box);
    }

    @Test
    void overhangClampsToImage() {
        Rectangle box = CropBox.clamp(new Rectangle(-20, 450, 200, 200), 500, 500);

        assertEquals(0, box.x);
        assertEquals(180, box.width);
        assertEquals(50, box.height);
    }

    @Test
    void fullyOutsideThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> CropBox.clamp(new Rectangle(600, 600, 50, 50), 500, 500));
    }

    @Test
    void nonPositiveSizeThrows() {
        assertThrows(IllegalArgumentException.class, () -> CropBox.of(0, 0, 0, 10));
    }
}
