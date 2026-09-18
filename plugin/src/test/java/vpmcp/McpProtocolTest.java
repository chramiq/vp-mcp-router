package vpmcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vpmcp.core.DirectToolInvoker;
import vpmcp.core.JsonRpc;
import vpmcp.core.McpProtocolHandler;
import vpmcp.core.McpToolRegistry;
import vpmcp.dev.EchoTool;
import vpmcp.vp.FileResourceProvider;

/**
 * Protocol envelope contracts: malformed input maps to JSON-RPC errors,
 * image outputs become image content blocks, and a handler without a
 * resource provider stays tools-only. Each test breaks iff the mapping
 * it names regresses.
 */
class McpProtocolTest {

    private final McpProtocolHandler toolsOnly = new McpProtocolHandler(
            new McpToolRegistry().register(new EchoTool()), new DirectToolInvoker(), "test");

    @Test
    void missingMethodIsInvalidRequest() {
        JsonObject response = toolsOnly.handle(request("{\"jsonrpc\":\"2.0\",\"id\":1}"));

        assertEquals(JsonRpc.INVALID_REQUEST, response.get("error").getAsJsonObject().get("code").getAsInt());
    }

    @Test
    void unknownMethodIsNotFound() {
        JsonObject response = toolsOnly.handle(request("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"nope\"}"));

        assertEquals(JsonRpc.METHOD_NOT_FOUND, response.get("error").getAsJsonObject().get("code").getAsInt());
    }

    @Test
    void notificationsGetNoResponse() {
        JsonObject response =
                toolsOnly.handle(request("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"));

        assertEquals(null, response);
    }

    @Test
    void toolCallWithoutParamsIsInvalid() {
        JsonObject response =
                toolsOnly.handle(request("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\"}"));

        assertEquals(JsonRpc.INVALID_PARAMS, response.get("error").getAsJsonObject().get("code").getAsInt());
    }

    @Test
    void unknownToolIsInvalidParams() {
        JsonObject response = toolsOnly.handle(request(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"nope\"}}"));

        assertEquals(JsonRpc.INVALID_PARAMS, response.get("error").getAsJsonObject().get("code").getAsInt());
    }

    @Test
    void echoRoundTripsThroughToolsCall() {
        JsonObject response = toolsOnly.handle(request("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"echo\",\"arguments\":{\"message\":\"hello\"}}}"));

        assertFalse(response.getAsJsonObject("result").get("isError").getAsBoolean());
        assertTrue(response.toString().contains("hello"));
    }

    @Test
    void imageOutputBecomesImageContentBlock() {
        McpProtocolHandler handler = new McpProtocolHandler(new McpToolRegistry()
                .register(new StubImageTool()), new DirectToolInvoker(), "test");

        JsonObject response = handler.handle(request("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"stub-image\",\"arguments\":{}}}"));

        JsonObject result = response.getAsJsonObject("result");
        assertFalse(result.get("isError").getAsBoolean());
        assertEquals("text", result.getAsJsonArray("content").get(0).getAsJsonObject().get("type").getAsString());
        JsonObject image = result.getAsJsonArray("content").get(1).getAsJsonObject();
        assertEquals("image", image.get("type").getAsString());
        assertEquals("aGVsbG8=", image.get("data").getAsString());
        assertEquals("image/png", image.get("mimeType").getAsString());
        assertFalse(response.toString().contains("\"image_data\""));
    }

    @Test
    void legacyHandlerStaysToolsOnly() {
        JsonObject response =
                toolsOnly.handle(request("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"resources/list\"}"));

        assertEquals(JsonRpc.METHOD_NOT_FOUND, response.get("error").getAsJsonObject().get("code").getAsInt());
    }

    @Test
    void resourcesListServesPackAndAliases(@TempDir File dir) {
        McpProtocolHandler handler = handlerWithPack(dir);

        JsonObject response =
                handler.handle(request("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"resources/list\"}"));

        String listed = response.getAsJsonObject("result").getAsJsonArray("resources").toString();
        assertTrue(listed.contains("vp://capabilities"));
        assertTrue(listed.contains("vp://diagram-types"));
        assertTrue(listed.contains("vp://schemas/vT/meta.json"));
    }

    @Test
    void resourcesReadServesFilesAndRejectsTraversal(@TempDir File dir) {
        McpProtocolHandler handler = handlerWithPack(dir);

        JsonObject meta = handler.handle(request("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"resources/read\","
                + "\"params\":{\"uri\":\"vp://schemas/vT/meta.json\"}}"));
        assertTrue(meta.getAsJsonObject("result").getAsJsonArray("contents").get(0).getAsJsonObject()
                .get("text").getAsString().contains("vT"));

        JsonObject traversal = handler.handle(request("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"resources/read\","
                + "\"params\":{\"uri\":\"vp://schemas/vT/../../other.json\"}}"));
        assertEquals(JsonRpc.INVALID_PARAMS,
                traversal.get("error").getAsJsonObject().get("code").getAsInt());

        JsonObject unknown = handler.handle(request("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"resources/read\","
                + "\"params\":{\"uri\":\"vp://nope\"}}"));
        assertEquals(JsonRpc.INVALID_PARAMS, unknown.get("error").getAsJsonObject().get("code").getAsInt());
    }

    @Test
    void initializeAdvertisesResourcesOnlyWhenPresent(@TempDir File dir) {
        JsonObject withResources = handlerWithPack(dir)
                .handle(request("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"));
        assertTrue(withResources.getAsJsonObject("result").getAsJsonObject("capabilities").has("resources"));

        JsonObject withoutResources = toolsOnly
                .handle(request("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"));
        assertFalse(withoutResources.getAsJsonObject("result").getAsJsonObject("capabilities").has("resources"));
    }

    private McpProtocolHandler handlerWithPack(File dir) {
        File pack = new File(dir, "schemas/vT");
        pack.mkdirs();
        write(new File(pack, "meta.json"), "{\"version\":\"vT\"}");
        JsonObject capabilities = new JsonObject();
        capabilities.addProperty("plugin_version", "test");
        return new McpProtocolHandler(new McpToolRegistry(), new DirectToolInvoker(), "test",
                new FileResourceProvider(dir, "vT", capabilities));
    }

    private void write(File file, String text) {
        try {
            java.nio.file.Files.write(file.toPath(), text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private JsonObject request(String raw) {
        return JsonParser.parseString(raw).getAsJsonObject();
    }
}
