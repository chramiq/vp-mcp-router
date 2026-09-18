package vpmcp.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vp.plugin.ApplicationManager;
import com.vp.plugin.VPProductInfo;
import com.vp.plugin.model.IProject;
import vpmcp.core.McpTool;
import vpmcp.core.McpToolException;
import vpmcp.core.ToolDefinition;
import vpmcp.vp.GuardStatus;

/** Live handshake: versions plus the currently open project, if any. */
public final class CapabilitiesTool implements McpTool {

    private static final String DESCRIPTION =
            "Report the live handshake: plugin and schema versions, the Visual "
            + "Paradigm edition in use, and the currently open project, if any.";

    private static final String INPUT_SCHEMA =
            "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}";

    private final ToolDefinition definition = new ToolDefinition("vp_capabilities", DESCRIPTION,
            JsonParser.parseString(INPUT_SCHEMA).getAsJsonObject());

    private final String pluginVersion;
    private final String schemaVersion;

    public CapabilitiesTool(String pluginVersion, String schemaVersion) {
        this.pluginVersion = pluginVersion;
        this.schemaVersion = schemaVersion;
    }

    @Override
    public ToolDefinition getDefinition() {
        return definition;
    }

    @Override
    public JsonObject execute(JsonObject params) throws McpToolException {
        JsonObject result = new JsonObject();
        result.addProperty("plugin_version", pluginVersion);
        result.addProperty("schema_version", schemaVersion);
        try {
            VPProductInfo product = ApplicationManager.instance().getProductInfo();
            result.addProperty("vp_name", product == null ? null : product.getName());
            result.addProperty("vp_version", product == null ? null : product.getVersion());
        } catch (RuntimeException | LinkageError unavailable) {
            result.addProperty("vp_error", unavailable.toString());
        }
        IProject project = ApplicationManager.instance().getProjectManager().getProject();
        result.addProperty("open_project", project == null ? null : project.getName());
        JsonObject guard = GuardStatus.latest();
        if (guard != null) {
            result.add("schema_guard", guard);
        }
        return result;
    }
}
