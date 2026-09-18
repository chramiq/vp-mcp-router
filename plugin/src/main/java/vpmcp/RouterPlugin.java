package vpmcp;

import com.google.gson.JsonObject;
import com.vp.plugin.ApplicationManager;
import com.vp.plugin.VPPlugin;
import com.vp.plugin.VPPluginInfo;
import com.vp.plugin.VPProductInfo;
import java.io.File;
import vpmcp.core.McpServer;
import vpmcp.core.McpToolRegistry;
import vpmcp.tools.ApplyBatchTool;
import vpmcp.tools.CapabilitiesTool;
import vpmcp.tools.ExportImageTool;
import vpmcp.tools.GetDiagramByUrlTool;
import vpmcp.tools.NeighborhoodTool;
import vpmcp.tools.PreviewBatchTool;
import vpmcp.tools.ListDiagramsTool;
import vpmcp.vp.EdtToolInvoker;
import vpmcp.vp.FileResourceProvider;
import vpmcp.vp.GuardStatus;
import vpmcp.vp.McpServerHandle;
import vpmcp.vp.SchemaGuard;
import vpmcp.vp.ServerConfig;
import vpmcp.vp.VpLog;

/**
 * Router entry point. Mirrors the vendored plugin wiring but registers the
 * router toolset; vendored files stay untouched so upstream diffs stay clean.
 */
public final class RouterPlugin implements VPPlugin {

    private static final String VERSION = "0.8.0";
    private static final String SCHEMA_VERSION = "v18.1";

    private McpServer server;

    @Override
    public void loaded(VPPluginInfo info) {
        File pluginDir = info == null ? null : info.getPluginDir();
        VpLog.useLogFile(pluginDir);
        logEnvironment(pluginDir);

        try {
            ServerConfig config = ServerConfig.load(pluginDir);
            JsonObject capabilities = capabilitiesSnapshot();
            JsonObject guard = SchemaGuard.check(pluginDir, SCHEMA_VERSION,
                    capabilities.has("vp_version") && !capabilities.get("vp_version").isJsonNull()
                            ? capabilities.get("vp_version").getAsString() : null);
            GuardStatus.publish(guard);
            capabilities.add("schema_guard", guard);
            if (guard.has("warning")) {
                VpLog.info("Schema guard: " + guard.get("warning").getAsString());
            }
            McpToolRegistry registry = new McpToolRegistry()
                    .register(new CapabilitiesTool(VERSION, SCHEMA_VERSION))
                    .register(new ListDiagramsTool())
                    .register(new ExportImageTool())
                    .register(new PreviewBatchTool())
                    .register(new ApplyBatchTool())
                    .register(new NeighborhoodTool())
                    .register(new GetDiagramByUrlTool());
            server = new McpServer(config.getBindAddress(), config.getPort(), registry,
                    new EdtToolInvoker(), VERSION, VpLog::error,
                    new FileResourceProvider(pluginDir, SCHEMA_VERSION, capabilities));
            server.start();
            McpServerHandle.started(server, registry, pluginDir);
            VpLog.info("MCP server listening on " + server.getEndpointUrl());
        } catch (Throwable failure) {
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

    private void logEnvironment(File pluginDir) {
        VpLog.info("--- VP MCP Router " + VERSION + " loading ---");
        VpLog.info("Plugin directory : " + pluginDir);
        VpLog.info("Java runtime     : " + System.getProperty("java.version"));
        try {
            ApplicationManager application = ApplicationManager.instance();
            VPProductInfo product = application == null ? null : application.getProductInfo();
            if (product != null) {
                VpLog.info("Visual Paradigm  : " + product.getName() + " " + product.getVersion());
            }
        } catch (RuntimeException | LinkageError unavailable) {
            VpLog.info("Visual Paradigm  : product info unavailable (" + unavailable + ")");
        }
    }

    private JsonObject capabilitiesSnapshot() {
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("plugin_version", VERSION);
        snapshot.addProperty("schema_version", SCHEMA_VERSION);
        try {
            ApplicationManager application = ApplicationManager.instance();
            VPProductInfo product = application == null ? null : application.getProductInfo();
            snapshot.addProperty("vp_name", product == null ? null : product.getName());
            snapshot.addProperty("vp_version", product == null ? null : product.getVersion());
        } catch (RuntimeException | LinkageError unavailable) {
            snapshot.addProperty("vp_error", unavailable.toString());
        }
        return snapshot;
    }
}
