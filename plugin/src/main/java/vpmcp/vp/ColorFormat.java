package vpmcp.vp;

import java.awt.Color;

/** Formats AWT colours the way the JSON graph reports them. */
public final class ColorFormat {

    private ColorFormat() {
    }

    /**
     * @return {@code "#RRGGBB"}, or null when Visual Paradigm reports no colour of its own,
     *         which means the element inherits it from the diagram or stereotype style.
     *         Opacity is reported separately, through the fill and line transparency.
     */
    public static String toHex(Color color) {
        if (color == null) {
            return null;
        }
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }
}
