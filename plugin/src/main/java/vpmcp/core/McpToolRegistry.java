package vpmcp.core;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds every tool the server exposes. Tools must be registered before the server starts;
 * afterwards the registry is only read, so no synchronisation is needed.
 */
public final class McpToolRegistry {

    private final Map<String, McpTool> tools = new LinkedHashMap<>();

    public McpToolRegistry register(McpTool tool) {
        String name = tool.getDefinition().getName();
        if (tools.containsKey(name)) {
            throw new IllegalArgumentException("Duplicate MCP tool name: " + name);
        }
        tools.put(name, tool);
        return this;
    }

    public McpTool get(String name) {
        return tools.get(name);
    }

    public Collection<McpTool> list() {
        return Collections.unmodifiableCollection(tools.values());
    }
}
