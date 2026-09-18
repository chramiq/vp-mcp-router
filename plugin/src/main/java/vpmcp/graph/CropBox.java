package vpmcp.graph;

import java.awt.Rectangle;

/**
 * Crop-box math in diagram coordinates. Clamping keeps every box inside
 * the render, so a wrong scale degrades to a smaller crop, never an
 * exception.
 */
public final class CropBox {

    private CropBox() {
    }

    public static Rectangle of(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Crop needs positive width and height.");
        }
        return new Rectangle(x, y, width, height);
    }

    public static Rectangle around(int x, int y, int width, int height, int pad) {
        return of(x - pad, y - pad, width + pad * 2, height + pad * 2);
    }

    public static Rectangle shift(Rectangle box, int dx, int dy) {
        return new Rectangle(box.x + dx, box.y + dy, box.width, box.height);
    }

    public static Rectangle clamp(Rectangle box, int imageWidth, int imageHeight) {
        int x = Math.max(0, box.x);
        int y = Math.max(0, box.y);
        int width = Math.min(box.width - (x - box.x), imageWidth - x);
        int height = Math.min(box.height - (y - box.y), imageHeight - y);
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Crop box falls outside the render.");
        }
        return new Rectangle(x, y, width, height);
    }
}
