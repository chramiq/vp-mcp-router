package vpmcp.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * One parsed HTTP/1.1 request.
 *
 * <p>Parsed by hand rather than through {@code com.sun.net.httpserver}, because the JRE
 * bundled with Visual Paradigm does not ship the {@code jdk.httpserver} module. Only what
 * an MCP client actually sends is supported: a request line, headers, and a body sized by
 * {@code Content-Length} or delivered in chunks.
 */
public final class HttpRequest {

    private static final int MAX_LINE_BYTES = 8192;
    private static final int MAX_HEADERS = 100;
    private static final int MAX_BODY_BYTES = 16 * 1024 * 1024;

    private final String method;
    private final String path;
    private final Map<String, String> headers;
    private final String body;

    private HttpRequest(String method, String path, Map<String, String> headers, String body) {
        this.method = method;
        this.path = path;
        this.headers = headers;
        this.body = body;
    }

    /**
     * @return the request, or null when the peer closed the connection without sending one.
     * @throws MalformedHttpException when the bytes are not a request this server can serve.
     */
    public static HttpRequest read(InputStream input) throws IOException, MalformedHttpException {
        String requestLine = readLine(input);
        if (requestLine == null || requestLine.isEmpty()) {
            return null;
        }

        String[] parts = requestLine.split(" ");
        if (parts.length < 2) {
            throw new MalformedHttpException("Malformed request line: " + requestLine);
        }
        String method = parts[0].toUpperCase();
        String target = parts[1];
        int queryStart = target.indexOf('?');
        String path = queryStart >= 0 ? target.substring(0, queryStart) : target;

        Map<String, String> headers = new HashMap<>();
        for (int count = 0; ; count++) {
            String line = readLine(input);
            if (line == null || line.isEmpty()) {
                break;
            }
            if (count >= MAX_HEADERS) {
                throw new MalformedHttpException("Too many headers.");
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.put(line.substring(0, colon).trim().toLowerCase(), line.substring(colon + 1).trim());
            }
        }

        return new HttpRequest(method, path, headers, readBody(input, headers));
    }

    private static String readBody(InputStream input, Map<String, String> headers) throws IOException, MalformedHttpException {
        String encoding = headers.get("transfer-encoding");
        if (encoding != null && encoding.toLowerCase().contains("chunked")) {
            return new String(readChunked(input), StandardCharsets.UTF_8);
        }

        String length = headers.get("content-length");
        if (length == null) {
            return "";
        }
        int size;
        try {
            size = Integer.parseInt(length.trim());
        } catch (NumberFormatException notANumber) {
            throw new MalformedHttpException("Content-Length is not a number: " + length);
        }
        if (size < 0 || size > MAX_BODY_BYTES) {
            throw new MalformedHttpException("Unsupported Content-Length: " + size);
        }
        byte[] body = new byte[size];
        readFully(input, body);
        return new String(body, StandardCharsets.UTF_8);
    }

    private static byte[] readChunked(InputStream input) throws IOException, MalformedHttpException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        while (true) {
            String sizeLine = readLine(input);
            if (sizeLine == null) {
                throw new MalformedHttpException("Truncated chunked body.");
            }
            int extension = sizeLine.indexOf(';');
            if (extension >= 0) {
                sizeLine = sizeLine.substring(0, extension);
            }
            int size;
            try {
                size = Integer.parseInt(sizeLine.trim(), 16);
            } catch (NumberFormatException notHex) {
                throw new MalformedHttpException("Malformed chunk size: " + sizeLine);
            }
            if (size == 0) {
                String trailer;
                while ((trailer = readLine(input)) != null && !trailer.isEmpty()) {
                    // Trailing headers are read and discarded.
                }
                return body.toByteArray();
            }
            if (body.size() + size > MAX_BODY_BYTES) {
                throw new MalformedHttpException("Chunked body is too large.");
            }
            byte[] chunk = new byte[size];
            readFully(input, chunk);
            readLine(input);
            body.write(chunk);
        }
    }

    private static void readFully(InputStream input, byte[] target) throws IOException, MalformedHttpException {
        int filled = 0;
        while (filled < target.length) {
            int read = input.read(target, filled, target.length - filled);
            if (read == -1) {
                throw new MalformedHttpException("Body ended after " + filled + " of " + target.length + " bytes.");
            }
            filled += read;
        }
    }

    private static String readLine(InputStream input) throws IOException, MalformedHttpException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int read = input.read();
        if (read == -1) {
            return null;
        }
        while (read != -1 && read != '\n') {
            if (read != '\r') {
                if (line.size() >= MAX_LINE_BYTES) {
                    throw new MalformedHttpException("Header line exceeds " + MAX_LINE_BYTES + " bytes.");
                }
                line.write(read);
            }
            read = input.read();
        }
        return new String(line.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    /** @param name lower-case header name. */
    public String getHeader(String name) {
        return headers.get(name);
    }

    public String getBody() {
        return body;
    }
}
