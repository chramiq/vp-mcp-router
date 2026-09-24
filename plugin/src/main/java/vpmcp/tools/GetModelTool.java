package vpmcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IProject;
import vpmcp.core.McpTool;
import vpmcp.core.McpToolException;
import vpmcp.core.ToolDefinition;
import vpmcp.vp.DiagramLocator;
import vpmcp.vp.ModelPropertiesReader;

/**
 * Reads one model element of the open project by id: the logical side
 * (name, type, documentation, stereotypes, members) plus the diagrams it is
 * shown on. Works for elements that appear on no diagram at all.
 */
public final class GetModelTool implements McpTool {

    private static final String DESCRIPTION =
            "Read one model element of the Visual Paradigm project that is currently open, "
            + "by its model id: name, type, documentation, stereotypes, tagged values, "
            + "members and the diagrams it is shown on. Use it for elements that appear on "
            + "no diagram; for the rendering of a shown element, prefer get_diagram_by_url.";

    private static final String INPUT_SCHEMA =
            "{"
            + "\"type\":\"object\","
            + "\"properties\":{"
            + "\"model\":{\"type\":\"string\",\"description\":\"Model element id.\"},"
            + "\"detail\":{\"type\":\"string\",\"enum\":[\"standard\",\"full\"],\"default\":\"standard\","
            + "\"description\":\"\\\"full\\\" additionally dumps every raw model property "
            + "Visual Paradigm exposes, which is exhaustive but much larger.\"}"
            + "},"
            + "\"required\":[\"model\"],"
            + "\"additionalProperties\":false"
            + "}";

    private final ToolDefinition definition = new ToolDefinition("vp_get_model", DESCRIPTION,
            JsonParser.parseString(INPUT_SCHEMA).getAsJsonObject());

    @Override
    public ToolDefinition getDefinition() {
        return definition;
    }

    @Override
    public JsonObject execute(JsonObject params) throws McpToolException {
        String id = requireString(params, "model");
        boolean fullDetail = "full".equalsIgnoreCase(optionalString(params, "detail", "standard"));
        return read(DiagramLocator.requireOpenProject(), id, fullDetail);
    }

    /** The read core, separated from the live-project lookup for unit tests. */
    public static JsonObject read(IProject project, String id, boolean fullDetail) throws McpToolException {
        IModelElement model = project.getModelElementById(id);
        if (model == null) {
            throw new McpToolException("No model element with id \"" + id
                    + "\" exists in project \"" + project.getName() + "\".");
        }

        ModelPropertiesReader modelReader = new ModelPropertiesReader();
        JsonObject result = new JsonObject();
        result.addProperty("id", model.getId());
        result.addProperty("name", model.getName());
        result.addProperty("model_type", model.getModelType());
        result.addProperty("documentation", model.getDocumentation());
        result.addProperty("description", model.getDescription());
        result.add("parent", modelReader.reference(model.getParent()));
        result.add("stereotypes", modelReader.readStereotypes(model));
        result.add("tagged_values", modelReader.readTaggedValues(model));
        result.add("members", modelReader.readMembers(model));
        result.add("sub_diagrams", modelReader.readSubDiagrams(model));
        result.add("shown_on", shownOn(project, model));
        if (fullDetail) {
            result.add("raw_model_properties", modelReader.readRawModelProperties(model));
        }
        return result;
    }

    /** Every diagram that carries a view of this model, with that view's id. */
    private static JsonArray shownOn(IProject project, IModelElement model) {
        JsonArray shownOn = new JsonArray();
        IDiagramUIModel[] diagrams = project.toDiagramArray();
        if (diagrams == null) {
            return shownOn;
        }
        for (IDiagramUIModel diagram : diagrams) {
            if (diagram == null) {
                continue;
            }
            IDiagramElement[] elements = diagram.toDiagramElementArray();
            if (elements == null) {
                continue;
            }
            for (IDiagramElement element : elements) {
                IModelElement candidate = element == null ? null : element.getModelElement();
                if (candidate == null || !candidate.getId().equals(model.getId())) {
                    continue;
                }
                JsonObject entry = new JsonObject();
                entry.addProperty("diagram_id", diagram.getId());
                entry.addProperty("diagram_name", diagram.getName());
                entry.addProperty("diagram_type", diagram.getType());
                entry.addProperty("view_id", element.getId());
                shownOn.add(entry);
                break;
            }
        }
        return shownOn;
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
