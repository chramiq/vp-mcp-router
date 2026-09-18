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
}
