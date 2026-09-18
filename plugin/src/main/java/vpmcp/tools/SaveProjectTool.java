package vpmcp.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vp.plugin.ApplicationManager;
import com.vp.plugin.model.IProject;
import java.io.File;
import vpmcp.core.McpTool;
import vpmcp.core.McpToolException;
import vpmcp.core.ToolDefinition;
import vpmcp.vp.DiagramLocator;

/** Saves the open project to its .vpp file. Explicit confirm required. */
public final class SaveProjectTool implements McpTool {

    private static final String DESCRIPTION =
            "Save the open Visual Paradigm project to its file. "
            + "Requires {\"confirm\": true}. Reports the file path.";

    private static final String INPUT_SCHEMA =
            "{\"type\":\"object\","
            + "\"properties\":{\"confirm\":{\"type\":\"boolean\",\"description\":\"Must be true.\"}},"
            + "\"required\":[\"confirm\"],\"additionalProperties\":false}";

    private final ToolDefinition definition = new ToolDefinition("vp_save_project", DESCRIPTION,
            JsonParser.parseString(INPUT_SCHEMA).getAsJsonObject());

    @Override
    public ToolDefinition getDefinition() {
        return definition;
    }

    @Override
    public JsonObject execute(JsonObject params) throws McpToolException {
        JsonElement confirm = params.get("confirm");
        if (confirm == null || !confirm.isJsonPrimitive() || !confirm.getAsBoolean()) {
            throw new McpToolException("Refusing to save: pass {\"confirm\": true}.");
        }
        IProject project = DiagramLocator.requireOpenProject();
        boolean saved = ApplicationManager.instance().getProjectManager().saveProject();
        File file = project.getProjectFile();
        JsonObject result = new JsonObject();
        result.addProperty("saved", saved);
        result.addProperty("project", project.getName());
        result.addProperty("file", file == null ? null : file.getAbsolutePath());
        return result;
    }
}
