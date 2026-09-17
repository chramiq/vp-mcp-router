package vpmcp.core;

import com.google.gson.JsonObject;

/** Runs the tool on the calling thread. Used by tools with no thread affinity. */
public final class DirectToolInvoker implements ToolInvoker {

    @Override
    public JsonObject invoke(McpTool tool, JsonObject arguments) throws Exception {
        return tool.execute(arguments);
    }
}
