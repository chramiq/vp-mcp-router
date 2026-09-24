package vpmcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IProject;
import vpmcp.core.McpTool;
import vpmcp.core.McpToolException;
import vpmcp.core.ToolDefinition;
import vpmcp.vp.DiagramLocator;

/**
 * Lists every model element of the open project, including ones that appear
 * on no diagram. This is the model tree beside {@code vp_list_diagrams}.
 */
public final class ListModelsTool implements McpTool {

    private static final String DESCRIPTION =
            "List the model elements of the Visual Paradigm project that is currently open: "
            + "every logical element (classes, actors, relationships, attributes...), including "
            + "elements that appear on no diagram. Returns a light index of id, name, model_type "
            + "and parent_id per element; use vp_get_model to read one in full.";

    private static final String INPUT_SCHEMA =
            "{"
            + "\"type\":\"object\","
            + "\"properties\":{"
            + "\"model_type\":{\"type\":\"string\","
            + "\"description\":\"Only list elements of this model_type, for example \\\"Class\\\" "
            + "or \\\"Association\\\". Omit to list all.\"}"
            + "},"
            + "\"additionalProperties\":false"
            + "}";

    private final ToolDefinition definition = new ToolDefinition("vp_list_models", DESCRIPTION,
            JsonParser.parseString(INPUT_SCHEMA).getAsJsonObject());

    @Override
    public ToolDefinition getDefinition() {
        return definition;
    }

    @Override
    public JsonObject execute(JsonObject params) throws McpToolException {
        String filter = optionalString(params, "model_type");
        return index(DiagramLocator.requireOpenProject(), filter);
    }

    /** The index core, separated from the live-project lookup for unit tests. */
    public static JsonObject index(IProject project, String filter) {
        IModelElement[] models = project.toAllLevelModelElementArray();

        JsonArray list = new JsonArray();
        if (models != null) {
            for (IModelElement model : models) {
                if (model == null) {
                    continue;
                }
                addEntry(list, model, filter);
                // Members (attributes, operations, columns) are not in the
                // project arrays; they hang off their parent's child index.
                IModelElement[] children = model.toChildArray();
                if (children != null) {
                    for (IModelElement child : children) {
                        if (child != null) {
                            addEntry(list, child, filter);
                        }
                    }
                }
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("project_name", project.getName());
        result.addProperty("count", list.size());
        result.add("models", list);
        return result;
    }

    private static void addEntry(JsonArray list, IModelElement model, String filter) {
        String modelType = model.getModelType();
        if (filter != null && !filter.equals(modelType)) {
            return;
        }
        JsonObject entry = new JsonObject();
        entry.addProperty("id", model.getId());
        entry.addProperty("name", model.getName());
        entry.addProperty("model_type", modelType);
        entry.addProperty("parent_id", model.getParent() == null ? null : model.getParent().getId());
        list.add(entry);
    }

    private String optionalString(JsonObject params, String name) {
        JsonElement value = params.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }
}
