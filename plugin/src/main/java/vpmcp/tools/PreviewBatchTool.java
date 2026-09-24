package vpmcp.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vp.plugin.model.IProject;
import vpmcp.core.McpTool;
import vpmcp.core.McpToolException;
import vpmcp.core.ToolDefinition;
import vpmcp.vp.DiagramLocator;
import vpmcp.write.PlanValidator;

/**
 * Dry-runs a write batch: validates every op and returns the resolved plan
 * without creating, updating, or saving anything. Nothing here mutates.
 */
public final class PreviewBatchTool implements McpTool {

    private static final String DESCRIPTION =
            "Validate a batch of diagram writes without applying anything. "
            + "Every op carries its discriminator, e.g. {\"op\": \"create_element\", ...}. "
            + "Ops: create_diagram {id, diagram_type, name}, "
            + "create_element {id, diagram, model_type, name, x, y, width, height}, "
            + "connect {id, diagram, rel_type, from, to, name, points?}, "
            + "add_member {id, parent, member_type, name, type?}, "
            + "update_element {id, model, name?, documentation?, stereotypes?} "
            + "(stereotypes replace all; empty documentation clears it), "
            + "duplicate_diagram {id, diagram, name} (shallow copy: fresh views, shared models), "
            + "move_element {id, element, x?, y?, width?, height?}, "
            + "show_element {id, diagram, model, x?, y?, width?, height?} (view of an existing model), "
            + "delete_model {id, model} (model plus all its views, final), "
            + "delete_diagram {id, diagram}, delete_element {id, element}. "
            + "Ids and diagram/endpoint slots accept plan refs as {\"ref\": \"<op id>\"}. "
            + "Returns {valid, plan[] (each with its undo), errors[]}; valid plans are applied with apply_batch.";

    private static final String INPUT_SCHEMA =
            "{\"type\":\"object\","
            + "\"properties\":{\"ops\":{\"type\":\"array\",\"description\":\"Write ops in dependency order.\"}},"
            + "\"required\":[\"ops\"],\"additionalProperties\":false}";

    private final ToolDefinition definition = new ToolDefinition("vp_preview_batch", DESCRIPTION,
            JsonParser.parseString(INPUT_SCHEMA).getAsJsonObject());

    @Override
    public ToolDefinition getDefinition() {
        return definition;
    }

    @Override
    public JsonObject execute(JsonObject params) throws McpToolException {
        JsonElement ops = params.get("ops");
        if (ops == null || !ops.isJsonArray()) {
            throw new McpToolException("\"ops\" is required and must be an array.");
        }
        IProject project = DiagramLocator.requireOpenProject();
        JsonObject result = new JsonObject();
        result.addProperty("mutated", false);
        result.add("validation", PlanValidator.validate(project, ops.getAsJsonArray()));
        return result;
    }
}
