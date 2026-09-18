package vpmcp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import vpmcp.core.McpTool;
import vpmcp.core.McpToolException;
import vpmcp.core.ToolDefinition;

/** Minimal image-envelope producer for protocol tests. Never shipped as a tool. */
final class StubImageTool implements McpTool {

    private final ToolDefinition definition = new ToolDefinition("stub-image", "stub",
            JsonParser.parseString("{\"type\":\"object\"}").getAsJsonObject());

    @Override
    public ToolDefinition getDefinition() {
        return definition;
    }

    @Override
    public JsonObject execute(JsonObject params) throws McpToolException {
        JsonObject output = new JsonObject();
        output.addProperty("summary", "stub render");
        output.addProperty("image_mime", "image/png");
        output.addProperty("image_data", "aGVsbG8=");
        return output;
    }
}
