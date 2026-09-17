package vpmcp.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Serves MCP resources. Implementations stay behind this boundary so the
 * protocol layer never touches filesystem or VP state directly.
 */
public interface ResourceProvider {

    /** Descriptors of the form {uri, name, mimeType}. */
    JsonArray list() throws McpToolException;

    /** Content envelope of the form {uri, mimeType, text}. */
    JsonObject read(String uri) throws McpToolException;
}
