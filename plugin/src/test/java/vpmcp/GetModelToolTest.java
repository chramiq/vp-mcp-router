package vpmcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IProject;
import org.junit.jupiter.api.Test;
import vpmcp.core.McpToolException;
import vpmcp.tools.GetModelTool;

/**
 * Model-read contracts. Fixtures are built by helpers; each test breaks iff
 * the read contract it names breaks.
 */
class GetModelToolTest {

    @Test
    void readsIdentityAndTheDiagramsShowingIt() throws McpToolException {
        IModelElement model = VpFakes.model("m1", "Widget", "Class");
        IDiagramUIModel shown = VpFakes.diagram("d1", "Main", "ClassDiagram",
                VpFakes.shape("v1", model, "Class", 0, 0, 10, 10));
        IDiagramUIModel empty = VpFakes.diagram("d2", "Other", "ClassDiagram");
        IProject project = VpFakes.project("p", new IModelElement[] {model},
                new IDiagramUIModel[] {shown, empty});

        JsonObject result = GetModelTool.read(project, "m1", false);

        assertEquals("Widget", result.get("name").getAsString());
        assertEquals("Class", result.get("model_type").getAsString());
        assertEquals(1, result.getAsJsonArray("shown_on").size());
        JsonObject where = result.getAsJsonArray("shown_on").get(0).getAsJsonObject();
        assertEquals("d1", where.get("diagram_id").getAsString());
        assertEquals("v1", where.get("view_id").getAsString());
    }

    @Test
    void modelOnNoDiagramHasEmptyShownOn() throws McpToolException {
        IModelElement orphan = VpFakes.model("mOrphan", "Ghost", "Class");
        IProject project = VpFakes.project("p", new IModelElement[] {orphan},
                new IDiagramUIModel[] {
                        VpFakes.diagram("d1", "Main", "ClassDiagram",
                                VpFakes.shape("v1", VpFakes.model("mOther", "Other", "Class"),
                                        "Class", 0, 0, 10, 10))
                });

        JsonObject result = GetModelTool.read(project, "mOrphan", false);

        assertEquals(0, result.getAsJsonArray("shown_on").size());
        assertEquals("Ghost", result.get("name").getAsString());
    }

    @Test
    void unknownIdIsExplicitError() {
        IProject project = VpFakes.project("p", new IModelElement[0],
                new IDiagramUIModel[0]);

        McpToolException missing = assertThrows(McpToolException.class,
                () -> GetModelTool.read(project, "nope", false));
        assertTrue(missing.getMessage().contains("nope"));
    }
}
