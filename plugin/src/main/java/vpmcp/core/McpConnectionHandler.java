package vpmcp.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Serves one connection: reads a request, runs it through {@link McpProtocolHandler} and
 * writes the response. POST carries a JSON-RPC message; GET opens the optional
 * server-to-client event stream that some clients expect to exist.
 *
 * <p>Each connection handles a single request and is then closed, which is why every
 * response carries {@code Connection: close}.
 */
final class McpConnectionHandler implements Runnable {

    private static final List<String> LOCAL_HOSTS = Arrays.asList("localhost", "127.0.0.1", "::1", "[::1]");
    private static final long SSE_KEEP_ALIVE_SECONDS = 25;

    private final Gson gson = new GsonBuilder().serializeNulls().create();

    private final Socket socket;
    private final String endpointPath;
    private final McpProtocolHandler protocol;
    private final BooleanSupplier running;
    private final ServerLog log;

    McpConnectionHandler(Socket socket, String endpointPath, McpProtocolHandler protocol, BooleanSupplier running, ServerLog log) {
        this.socket = socket;
        this.endpointPath = endpointPath;
        this.protocol = protocol;
        this.running = running;
        this.log = log;
    }

    @Override
    public void run() {
        try {
            InputStream input = new BufferedInputStream(socket.getInputStream());
            OutputStream output = socket.getOutputStream();

            HttpRequest request;
            try {
                request = HttpRequest.read(input);
            } catch (MalformedHttpException malformed) {
                writeText(output, 400, "Bad Request", "text/plain; charset=utf-8", malformed.getMessage());
                return;
            }
            if (request == null) {
                return;
            }
            route(request, output);
        } catch (IOException disconnected) {
            // The peer went away mid-exchange; there is nobody left to tell.
        } catch (RuntimeException failure) {
            log.error("MCP connection handler failed.", failure);
        } finally {
            closeQuietly();
        }
    }

    private void route(HttpRequest request, OutputStream output) throws IOException {
        if (!isLocalOrigin(request.getHeader("origin"))) {
            writeText(output, 403, "Forbidden", "text/plain; charset=utf-8", "Forbidden: non-local Origin.");
            return;
        }
        if (!endpointPath.equals(request.getPath())) {
            writeText(output, 404, "Not Found", "text/plain; charset=utf-8", "Not found. The MCP endpoint is " + endpointPath + ".");
            return;
        }
        if ("POST".equals(request.getMethod())) {
            handlePost(request, output);
        } else if ("GET".equals(request.getMethod())) {
            handleEventStream(output);
        } else {
            writeResponse(output, 405, "Method Not Allowed", "text/plain; charset=utf-8",
                    "Method not allowed.".getBytes(StandardCharsets.UTF_8), "Allow: GET, POST");
        }
    }

    private void handlePost(HttpRequest request, OutputStream output) throws IOException {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(request.getBody());
        } catch (JsonParseException invalid) {
            writeJson(output, 400, "Bad Request", JsonRpc.error(null, JsonRpc.PARSE_ERROR, "Invalid JSON."));
            return;
        }
        if (parsed == null || !parsed.isJsonObject()) {
            // MCP 2025-06-18 removed JSON-RPC batching, so arrays are not accepted either.
            writeJson(output, 400, "Bad Request", JsonRpc.error(null, JsonRpc.INVALID_REQUEST, "Expected a single JSON-RPC object."));
            return;
        }

        JsonObject response = protocol.handle(parsed.getAsJsonObject());
        if (response == null) {
            writeResponse(output, 202, "Accepted", null, new byte[0], null);
            return;
        }
        writeJson(output, 200, "OK", response);
    }

    private void handleEventStream(OutputStream output) throws IOException {
        StringBuilder headers = new StringBuilder();
        headers.append("HTTP/1.1 200 OK\r\n");
        headers.append("Content-Type: text/event-stream; charset=utf-8\r\n");
        headers.append("Cache-Control: no-cache\r\n");
        headers.append("Connection: close\r\n\r\n");
        output.write(headers.toString().getBytes(StandardCharsets.UTF_8));
        output.flush();

        try {
            while (running.getAsBoolean()) {
                output.write(": keep-alive\n\n".getBytes(StandardCharsets.UTF_8));
                output.flush();
                for (long tick = 0; tick < SSE_KEEP_ALIVE_SECONDS && running.getAsBoolean(); tick++) {
                    Thread.sleep(1000);
                }
            }
        } catch (IOException disconnected) {
            // The client closed the stream; nothing to report.
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isLocalOrigin(String origin) {
        if (origin == null) {
            return true;
        }
        try {
            String host = new URI(origin).getHost();
            return host != null && LOCAL_HOSTS.contains(host.toLowerCase());
        } catch (URISyntaxException malformed) {
            return false;
        }
    }

    private void writeJson(OutputStream output, int status, String reason, JsonObject body) throws IOException {
        writeText(output, status, reason, "application/json; charset=utf-8", gson.toJson(body));
    }

    private void writeText(OutputStream output, int status, String reason, String contentType, String body) throws IOException {
        writeResponse(output, status, reason, contentType, body.getBytes(StandardCharsets.UTF_8), null);
    }

    private void writeResponse(OutputStream output, int status, String reason, String contentType, byte[] body, String extraHeader) throws IOException {
        StringBuilder headers = new StringBuilder();
        headers.append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n");
        if (contentType != null) {
            headers.append("Content-Type: ").append(contentType).append("\r\n");
        }
        if (extraHeader != null) {
            headers.append(extraHeader).append("\r\n");
        }
        headers.append("Content-Length: ").append(body.length).append("\r\n");
        headers.append("Connection: close\r\n\r\n");

        output.write(headers.toString().getBytes(StandardCharsets.UTF_8));
        output.write(body);
        output.flush();
    }

    private void closeQuietly() {
        try {
            socket.close();
        } catch (IOException alreadyClosed) {
            // Nothing useful to do about a socket that will not close.
        }
    }
}
