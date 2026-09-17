package vpmcp.core;

/**
 * A tool failure that is meaningful to the caller of the tool, such as a diagram that
 * cannot be found. It is reported back as tool output with {@code isError: true} rather
 * than as a JSON-RPC transport error, so the model can read the reason and react to it.
 */
public class McpToolException extends Exception {

    public McpToolException(String message) {
        super(message);
    }

    public McpToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
