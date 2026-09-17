package vpmcp.vp;

import java.io.File;
import vpmcp.core.McpServer;
import vpmcp.core.McpToolRegistry;

/**
 * Publishes the running server so the menu action can report on it.
 *
 * <p>Visual Paradigm instantiates action controllers itself, from the class name in
 * plugin.xml, so there is no constructor through which the instance could be handed over.
 */
public final class McpServerHandle {

    private static volatile McpServer server;
    private static volatile McpToolRegistry registry;
    private static volatile String failure;
    private static volatile File pluginDir;

    private McpServerHandle() {
    }

    public static void started(McpServer startedServer, McpToolRegistry startedRegistry, File dir) {
        server = startedServer;
        registry = startedRegistry;
        failure = null;
        pluginDir = dir;
    }

    public static void failed(String reason, File dir) {
        server = null;
        registry = null;
        failure = reason;
        pluginDir = dir;
    }

    public static void stopped() {
        server = null;
        registry = null;
    }

    public static McpServer getServer() {
        return server;
    }

    public static McpToolRegistry getRegistry() {
        return registry;
    }

    /** Why the server is not running, or null when it started normally. */
    public static String getFailure() {
        return failure;
    }

    public static File getPluginDir() {
        return pluginDir;
    }
}
