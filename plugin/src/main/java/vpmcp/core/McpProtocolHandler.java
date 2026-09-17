package vpmcp.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Implements the MCP methods on top of parsed JSON-RPC envelopes. Deliberately free of any
 * Visual Paradigm dependency, so the whole protocol layer can be exercised outside of VP.
 */
public final class McpProtocolHandler {

    public static final String PROTOCOL_VERSION = "2025-06-18";

    private static final String SERVER_NAME = "visual-paradigm-mcp";

    /** Explicit nulls are meaningful here: they mean "Visual Paradigm reported no value". */
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    private final McpToolRegistry registry;
    private final ToolInvoker invoker;
    private final String serverVersion;
    private final ResourceProvider resources;

    public McpProtocolHandler(McpToolRegistry registry, ToolInvoker invoker, String serverVersion) {
        this(registry, invoker, serverVersion, null);
    }

    public McpProtocolHandler(McpToolRegistry registry, ToolInvoker invoker, String serverVersion,
            ResourceProvider resources) {
        this.registry = registry;
        this.invoker = invoker;
        this.serverVersion = serverVersion;
        this.resources = resources;
    }

    /**
     * @return the response envelope, or null when the message was a notification and the
     *         caller should answer with an empty HTTP 202.
     */
    public JsonObject handle(JsonObject request) {
        JsonElement id = request.get("id");

        if (!request.has("method") || !request.get("method").isJsonPrimitive()) {
            return JsonRpc.error(id, JsonRpc.INVALID_REQUEST, "Missing \"method\".");
        }
        String method = request.get("method").getAsString();

        if (method.startsWith("notifications/")) {
            return null;
        }

        switch (method) {
            case "initialize":
                return JsonRpc.result(id, initialize());
            case "ping":
                return JsonRpc.result(id, new JsonObject());
            case "tools/list":
                return JsonRpc.result(id, toolsList());
            case "tools/call":
                return toolsCall(id, request);
            case "resources/list":
                return resourcesList(id);
            case "resources/read":
                return resourcesRead(id, request);
            default:
                return JsonRpc.error(id, JsonRpc.METHOD_NOT_FOUND, "Unknown method: " + method);
        }
    }

    private JsonObject initialize() {
        JsonObject tools = new JsonObject();
        tools.addProperty("listChanged", false);

        JsonObject capabilities = new JsonObject();
        capabilities.add("tools", tools);
        if (resources != null) {
            JsonObject resourceCaps = new JsonObject();
            resourceCaps.addProperty("listChanged", false);
            capabilities.add("resources", resourceCaps);
        }

        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", SERVER_NAME);
        serverInfo.addProperty("version", serverVersion);

        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", PROTOCOL_VERSION);
        result.add("capabilities", capabilities);
        result.add("serverInfo", serverInfo);
        return result;
    }

    private JsonObject resourcesList(JsonElement id) {
        if (resources == null) {
            return JsonRpc.error(id, JsonRpc.METHOD_NOT_FOUND, "Unknown method: resources/list");
        }
        try {
            JsonObject result = new JsonObject();
            result.add("resources", resources.list());
            return JsonRpc.result(id, result);
        } catch (McpToolException failure) {
            return JsonRpc.result(id, toolResult(failure.getMessage(), true));
        }
    }

    private JsonObject resourcesRead(JsonElement id, JsonObject request) {
        if (resources == null) {
            return JsonRpc.error(id, JsonRpc.METHOD_NOT_FOUND, "Unknown method: resources/read");
        }
        JsonElement paramsElement = request.get("params");
        if (paramsElement == null || !paramsElement.isJsonObject()) {
            return JsonRpc.error(id, JsonRpc.INVALID_PARAMS, "\"params\" must be an object.");
        }
        JsonElement uriElement = paramsElement.getAsJsonObject().get("uri");
        if (uriElement == null || !uriElement.isJsonPrimitive()) {
            return JsonRpc.error(id, JsonRpc.INVALID_PARAMS, "\"params.uri\" is required.");
        }
        try {
            JsonArray contents = new JsonArray();
            contents.add(resources.read(uriElement.getAsString()));
            JsonObject result = new JsonObject();
            result.add("contents", contents);
            return JsonRpc.result(id, result);
        } catch (McpToolException failure) {
            return JsonRpc.error(id, JsonRpc.INVALID_PARAMS, failure.getMessage());
        }
    }

    private JsonObject toolsList() {
        JsonArray tools = new JsonArray();
        for (McpTool tool : registry.list()) {
            tools.add(tool.getDefinition().toJson());
        }
        JsonObject result = new JsonObject();
        result.add("tools", tools);
        return result;
    }

    private JsonObject toolsCall(JsonElement id, JsonObject request) {
        JsonElement paramsElement = request.get("params");
        if (paramsElement == null || !paramsElement.isJsonObject()) {
            return JsonRpc.error(id, JsonRpc.INVALID_PARAMS, "\"params\" must be an object.");
        }
        JsonObject params = paramsElement.getAsJsonObject();

        JsonElement nameElement = params.get("name");
        if (nameElement == null || !nameElement.isJsonPrimitive()) {
            return JsonRpc.error(id, JsonRpc.INVALID_PARAMS, "\"params.name\" is required.");
        }
        String name = nameElement.getAsString();

        McpTool tool = registry.get(name);
        if (tool == null) {
            return JsonRpc.error(id, JsonRpc.INVALID_PARAMS, "Unknown tool: " + name);
        }

        JsonElement argumentsElement = params.get("arguments");
        JsonObject arguments = argumentsElement != null && argumentsElement.isJsonObject()
                ? argumentsElement.getAsJsonObject()
                : new JsonObject();

        try {
            JsonObject output = invoker.invoke(tool, arguments);
            return JsonRpc.result(id, toolResult(gson.toJson(output), false));
        } catch (Throwable failure) {
            // Includes NoClassDefFoundError when the Open API is absent, which is what
            // happens when the protocol layer is exercised outside Visual Paradigm.
            return JsonRpc.result(id, toolResult(describe(failure), true));
        }
    }

    private JsonObject toolResult(String text, boolean isError) {
        JsonObject content = new JsonObject();
        content.addProperty("type", "text");
        content.addProperty("text", text);

        JsonArray contents = new JsonArray();
        contents.add(content);

        JsonObject result = new JsonObject();
        result.add("content", contents);
        result.addProperty("isError", isError);
        return result;
    }

    private String describe(Throwable failure) {
        StringBuilder message = new StringBuilder();
        message.append(failure.getClass().getSimpleName());
        if (failure.getMessage() != null) {
            message.append(": ").append(failure.getMessage());
        }
        Throwable cause = failure.getCause();
        for (int depth = 0; cause != null && depth < 5; depth++) {
            message.append(" <- ").append(cause.getClass().getSimpleName());
            if (cause.getMessage() != null) {
                message.append(": ").append(cause.getMessage());
            }
            cause = cause.getCause();
        }
        return message.toString();
    }
}
