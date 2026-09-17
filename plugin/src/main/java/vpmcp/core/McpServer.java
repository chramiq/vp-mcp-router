package vpmcp.core;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Embedded MCP endpoint. Binds a loopback address only, as required by the Streamable HTTP
 * transport, and serves the whole protocol from a single {@code /mcp} path.
 *
 * <p>Built on a plain {@link ServerSocket} rather than {@code com.sun.net.httpserver}: the
 * JRE bundled with Visual Paradigm does not include the {@code jdk.httpserver} module, so
 * using it fails at load time with {@code NoClassDefFoundError}. Everything here comes from
 * {@code java.base}.
 */
public final class McpServer {

    public static final String ENDPOINT_PATH = "/mcp";

    private static final int ACCEPT_BACKLOG = 50;
    private static final int READ_TIMEOUT_MILLIS = 30_000;

    private final String bindAddress;
    private final int port;
    private final McpProtocolHandler protocol;
    private final ServerLog log;

    private volatile boolean running;
    private ServerSocket serverSocket;
    private ExecutorService workers;

    public McpServer(String bindAddress, int port, McpToolRegistry registry, ToolInvoker invoker, String serverVersion, ServerLog log) {
        this(bindAddress, port, registry, invoker, serverVersion, log, null);
    }

    public McpServer(String bindAddress, int port, McpToolRegistry registry, ToolInvoker invoker, String serverVersion, ServerLog log,
            ResourceProvider resources) {
        this.bindAddress = bindAddress;
        this.port = port;
        this.protocol = new McpProtocolHandler(registry, invoker, serverVersion, resources);
        this.log = log;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(InetAddress.getByName(bindAddress), port), ACCEPT_BACKLOG);

        running = true;
        workers = Executors.newCachedThreadPool(runnable -> newDaemon(runnable, "vpmcp-http"));
        newDaemon(this::acceptLoop, "vpmcp-accept").start();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(READ_TIMEOUT_MILLIS);
                workers.execute(new McpConnectionHandler(socket, ENDPOINT_PATH, protocol, this::isRunning, log));
            } catch (IOException failure) {
                if (running) {
                    log.error("MCP server stopped accepting connections.", failure);
                }
                return;
            } catch (RuntimeException failure) {
                if (running) {
                    log.error("MCP server failed to dispatch a connection.", failure);
                }
            }
        }
    }

    public void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException alreadyClosed) {
                // Closing the listening socket is what breaks the accept loop; failure is moot.
            }
            serverSocket = null;
        }
        if (workers != null) {
            workers.shutdownNow();
            workers = null;
        }
    }

    public boolean isRunning() {
        return running;
    }

    public String getEndpointUrl() {
        return "http://" + bindAddress + ":" + getPort() + ENDPOINT_PATH;
    }

    /** The port actually bound, which differs from the requested one when 0 was requested. */
    public int getPort() {
        ServerSocket bound = serverSocket;
        return bound != null && bound.isBound() ? bound.getLocalPort() : port;
    }

    private static Thread newDaemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }
}
