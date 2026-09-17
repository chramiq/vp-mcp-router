package vpmcp.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IProject;
import java.util.ArrayList;
import java.util.List;
import vpmcp.core.McpTool;
import vpmcp.core.McpToolException;
import vpmcp.core.ToolDefinition;
import vpmcp.vp.DiagramExtractor;
import vpmcp.vp.DiagramLocator;
import vpmcp.vp.VppUrl;

/** Returns one diagram of the open project as a graph of nodes and edges. */
public final class GetDiagramByUrlTool implements McpTool {

    private static final String DESCRIPTION =
            "Read a diagram from the Visual Paradigm project that is currently open, and return it as a "
            + "JSON graph. Every node and edge carries both its model data (name, type, documentation, "
            + "stereotypes, tagged values, attributes and operations) and its exact formatting (fill "
            + "colour, line colour, line style and weight, font colour, bold and italic, caption "
            + "placement, bounds). Colours are reported as they are, for example \"#FF0000\"; the "
            + "meaning of a colour is not interpreted here.";

    private static final String INPUT_SCHEMA =
            "{"
            + "\"type\":\"object\","
            + "\"properties\":{"
            + "\"vpp_url\":{"
            + "\"type\":\"string\","
            + "\"description\":\"Address of the diagram, for example \\\"MOM.vpp://diagram/YRvbHuaFYFAEMEOa\\\". "
            + "A bare diagram id such as \\\"YRvbHuaFYFAEMEOa\\\" is accepted too.\""
            + "},"
            + "\"detail\":{"
            + "\"type\":\"string\","
            + "\"enum\":[\"standard\",\"full\"],"
            + "\"default\":\"standard\","
            + "\"description\":\"\\\"standard\\\" returns the curated model and formatting fields. "
            + "\\\"full\\\" additionally dumps every raw model and view property Visual Paradigm exposes, "
            + "which is exhaustive but much larger.\""
            + "}"
            + "},"
            + "\"required\":[\"vpp_url\"],"
            + "\"additionalProperties\":false"
            + "}";

    private final ToolDefinition definition = new ToolDefinition("get_diagram_by_url", DESCRIPTION,
            JsonParser.parseString(INPUT_SCHEMA).getAsJsonObject());

    @Override
    public ToolDefinition getDefinition() {
        return definition;
    }

    @Override
    public JsonObject execute(JsonObject params) throws McpToolException {
        VppUrl url = VppUrl.parse(requireString(params, "vpp_url"));
        boolean fullDetail = "full".equalsIgnoreCase(optionalString(params, "detail", "standard"));

        List<String> warnings = new ArrayList<>();
        IProject project = DiagramLocator.requireOpenProject();
        IDiagramUIModel diagram = DiagramLocator.locate(url, project, warnings);
        return new DiagramExtractor(fullDetail, warnings).extract(diagram, project);
    }

    private String requireString(JsonObject params, String name) throws McpToolException {
        JsonElement value = params.get(name);
        if (value == null || !value.isJsonPrimitive()) {
            throw new McpToolException("\"" + name + "\" is required and must be a string.");
        }
        return value.getAsString();
    }

    private String optionalString(JsonObject params, String name, String fallback) {
        JsonElement value = params.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }
}
