package vpmcp.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vp.plugin.ApplicationManager;
import com.vp.plugin.ExportDiagramAsImageOption;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IProject;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import vpmcp.core.McpTool;
import vpmcp.core.McpToolException;
import vpmcp.core.ToolDefinition;
import vpmcp.vp.DiagramLocator;
import vpmcp.vp.VppUrl;

/** Renders one diagram of the open project to a PNG image. */
public final class ExportImageTool implements McpTool {

    private static final String DESCRIPTION =
            "Render a diagram from the Visual Paradigm project that is currently open "
            + "as a PNG image. Returns the image inline plus the diagram identity.";

    private static final String INPUT_SCHEMA =
            "{"
            + "\"type\":\"object\","
            + "\"properties\":{"
            + "\"vpp_url\":{"
            + "\"type\":\"string\","
            + "\"description\":\"Address of the diagram, for example \\\"MOM.vpp://diagram/YRvbHuaFYFAEMEOa\\\". "
            + "A bare diagram id such as \\\"YRvbHuaFYFAEMEOa\\\" is accepted too.\""
            + "}"
            + "},"
            + "\"required\":[\"vpp_url\"],"
            + "\"additionalProperties\":false"
            + "}";

    private final ToolDefinition definition = new ToolDefinition("vp_export_image", DESCRIPTION,
            JsonParser.parseString(INPUT_SCHEMA).getAsJsonObject());

    @Override
    public ToolDefinition getDefinition() {
        return definition;
    }

    @Override
    public JsonObject execute(JsonObject params) throws McpToolException {
        VppUrl url = VppUrl.parse(requireString(params, "vpp_url"));

        List<String> warnings = new ArrayList<>();
        IProject project = DiagramLocator.requireOpenProject();
        IDiagramUIModel diagram = DiagramLocator.locate(url, project, warnings);

        Image image;
        try {
            image = ApplicationManager.instance().getModelConvertionManager()
                    .exportDiagramAsImage(diagram,
                            new ExportDiagramAsImageOption(ExportDiagramAsImageOption.IMAGE_TYPE_PNG));
        } catch (RuntimeException failure) {
            throw new McpToolException("Export failed for diagram " + diagram.getId() + ": " + failure);
        }
        if (image == null) {
            throw new McpToolException("Export returned no image for diagram " + diagram.getId() + ".");
        }

        byte[] png;
        try {
            png = toPng(image);
        } catch (Exception failure) {
            throw new McpToolException("PNG encoding failed: " + failure.getMessage());
        }

        JsonObject identity = new JsonObject();
        identity.addProperty("id", diagram.getId());
        identity.addProperty("name", diagram.getName());
        identity.addProperty("type", diagram.getType());

        JsonObject result = new JsonObject();
        result.add("diagram", identity);
        result.addProperty("summary",
                "PNG render of diagram '" + diagram.getName() + "' (" + png.length + " bytes).");
        result.addProperty("image_mime", "image/png");
        result.addProperty("image_data", Base64.getEncoder().encodeToString(png));
        return result;
    }

    private byte[] toPng(Image image) throws Exception {
        RenderedImage rendered;
        if (image instanceof RenderedImage) {
            rendered = (RenderedImage) image;
        } else {
            int width = image.getWidth(null);
            int height = image.getHeight(null);
            BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            buffered.getGraphics().drawImage(image, 0, 0, null);
            rendered = buffered;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(rendered, "png", bytes)) {
            throw new IllegalStateException("No PNG writer available.");
        }
        return bytes.toByteArray();
    }

    private String requireString(JsonObject params, String name) throws McpToolException {
        JsonElement value = params.get(name);
        if (value == null || !value.isJsonPrimitive()) {
            throw new McpToolException("\"" + name + "\" is required and must be a string.");
        }
        return value.getAsString();
    }
}
