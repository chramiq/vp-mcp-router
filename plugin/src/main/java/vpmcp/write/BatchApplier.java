package vpmcp.write;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vp.plugin.ApplicationManager;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IRelationship;
import com.vp.plugin.model.IProject;
import com.vp.plugin.model.factory.IModelElementFactory;
import java.util.HashMap;
import java.util.Map;
import vpmcp.vp.VpLog;

/**
 * Executes a validated batch against the open project. The plan is
 * re-validated immediately before the first mutation (preview results may
 * be stale), execution stops at the first failure, and every mutation is
 * audit-logged. Nothing here saves the project; the user saves in VP.
 */
public final class BatchApplier {

    private BatchApplier() {
    }

    public static JsonObject apply(IProject project, JsonArray ops) {
        JsonObject validation = PlanValidator.validate(project, ops);
        JsonArray applied = new JsonArray();
        JsonArray errors = new JsonArray();
        if (!validation.get("valid").getAsBoolean()) {
            return result(false, validation, applied, copyErrors(validation));
        }

        DiagramManager diagrams = ApplicationManager.instance().getDiagramManager();
        IModelElementFactory factory = IModelElementFactory.instance();
        Map<String, IDiagramUIModel> planDiagrams = new HashMap<>();
        Map<String, IDiagramElement> planViews = new HashMap<>();
        Map<String, IModelElement> planModels = new HashMap<>();

        for (JsonElement item : ops) {
            JsonObject op = item.getAsJsonObject();
            String id = op.get("id").getAsString();
            try {
                applied.add(executeOne(project, diagrams, factory, op, id, planDiagrams, planViews, planModels));
            } catch (RuntimeException failure) {
                errors.add(failureEntry(id, failure.toString()));
                break;
            }
        }
        return result(!applied.isEmpty(), validation, applied, errors);
    }

    private static JsonObject executeOne(IProject project, DiagramManager diagrams, IModelElementFactory factory,
            JsonObject op, String id, Map<String, IDiagramUIModel> planDiagrams,
            Map<String, IDiagramElement> planViews, Map<String, IModelElement> planModels) {
        switch (op.get("op").getAsString()) {
            case "create_diagram":
                return createDiagram(diagrams, op, id, planDiagrams);
            case "create_element":
                return createElement(project, diagrams, factory, op, id, planDiagrams, planViews, planModels);
            case "connect":
                return connect(project, diagrams, factory, op, id, planDiagrams, planViews, planModels);
            default:
                throw new IllegalStateException("Unvalidated op \"" + op.get("op").getAsString() + "\".");
        }
    }

    private static JsonObject createDiagram(DiagramManager diagrams, JsonObject op, String id,
            Map<String, IDiagramUIModel> planDiagrams) {
        IDiagramUIModel diagram = diagrams.createDiagram(op.get("diagram_type").getAsString());
        diagram.setName(op.get("name").getAsString().trim());
        diagrams.openDiagram(diagram);
        planDiagrams.put(id, diagram);
        VpLog.info("APPLY create_diagram id=" + id + " vp_id=" + diagram.getId());
        return appliedEntry(id, "diagram", diagram.getId(), diagram.getName());
    }

    private static JsonObject createElement(IProject project, DiagramManager diagrams, IModelElementFactory factory,
            JsonObject op, String id, Map<String, IDiagramUIModel> planDiagrams,
            Map<String, IDiagramElement> planViews, Map<String, IModelElement> planModels) {
        IDiagramUIModel diagram = requireDiagram(project, op.get("diagram"), planDiagrams);
        IModelElement model = newElement(factory, op.get("model_type").getAsString());
        model.setName(op.get("name").getAsString().trim());
        IDiagramElement view = diagrams.createDiagramElement(diagram, model);
        view.setBounds(number(op, "x", 10), number(op, "y", 10), number(op, "width", 80),
                number(op, "height", 40));
        planViews.put(id, view);
        planModels.put(id, model);
        VpLog.info("APPLY create_element id=" + id + " vp_id=" + view.getId());
        return appliedEntry(id, "element", view.getId(), model.getName());
    }

    private static JsonObject connect(IProject project, DiagramManager diagrams, IModelElementFactory factory,
            JsonObject op, String id, Map<String, IDiagramUIModel> planDiagrams,
            Map<String, IDiagramElement> planViews, Map<String, IModelElement> planModels) {
        IDiagramUIModel diagram = requireDiagram(project, op.get("diagram"), planDiagrams);
        IModelElement relModel = newRelationship(factory, op.get("rel_type").getAsString());
        if (!(relModel instanceof IRelationship)) {
            throw new IllegalStateException("Relationship model has no ends.");
        }
        Endpoint from = requireEndpoint(project, op.get("from"), planViews, planModels);
        Endpoint to = requireEndpoint(project, op.get("to"), planViews, planModels);
        IRelationship rel = (IRelationship) relModel;
        rel.setFrom(from.model);
        rel.setTo(to.model);
        if (op.has("name") && op.get("name").isJsonPrimitive()) {
            relModel.setName(op.get("name").getAsString());
        }
        IDiagramElement connector = diagrams.createConnector(diagram, relModel, from.view, to.view, null);
        VpLog.info("APPLY connect id=" + id + " vp_id=" + connector.getId());
        return appliedEntry(id, "relationship", connector.getId(), relModel.getName());
    }

    private static IModelElement newElement(IModelElementFactory factory, String modelType) {
        switch (modelType) {
            case "Actor":
                return factory.createActor();
            case "UseCase":
                return factory.createUseCase();
            case "Class":
                return factory.createClass();
            default:
                throw new IllegalStateException("Unvalidated model_type \"" + modelType + "\".");
        }
    }

    private static IModelElement newRelationship(IModelElementFactory factory, String relType) {
        switch (relType) {
            case "Association":
                return factory.createAssociation();
            case "Include":
                return factory.createInclude();
            case "Extend":
                return factory.createExtend();
            case "Generalization":
                return factory.createGeneralization();
            case "Dependency":
                return factory.createDependency();
            default:
                throw new IllegalStateException("Unvalidated rel_type \"" + relType + "\".");
        }
    }

    private static IDiagramUIModel requireDiagram(IProject project, JsonElement reference,
            Map<String, IDiagramUIModel> planDiagrams) {
        if (reference != null) {
            if (reference.isJsonObject()) {
                IDiagramUIModel planned = planDiagrams.get(reference.getAsJsonObject().get("ref").getAsString());
                if (planned != null) {
                    return planned;
                }
            } else if (reference.isJsonPrimitive()) {
                for (IDiagramUIModel diagram : project.toDiagramArray()) {
                    if (diagram != null && reference.getAsString().equals(diagram.getId())) {
                        return diagram;
                    }
                }
            }
        }
        throw new IllegalStateException("Diagram reference no longer resolves; re-run preview.");
    }

    private static Endpoint requireEndpoint(IProject project, JsonElement reference,
            Map<String, IDiagramElement> planViews, Map<String, IModelElement> planModels) {
        if (reference != null) {
            if (reference.isJsonObject()) {
                String id = reference.getAsJsonObject().get("ref").getAsString();
                if (planViews.containsKey(id) && planModels.containsKey(id)) {
                    return new Endpoint(planViews.get(id), planModels.get(id));
                }
            } else if (reference.isJsonPrimitive()) {
                IDiagramElement view = project.getDiagramElementById(reference.getAsString());
                if (view != null && view.getModelElement() != null) {
                    return new Endpoint(view, view.getModelElement());
                }
            }
        }
        throw new IllegalStateException("Endpoint reference no longer resolves; re-run preview.");
    }

    private static int number(JsonObject op, String name, int fallback) {
        JsonElement value = op.get(name);
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            return value.getAsInt();
        }
        return fallback;
    }

    private static JsonObject appliedEntry(String id, String kind, String vpId, String name) {
        JsonObject entry = new JsonObject();
        entry.addProperty("id", id);
        entry.addProperty("kind", kind);
        entry.addProperty("vp_id", vpId);
        entry.addProperty("name", name);
        return entry;
    }

    private static JsonObject failureEntry(String id, String message) {
        JsonObject entry = new JsonObject();
        entry.addProperty("id", id);
        entry.addProperty("message", message);
        return entry;
    }

    private static JsonArray copyErrors(JsonObject validation) {
        JsonArray copy = new JsonArray();
        validation.getAsJsonArray("errors").forEach(copy::add);
        return copy;
    }

    private static JsonObject result(boolean mutated, JsonObject validation, JsonArray applied, JsonArray errors) {
        JsonObject result = new JsonObject();
        result.addProperty("mutated", mutated);
        result.add("validation", validation);
        result.add("applied", applied);
        result.add("errors", errors);
        return result;
    }

    private static final class Endpoint {
        final IDiagramElement view;
        final IModelElement model;

        Endpoint(IDiagramElement view, IModelElement model) {
            this.view = view;
            this.model = model;
        }
    }
}
