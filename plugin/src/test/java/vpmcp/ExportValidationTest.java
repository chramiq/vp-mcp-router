package vpmcp;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import vpmcp.core.McpToolException;
import vpmcp.tools.ExportImageTool;

/**
 * Export validation contracts. Bad input must die in validation, before
 * anything touches VP — so these run on plain JDK.
 */
class ExportValidationTest {

    private final ExportImageTool tool = new ExportImageTool();

    @Test
    void unknownFormatRejected() {
        JsonObject params = JsonParser
                .parseString("{\"vpp_url\":\"x\",\"format\":\"bmp\"}").getAsJsonObject();

        McpToolException refused = assertThrows(McpToolException.class, () -> tool.execute(params));
        assertTrue(refused.getMessage().contains("format"));
    }

    @Test
    void missingUrlRejected() {
        JsonObject params = JsonParser.parseString("{\"format\":\"pdf\"}").getAsJsonObject();

        McpToolException refused = assertThrows(McpToolException.class, () -> tool.execute(params));
        assertTrue(refused.getMessage().contains("vpp_url"));
    }

    @Test
    void cropAndElementTogetherRejected() {
        JsonObject params = JsonParser.parseString("{\"vpp_url\":\"x\","
                + "\"crop\":{\"x\":0,\"y\":0,\"width\":10,\"height\":10},"
                + "\"crop_to_element\":\"v1\"}").getAsJsonObject();

        McpToolException refused = assertThrows(McpToolException.class, () -> tool.execute(params));
        assertTrue(refused.getMessage().contains("only one"));
    }

    @Test
    void cropOnVectorRejected() {
        JsonObject params = JsonParser.parseString(
                "{\"vpp_url\":\"x\",\"format\":\"svg\",\"crop\":{\"x\":0,\"y\":0,\"width\":10,\"height\":10}}")
                .getAsJsonObject();

        McpToolException refused = assertThrows(McpToolException.class, () -> tool.execute(params));
        assertTrue(refused.getMessage().contains("png"));
    }

    @Test
    void malformedCropRejected() {
        JsonObject params = JsonParser
                .parseString("{\"vpp_url\":\"x\",\"crop\":{\"x\":\"left\"}}").getAsJsonObject();

        McpToolException refused = assertThrows(McpToolException.class, () -> tool.execute(params));
        assertTrue(refused.getMessage().contains("crop"));
    }
}
