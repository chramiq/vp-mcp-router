package vpmcp;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import vpmcp.core.McpToolException;
import vpmcp.tools.OpenDiagramTool;

/** Parameter gate: a request without a diagram dies before touching VP. */
class OpenDiagramToolTest {

    private final OpenDiagramTool tool = new OpenDiagramTool();

    @Test
    void missingDiagramRefuses() {
        JsonObject params = JsonParser.parseString("{}").getAsJsonObject();

        McpToolException refused = assertThrows(McpToolException.class, () -> tool.execute(params));
        assertTrue(refused.getMessage().contains("diagram"));
    }

    @Test
    void blankDiagramRefuses() {
        JsonObject params = JsonParser.parseString("{\"diagram\":\"  \"}").getAsJsonObject();

        McpToolException refused = assertThrows(McpToolException.class, () -> tool.execute(params));
        assertTrue(refused.getMessage().contains("diagram"));
    }
}
