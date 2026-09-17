package vpmcp.core;

import com.google.gson.JsonObject;

/**
 * A single MCP tool. Adding a new capability to the server means writing one class that
 * implements this interface and registering it in an {@link McpToolRegistry}.
 */
public interface McpTool {

    ToolDefinition getDefinition();

    /**
     * @param params the {@code arguments} object of a {@code tools/call} request; never null,
     *               but possibly empty.
     * @return the tool result, serialised into the response as JSON text.
     */
    JsonObject execute(JsonObject params) throws McpToolException;
}
