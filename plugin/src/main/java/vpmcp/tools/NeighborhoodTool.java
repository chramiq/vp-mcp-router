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
import vpmcp.graph.Neighborhood;
import vpmcp.vp.DiagramExtractor;
import vpmcp.vp.DiagramLocator;
import vpmcp.vp.VppUrl;

/**
 * N-hop subgraph around one element: the focused read for large diagrams.
 * Nodes and edges keep their full extractor shape; only the set shrinks.
 */
public final class NeighborhoodTool implements McpTool {

    private static final String DESCRIPTION =
            "Read the neighborhood around one element of a diagram: the element "
            + "itself plus everything within depth edges. Use it to traverse "
            + "large diagrams without reading them whole. Element is a view id "
            + "as listed by get_diagram_by_url node reads.";

    private static final String INPUT_SCHEMA =
            "{"
            + "\"type\":\"object\","
            + "\"properties\":{"
            + "\"vpp_url\":{\"type\":\"string\","
            + "\"description\":\"Address of the diagram, or a bare diagram id.\"},"
            + "\"element\":{\"type\":\"string\",\"description\":\"View id of the center element.\"},"
            + "\"depth\":{\"type\":\"integer\",\"minimum\":0,\"default\":1}"
            + "},"
            + "\"required\":[\"vpp_url\",\"element\"],"
            + "\"additionalProperties\":false"
            + "}";

    private final ToolDefinition definition = new ToolDefinition("vp_get_neighborhood", DESCRIPTION,
            JsonParser.parseString(INPUT_SCHEMA).getAsJsonObject());

    @Override
    public ToolDefinition getDefinition() {
        return definition;
    }

    @Override
    public JsonObject execute(JsonObject params) throws McpToolException {
        String element = requireString(params, "element");
        int depth = validatedDepth(params);
        VppUrl url = VppUrl.parse(requireString(params, "vpp_url"));

        List<String> warnings = new ArrayList<>();
        IProject project = DiagramLocator.requireOpenProject();
        IDiagramUIModel diagram = DiagramLocator.locate(url, project, warnings);
        JsonObject graph = new DiagramExtractor(false, warnings).extract(diagram, project);
        JsonObject neighborhood = Neighborhood.around(graph, element, depth);
        if (neighborhood.getAsJsonArray("nodes").isEmpty()) {
            throw new McpToolException("Unknown element \"" + element + "\" on this diagram.");
        }
        if (!warnings.isEmpty()) {
            neighborhood.add("warnings", warningsToJson(warnings));
        }
        return neighborhood;
    }

    private int validatedDepth(JsonObject params) throws McpToolException {
        JsonElement depth = params.get("depth");
        if (depth == null || depth.isJsonNull()) {
            return 1;
        }
        if (!depth.isJsonPrimitive() || !depth.getAsJsonPrimitive().isNumber()) {
            throw new McpToolException("\"depth\" must be a non-negative integer.");
        }
        int value = depth.getAsInt();
        if (value < 0) {
            throw new McpToolException("\"depth\" must be a non-negative integer.");
        }
        return value;
    }

    private com.google.gson.JsonArray warningsToJson(List<String> warnings) {
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        warnings.forEach(array::add);
        return array;
    }

    private String requireString(JsonObject params, String name) throws McpToolException {
        JsonElement value = params.get(name);
        if (value == null || !value.isJsonPrimitive()) {
            throw new McpToolException("\"" + name + "\" is required and must be a string.");
        }
        return value.getAsString();
    }
}
