package vpmcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import vpmcp.core.McpToolException;
import vpmcp.tools.SaveProjectTool;

/** Confirm-gate and save-target contracts. Declined saves die before touching VP;
 * never-saved projects are refused before saveProject() can block on a dialog. */
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

    @Test
    void nullProjectFileRefuses() {
        java.util.Map<String, Object> values = new java.util.HashMap<>();
        values.put("getProjectFile", null);

        McpToolException refused = assertThrows(McpToolException.class,
                () -> SaveProjectTool.requireSaveTarget(VpFakes.of(
                        com.vp.plugin.model.IProject.class, values)));
        assertTrue(refused.getMessage().contains("never been saved"), refused.getMessage());
    }

    @Test
    void missingProjectFileRefuses() {
        com.vp.plugin.model.IProject project = VpFakes.of(com.vp.plugin.model.IProject.class,
                java.util.Map.of("getProjectFile",
                        new java.io.File("/nonexistent-dir-9f3/missing.vpp")));

        McpToolException refused = assertThrows(McpToolException.class,
                () -> SaveProjectTool.requireSaveTarget(project));
        assertTrue(refused.getMessage().contains("never been saved"), refused.getMessage());
    }

    @Test
    void existingProjectFilePasses(@org.junit.jupiter.api.io.TempDir java.io.File dir)
            throws java.io.IOException, McpToolException {
        java.io.File vpp = new java.io.File(dir, "project.vpp");
        assertTrue(vpp.createNewFile());
        com.vp.plugin.model.IProject project = VpFakes.of(com.vp.plugin.model.IProject.class,
                java.util.Map.of("getProjectFile", vpp));

        assertEquals(vpp, SaveProjectTool.requireSaveTarget(project));
    }
}
