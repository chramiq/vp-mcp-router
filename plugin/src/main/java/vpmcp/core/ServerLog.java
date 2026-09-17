package vpmcp.core;

/**
 * Where the server reports failures that cannot be handed back to a client, such as a
 * connection that broke before a response could be written. Without this the plug-in would
 * fail silently, which is the hardest kind of failure to diagnose inside Visual Paradigm.
 */
public interface ServerLog {

    void error(String message, Throwable failure);
}
