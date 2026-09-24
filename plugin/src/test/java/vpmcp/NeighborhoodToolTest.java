package vpmcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IProject;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import vpmcp.core.McpToolException;
import vpmcp.tools.NeighborhoodTool;
import vpmcp.vp.DiagramExtractor;

/**
 * Center-resolution contracts for vp_get_neighborhood: view ids pass through,
 * model ids resolve to the model's view on the addressed diagram.
 */
class NeighborhoodToolTest {

    @Test
    void viewIdPassesThrough() throws McpToolException {
        IModelElement model = VpFakes.model("m1", "A", "Class");
        JsonObject graph = graphOf(model);
        IProject project = VpFakes.project("p", new IModelElement[] {model},
                new IDiagramUIModel[0]);

        String center = NeighborhoodTool.resolveCenter(graph, project, "v1", new ArrayList<>());

        assertEquals("v1", center);
    }

    @Test
    void modelIdResolvesToItsViewOnTheDiagram() throws McpToolException {
        IModelElement model = VpFakes.model("m1", "A", "Class");
        JsonObject graph = graphOf(model);
        IProject project = VpFakes.project("p", new IModelElement[] {model},
                new IDiagramUIModel[0]);

        List<String> warnings = new ArrayList<>();
        String center = NeighborhoodTool.resolveCenter(graph, project, "m1", warnings);

        assertEquals("v1", center);
        assertEquals(1, warnings.size());
    }

    @Test
    void modelExistingButNotShownIsExplicitError() {
        IModelElement shown = VpFakes.model("m1", "A", "Class");
        IModelElement hidden = VpFakes.model("m2", "B", "Class");
        JsonObject graph = graphOf(shown);
        IProject project = VpFakes.project("p", new IModelElement[] {shown, hidden},
                new IDiagramUIModel[0]);

        McpToolException unshown = assertThrows(McpToolException.class,
                () -> NeighborhoodTool.resolveCenter(graph, project, "m2", new ArrayList<>()));
        assertTrue(unshown.getMessage().contains("not shown on this diagram"));
        assertTrue(unshown.getMessage().contains("vp_get_model"));
    }

    private JsonObject graphOf(IModelElement model) {
        IDiagramUIModel diagram = VpFakes.diagram("d", "D", "ClassDiagram",
                VpFakes.shape("v1", model, "Class", 0, 0, 10, 10));
        return new DiagramExtractor(false, new ArrayList<>()).extract(diagram,
                VpFakes.project("p"));
    }
}
