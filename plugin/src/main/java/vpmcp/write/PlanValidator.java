package vpmcp.write;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IProject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Validates a write batch without mutating anything. Reads the open project
 * (diagram and element existence) but never creates, updates, or saves.
 * Every rejection lands in {@code errors[]}; {@code valid} is true iff empty.
 */
public final class PlanValidator {

    private static final Set<String> DIAGRAMS = new HashSet<>(Arrays.asList("ClassDiagram",
            "UseCaseDiagram", "ActivityDiagram", "StateDiagram", "ERDiagram", "InteractionDiagram",
            "DeploymentDiagram"));
    private static final Set<String> ELEMENTS = new HashSet<>(Arrays.asList("Actor", "UseCase",
            "Class", "Activity", "InitialNode", "DecisionNode", "ActivityFinalNode", "State2",
            "DBTable", "Component", "Node", "LifeLine"));
    private static final Set<String> MEMBERS =
            new HashSet<>(Arrays.asList("Attribute", "Operation", "DBColumn"));
    private static final Set<String> RELATIONSHIPS = new HashSet<>(Arrays.asList("Association",
            "Include", "Extend", "Generalization", "Dependency", "Message", "Transition2"));
    private static final Set<String> OPS =
            new HashSet<>(Arrays.asList("create_diagram", "create_element", "connect",
                    "duplicate_diagram", "add_member", "update_element", "move_element",
                    "show_element", "delete_model", "delete_diagram", "delete_element"));

    private PlanValidator() {
    }

    public static JsonObject validate(IProject project, JsonArray ops) {
        JsonArray plan = new JsonArray();
        JsonArray errors = new JsonArray();
        Map<String, String> symbols = new HashMap<>();

        if (ops == null) {
            errors.add(error("<batch>", "Missing \"ops\" array."));
            return result(false, plan, errors);
        }

        for (JsonElement item : ops) {
            if (item == null || !item.isJsonObject()) {
                errors.add(error("<batch>", "Every op must be an object."));
                continue;
            }
            validateOp(project, item.getAsJsonObject(), symbols, plan, errors);
        }
        return result(errors.size() == 0, plan, errors);
    }

    private static void validateOp(IProject project, JsonObject op, Map<String, String> symbols, JsonArray plan,
            JsonArray errors) {
        String id = text(op, "id");
        String kind = text(op, "op");
        if (id == null || id.isEmpty()) {
            errors.add(error("<batch>", "Op is missing its \"id\"."));
            return;
        }
        if (symbols.containsKey(id)) {
            errors.add(error(id, "Duplicate op id."));
            return;
        }
        if (kind == null) {
            errors.add(error(id, "Op is missing its \"op\" type."));
            return;
        }

        switch (kind) {
            case "create_diagram":
                validateCreateDiagram(op, id, symbols, plan, errors);
                break;
            case "create_element":
                validateCreateElement(project, op, id, symbols, plan, errors);
                break;
            case "connect":
                validateConnect(project, op, id, symbols, plan, errors);
                break;
            case "duplicate_diagram":
                validateDuplicateDiagram(project, op, id, symbols, plan, errors);
                break;
            case "add_member":
                validateAddMember(project, op, id, symbols, plan, errors);
                break;
            case "update_element":
                validateUpdateElement(project, op, id, symbols, plan, errors);
                break;
            case "move_element":
                validateMoveElement(project, op, id, symbols, plan, errors);
                break;
            case "show_element":
                validateShowElement(project, op, id, symbols, plan, errors);
                break;
            case "delete_model":
                validateDeleteModel(project, op, id, symbols, plan, errors);
                break;
            case "delete_diagram":
                validateDeleteDiagram(project, op, id, symbols, plan, errors);
                break;
            case "delete_element":
                validateDeleteElement(project, op, id, symbols, plan, errors);
                break;
            default:
                errors.add(error(id, "Unknown op \"" + kind + "\"; allowed: " + OPS + "."));
                break;
        }
    }

    private static void validateCreateDiagram(JsonObject op, String id, Map<String, String> symbols, JsonArray plan,
            JsonArray errors) {
        String diagramType = text(op, "diagram_type");
        String name = text(op, "name");
        if (diagramType == null || !DIAGRAMS.contains(diagramType)) {
            errors.add(error(id, "Unsupported diagram_type; allowed: " + DIAGRAMS + "."));
            return;
        }
        if (name == null || name.trim().isEmpty()) {
            errors.add(error(id, "Diagram \"name\" is required."));
            return;
        }
        symbols.put(id, "diagram");
        plan.add(entry(id, "create_diagram", diagramType + " '" + name.trim() + "'",
                "delete diagram '" + name.trim() + "'"));
    }

    private static void validateCreateElement(IProject project, JsonObject op, String id,
            Map<String, String> symbols, JsonArray plan, JsonArray errors) {
        String diagram = resolveDiagram(project, op.get("diagram"), symbols);
        if (diagram == null) {
            errors.add(error(id, "Unknown diagram reference; use an existing diagram id or a plan ref."));
            return;
        }
        String modelType = text(op, "model_type");
        if (modelType != null && MEMBERS.contains(modelType)) {
            errors.add(error(id,
                    "Members are not placeable; use add_member with a parent element."));
            return;
        }
        if (modelType == null || !ELEMENTS.contains(modelType)) {
            errors.add(error(id, "Unsupported model_type; allowed: " + ELEMENTS + "."));
            return;
        }
        String name = text(op, "name");
        if (name == null || name.trim().isEmpty()) {
            errors.add(error(id, "Element \"name\" is required."));
            return;
        }
        if (!optionalNumbers(op, "x", "y", "width", "height")) {
            errors.add(error(id, "Geometry fields x/y/width/height must be numbers."));
            return;
        }
        symbols.put(id, "element");
        plan.add(entry(id, "create_element",
                modelType + " '" + name.trim() + "' on diagram '" + diagram + "'",
                "remove '" + name.trim() + "' from its diagram and delete its model"));
    }

    private static void validateConnect(IProject project, JsonObject op, String id, Map<String, String> symbols,
            JsonArray plan, JsonArray errors) {
        String diagram = resolveDiagram(project, op.get("diagram"), symbols);
        if (diagram == null) {
            errors.add(error(id, "Unknown diagram reference; use an existing diagram id or a plan ref."));
            return;
        }
        String relType = text(op, "rel_type");
        if (relType == null || !RELATIONSHIPS.contains(relType)) {
            errors.add(error(id, "Unsupported rel_type; allowed: " + RELATIONSHIPS + "."));
            return;
        }
        String from = resolveElement(project, op.get("from"), symbols);
        String to = resolveElement(project, op.get("to"), symbols);
        if (from == null || to == null) {
            errors.add(error(id, "Unknown endpoint; use an existing element id or a plan ref."));
            return;
        }
        String problem = pointsProblem(op.get("points"));
        if (problem == null) {
            problem = memberProblem(op.get("from_member"), "from_member");
        }
        if (problem == null) {
            problem = memberProblem(op.get("to_member"), "to_member");
        }
        if (problem != null) {
            errors.add(error(id, problem));
            return;
        }
        symbols.put(id, "relationship");
        plan.add(entry(id, "connect", relType + " from '" + from + "' to '" + to + "'",
                "remove the " + relType + " connector and delete its model"));
    }

    private static void validateDuplicateDiagram(IProject project, JsonObject op, String id,
            Map<String, String> symbols, JsonArray plan, JsonArray errors) {
        String diagram = resolveDiagram(project, op.get("diagram"), symbols);
        if (diagram == null) {
            errors.add(error(id, "Unknown diagram reference; use an existing diagram id or a plan ref."));
            return;
        }
        String name = text(op, "name");
        if (name == null || name.trim().isEmpty()) {
            errors.add(error(id, "Duplicate \"name\" is required."));
            return;
        }
        symbols.put(id, "diagram");
        plan.add(entry(id, "duplicate_diagram",
                "copy of diagram '" + diagram + "' as '" + name.trim() + "'",
                "delete diagram '" + name.trim() + "' (shared models are kept)"));
    }

    private static void validateAddMember(IProject project, JsonObject op, String id,
            Map<String, String> symbols, JsonArray plan, JsonArray errors) {
        String parent = resolveElement(project, op.get("parent"), symbols);
        if (parent == null) {
            errors.add(error(id, "Unknown parent; use an existing element id or a plan ref."));
            return;
        }
        String memberType = text(op, "member_type");
        if (memberType == null || !MEMBERS.contains(memberType)) {
            errors.add(error(id, "Unsupported member_type; allowed: " + MEMBERS + "."));
            return;
        }
        String name = text(op, "name");
        if (name == null || name.trim().isEmpty()) {
            errors.add(error(id, "Member \"name\" is required."));
            return;
        }
        JsonElement type = op.get("type");
        if (type != null && !type.isJsonNull()
                && (!type.isJsonPrimitive() || type.getAsString().trim().isEmpty())) {
            errors.add(error(id, "Member \"type\" must be a non-blank string."));
            return;
        }
        symbols.put(id, "member");
        plan.add(entry(id, "add_member",
                memberType + " '" + name.trim() + "' on '" + parent + "'",
                "remove '" + name.trim() + "' from its parent"));
    }

    private static void validateUpdateElement(IProject project, JsonObject op, String id,
            Map<String, String> symbols, JsonArray plan, JsonArray errors) {
        String model = resolveModel(project, op.get("model"), symbols);
        if (model == null) {
            errors.add(error(id, "Unknown model; use an existing model id or a plan ref."));
            return;
        }
        JsonElement name = op.get("name");
        if (name != null && !name.isJsonNull()
                && (!name.isJsonPrimitive() || name.getAsString().trim().isEmpty())) {
            errors.add(error(id, "Updated \"name\" must be a non-blank string."));
            return;
        }
        JsonElement documentation = op.get("documentation");
        if (documentation != null && !documentation.isJsonNull()
                && !documentation.isJsonPrimitive()) {
            errors.add(error(id, "Updated \"documentation\" must be a string; empty clears it."));
            return;
        }
        JsonElement stereotypes = op.get("stereotypes");
        if (stereotypes != null && !stereotypes.isJsonNull()) {
            if (!stereotypes.isJsonArray()) {
                errors.add(error(id,
                        "Updated \"stereotypes\" must be an array of strings; it replaces all."));
                return;
            }
            for (JsonElement item : stereotypes.getAsJsonArray()) {
                if (item == null || !item.isJsonPrimitive()
                        || item.getAsString().trim().isEmpty()) {
                    errors.add(error(id,
                            "Updated \"stereotypes\" must be an array of non-blank strings."));
                    return;
                }
            }
        }
        if ((name == null || name.isJsonNull())
                && (documentation == null || documentation.isJsonNull())
                && (stereotypes == null || stereotypes.isJsonNull())) {
            errors.add(error(id,
                    "Update needs at least one of name/documentation/stereotypes."));
            return;
        }
        symbols.put(id, "element");
        plan.add(entry(id, "update_element", "update model '" + model + "'",
                "restore its previous name, documentation and stereotypes"));
    }

    private static String pointsProblem(JsonElement points) {
        if (points == null || points.isJsonNull()) {
            return null;
        }
        if (!points.isJsonArray() || points.getAsJsonArray().size() < 2) {
            return "\"points\" must be an array of at least two {x, y} waypoints.";
        }
        for (JsonElement item : points.getAsJsonArray()) {
            if (item == null || !item.isJsonObject() || !isNumber(item.getAsJsonObject(), "x")
                    || !isNumber(item.getAsJsonObject(), "y")) {
                return "\"points\" entries must be {x, y} numbers.";
            }
        }
        return null;
    }

    private static boolean isNumber(JsonObject holder, String name) {
        JsonElement value = holder.get(name);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
    }

    private static void validateMoveElement(IProject project, JsonObject op, String id,
            Map<String, String> symbols, JsonArray plan, JsonArray errors) {
        String element = resolveElement(project, op.get("element"), symbols);
        if (element == null) {
            errors.add(error(id, "Unknown element reference; use an existing element id or a plan ref."));
            return;
        }
        boolean any = false;
        for (String field : new String[] {"x", "y", "width", "height"}) {
            JsonElement value = op.get(field);
            if (value == null || value.isJsonNull()) {
                continue;
            }
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                errors.add(error(id, "Geometry fields x/y/width/height must be numbers."));
                return;
            }
            any = true;
        }
        if (!any) {
            errors.add(error(id, "Move needs at least one of x/y/width/height."));
            return;
        }
        symbols.put(id, "element");
        plan.add(entry(id, "move_element", "move '" + element + "'",
                "restore its previous bounds"));
    }

    private static void validateShowElement(IProject project, JsonObject op, String id,
            Map<String, String> symbols, JsonArray plan, JsonArray errors) {
        String diagram = resolveDiagram(project, op.get("diagram"), symbols);
        if (diagram == null) {
            errors.add(error(id, "Unknown diagram reference; use an existing diagram id or a plan ref."));
            return;
        }
        String model = resolveModel(project, op.get("model"), symbols);
        if (model == null) {
            errors.add(error(id, "Unknown model; use an existing model id or a plan ref."));
            return;
        }
        if (!optionalNumbers(op, "x", "y", "width", "height")) {
            errors.add(error(id, "Geometry fields x/y/width/height must be numbers."));
            return;
        }
        symbols.put(id, "element");
        plan.add(entry(id, "show_element",
                "show model '" + model + "' on diagram '" + diagram + "' (model is shared)",
                "remove the view; the shared model is kept"));
    }

    private static void validateDeleteModel(IProject project, JsonObject op, String id,
            Map<String, String> symbols, JsonArray plan, JsonArray errors) {
        String model = resolveModel(project, op.get("model"), symbols);
        if (model == null) {
            errors.add(error(id, "Unknown model; use an existing model id or a plan ref."));
            return;
        }
        symbols.put(id, "deletion");
        plan.add(entry(id, "delete_model", "delete model '" + model + "' and all its views",
                "none; deletion is final (project is not auto-saved)"));
    }

    private static String resolveModel(IProject project, JsonElement reference,
            Map<String, String> symbols) {
        for (String kind : new String[] {"element", "member", "relationship"}) {
            String id = resolveRef(reference, symbols, kind);
            if (id != null) {
                return id;
            }
        }
        if (reference != null && reference.isJsonPrimitive()) {
            String candidate = reference.getAsString();
            try {
                if (project != null && project.getModelElementById(candidate) != null) {
                    return candidate;
                }
            } catch (RuntimeException unknown) {
                return null;
            }
        }
        return null;
    }

    private static void validateDeleteDiagram(IProject project, JsonObject op, String id,
            Map<String, String> symbols, JsonArray plan, JsonArray errors) {
        String diagram = resolveDiagram(project, op.get("diagram"), symbols);
        if (diagram == null) {
            errors.add(error(id, "Unknown diagram reference; use an existing diagram id or a plan ref."));
            return;
        }
        symbols.put(id, "deletion");
        plan.add(entry(id, "delete_diagram", "delete diagram '" + diagram + "'",
                "none; deletion is final (project is not auto-saved)"));
    }

    private static void validateDeleteElement(IProject project, JsonObject op, String id,
            Map<String, String> symbols, JsonArray plan, JsonArray errors) {
        String element = resolveElement(project, op.get("element"), symbols);
        if (element == null && isRefTo(op.get("element"), symbols, "relationship")) {
            element = text(op.getAsJsonObject("element"), "ref");
        }
        if (element == null) {
            errors.add(error(id, "Unknown element reference; use an existing element id or a plan ref."));
            return;
        }
        symbols.put(id, "deletion");
        plan.add(entry(id, "delete_element", "delete element '" + element + "'",
                "none; deletion is final (project is not auto-saved)"));
    }

    private static boolean isRefTo(JsonElement reference, Map<String, String> symbols, String wantKind) {
        return reference != null && reference.isJsonObject()
                && wantKind.equals(symbols.get(text(reference.getAsJsonObject(), "ref")));
    }

    private static String memberProblem(JsonElement member, String name) {
        if (member == null || member.isJsonNull()) {
            return null;
        }
        if (!member.isJsonPrimitive() || member.getAsString().trim().isEmpty()) {
            return "\"" + name + "\" must be a member model id.";
        }
        return null;
    }

    private static String resolveDiagram(IProject project, JsonElement reference, Map<String, String> symbols) {
        String id = resolveRef(reference, symbols, "diagram");
        if (id != null) {
            return id;
        }
        if (reference != null && reference.isJsonPrimitive()) {
            String candidate = reference.getAsString();
            IDiagramUIModel[] diagrams = project == null ? null : project.toDiagramArray();
            if (diagrams != null) {
                for (IDiagramUIModel diagram : diagrams) {
                    if (diagram != null && candidate.equals(diagram.getId())) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private static String resolveElement(IProject project, JsonElement reference, Map<String, String> symbols) {
        String id = resolveRef(reference, symbols, "element");
        if (id != null) {
            return id;
        }
        if (reference != null && reference.isJsonPrimitive()) {
            String candidate = reference.getAsString();
            try {
                if (project != null && project.getDiagramElementById(candidate) != null) {
                    return candidate;
                }
            } catch (RuntimeException unknown) {
                return null;
            }
        }
        return null;
    }

    private static String resolveRef(JsonElement reference, Map<String, String> symbols, String wantKind) {
        if (reference != null && reference.isJsonObject() && reference.getAsJsonObject().has("ref")) {
            String id = text(reference.getAsJsonObject(), "ref");
            if (wantKind.equals(symbols.get(id))) {
                return id;
            }
        }
        return null;
    }

    private static boolean optionalNumbers(JsonObject op, String... names) {
        for (String name : names) {
            JsonElement value = op.get(name);
            if (value != null && !value.isJsonNull() && !value.isJsonPrimitive()) {
                return false;
            }
            if (value != null && value.isJsonPrimitive() && !value.getAsJsonPrimitive().isNumber()) {
                return false;
            }
        }
        return true;
    }

    private static String text(JsonObject holder, String name) {
        JsonElement value = holder.get(name);
        if (value == null || !value.isJsonPrimitive()) {
            return null;
        }
        return value.getAsString();
    }

    private static JsonObject entry(String id, String op, String summary, String undo) {
        JsonObject entry = new JsonObject();
        entry.addProperty("id", id);
        entry.addProperty("op", op);
        entry.addProperty("summary", summary);
        entry.addProperty("undo", undo);
        return entry;
    }

    private static JsonObject entry(String id, String op, String summary) {
        return entry(id, op, summary, "none");
    }

    private static JsonObject error(String id, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("id", id);
        error.addProperty("message", message);
        return error;
    }

    private static JsonObject result(boolean valid, JsonArray plan, JsonArray errors) {
        JsonObject result = new JsonObject();
        result.addProperty("valid", valid);
        result.add("plan", plan);
        result.add("errors", errors);
        return result;
    }
}
