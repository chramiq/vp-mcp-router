package vpmcp.core;

import com.google.gson.JsonObject;

/** Name, description and input JSON Schema advertised for a tool by {@code tools/list}. */
public final class ToolDefinition {

    private final String name;
    private final String description;
    private final JsonObject inputSchema;

    public ToolDefinition(String name, String description, JsonObject inputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public JsonObject getInputSchema() {
        return inputSchema;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("description", description);
        json.add("inputSchema", inputSchema.deepCopy());
        return json;
    }
}
