package vpmcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IProject;
import org.junit.jupiter.api.Test;
import vpmcp.tools.ListModelsTool;

/**
 * Model-index contracts. Fixtures are built by helpers, so counts derive from
 * construction; each test breaks iff the index contract it names breaks.
 */
class ListModelsToolTest {

    @Test
    void everyModelIndexedWithParentLink() {
        IModelElement parent = VpFakes.model("mRoot", "Root", "Class");
        IModelElement child = VpFakes.model("mAttr", "size", "Attribute", parent);
        IModelElement[] models = {parent, child, VpFakes.model("mActor", "User", "Actor")};
        IProject project = VpFakes.project("p", models, new com.vp.plugin.diagram.IDiagramUIModel[0]);

        JsonObject result = ListModelsTool.index(project, null);

        assertEquals(models.length, result.get("count").getAsInt());
        JsonObject indexedChild = entryById(result, "mAttr");
        assertEquals("mRoot", indexedChild.get("parent_id").getAsString());
        JsonObject indexedRoot = entryById(result, "mRoot");
        assertTrue(indexedRoot.get("parent_id").isJsonNull());
        assertEquals("Attribute", indexedChild.get("model_type").getAsString());
    }

    @Test
    void modelTypeFilterKeepsOnlyThatType() {
        IModelElement[] models = {
            VpFakes.model("m1", "A", "Class"),
            VpFakes.model("m2", "B", "Actor"),
            VpFakes.model("m3", "C", "Class")
        };
        IProject project = VpFakes.project("p", models, new com.vp.plugin.diagram.IDiagramUIModel[0]);

        JsonObject result = ListModelsTool.index(project, "Class");

        assertEquals(2, result.get("count").getAsInt());
        result.getAsJsonArray("models").forEach(entry ->
                assertEquals("Class", entry.getAsJsonObject().get("model_type").getAsString()));
    }

    @Test
    void membersAreIndexedBesideTheirParents() {
        IModelElement parent = VpFakes.modelWithChildren("cls", "Probe", "Class",
                new IModelElement[] {VpFakes.model("attr", "size", "Attribute", null)});
        // Members hang off a parent; give the child that parent for linkage.
        IModelElement linkedParent = VpFakes.modelWithChildren("cls", "Probe", "Class",
                new IModelElement[] {VpFakes.model("attr", "size", "Attribute", parent)});
        IProject project = VpFakes.project("p", new IModelElement[] {linkedParent},
                new com.vp.plugin.diagram.IDiagramUIModel[0]);

        JsonObject result = ListModelsTool.index(project, null);

        assertEquals(2, result.get("count").getAsInt());
        JsonObject indexedMember = result.getAsJsonArray("models").asList().stream()
                .map(entry -> entry.getAsJsonObject())
                .filter(entry -> "attr".equals(entry.get("id").getAsString()))
                .findFirst().orElseThrow();
        assertEquals("cls", indexedMember.get("parent_id").getAsString());
    }

    private JsonObject entryById(JsonObject result, String id) {
        return result.getAsJsonArray("models").asList().stream()
                .map(entry -> entry.getAsJsonObject())
                .filter(entry -> id.equals(entry.get("id").getAsString()))
                .findFirst().orElseThrow();
    }
}
