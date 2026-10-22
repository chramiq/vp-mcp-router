package vpmcp.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vp.plugin.ApplicationManager;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IProject;
import java.util.ArrayList;
import vpmcp.core.McpTool;
import vpmcp.core.McpToolException;
import vpmcp.core.ToolDefinition;
import vpmcp.vp.DiagramLocator;
import vpmcp.vp.VppUrl;

/** Brings a diagram to the front in the Visual Paradigm editor. */
public final class OpenDiagramTool implements McpTool {

    private static final String DESCRIPTION =
            "Open (activate) a diagram in the Visual Paradigm editor so the "
            + "operator sees it. Takes a diagram id or a vpp url; returns the "
            + "diagram that was brought to the front.";

    private static final String INPUT_SCHEMA =
            "{\"type\":\"object\","
            + "\"properties\":{\"diagram\":{\"type\":\"string\",\"description\":"
            + "\"Diagram id or vpp url, for example \\\"7ffjxoWD.AACAQns\\\".\"}},"
            + "\"required\":[\"diagram\"],\"additionalProperties\":false}";

    private final ToolDefinition definition = new ToolDefinition("vp_open_diagram", DESCRIPTION,
            JsonParser.parseString(INPUT_SCHEMA).getAsJsonObject());

    @Override
    public ToolDefinition getDefinition() {
        return definition;
    }

    @Override
    public JsonObject execute(JsonObject params) throws McpToolException {
        String raw = params.has("diagram") && params.get("diagram").isJsonPrimitive()
                ? params.get("diagram").getAsString() : null;
        if (raw == null || raw.trim().isEmpty()) {
            throw new McpToolException("\"diagram\" is required (id or vpp url).");
        }
        IProject project = DiagramLocator.requireOpenProject();
        IDiagramUIModel diagram = DiagramLocator.locate(VppUrl.parse(raw), project, new ArrayList<>());
        ApplicationManager application = ApplicationManager.instance();
        DiagramManager diagrams = application == null ? null : application.getDiagramManager();
        if (diagrams == null) {
            throw new McpToolException("Visual Paradigm has no diagram manager.");
        }
        diagrams.openDiagram(diagram);

        JsonObject result = new JsonObject();
        result.addProperty("opened", true);
        result.addProperty("id", diagram.getId());
        result.addProperty("name", diagram.getName());
        result.addProperty("type", diagram.getType());
        return result;
    }
}
