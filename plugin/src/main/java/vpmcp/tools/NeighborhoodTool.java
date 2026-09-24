package vpmcp.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IModelElement;
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
            + "as listed by get_diagram_by_url node reads, or a model id as "
            + "listed by vp_list_models; a model id is resolved to its view on "
            + "the addressed diagram.";

    private static final String INPUT_SCHEMA =
            "{"
            + "\"type\":\"object\","
            + "\"properties\":{"
            + "\"vpp_url\":{\"type\":\"string\","
            + "\"description\":\"Address of the diagram, or a bare diagram id.\"},"
            + "\"element\":{\"type\":\"string\","
            + "\"description\":\"View id or model id of the center element.\"},"
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
        String center = resolveCenter(graph, project, element, warnings);
        JsonObject neighborhood = Neighborhood.around(graph, center, depth);
        if (neighborhood.getAsJsonArray("nodes").isEmpty()) {
            throw new McpToolException("Unknown element \"" + center + "\" on this diagram.");
        }
        if (!warnings.isEmpty()) {
            neighborhood.add("warnings", warningsToJson(warnings));
        }
        return neighborhood;
    }

    /**
     * A view id passes through. Anything else is tried as a model id: when the
     * model has a view on this diagram, that view becomes the center; when the
     * model exists but is not shown here, the error names the situation.
     */
    public static String resolveCenter(JsonObject graph, IProject project, String element,
            List<String> warnings) throws McpToolException {
        for (JsonElement node : graph.getAsJsonArray("nodes")) {
            if (element.equals(node.getAsJsonObject().get("id").getAsString())) {
                return element;
            }
        }
        for (JsonElement node : graph.getAsJsonArray("nodes")) {
            JsonObject entry = node.getAsJsonObject();
            JsonElement candidate = entry.get("model_id");
            if (candidate != null && !candidate.isJsonNull()
                    && element.equals(candidate.getAsString())) {
                warnings.add("Center \"" + element + "\" was given as a model id; "
                        + "using its view on this diagram.");
                return entry.get("id").getAsString();
            }
        }
        IModelElement model = project.getModelElementById(element);
        if (model != null) {
            throw new McpToolException("Element \"" + element + "\" is the model \""
                    + model.getName() + "\" of type " + model.getModelType()
                    + ", which is not shown on this diagram; its neighborhood here is "
                    + "undefined. Use vp_get_model to read it.");
        }
        return element;
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
