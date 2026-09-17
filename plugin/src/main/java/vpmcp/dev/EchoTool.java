package vpmcp.dev;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import vpmcp.core.McpTool;
import vpmcp.core.ToolDefinition;

/** A tool with no Visual Paradigm dependency, used to exercise the transport by itself. */
public final class EchoTool implements McpTool {

    private static final String INPUT_SCHEMA =
            "{\"type\":\"object\","
            + "\"properties\":{\"message\":{\"type\":\"string\",\"description\":\"Text to echo back.\"}},"
            + "\"required\":[\"message\"],"
            + "\"additionalProperties\":false}";

    private final ToolDefinition definition = new ToolDefinition("echo",
            "Echo the given message back. Exists so the MCP transport can be tested without Visual Paradigm.",
            JsonParser.parseString(INPUT_SCHEMA).getAsJsonObject());

    @Override
    public ToolDefinition getDefinition() {
        return definition;
    }

    @Override
    public JsonObject execute(JsonObject params) {
        JsonElement message = params.get("message");
        JsonObject result = new JsonObject();
        result.addProperty("echo", message == null ? null : message.getAsString());
        return result;
    }
}
