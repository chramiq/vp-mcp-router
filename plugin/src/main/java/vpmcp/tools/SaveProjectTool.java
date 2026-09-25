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
            + "Requires {\"confirm\": true}. Reports the file path. "
            + "Refuses when the project has never been saved (no .vpp file): "
            + "save once in VP first, otherwise the native Save-As dialog blocks.";

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
        File file = requireSaveTarget(project);
        boolean saved = ApplicationManager.instance().getProjectManager().saveProject();
        JsonObject result = new JsonObject();
        result.addProperty("saved", saved);
        result.addProperty("project", project.getName());
        result.addProperty("file", file.getAbsolutePath());
        return result;
    }

    /**
     * Save target check. Never-saved projects have no .vpp file, and
     * saveProject() then blocks on a native Save-As dialog instead of
     * failing — refuse upfront with a structured error telling the user to
     * save once in VP. Public static so unit tests can pin the contract
     * without live VP singletons.
     */
    public static File requireSaveTarget(IProject project) throws McpToolException {
        File file;
        try {
            file = project.getProjectFile();
        } catch (RuntimeException unreadable) {
            throw new McpToolException(
                    "Cannot determine the project file; save once in VP, then retry.");
        }
        if (file == null || !file.isFile()) {
            throw new McpToolException("Project has never been saved to disk (no .vpp file); "
                    + "save once in VP (File > Save), then retry vp_save_project.");
        }
        return file;
    }
}
