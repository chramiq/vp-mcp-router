package vpmcp;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import vpmcp.core.McpToolException;
import vpmcp.tools.ApplyBatchTool;

/**
 * Confirm-gate contracts. Both tests run without VP: a declined batch
 * must die before anything touches the project.
 */
class ApplyBatchToolTest {

    private final ApplyBatchTool tool = new ApplyBatchTool();

    @Test
    void missingConfirmRefuses() {
        JsonObject params = JsonParser.parseString("{\"ops\":[]}").getAsJsonObject();

        McpToolException refused = assertThrows(McpToolException.class, () -> tool.execute(params));
        assertTrue(refused.getMessage().contains("confirm"));
    }

    @Test
    void falseConfirmRefuses() {
        JsonObject params = JsonParser.parseString("{\"ops\":[],\"confirm\":false}").getAsJsonObject();

        McpToolException refused = assertThrows(McpToolException.class, () -> tool.execute(params));
        assertTrue(refused.getMessage().contains("confirm"));
    }
}
