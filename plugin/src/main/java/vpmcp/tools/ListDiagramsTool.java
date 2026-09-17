package vpmcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IProject;
import vpmcp.core.McpTool;
import vpmcp.core.McpToolException;
import vpmcp.core.ToolDefinition;
import vpmcp.vp.DiagramLocator;

/** Lists the diagrams of the open project as a light index. */
public final class ListDiagramsTool implements McpTool {

    private static final String DESCRIPTION =
            "List the diagrams of the Visual Paradigm project that is currently open. "
            + "Returns a light index of id, name and type per diagram; "
            + "use get_diagram_by_url to read one in full.";

    private static final String INPUT_SCHEMA =
            "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}";

    private final ToolDefinition definition = new ToolDefinition("vp_list_diagrams", DESCRIPTION,
            JsonParser.parseString(INPUT_SCHEMA).getAsJsonObject());

    @Override
    public ToolDefinition getDefinition() {
        return definition;
    }

    @Override
    public JsonObject execute(JsonObject params) throws McpToolException {
        IProject project = DiagramLocator.requireOpenProject();
        IDiagramUIModel[] diagrams = project.toDiagramArray();

        JsonArray list = new JsonArray();
        if (diagrams != null) {
            for (IDiagramUIModel diagram : diagrams) {
                if (diagram == null) {
                    continue;
                }
                JsonObject entry = new JsonObject();
                entry.addProperty("id", diagram.getId());
                entry.addProperty("name", diagram.getName());
                entry.addProperty("type", diagram.getType());
                list.add(entry);
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("project_name", project.getName());
        result.addProperty("count", list.size());
        result.add("diagrams", list);
        return result;
    }
}
