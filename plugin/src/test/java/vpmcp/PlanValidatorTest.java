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
