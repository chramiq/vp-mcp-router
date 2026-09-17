package vpmcp;

import com.vp.plugin.ApplicationManager;
import com.vp.plugin.VPPlugin;
import com.vp.plugin.VPPluginInfo;
import com.vp.plugin.VPProductInfo;
import java.io.File;
import vpmcp.core.McpServer;
import vpmcp.core.McpToolRegistry;
import vpmcp.tools.GetDiagramByUrlTool;
import vpmcp.vp.EdtToolInvoker;
import vpmcp.vp.McpServerHandle;
import vpmcp.vp.ServerConfig;
import vpmcp.vp.VpLog;

/**
 * Entry point declared in plugin.xml. Starts the MCP endpoint when Visual Paradigm loads the
 * plug-in and shuts it down on unload.
 *
 * <p>New capabilities are added by writing a class that implements
 * {@link vpmcp.core.McpTool} and registering it below; nothing else has to change.
 */
public final class VpMcpPlugin implements VPPlugin {

    private static final String VERSION = "1.0.0";

    private McpServer server;

    @Override
    public void loaded(VPPluginInfo info) {
        File pluginDir = info == null ? null : info.getPluginDir();
        VpLog.useLogFile(pluginDir);
        logEnvironment(pluginDir);

        try {
            ServerConfig config = ServerConfig.load(pluginDir);
            McpToolRegistry registry = new McpToolRegistry().register(new GetDiagramByUrlTool());
            server = new McpServer(config.getBindAddress(), config.getPort(), registry, new EdtToolInvoker(), VERSION, VpLog::error);
            server.start();
            McpServerHandle.started(server, registry, pluginDir);
            VpLog.info("MCP server listening on " + server.getEndpointUrl());
        } catch (Throwable failure) {
            // A failure here must not take Visual Paradigm down with it; the most likely cause
            // is the port already being in use, which mcp.properties can fix.
            server = null;
            McpServerHandle.failed(failure.getClass().getSimpleName() + ": " + failure.getMessage(), pluginDir);
            VpLog.error("MCP server failed to start.", failure);
        }
    }

    @Override
    public void unloaded() {
        if (server != null) {
            server.stop();
            server = null;
            McpServerHandle.stopped();
            VpLog.info("MCP server stopped.");
        }
    }

    /** Recorded on every load, because it answers most of the questions a failure raises. */
    private void logEnvironment(File pluginDir) {
        VpLog.info("--- Visual Paradigm MCP Server " + VERSION + " loading ---");
        VpLog.info("Plugin directory : " + pluginDir);
        VpLog.info("Java runtime     : " + System.getProperty("java.version") + " at " + System.getProperty("java.home"));
        try {
            ApplicationManager application = ApplicationManager.instance();
            VPProductInfo product = application == null ? null : application.getProductInfo();
            if (product != null) {
                VpLog.info("Visual Paradigm  : " + product.getName() + " " + product.getVersion() + " build " + product.getBuildNumber());
            }
        } catch (RuntimeException | LinkageError unavailable) {
            VpLog.info("Visual Paradigm  : product info unavailable (" + unavailable + ")");
        }
    }
}
