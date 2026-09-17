package vpmcp.core;

import com.google.gson.JsonObject;

/**
 * Runs a tool on whatever thread its backing API requires. HTTP requests arrive on pool
 * threads, but the Visual Paradigm Open API expects to be used from the Swing event
 * dispatch thread, so the plug-in supplies an implementation that hops onto it.
 */
public interface ToolInvoker {

    JsonObject invoke(McpTool tool, JsonObject arguments) throws Exception;
}
