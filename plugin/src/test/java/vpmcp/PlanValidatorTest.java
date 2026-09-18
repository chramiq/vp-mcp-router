package vpmcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vp.plugin.model.IProject;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vpmcp.write.PlanValidator;

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
        assertTrue(result.toString().contains("model_type"));
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
