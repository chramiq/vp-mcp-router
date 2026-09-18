package vpmcp;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import vpmcp.core.McpToolException;
import vpmcp.tools.SaveProjectTool;

/** Confirm-gate contracts. Declined saves die before touching VP. */
class SaveProjectToolTest {

    private final SaveProjectTool tool = new SaveProjectTool();

    @Test
    void missingConfirmRefuses() {
        JsonObject params = JsonParser.parseString("{}").getAsJsonObject();

        McpToolException refused = assertThrows(McpToolException.class, () -> tool.execute(params));
        assertTrue(refused.getMessage().contains("confirm"));
    }

    @Test
    void falseConfirmRefuses() {
        JsonObject params = JsonParser.parseString("{\"confirm\":false}").getAsJsonObject();

        McpToolException refused = assertThrows(McpToolException.class, () -> tool.execute(params));
        assertTrue(refused.getMessage().contains("confirm"));
    }
}
