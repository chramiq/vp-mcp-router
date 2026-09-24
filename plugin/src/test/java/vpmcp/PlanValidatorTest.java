package vpmcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vp.plugin.model.IProject;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vpmcp.write.PlanValidator;
import vpmcp.write.TypeTiers;

/**
 * Preview-batch validation contracts. The fake project holds one diagram
 * with one element; every rejection below must fire, every valid plan
 * must pass, or the validator is lying about what apply would do.
 */
class PlanValidatorTest {

    private final IProject project = projectWith("d1", "v9");

    @Test
    void chainedPlanThroughRefsValidates() {
        JsonArray ops = parse("["
                + "{\"id\":\"d\",\"op\":\"create_diagram\",\"diagram_type\":\"UseCaseDiagram\",\"name\":\"Shop\"},"
                + "{\"id\":\"a\",\"op\":\"create_element\",\"diagram\":{\"ref\":\"d\"},"
                + " \"model_type\":\"Actor\",\"name\":\"Customer\"},"
                + "{\"id\":\"u\",\"op\":\"create_element\",\"diagram\":{\"ref\":\"d\"},"
                + " \"model_type\":\"UseCase\",\"name\":\"Checkout\",\"x\":10,\"y\":20,\"width\":80,\"height\":40},"
                + "{\"id\":\"r\",\"op\":\"connect\",\"diagram\":{\"ref\":\"d\"},\"rel_type\":\"Association\","
                + " \"from\":{\"ref\":\"a\"},\"to\":{\"ref\":\"u\"}}]");

        JsonObject result = PlanValidator.validate(project, ops);

        assertTrue(result.get("valid").getAsBoolean(), result.toString());
        assertEquals(4, result.getAsJsonArray("plan").size());
        assertEquals(0, result.getAsJsonArray("errors").size());
    }

    @Test
    void existingIdsResolveWithoutRefs() {
        JsonArray ops = parse("["
                + "{\"id\":\"u\",\"op\":\"create_element\",\"diagram\":\"d1\","
                + " \"model_type\":\"Class\",\"name\":\"Item\"},"
                + "{\"id\":\"r\",\"op\":\"connect\",\"diagram\":\"d1\",\"rel_type\":\"Dependency\","
                + " \"from\":\"v9\",\"to\":{\"ref\":\"u\"}}]");

        JsonObject result = PlanValidator.validate(project, ops);

        assertTrue(result.get("valid").getAsBoolean(), result.toString());
    }

    @Test
    void unknownOpRejected() {
        JsonObject result = PlanValidator.validate(project, parse("[{\"id\":\"x\",\"op\":\"delete_all\"}]"));

        assertFalse(result.get("valid").getAsBoolean());
        assertTrue(result.toString().contains("Unknown op"));
    }

    @Test
    void unknownModelTypeRejected() {
        JsonObject result = PlanValidator.validate(project, parse("["
                + "{\"id\":\"u\",\"op\":\"create_element\",\"diagram\":\"d1\","
                + " \"model_type\":\"QuantumEntanglement\",\"name\":\"Q\"}]"));

        assertFalse(result.get("valid").getAsBoolean());
        assertTrue(result.toString().contains("cannot create"));
    }

    @Test
    void duplicateIdsRejected() {
        JsonArray ops = parse("["
                + "{\"id\":\"d\",\"op\":\"create_diagram\",\"diagram_type\":\"ClassDiagram\",\"name\":\"A\"},"
                + "{\"id\":\"d\",\"op\":\"create_diagram\",\"diagram_type\":\"ClassDiagram\",\"name\":\"B\"}]");

        JsonObject result = PlanValidator.validate(project, ops);

        assertFalse(result.get("valid").getAsBoolean());
        assertTrue(result.toString().contains("Duplicate"));
    }

    @Test
    void danglingRefRejected() {
        JsonArray ops = parse("[{\"id\":\"u\",\"op\":\"create_element\",\"diagram\":{\"ref\":\"ghost\"},"
                + " \"model_type\":\"Class\",\"name\":\"X\"}]");

        JsonObject result = PlanValidator.validate(project, ops);

        assertFalse(result.get("valid").getAsBoolean());
    }

    @Test
    void unknownEndpointRejected() {
        JsonArray ops = parse("[{\"id\":\"r\",\"op\":\"connect\",\"diagram\":\"d1\",\"rel_type\":\"Association\","
                + " \"from\":\"v9\",\"to\":\"missing-view\"}]");

        JsonObject result = PlanValidator.validate(project, ops);

        assertFalse(result.get("valid").getAsBoolean());
        assertTrue(result.toString().contains("endpoint"));
    }

    @Test
    void blankNameRejected() {
        JsonArray ops = parse("[{\"id\":\"u\",\"op\":\"create_element\",\"diagram\":\"d1\","
                + " \"model_type\":\"Class\",\"name\":\"  \"}]");

        JsonObject result = PlanValidator.validate(project, ops);

        assertFalse(result.get("valid").getAsBoolean());
    }

    @Test
    void nonNumericGeometryRejected() {
        JsonArray ops = parse("[{\"id\":\"u\",\"op\":\"create_element\",\"diagram\":\"d1\","
                + " \"model_type\":\"Class\",\"name\":\"X\",\"x\":\"left\"}]");

        JsonObject result = PlanValidator.validate(project, ops);

        assertFalse(result.get("valid").getAsBoolean());
    }

    @Test
    void nonObjectsRejectedWithoutThrowing() {
        JsonObject result = PlanValidator.validate(project, parse("[42]"));

        assertFalse(result.get("valid").getAsBoolean());
    }

    @Test
    void deleteExistingDiagramAndElementValidate() {
        JsonArray ops = parse("["
                + "{\"id\":\"x\",\"op\":\"delete_element\",\"element\":\"v9\"},"
                + "{\"id\":\"y\",\"op\":\"delete_diagram\",\"diagram\":\"d1\"}]");

        JsonObject result = PlanValidator.validate(project, ops);

        assertTrue(result.get("valid").getAsBoolean(), result.toString());
        assertEquals(2, result.getAsJsonArray("plan").size());
    }

    @Test
    void deleteUnknownTargetRejected() {
        JsonObject result = PlanValidator.validate(project,
                parse("[{\"id\":\"x\",\"op\":\"delete_element\",\"element\":\"ghost\"}]"));

        assertFalse(result.get("valid").getAsBoolean());
    }

    @Test
    void memberPinningAccepted() {
        JsonArray ops = parse("[{\"id\":\"r\",\"op\":\"connect\",\"diagram\":\"d1\",\"rel_type\":\"Association\","
                + " \"from\":\"v9\",\"to\":\"v9\",\"from_member\":\"m1\",\"to_member\":\"m2\"}]");

        JsonObject result = PlanValidator.validate(project, ops);

        assertTrue(result.get("valid").getAsBoolean(), result.toString());
    }

    @Test
    void blankMemberIdRejected() {
        JsonArray ops = parse("[{\"id\":\"r\",\"op\":\"connect\",\"diagram\":\"d1\",\"rel_type\":\"Association\","
                + " \"from\":\"v9\",\"to\":\"v9\",\"to_member\":\"  \"}]");

        JsonObject result = PlanValidator.validate(project, ops);

        assertFalse(result.get("valid").getAsBoolean());
    }

    @Test
    void deleteByPlanRefValidates() {        JsonArray ops = parse("["
                + "{\"id\":\"d\",\"op\":\"create_diagram\",\"diagram_type\":\"ClassDiagram\",\"name\":\"Temp\"},"
                + "{\"id\":\"e\",\"op\":\"create_element\",\"diagram\":{\"ref\":\"d\"},"
                + " \"model_type\":\"Class\",\"name\":\"Temp\"},"
                + "{\"id\":\"k\",\"op\":\"delete_element\",\"element\":{\"ref\":\"e\"}}]");

        JsonObject result = PlanValidator.validate(project, ops);

        assertTrue(result.get("valid").getAsBoolean(), result.toString());
    }

    @Test
    void everyPlanEntryNamesItsUndo() {
        JsonArray ops = parse("["
                + "{\"id\":\"d\",\"op\":\"create_diagram\",\"diagram_type\":\"ClassDiagram\",\"name\":\"Temp\"},"
                + "{\"id\":\"x\",\"op\":\"delete_diagram\",\"diagram\":\"d1\"}]");

        JsonObject result = PlanValidator.validate(project, ops);

        assertTrue(result.get("valid").getAsBoolean(), result.toString());
        result.getAsJsonArray("plan").forEach(entry -> {
            JsonObject planEntry = entry.getAsJsonObject();
            assertTrue(planEntry.has("undo"), "plan entry without undo: " + planEntry);
            assertFalse(planEntry.get("undo").getAsString().isEmpty());
        });
    }

    @Test
    void duplicateDiagramValidates() {
        JsonArray ops = parse("[{\"id\":\"c\",\"op\":\"duplicate_diagram\",\"diagram\":\"d1\",\"name\":\"Copy\"}]");

        JsonObject result = PlanValidator.validate(project, ops);

        assertTrue(result.get("valid").getAsBoolean(), result.toString());
        assertEquals(1, result.getAsJsonArray("plan").size());
        assertTrue(result.getAsJsonArray("plan").get(0).getAsJsonObject().get("undo").getAsString()
                .contains("shared models"));
    }

    @Test
    void duplicateDiagramRequiresName() {
        JsonObject result = PlanValidator.validate(project,
                parse("[{\"id\":\"c\",\"op\":\"duplicate_diagram\",\"diagram\":\"d1\",\"name\":\"  \"}]"));

        assertFalse(result.get("valid").getAsBoolean());
    }

    @Test
    void duplicateDiagramUnknownSourceRejected() {
        JsonObject result = PlanValidator.validate(project,
                parse("[{\"id\":\"c\",\"op\":\"duplicate_diagram\",\"diagram\":\"nope\",\"name\":\"Copy\"}]"));

        assertFalse(result.get("valid").getAsBoolean());
    }

    @Test
    void newDiagramFamiliesValidate() {
        JsonArray ops = parse("["
                + "{\"id\":\"a\",\"op\":\"create_diagram\",\"diagram_type\":\"ActivityDiagram\",\"name\":\"A\"},"
                + "{\"id\":\"s\",\"op\":\"create_diagram\",\"diagram_type\":\"StateDiagram\",\"name\":\"S\"},"
                + "{\"id\":\"e\",\"op\":\"create_diagram\",\"diagram_type\":\"ERDiagram\",\"name\":\"E\"},"
                + "{\"id\":\"q\",\"op\":\"create_diagram\",\"diagram_type\":\"InteractionDiagram\",\"name\":\"Q\"},"
                + "{\"id\":\"p\",\"op\":\"create_diagram\",\"diagram_type\":\"DeploymentDiagram\",\"name\":\"P\"}]");

        JsonObject result = PlanValidator.validate(project, ops);

        assertTrue(result.get("valid").getAsBoolean(), result.toString());
    }

    @Test
    void addMemberValidates() {
        JsonArray ops = parse("[{\"id\":\"m\",\"op\":\"add_member\",\"parent\":\"v9\","
                + " \"member_type\":\"Attribute\",\"name\":\"total\",\"type\":\"int\"}]");

        JsonObject result = PlanValidator.validate(project, ops);

        assertTrue(result.get("valid").getAsBoolean(), result.toString());
    }

    @Test
    void addMemberUnknownParentRejected() {
        JsonObject result = PlanValidator.validate(project,
                parse("[{\"id\":\"m\",\"op\":\"add_member\",\"parent\":\"nope\","
                        + " \"member_type\":\"Attribute\",\"name\":\"total\"}]"));

        assertFalse(result.get("valid").getAsBoolean());
    }

    @Test
    void memberAsTopLevelRejected() {
        JsonObject result = PlanValidator.validate(project,
                parse("[{\"id\":\"u\",\"op\":\"create_element\",\"diagram\":\"d1\","
                        + " \"model_type\":\"DBColumn\",\"name\":\"c\"}]"));

        assertFalse(result.get("valid").getAsBoolean());
        assertTrue(result.toString().contains("add_member"));
    }

    @Test
    void pointsValid() {
        JsonArray ops = parse("[{\"id\":\"r\",\"op\":\"connect\",\"diagram\":\"d1\","
                + " \"rel_type\":\"Message\",\"from\":\"v9\",\"to\":\"v9\","
                + " \"points\":[{\"x\":1,\"y\":2},{\"x\":3,\"y\":4}]}]");

        JsonObject result = PlanValidator.validate(project, ops);

        assertTrue(result.get("valid").getAsBoolean(), result.toString());
    }

    @Test
    void pointsRejected() {
        JsonObject result = PlanValidator.validate(project,
                parse("[{\"id\":\"r\",\"op\":\"connect\",\"diagram\":\"d1\","
                        + " \"rel_type\":\"Association\",\"from\":\"v9\",\"to\":\"v9\","
                        + " \"points\":[{\"x\":1}]}]"));

        assertFalse(result.get("valid").getAsBoolean());
    }

    @Test
    void moveElementValidates() {
        JsonObject result = PlanValidator.validate(project,
                parse("[{\"id\":\"m\",\"op\":\"move_element\",\"element\":\"v9\",\"x\":50}]"));

        assertTrue(result.get("valid").getAsBoolean(), result.toString());
    }

    @Test
    void moveElementNeedsGeometry() {
        JsonObject result = PlanValidator.validate(project,
                parse("[{\"id\":\"m\",\"op\":\"move_element\",\"element\":\"v9\"}]"));

        assertFalse(result.get("valid").getAsBoolean());
    }

    @Test
    void showElementValidates() {
        IProject withModel = VpFakes.of(IProject.class, Map.of(), (method, args) -> {
            if ("toDiagramArray".equals(method.getName())) {
                return new com.vp.plugin.diagram.IDiagramUIModel[] {
                        VpFakes.diagram("d1", "Existing", "ClassDiagram")};
            }
            if ("getDiagramElementById".equals(method.getName()) && args.length == 1
                    && "v9".equals(args[0])) {
                return VpFakes.shape("v9", VpFakes.model("m", "E", "Class"), "Class", 0, 0, 10, 10);
            }
            if ("getModelElementById".equals(method.getName()) && args.length == 1
                    && "mx".equals(args[0])) {
                return VpFakes.model("mx", "Shared", "Class");
            }
            return null;
        });

        JsonObject result = PlanValidator.validate(withModel,
                parse("[{\"id\":\"s\",\"op\":\"show_element\",\"diagram\":\"d1\",\"model\":\"mx\"}]"));

        assertTrue(result.get("valid").getAsBoolean(), result.toString());
    }

    @Test
    void deleteModelUnknownRejected() {
        JsonObject result = PlanValidator.validate(project,
                parse("[{\"id\":\"k\",\"op\":\"delete_model\",\"model\":\"mx\"}]"));

        assertFalse(result.get("valid").getAsBoolean());
    }

    @Test
    void updateElementValidatesWithUndo() {
        IProject withModel = VpFakes.project("p",
                new com.vp.plugin.model.IModelElement[] {VpFakes.model("mx", "Shared", "Class")},
                new com.vp.plugin.diagram.IDiagramUIModel[0]);

        JsonObject result = PlanValidator.validate(withModel, parse("[{\"id\":\"u\","
                + "\"op\":\"update_element\",\"model\":\"mx\",\"name\":\"Renamed\","
                + "\"documentation\":\"docs\",\"stereotypes\":[\"entity\"]}]"));

        assertTrue(result.get("valid").getAsBoolean(), result.toString());
        JsonObject planEntry = result.getAsJsonArray("plan").get(0).getAsJsonObject();
        assertTrue(planEntry.get("undo").getAsString().contains("restore"));
    }

    @Test
    void updateElementUnknownModelRejected() {
        JsonObject result = PlanValidator.validate(project,
                parse("[{\"id\":\"u\",\"op\":\"update_element\",\"model\":\"ghost\",\"name\":\"N\"}]"));

        assertFalse(result.get("valid").getAsBoolean());
    }

    @Test
    void updateElementNeedsAField() {
        IProject withModel = VpFakes.project("p",
                new com.vp.plugin.model.IModelElement[] {VpFakes.model("mx", "Shared", "Class")},
                new com.vp.plugin.diagram.IDiagramUIModel[0]);

        JsonObject result = PlanValidator.validate(withModel,
                parse("[{\"id\":\"u\",\"op\":\"update_element\",\"model\":\"mx\"}]"));

        assertFalse(result.get("valid").getAsBoolean());
        assertTrue(result.toString().contains("at least one"));
    }

    @Test
    void updateElementBlankNameRejected() {
        IProject withModel = VpFakes.project("p",
                new com.vp.plugin.model.IModelElement[] {VpFakes.model("mx", "Shared", "Class")},
                new com.vp.plugin.diagram.IDiagramUIModel[0]);

        JsonObject result = PlanValidator.validate(withModel,
                parse("[{\"id\":\"u\",\"op\":\"update_element\",\"model\":\"mx\",\"name\":\"  \"}]"));

        assertFalse(result.get("valid").getAsBoolean());
    }

    @Test
    void updateElementStereotypesMustBeNonBlankStrings() {
        IProject withModel = VpFakes.project("p",
                new com.vp.plugin.model.IModelElement[] {VpFakes.model("mx", "Shared", "Class")},
                new com.vp.plugin.diagram.IDiagramUIModel[0]);

        JsonObject result = PlanValidator.validate(withModel, parse("[{\"id\":\"u\","
                + "\"op\":\"update_element\",\"model\":\"mx\",\"stereotypes\":[\"ok\",\"\"]}]"));

        assertFalse(result.get("valid").getAsBoolean());
        assertTrue(result.toString().contains("stereotypes"));
    }

    @Test
    void updateElementByMemberPlanRefValidates() {
        JsonArray ops = parse("["
                + "{\"id\":\"m\",\"op\":\"add_member\",\"parent\":\"v9\","
                + " \"member_type\":\"Attribute\",\"name\":\"total\"},"
                + "{\"id\":\"u\",\"op\":\"update_element\",\"model\":{\"ref\":\"m\"},"
                + " \"documentation\":\"changed\"}]");

        JsonObject result = PlanValidator.validate(project, ops);

        assertTrue(result.get("valid").getAsBoolean(), result.toString());
    }

    @Test
    void updateMemberValidatesWithUndo() {
        IProject withMember = VpFakes.project("p",
                new com.vp.plugin.model.IModelElement[] {VpFakes.model("mem", "size", "Attribute")},
                new com.vp.plugin.diagram.IDiagramUIModel[0]);

        JsonObject result = PlanValidator.validate(withMember, parse("[{\"id\":\"u\","
                + "\"op\":\"update_member\",\"member\":\"mem\",\"type\":\"int\","
                + "\"visibility\":\"private\",\"multiplicity\":\"1\"}]"));

        assertTrue(result.get("valid").getAsBoolean(), result.toString());
        JsonObject planEntry = result.getAsJsonArray("plan").get(0).getAsJsonObject();
        assertTrue(planEntry.get("undo").getAsString().contains("restore"));
    }

    @Test
    void updateMemberByPlanRefValidates() {
        JsonArray ops = parse("["
                + "{\"id\":\"m\",\"op\":\"add_member\",\"parent\":\"v9\","
                + " \"member_type\":\"Attribute\",\"name\":\"total\"},"
                + "{\"id\":\"u\",\"op\":\"update_member\",\"member\":{\"ref\":\"m\"},"
                + " \"type\":\"int\"}]");

        JsonObject result = PlanValidator.validate(project, ops);

        assertTrue(result.get("valid").getAsBoolean(), result.toString());
    }

    @Test
    void updateMemberOnlyAcceptsMemberKinds() {
        IProject withModel = VpFakes.project("p",
                new com.vp.plugin.model.IModelElement[] {VpFakes.model("cls", "Thing", "Class")},
                new com.vp.plugin.diagram.IDiagramUIModel[0]);

        JsonObject result = PlanValidator.validate(withModel,
                parse("[{\"id\":\"u\",\"op\":\"update_member\",\"member\":\"cls\",\"type\":\"int\"}]"));

        assertFalse(result.get("valid").getAsBoolean());
        assertTrue(result.toString().contains("member"));
    }

    @Test
    void updateMemberNeedsAField() {
        IProject withMember = VpFakes.project("p",
                new com.vp.plugin.model.IModelElement[] {VpFakes.model("mem", "size", "Attribute")},
                new com.vp.plugin.diagram.IDiagramUIModel[0]);

        JsonObject result = PlanValidator.validate(withMember,
                parse("[{\"id\":\"u\",\"op\":\"update_member\",\"member\":\"mem\"}]"));

        assertFalse(result.get("valid").getAsBoolean());
    }

    @Test
    void updateMemberParametersMustBeNamedObjects() {
        IProject withMember = VpFakes.project("p",
                new com.vp.plugin.model.IModelElement[] {VpFakes.model("op", "run", "Operation")},
                new com.vp.plugin.diagram.IDiagramUIModel[0]);

        JsonObject result = PlanValidator.validate(withMember, parse("[{\"id\":\"u\","
                + "\"op\":\"update_member\",\"member\":\"op\","
                + "\"parameters\":[{\"type\":\"int\"}]}]"));

        assertFalse(result.get("valid").getAsBoolean());
        assertTrue(result.toString().contains("name"));
    }

    @Test
    void updateMemberLengthMustBeNumber() {
        IProject withMember = VpFakes.project("p",
                new com.vp.plugin.model.IModelElement[] {VpFakes.model("col", "id", "DBColumn")},
                new com.vp.plugin.diagram.IDiagramUIModel[0]);

        JsonObject result = PlanValidator.validate(withMember, parse("[{\"id\":\"u\","
                + "\"op\":\"update_member\",\"member\":\"col\",\"length\":\"long\"}]"));

        assertFalse(result.get("valid").getAsBoolean());
    }

    @Test
    void updateMemberResolvesThroughParentScan() {
        // VP's id index skips members; resolution must walk the parents.
        IProject withMember = VpFakes.project("p",
                new com.vp.plugin.model.IModelElement[] {VpFakes.modelWithChildren(
                        "cls", "Probe", "Class",
                        new com.vp.plugin.model.IModelElement[] {
                                VpFakes.model("attr", "size", "Attribute")})},
                new com.vp.plugin.diagram.IDiagramUIModel[0]);

        JsonObject result = PlanValidator.validate(withMember, parse("[{\"id\":\"u\","
                + "\"op\":\"update_member\",\"member\":\"attr\",\"type\":\"int\"}]"));

        assertTrue(result.get("valid").getAsBoolean(), result.toString());
    }

    @Test
    void styleElementValidatesWithUndo() {
        JsonObject result = PlanValidator.validate(project, parse("[{\"id\":\"s\","
                + "\"op\":\"style_element\",\"element\":\"v9\",\"background\":\"#FF0000\","
                + "\"line_weight\":2,\"font_bold\":true}]"));

        assertTrue(result.get("valid").getAsBoolean(), result.toString());
        JsonObject planEntry = result.getAsJsonArray("plan").get(0).getAsJsonObject();
        assertTrue(planEntry.get("undo").getAsString().contains("restore"));
    }

    @Test
    void styleElementNeedsAField() {
        JsonObject result = PlanValidator.validate(project,
                parse("[{\"id\":\"s\",\"op\":\"style_element\",\"element\":\"v9\"}]"));

        assertFalse(result.get("valid").getAsBoolean());
        assertTrue(result.toString().contains("at least one"));
    }

    @Test
    void styleElementRejectsNonHexColours() {
        JsonObject result = PlanValidator.validate(project, parse("[{\"id\":\"s\","
                + "\"op\":\"style_element\",\"element\":\"v9\",\"background\":\"red\"}]"));

        assertFalse(result.get("valid").getAsBoolean());
        assertTrue(result.toString().contains("hex"));
    }

    @Test
    void styleElementRejectsNonPositiveWeights() {
        JsonObject result = PlanValidator.validate(project, parse("[{\"id\":\"s\","
                + "\"op\":\"style_element\",\"element\":\"v9\",\"line_weight\":0}]"));

        assertFalse(result.get("valid").getAsBoolean());
    }

    @Test
    void styleElementUnknownTargetRejected() {
        JsonObject result = PlanValidator.validate(project, parse("[{\"id\":\"s\","
                + "\"op\":\"style_element\",\"element\":\"ghost\",\"background\":\"#FF0000\"}]"));

        assertFalse(result.get("valid").getAsBoolean());
    }

    @Test
    void createRawValidatesWithDiagramAndWarnsUnverified() {
        JsonObject result = PlanValidator.validate(project, parse("[{\"id\":\"r\","
                + "\"op\":\"create_raw\",\"factory_method\":\"createBPMNProcess\","
                + "\"diagram\":\"d1\",\"name\":\"Proc\"}]"));

        assertTrue(result.get("valid").getAsBoolean(), result.toString());
        JsonObject planEntry = result.getAsJsonArray("plan").get(0).getAsJsonObject();
        assertTrue(planEntry.get("summary").getAsString().contains("unverified"));
        assertTrue(planEntry.get("undo").getAsString().contains("delete"));
    }

    @Test
    void createRawWithoutDiagramIsAnOrphanModel() {
        JsonObject result = PlanValidator.validate(project, parse("[{\"id\":\"r\","
                + "\"op\":\"create_raw\",\"factory_method\":\"createRequirement\"}]"));

        assertTrue(result.get("valid").getAsBoolean(), result.toString());
        JsonObject planEntry = result.getAsJsonArray("plan").get(0).getAsJsonObject();
        assertTrue(planEntry.get("summary").getAsString().contains("orphan"));
    }

    @Test
    void createRawMethodMustStartWithCreate() {
        JsonObject result = PlanValidator.validate(project,
                parse("[{\"id\":\"r\",\"op\":\"create_raw\",\"factory_method\":\"dispose\"}]"));

        assertFalse(result.get("valid").getAsBoolean());
        assertTrue(result.toString().contains("create"));
    }

    @Test
    void createRawBlankMethodRejected() {
        JsonObject result = PlanValidator.validate(project,
                parse("[{\"id\":\"r\",\"op\":\"create_raw\",\"factory_method\":\"  \"}]"));

        assertFalse(result.get("valid").getAsBoolean());
    }

    @Test
    void createRawUnknownDiagramRejected() {
        JsonObject result = PlanValidator.validate(project, parse("[{\"id\":\"r\","
                + "\"op\":\"create_raw\",\"factory_method\":\"createRequirement\","
                + "\"diagram\":\"ghost\"}]"));

        assertFalse(result.get("valid").getAsBoolean());
    }

    @Test
    void packDiagramTypeValidatesUnverified() {
        TypeTiers tiers = new TypeTiers(
                java.util.Set.of("Brainstorm", "MindMap"), java.util.Set.of());

        JsonObject result = PlanValidator.validate(project, parse("[{\"id\":\"d\","
                + "\"op\":\"create_diagram\",\"diagram_type\":\"MindMap\",\"name\":\"Ideas\"}]"), tiers);

        assertTrue(result.get("valid").getAsBoolean(), result.toString());
        JsonObject planEntry = result.getAsJsonArray("plan").get(0).getAsJsonObject();
        assertTrue(planEntry.get("unverified").getAsBoolean());
        assertTrue(planEntry.get("summary").getAsString()
                .contains(TypeTiers.unverifiedNote()));
    }

    @Test
    void unknownDiagramTypeIsImpossibleNotForbidden() {
        TypeTiers tiers = new TypeTiers(
                java.util.Set.of("Brainstorm"), java.util.Set.of());

        JsonObject result = PlanValidator.validate(project, parse("[{\"id\":\"d\","
                + "\"op\":\"create_diagram\",\"diagram_type\":\"QuantumDiagram\",\"name\":\"X\"}]"), tiers);

        assertFalse(result.get("valid").getAsBoolean());
        assertTrue(result.toString().contains("VP has no diagram type"));
    }

    @Test
    void packElementTypeValidatesUnverified() {
        TypeTiers tiers = new TypeTiers(java.util.Set.of(),
                java.util.Set.of("Requirement", "Constraint"));

        JsonObject result = PlanValidator.validate(project, parse("[{\"id\":\"e\","
                + "\"op\":\"create_element\",\"diagram\":\"d1\",\"model_type\":\"Requirement\","
                + "\"name\":\"Stability\"}]"), tiers);

        assertTrue(result.get("valid").getAsBoolean(), result.toString());
        JsonObject planEntry = result.getAsJsonArray("plan").get(0).getAsJsonObject();
        assertTrue(planEntry.get("unverified").getAsBoolean());
    }

    @Test
    void packRelationshipTypeValidatesUnverified() {
        TypeTiers tiers = new TypeTiers(java.util.Set.of(), java.util.Set.of("Abstraction"));

        JsonObject result = PlanValidator.validate(project, parse("[{\"id\":\"r\","
                + "\"op\":\"connect\",\"diagram\":\"d1\",\"rel_type\":\"Abstraction\","
                + "\"from\":\"v9\",\"to\":\"v9\"}]"), tiers);

        assertTrue(result.get("valid").getAsBoolean(), result.toString());
        assertTrue(result.getAsJsonArray("plan").get(0).getAsJsonObject()
                .get("unverified").getAsBoolean());
    }

    @Test
    void packMemberTypeValidatesUnverified() {
        TypeTiers tiers = new TypeTiers(java.util.Set.of(), java.util.Set.of("Constraint"));

        JsonObject result = PlanValidator.validate(project, parse("[{\"id\":\"m\","
                + "\"op\":\"add_member\",\"parent\":\"v9\",\"member_type\":\"Constraint\","
                + "\"name\":\"Invariant\"}]"), tiers);

        assertTrue(result.get("valid").getAsBoolean(), result.toString());
        assertTrue(result.getAsJsonArray("plan").get(0).getAsJsonObject()
                .get("unverified").getAsBoolean());
    }

    @Test
    void verifiedMemberStillNotPlaceableAsTopLevel() {
        TypeTiers tiers = new TypeTiers(java.util.Set.of(), java.util.Set.of("Constraint"));

        JsonObject result = PlanValidator.validate(project, parse("[{\"id\":\"e\","
                + "\"op\":\"create_element\",\"diagram\":\"d1\",\"model_type\":\"Attribute\","
                + "\"name\":\"size\"}]"), tiers);

        assertFalse(result.get("valid").getAsBoolean());
        assertTrue(result.toString().contains("add_member"));
    }

    @Test
    void typeTiersLoadReadsAPack(@org.junit.jupiter.api.io.TempDir File packParent) throws java.io.IOException {
        File packDir = new File(packParent, "schemas/vTest");
        assertTrue(packDir.mkdirs());
        java.nio.file.Files.write(new File(packDir, "diagram-types.json").toPath(),
                "[{\"name\":\"DT\",\"value\":\"Brainstorm\"}]".getBytes());
        java.nio.file.Files.write(new File(packDir, "factory-creates.json").toPath(),
                "[\"createRequirement\",\"create\"]".getBytes());

        TypeTiers tiers = TypeTiers.load(packParent, "vTest");

        assertTrue(tiers.isKnownDiagram("Brainstorm"));
        assertFalse(tiers.isKnownDiagram("MindMap"));
        assertTrue(tiers.isCreatable("Requirement"));
        assertFalse(tiers.isCreatable("")); // the generic create(String) is not a model type
        assertTrue(tiers.isVerifiedDiagram("ClassDiagram"));
    }

    @Test
    void typeTiersLoadDegradesWithoutAPack(@org.junit.jupiter.api.io.TempDir File emptyDir) {
        TypeTiers tiers = TypeTiers.load(emptyDir, "vMissing");

        assertFalse(tiers.isKnownDiagram("Brainstorm"));
        assertTrue(tiers.isVerifiedDiagram("ClassDiagram"));
        assertFalse(tiers.isCreatable("Requirement"));
    }

    private IProject projectWith(String diagramId, String elementId) {
        Map<String, Object> values = new HashMap<>();
        values.put("toDiagramArray", new com.vp.plugin.diagram.IDiagramUIModel[] {
                VpFakes.diagram(diagramId, "Existing", "ClassDiagram")});
        com.vp.plugin.diagram.IShapeUIModel element =
                VpFakes.shape(elementId, VpFakes.model("m", "E", "Class"), "Class", 0, 0, 10, 10);
        return VpFakes.of(IProject.class, values,
                (method, args) -> "getDiagramElementById".equals(method.getName())
                        && args.length == 1 && elementId.equals(args[0]) ? element : null);
    }

    private JsonArray parse(String raw) {
        return JsonParser.parseString(raw).getAsJsonArray();
    }
}
