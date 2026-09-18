package vpmcp.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vp.plugin.model.IProject;
import vpmcp.core.McpTool;
import vpmcp.core.McpToolException;
import vpmcp.core.ToolDefinition;
import vpmcp.vp.DiagramLocator;
import vpmcp.write.BatchApplier;

/**
 * Applies a write batch to the open project. The confirm gate runs before
 * anything touches VP, so declining never reaches the model. Confirmed
 * batches re-validate immediately before the first mutation.
 */
public final class ApplyBatchTool implements McpTool {

    private static final String DESCRIPTION =
            "Apply a batch of diagram writes to the open project. Same op "
            + "shapes as vp_preview_batch; dry-run there first. Requires "
            + "{\"confirm\": true} alongside \"ops\". The project is NOT "
            + "saved; persist it with vp_save_project {\"confirm\": true}.";

    private static final String INPUT_SCHEMA =
            "{\"type\":\"object\","
            + "\"properties\":{"
            + "\"ops\":{\"type\":\"array\",\"description\":\"Write ops in dependency order.\"},"
            + "\"confirm\":{\"type\":\"boolean\",\"description\":\"Must be true.\"}},"
            + "\"required\":[\"ops\",\"confirm\"],\"additionalProperties\":false}";

    private final ToolDefinition definition = new ToolDefinition("vp_apply_batch", DESCRIPTION,
            JsonParser.parseString(INPUT_SCHEMA).getAsJsonObject());

    @Override
    public ToolDefinition getDefinition() {
        return definition;
    }

    @Override
    public JsonObject execute(JsonObject params) throws McpToolException {
        JsonElement confirm = params.get("confirm");
        if (confirm == null || !confirm.isJsonPrimitive() || !confirm.getAsBoolean()) {
            throw new McpToolException("Refusing to apply: pass {\"confirm\": true} alongside \"ops\".");
        }
        JsonElement ops = params.get("ops");
        if (ops == null || !ops.isJsonArray()) {
            throw new McpToolException("\"ops\" is required and must be an array.");
        }
        IProject project = DiagramLocator.requireOpenProject();
        return BatchApplier.apply(project, ops.getAsJsonArray());
    }
}
