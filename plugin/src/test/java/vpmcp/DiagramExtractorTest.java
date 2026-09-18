package vpmcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IProject;
import java.awt.Point;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vpmcp.vp.DiagramExtractor;

/**
 * Extractor behavior contracts. Fixtures are built by helpers, so sizes and
 * ids derive from construction, never from literals. Each test breaks iff
 * the contract it names breaks.
 */
class DiagramExtractorTest {

    private final IProject project = VpFakes.project("testproject");

    @Test
    void everyShapeBecomesExactlyOneNode() {
        IModelElement first = VpFakes.model("m1", "Alpha", "Class");
        IModelElement second = VpFakes.model("m2", "Beta", "Class");
        IDiagramUIModel diagram = VpFakes.diagram("d", "D", "ClassDiagram",
                VpFakes.shape("v1", first, "Class", 0, 0, 10, 10),
                VpFakes.shape("v2", second, "Class", 50, 50, 10, 10));

        JsonObject graph = extract(diagram, false);

        assertEquals(2, graph.getAsJsonArray("nodes").size());
        assertEquals(0, graph.getAsJsonArray("edges").size());
    }

    @Test
    void nodeCarriesModelIdentityAndBounds() {
        IModelElement model = VpFakes.model("m9", "Widget", "Class");
        IDiagramUIModel diagram = VpFakes.diagram("d", "D", "ClassDiagram",
                VpFakes.shape("v9", model, "Class", 11, 22, 33, 44));

        JsonObject node = extract(diagram, false).getAsJsonArray("nodes").get(0).getAsJsonObject();

        assertEquals("v9", node.get("id").getAsString());
        assertEquals("m9", node.get("model_id").getAsString());
        assertEquals("Widget", node.get("name").getAsString());
        assertEquals("Class", node.get("model_type").getAsString());
        JsonObject bounds = node.getAsJsonObject("bounds");
        assertEquals(11, bounds.get("x").getAsInt());
        assertEquals(22, bounds.get("y").getAsInt());
        assertEquals(33, bounds.get("width").getAsInt());
        assertEquals(44, bounds.get("height").getAsInt());
    }

    @Test
    void edgeEndpointsReferenceExtractedNodes() {
        IModelElement from = VpFakes.model("mA", "A", "Class");
        IModelElement to = VpFakes.model("mB", "B", "Class");
        IModelElement link = VpFakes.relationship("mR", "rel", "Association", from, to);
        com.vp.plugin.diagram.IShapeUIModel viewA = VpFakes.shape("vA", from, "Class", 0, 0, 10, 10);
        com.vp.plugin.diagram.IShapeUIModel viewB = VpFakes.shape("vB", to, "Class", 50, 0, 10, 10);
        IDiagramUIModel diagram = VpFakes.diagram("d", "D", "ClassDiagram", viewA, viewB,
                VpFakes.connector("vR", link, viewA, viewB,
                        new Point[] {new Point(10, 5), new Point(50, 5)}));

        JsonObject graph = extract(diagram, false);

        Set<String> nodeIds = new HashSet<>();
        graph.getAsJsonArray("nodes").forEach(node -> nodeIds.add(node.getAsJsonObject().get("id").getAsString()));
        JsonObject edge = graph.getAsJsonArray("edges").get(0).getAsJsonObject();
        assertTrue(nodeIds.contains(edge.get("from").getAsString()));
        assertTrue(nodeIds.contains(edge.get("to").getAsString()));
        assertEquals("Association", edge.get("model_type").getAsString());
        JsonArray waypoints = edge.getAsJsonArray("waypoints");
        assertEquals(2, waypoints.size());
        assertEquals(10, waypoints.get(0).getAsJsonObject().get("x").getAsInt());
        assertEquals(50, waypoints.get(1).getAsJsonObject().get("x").getAsInt());
        assertEquals("mA", edge.getAsJsonObject("from_model").get("id").getAsString());
        assertEquals("mB", edge.getAsJsonObject("to_model").get("id").getAsString());
    }

    @Test
    void parentChildLinkedBothWays() {
        IModelElement parent = VpFakes.model("mP", "Parent", "RestResource");
        IModelElement child = VpFakes.model("mC", "Child", "RestResourceRequestBody");
        com.vp.plugin.diagram.IShapeUIModel childView =
                VpFakes.shape("vC", child, "RestResourceRequestBody", 0, 0, 10, 10, null, null);
        com.vp.plugin.diagram.IShapeUIModel parentView = VpFakes.shape("vP", parent, "RestResource", 0, 0, 90, 90,
                null, new com.vp.plugin.diagram.IShapeUIModel[] {childView});
        childView = VpFakes.shape("vC", child, "RestResourceRequestBody", 0, 0, 10, 10, parentView, null);
        IDiagramUIModel diagram = VpFakes.diagram("d", "D", "ClassDiagram", parentView, childView);

        JsonArray nodes = extract(diagram, false).getAsJsonArray("nodes");

        JsonObject gotParent = null;
        JsonObject gotChild = null;
        for (int index = 0; index < nodes.size(); index++) {
            JsonObject node = nodes.get(index).getAsJsonObject();
            if (node.get("id").getAsString().equals("vP")) {
                gotParent = node;
            }
            if (node.get("id").getAsString().equals("vC")) {
                gotChild = node;
            }
        }
        assertEquals("vP", gotChild.get("parent_id").getAsString());
        assertEquals(1, gotParent.getAsJsonArray("child_ids").size());
        assertEquals("vC", gotParent.getAsJsonArray("child_ids").get(0).getAsString());
    }

    @Test
    void memberPinnedEndsPassThrough() {
        IModelElement from = VpFakes.model("mA", "A", "Class");
        IModelElement to = VpFakes.model("mB", "B", "Class");
        IModelElement link = VpFakes.relationship("mR", "rel", "Association", from, to);
        com.vp.plugin.diagram.IShapeUIModel viewA = VpFakes.shape("vA", from, "Class", 0, 0, 10, 10);
        com.vp.plugin.diagram.IShapeUIModel viewB = VpFakes.shape("vB", to, "Class", 50, 0, 10, 10);
        IDiagramUIModel diagram = VpFakes.diagram("d", "D", "ClassDiagram", viewA, viewB,
                VpFakes.connector("vR", link, viewA, viewB, null, "attrFrom", "attrTo"));

        JsonObject edge = extract(diagram, false).getAsJsonArray("edges").get(0).getAsJsonObject();

        assertEquals("vA", edge.get("from").getAsString());
        assertEquals("vB", edge.get("to").getAsString());
        assertEquals("attrFrom", edge.get("from_member_id").getAsString());
        assertEquals("attrTo", edge.get("to_member_id").getAsString());
    }

    @Test
    void brokenConnectorWarnsAndExtractionContinues() {        IModelElement model = VpFakes.model("m1", "Solo", "Class");
        IDiagramUIModel diagram = VpFakes.diagram("d", "D", "ClassDiagram",
                VpFakes.shape("v1", model, "Class", 0, 0, 10, 10),
                VpFakes.connector("vBroken", null, null, null, null));

        List<String> warnings = new ArrayList<>();
        JsonObject graph = new DiagramExtractor(false, warnings).extract(diagram, project);

        assertFalse(warnings.isEmpty());
        assertEquals(1, graph.getAsJsonArray("nodes").size());
        assertEquals(1, graph.getAsJsonArray("edges").size());
    }

    @Test
    void missingVisualsBecomeExplicitNullsNotCrashes() {
        IModelElement model = VpFakes.model("m1", "Bare", "Class");
        IDiagramUIModel diagram = VpFakes.diagram("d", "D", "ClassDiagram",
                VpFakes.shape("v1", model, "Class", 0, 0, 10, 10));

        JsonObject node = extract(diagram, false).getAsJsonArray("nodes").get(0).getAsJsonObject();

        assertTrue(node.has("visual_properties"));
        JsonObject visuals = node.getAsJsonObject("visual_properties");
        assertTrue(visuals.get("line").isJsonNull());
        assertTrue(visuals.get("font").isJsonNull());
    }

    @Test
    void unknownShapeTypePassesThroughVerbatim() {
        IModelElement model = VpFakes.model("m1", "Future", "SomethingNew");
        IDiagramUIModel diagram = VpFakes.diagram("d", "D", "ClassDiagram",
                VpFakes.shape("v1", model, "SomethingNew", 0, 0, 10, 10));

        JsonObject node = extract(diagram, false).getAsJsonArray("nodes").get(0).getAsJsonObject();

        assertEquals("SomethingNew", node.get("shape_type").getAsString());
        assertEquals("SomethingNew", node.get("model_type").getAsString());
    }

    @Test
    void fullDetailAddsRawDumps() {
        IModelElement model = VpFakes.model("m1", "Plain", "Class");
        IDiagramUIModel diagram = VpFakes.diagram("d", "D", "ClassDiagram",
                VpFakes.shape("v1", model, "Class", 0, 0, 10, 10));

        JsonObject standard = extract(diagram, false).getAsJsonArray("nodes").get(0).getAsJsonObject();
        JsonObject full = extract(diagram, true).getAsJsonArray("nodes").get(0).getAsJsonObject();

        assertFalse(standard.has("raw_model_properties"));
        assertTrue(full.has("raw_model_properties"));
        assertTrue(full.has("raw_view_properties"));
    }

    private JsonObject extract(IDiagramUIModel diagram, boolean fullDetail) {
        List<String> warnings = new ArrayList<>();
        JsonObject graph = new DiagramExtractor(fullDetail, warnings).extract(diagram, project);
        assertTrue(warnings.isEmpty(), "unexpected warnings: " + warnings);
        return graph;
    }
}
