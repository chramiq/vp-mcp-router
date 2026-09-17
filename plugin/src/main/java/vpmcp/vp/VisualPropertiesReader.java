package vpmcp.vp;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.vp.plugin.diagram.ICaptionUIModel;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.format.IElementFont;
import com.vp.plugin.diagram.format.IFillColor;
import com.vp.plugin.diagram.format.ILineModel;
import com.vp.plugin.diagram.format.LineStyle;

/**
 * Turns the formatting of a diagram element into JSON. Nothing here interprets the values:
 * a red fill is reported as {@code "#FF0000"}, and what that means is left to the caller.
 */
public final class VisualPropertiesReader {

    public JsonObject readShape(IShapeUIModel shape) {
        JsonObject visual = readCommon(shape);
        visual.add("fill", readFill(shape.getFillColor()));
        return visual;
    }

    public JsonObject readConnector(IConnectorUIModel connector) {
        return readCommon(connector);
    }

    private JsonObject readCommon(IDiagramElement element) {
        JsonObject visual = new JsonObject();
        visual.add("line", readLine(element.getLineModel()));
        visual.add("font", readFont(element.getElementFont()));
        visual.add("caption", readCaption(element.getCaptionUIModel()));
        visual.addProperty("foreground", ColorFormat.toHex(element.getForeground()));
        visual.addProperty("background", ColorFormat.toHex(element.getBackground()));
        return visual;
    }

    private JsonElement readFill(IFillColor fill) {
        if (fill == null) {
            return JsonNull.INSTANCE;
        }
        JsonObject json = new JsonObject();
        json.addProperty("type", fill.getType() == IFillColor.TYPE_GRADIENT ? "gradient" : "solid");
        json.addProperty("color1", ColorFormat.toHex(fill.getColor1()));
        json.addProperty("color2", ColorFormat.toHex(fill.getColor2()));
        json.addProperty("transparency", fill.getTransparency());
        json.addProperty("gradient_style", fill.getGradientStyle());
        json.addProperty("is_transparent", fill.isTransparent());
        json.addProperty("is_opaque", fill.isOpaque());
        return json;
    }

    private JsonElement readLine(ILineModel line) {
        if (line == null) {
            return JsonNull.INSTANCE;
        }
        JsonObject json = new JsonObject();
        json.addProperty("color", ColorFormat.toHex(line.getColor()));
        json.addProperty("weight", line.getWeight());
        json.addProperty("transparency", line.getTransparency());
        json.addProperty("cap", capName(line.getCap()));
        LineStyle style = line.getLineStyle();
        json.addProperty("style", style == null ? null : style.name());
        return json;
    }

    private JsonElement readFont(IElementFont font) {
        if (font == null) {
            return JsonNull.INSTANCE;
        }
        JsonObject json = new JsonObject();
        json.addProperty("name", font.getName());
        json.addProperty("size", font.getSize());
        json.addProperty("color", ColorFormat.toHex(font.getColor()));
        json.addProperty("is_bold", font.isBold());
        json.addProperty("is_italic", font.isItalic());
        json.addProperty("awt_style", font.getStyle());
        return json;
    }

    private JsonElement readCaption(ICaptionUIModel caption) {
        if (caption == null) {
            return JsonNull.INSTANCE;
        }
        JsonObject bounds = new JsonObject();
        bounds.addProperty("x", caption.getX());
        bounds.addProperty("y", caption.getY());
        bounds.addProperty("width", caption.getWidth());
        bounds.addProperty("height", caption.getHeight());

        JsonObject json = new JsonObject();
        json.addProperty("is_visible", caption.isVisible());
        json.addProperty("side", sideName(caption.getSide()));
        json.add("bounds", bounds);
        return json;
    }

    private String capName(int cap) {
        if (cap == ILineModel.CAP_ROUND) {
            return "round";
        }
        if (cap == ILineModel.CAP_SQUARE) {
            return "square";
        }
        if (cap == ILineModel.CAP_NONE) {
            return "none";
        }
        return String.valueOf(cap);
    }

    private String sideName(int side) {
        if (side == ICaptionUIModel.SIDE_NONE) {
            return "none";
        }
        if (side == ICaptionUIModel.SIDE_NORTH) {
            return "north";
        }
        if (side == ICaptionUIModel.SIDE_EAST) {
            return "east";
        }
        if (side == ICaptionUIModel.SIDE_SOUTH) {
            return "south";
        }
        if (side == ICaptionUIModel.SIDE_WEST) {
            return "west";
        }
        if (side == ICaptionUIModel.SIDE_CENTER) {
            return "center";
        }
        if (side == ICaptionUIModel.SIDE_INVISIBLE) {
            return "invisible";
        }
        if (side == ICaptionUIModel.SIDE_INSIDENORTH) {
            return "inside_north";
        }
        if (side == ICaptionUIModel.SIDE_INSIDEEAST) {
            return "inside_east";
        }
        if (side == ICaptionUIModel.SIDE_INSIDESOUTH) {
            return "inside_south";
        }
        if (side == ICaptionUIModel.SIDE_INSIDEWEST) {
            return "inside_west";
        }
        if (side == ICaptionUIModel.SIDE_FREEMOVE) {
            return "free_move";
        }
        return String.valueOf(side);
    }
}
