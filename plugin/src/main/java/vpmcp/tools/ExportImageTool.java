package vpmcp.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vp.plugin.ApplicationManager;
import com.vp.plugin.ExportDiagramAsImageOption;
import com.vp.plugin.ModelConvertionManager;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IProject;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import vpmcp.core.McpTool;
import vpmcp.core.McpToolException;
import vpmcp.core.ToolDefinition;
import vpmcp.graph.CropBox;
import vpmcp.vp.DiagramLocator;
import vpmcp.vp.VppUrl;

/**
 * Renders one diagram of the open project. PNG returns inline; SVG returns
 * inline as text plus a file; PDF returns a file. An optional region crops
 * to {x, y, width, height}. Files persist under out_dir, defaulting to the
 * system temp directory, and their paths are reported.
 */
public final class ExportImageTool implements McpTool {

    private static final String DESCRIPTION =
            "Render a diagram from the Visual Paradigm project that is currently open. "
            + "format png returns the image inline; svg returns the vector markup as text; "
            + "pdf writes a file. Result fields per format: png "
            + "{diagram, summary, image_mime, image_data}; svg "
            + "{diagram, summary, file, mime, document_text}; pdf "
            + "{diagram, summary, file, mime, bytes}. "
            + "Optional out_dir keeps the file somewhere trackable; otherwise the temp directory.";

    private static final String INPUT_SCHEMA =
            "{"
            + "\"type\":\"object\","
            + "\"properties\":{"
            + "\"vpp_url\":{"
            + "\"type\":\"string\","
            + "\"description\":\"Address of the diagram, for example \\\"MOM.vpp://diagram/YRvbHuaFYFAEMEOa\\\". "
            + "A bare diagram id such as \\\"YRvbHuaFYFAEMEOa\\\" is accepted too.\""
            + "},"
            + "\"format\":{\"type\":\"string\",\"enum\":[\"png\",\"svg\",\"pdf\"],\"default\":\"png\"},"
            + "\"crop\":{\"type\":\"object\","
            + "\"description\":\"PNG only: crop to {x, y, width, height} in diagram coordinates.\","
            + "\"properties\":{\"x\":{\"type\":\"number\"},\"y\":{\"type\":\"number\"},"
            + "\"width\":{\"type\":\"number\"},\"height\":{\"type\":\"number\"}},"
            + "\"required\":[\"x\",\"y\",\"width\",\"height\"],\"additionalProperties\":false},"
            + "\"crop_to_element\":{\"type\":\"string\","
            + "\"description\":\"PNG only: crop around this view id with padding.\"},"
            + "\"out_dir\":{\"type\":\"string\","
            + "\"description\":\"Directory for the file; defaults to the temp directory.\"}"
            + "},"
            + "\"required\":[\"vpp_url\"],"
            + "\"additionalProperties\":false"
            + "}";

    private static final int RENDER_MARGIN = 2;

    private final ToolDefinition definition = new ToolDefinition("vp_export_image", DESCRIPTION,
            JsonParser.parseString(INPUT_SCHEMA).getAsJsonObject());

    @Override
    public ToolDefinition getDefinition() {
        return definition;
    }

    @Override
    public JsonObject execute(JsonObject params) throws McpToolException {
        String format = validatedFormat(params);
        int[] cropNumbers = validatedCropNumbers(params);
        String cropElement = validatedCropElement(params);
        if (cropNumbers != null && cropElement != null) {
            throw new McpToolException("Pass only one of \"crop\", \"crop_to_element\".");
        }
        if ((cropNumbers != null || cropElement != null) && !format.equals("png")) {
            throw new McpToolException("Cropping applies to format png only.");
        }
        File outDir = validatedOutDir(params);
        VppUrl url = VppUrl.parse(requireString(params, "vpp_url"));

        List<String> warnings = new ArrayList<>();
        IProject project = DiagramLocator.requireOpenProject();
        IDiagramUIModel diagram = DiagramLocator.locate(url, project, warnings);

        try {
            switch (format) {
                case "svg":
                    return vector(diagram, outDir,
                            ExportDiagramAsImageOption.IMAGE_TYPE_SVG, "svg", "image/svg+xml");
                case "pdf":
                    return fileOnly(diagram, outDir,
                            ExportDiagramAsImageOption.IMAGE_TYPE_PDF, "pdf", "application/pdf");
                default:
                    return png(diagram, cropBox(diagram, cropNumbers, cropElement));
            }
        } catch (McpToolException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new McpToolException("Export failed for diagram " + diagram.getId() + ": " + failure);
        } catch (Exception failure) {
            throw new McpToolException("Export failed for diagram " + diagram.getId() + ": " + failure.getMessage());
        }
    }

    private JsonObject png(IDiagramUIModel diagram, Rectangle crop) throws Exception {
        ModelConvertionManager convert = ApplicationManager.instance().getModelConvertionManager();
        ExportDiagramAsImageOption option =
                new ExportDiagramAsImageOption(ExportDiagramAsImageOption.IMAGE_TYPE_PNG);
        Image image = convert.exportDiagramAsImage(diagram, option);
        if (image == null) {
            throw new McpToolException("Export returned no image for diagram " + diagram.getId() + ".");
        }
        byte[] png = toPng(image, crop, diagram);
        JsonObject result = identity(diagram);
        String summary = "PNG render of diagram '" + diagram.getName() + "' (" + png.length + " bytes).";
        if (crop != null) {
            summary += " Cropped to " + crop.width + "x" + crop.height + " at (" + crop.x + "," + crop.y + ").";
        }
        result.addProperty("summary", summary);
        result.addProperty("image_mime", "image/png");
        result.addProperty("image_data", Base64.getEncoder().encodeToString(png));
        return result;
    }

    private JsonObject vector(IDiagramUIModel diagram, File outDir, int imageType,
            String extension, String mime) throws Exception {
        File file = writeVector(diagram, outDir, imageType, extension);
        String markup = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        JsonObject result = identity(diagram);
        result.addProperty("summary", mime + " render of diagram '" + diagram.getName() + "' ("
                + file.length() + " bytes) at " + file.getAbsolutePath() + ".");
        result.addProperty("file", file.getAbsolutePath());
        result.addProperty("mime", mime);
        result.addProperty("document_text", markup);
        return result;
    }

    private JsonObject fileOnly(IDiagramUIModel diagram, File outDir, int imageType,
            String extension, String mime) throws Exception {
        File file = writeVector(diagram, outDir, imageType, extension);
        JsonObject result = identity(diagram);
        result.addProperty("summary", mime + " render of diagram '" + diagram.getName() + "' ("
                + file.length() + " bytes) at " + file.getAbsolutePath() + ".");
        result.addProperty("file", file.getAbsolutePath());
        result.addProperty("mime", mime);
        result.addProperty("bytes", file.length());
        return result;
    }

    private File writeVector(IDiagramUIModel diagram, File outDir, int imageType,
            String extension) throws Exception {
        if (!outDir.isDirectory() && !outDir.mkdirs()) {
            throw new McpToolException("Cannot create directory " + outDir.getAbsolutePath() + ".");
        }
        String safe = diagram.getName().replaceAll("[^A-Za-z0-9_-]", "_");
        File file = new File(outDir, safe + "." + extension);
        ExportDiagramAsImageOption option = new ExportDiagramAsImageOption(imageType);
        ApplicationManager.instance().getModelConvertionManager()
                .exportDiagramAsImage(diagram, file, option);
        if (!file.isFile()) {
            throw new McpToolException("Export wrote no file for diagram " + diagram.getId() + ".");
        }
        return file;
    }

    private int[] contentOrigin(IDiagramUIModel diagram) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        IDiagramElement[] elements = diagram.toDiagramElementArray();
        if (elements != null) {
            for (IDiagramElement element : elements) {
                if (element == null) {
                    continue;
                }
                minX = Math.min(minX, element.getX());
                minY = Math.min(minY, element.getY());
            }
        }
        if (minX == Integer.MAX_VALUE) {
            return new int[] {0, 0};
        }
        return new int[] {minX, minY};
    }

    private JsonObject identity(IDiagramUIModel diagram) {
        JsonObject identity = new JsonObject();
        identity.addProperty("id", diagram.getId());
        identity.addProperty("name", diagram.getName());
        identity.addProperty("type", diagram.getType());
        JsonObject result = new JsonObject();
        result.add("diagram", identity);
        return result;
    }

    private byte[] toPng(Image image, Rectangle cropVp, IDiagramUIModel diagram) throws Exception {
        BufferedImage full = toBuffered(image);
        Rectangle view;
        if (cropVp == null) {
            view = new Rectangle(0, 0, full.getWidth(), full.getHeight());
        } else {
            int[] origin = contentOrigin(diagram);
            Rectangle pixels = CropBox.shift(cropVp, -origin[0] + RENDER_MARGIN, -origin[1] + RENDER_MARGIN);
            view = CropBox.clamp(pixels, full.getWidth(), full.getHeight());
        }
        BufferedImage part = full.getSubimage(view.x, view.y, view.width, view.height);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(part, "png", bytes)) {
            throw new IllegalStateException("No PNG writer available.");
        }
        return bytes.toByteArray();
    }

    private BufferedImage toBuffered(Image image) {
        if (image instanceof BufferedImage) {
            return (BufferedImage) image;
        }
        int width = image.getWidth(null);
        int height = image.getHeight(null);
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        buffered.getGraphics().drawImage(image, 0, 0, null);
        return buffered;
    }

    private Rectangle cropBox(IDiagramUIModel diagram, int[] numbers, String elementId)
            throws McpToolException {
        if (numbers != null) {
            return CropBox.of(numbers[0], numbers[1], numbers[2], numbers[3]);
        }
        if (elementId == null) {
            return null;
        }
        IDiagramElement[] elements = diagram.toDiagramElementArray();
        if (elements != null) {
            for (IDiagramElement element : elements) {
                if (element != null && elementId.equals(element.getId())) {
                    return CropBox.around(element.getX(), element.getY(), element.getWidth(),
                            element.getHeight(), 20);
                }
            }
        }
        throw new McpToolException("Unknown element \"" + elementId + "\" on this diagram.");
    }

    private String validatedFormat(JsonObject params) throws McpToolException {
        JsonElement format = params.get("format");
        if (format == null || format.isJsonNull()) {
            return "png";
        }
        if (!format.isJsonPrimitive()) {
            throw new McpToolException("\"format\" must be one of png, svg, pdf.");
        }
        String value = format.getAsString();
        if (!value.equals("png") && !value.equals("svg") && !value.equals("pdf")) {
            throw new McpToolException("\"format\" must be one of png, svg, pdf.");
        }
        return value;
    }

    private int[] validatedCropNumbers(JsonObject params) throws McpToolException {
        JsonElement crop = params.get("crop");
        if (crop == null || crop.isJsonNull()) {
            return null;
        }
        if (!crop.isJsonObject()) {
            throw new McpToolException("\"crop\" must be {x, y, width, height}.");
        }
        JsonObject box = crop.getAsJsonObject();
        int[] numbers = new int[4];
        String[] names = {"x", "y", "width", "height"};
        for (int index = 0; index < names.length; index++) {
            JsonElement value = box.get(names[index]);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                throw new McpToolException("\"crop." + names[index] + "\" must be a number.");
            }
            numbers[index] = value.getAsInt();
        }
        if (numbers[2] <= 0 || numbers[3] <= 0) {
            throw new McpToolException("\"crop\" needs positive width and height.");
        }
        return numbers;
    }

    private String validatedCropElement(JsonObject params) throws McpToolException {
        JsonElement element = params.get("crop_to_element");
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive()) {
            throw new McpToolException("\"crop_to_element\" must be a view id.");
        }
        return element.getAsString();
    }

    private File validatedOutDir(JsonObject params) throws McpToolException {
        JsonElement outDir = params.get("out_dir");
        if (outDir == null || outDir.isJsonNull()) {
            return new File(System.getProperty("java.io.tmpdir"));
        }
        if (!outDir.isJsonPrimitive()) {
            throw new McpToolException("\"out_dir\" must be a directory path.");
        }
        return new File(outDir.getAsString());
    }

    private String requireString(JsonObject params, String name) throws McpToolException {
        JsonElement value = params.get(name);
        if (value == null || !value.isJsonPrimitive()) {
            throw new McpToolException("\"" + name + "\" is required and must be a string.");
        }
        return value.getAsString();
    }
}
