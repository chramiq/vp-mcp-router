package vpmcp.dev;

import vpmcp.core.DirectToolInvoker;
import vpmcp.core.McpServer;
import vpmcp.core.McpToolRegistry;
import vpmcp.tools.GetDiagramByUrlTool;

/**
 * Runs the MCP endpoint outside Visual Paradigm so the protocol can be exercised with an
 * HTTP client. Calling {@code get_diagram_by_url} here reports that no project is open,
 * which is exactly the behaviour that should be verified.
 */
public final class DevServer {

    private DevServer() {
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8899;

        McpToolRegistry registry = new McpToolRegistry()
                .register(new EchoTool())
                .register(new GetDiagramByUrlTool());

        McpServer server = new McpServer("127.0.0.1", port, registry, new DirectToolInvoker(), "1.0.0-dev", DevServer::log);
        server.start();
        System.out.println("DevServer listening on " + server.getEndpointUrl());

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        Thread.currentThread().join();
    }

    private static void log(String message, Throwable failure) {
        System.err.println(message);
        if (failure != null) {
            failure.printStackTrace();
        }
    }
}
