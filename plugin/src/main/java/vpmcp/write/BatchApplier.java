package vpmcp.write;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vp.plugin.ApplicationManager;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.ICaptionUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IRelationship;
import com.vp.plugin.model.IProject;
import com.vp.plugin.model.factory.IModelElementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import vpmcp.vp.VpLog;

/**
 * Executes a validated batch against the open project. The plan is
 * re-validated immediately before the first mutation (preview results may
 * be stale). On failure, completed ops are compensated in reverse order
 * (saga): creations are deleted, deletions are final and only reported.
 * Compensation is best-effort; every step lands in the audit log.
 * Nothing here saves the project; the user saves in VP.
 */
public final class BatchApplier {

    private BatchApplier() {
    }

    public static JsonObject apply(IProject project, JsonArray ops) {
        JsonObject validation = PlanValidator.validate(project, ops);
        JsonArray applied = new JsonArray();
        JsonArray compensated = new JsonArray();
        JsonArray errors = new JsonArray();
        if (!validation.get("valid").getAsBoolean()) {
            return result(false, validation, applied, compensated, copyErrors(validation));
        }

        State state = new State(project, ApplicationManager.instance().getDiagramManager(),
                IModelElementFactory.instance());
        List<Compensation> compensations = new ArrayList<>();
        for (JsonElement item : ops) {
            JsonObject op = item.getAsJsonObject();
            String id = op.get("id").getAsString();
            try {
                applied.add(executeOne(state, op, id, compensations));
            } catch (RuntimeException failure) {
                errors.add(failureEntry(id, failure.toString()));
                compensate(compensations, compensated);
                break;
            }
        }
        return result(!applied.isEmpty(), validation, applied, compensated, errors);
    }

    private static void compensate(List<Compensation> compensations, JsonArray compensated) {
        for (int index = compensations.size() - 1; index >= 0; index--) {
            Compensation compensation = compensations.get(index);
            try {
                compensation.run();
                compensated.add(compensatedEntry(compensation.id, true, null));
                VpLog.info("COMPENSATE ok id=" + compensation.id);
            } catch (RuntimeException failure) {
                compensated.add(compensatedEntry(compensation.id, false, failure.toString()));
                VpLog.info("COMPENSATE failed id=" + compensation.id + ": " + failure);
            }
        }
    }

    private static JsonObject executeOne(State state, JsonObject op, String id, List<Compensation> compensations) {
        switch (op.get("op").getAsString()) {
            case "create_diagram":
                return createDiagram(state, op, id, compensations);
            case "create_element":
                return createElement(state, op, id, compensations);
            case "connect":
                return connect(state, op, id, compensations);
            case "duplicate_diagram":
                return duplicateDiagram(state, op, id, compensations);
            case "delete_diagram":
                return deleteDiagram(state, op, id);
            case "delete_element":
                return deleteElement(state, op, id);
            default:
                throw new IllegalStateException("Unvalidated op \"" + op.get("op").getAsString() + "\".");
        }
    }

    private static JsonObject createDiagram(State state, JsonObject op, String id,
            List<Compensation> compensations) {
        IDiagramUIModel diagram = state.diagrams.createDiagram(op.get("diagram_type").getAsString());
        diagram.setName(op.get("name").getAsString().trim());
        state.diagrams.openDiagram(diagram);
        state.diagramsByPlanId.put(id, diagram);
        compensations.add(new Compensation(id, () -> diagram.delete()));
        VpLog.info("APPLY create_diagram id=" + id + " vp_id=" + diagram.getId());
        return appliedEntry(id, "diagram", diagram.getId(), diagram.getName());
    }

    private static JsonObject createElement(State state, JsonObject op, String id,
            List<Compensation> compensations) {
        IDiagramUIModel diagram = requireDiagram(state, op.get("diagram"));
        IModelElement model = newElement(state.factory, op.get("model_type").getAsString());
        model.setName(op.get("name").getAsString().trim());
        IDiagramElement view = state.diagrams.createDiagramElement(diagram, model);
        int gx = number(op, "x", 10);
        int gy = number(op, "y", 10);
        int gw = number(op, "width", 80);
        int gh = number(op, "height", 40);
        view.setBounds(gx, gy, gw, gh);
        if (op.get("model_type").getAsString().equals("Actor")) {
            int[] caption = actorCaptionBounds(gx, gy, gw, gh);
            view.getCaptionUIModel().setBounds(caption[0], caption[1], caption[2], caption[3]);
        }
        state.views.put(id, view);
        state.models.put(id, model);
        state.owners.put(view.getId(), diagram);
        compensations.add(new Compensation(id, () -> deleteView(diagram, view, model)));
        VpLog.info("APPLY create_element id=" + id + " vp_id=" + view.getId());
        return appliedEntry(id, "element", view.getId(), model.getName());
    }

    private static JsonObject connect(State state, JsonObject op, String id, List<Compensation> compensations) {
        IDiagramUIModel diagram = requireDiagram(state, op.get("diagram"));
        IModelElement relModel = newRelationship(state.factory, op.get("rel_type").getAsString());
        if (!(relModel instanceof IRelationship)) {
            throw new IllegalStateException("Relationship model has no ends.");
        }
        Endpoint from = requireEndpoint(state, op.get("from"));
        Endpoint to = requireEndpoint(state, op.get("to"));
        IRelationship rel = (IRelationship) relModel;
        rel.setFrom(from.model);
        rel.setTo(to.model);
        if (op.has("name") && op.get("name").isJsonPrimitive()) {
            relModel.setName(op.get("name").getAsString());
        }
        IDiagramElement connector = state.diagrams.createConnector(diagram, relModel, from.view, to.view, null);
        pinMember(connector, op.get("from_member"), true);
        pinMember(connector, op.get("to_member"), false);
        state.views.put(id, connector);
        state.models.put(id, relModel);
        state.owners.put(connector.getId(), diagram);
        compensations.add(new Compensation(id, () -> deleteView(diagram, connector, relModel)));
        VpLog.info("APPLY connect id=" + id + " vp_id=" + connector.getId());
        return appliedEntry(id, "relationship", connector.getId(), relModel.getName());
    }

    /**
     * Shallow copy: fresh views on the same-type diagram, shared models.
     * Additive trials on the copy are safe; edits to shared model properties
     * leak to the source, and deleting the copy never deletes models.
     */
    private static JsonObject duplicateDiagram(State state, JsonObject op, String id,
            List<Compensation> compensations) {
        IDiagramUIModel src = requireDiagram(state, op.get("diagram"));
        String name = op.get("name").getAsString().trim();
        IDiagramUIModel copy = state.diagrams.createDiagram(src.getType());
        copy.setName(name);
        state.diagrams.openDiagram(copy);
        state.diagramsByPlanId.put(id, copy);
        Map<String, IDiagramElement> clones = new HashMap<>();
        int shapes = 0;
        int connectors = 0;
        int skipped = 0;
        IDiagramElement[] elements = src.toDiagramElementArray();
        if (elements != null) {
            for (IDiagramElement view : elements) {
                if (view == null || view instanceof IConnectorUIModel) {
                    continue;
                }
                IModelElement model = view.getModelElement();
                if (model == null) {
                    skipped++;
                    continue;
                }
                IDiagramElement clone = state.diagrams.createDiagramElement(copy, model);
                clone.setBounds(view.getX(), view.getY(), view.getWidth(), view.getHeight());
                copyCaption(view, clone);
                clones.put(view.getId(), clone);
                shapes++;
            }
            List<IConnectorUIModel> pending = new ArrayList<>();
            for (IDiagramElement view : elements) {
                if (view instanceof IConnectorUIModel) {
                    pending.add((IConnectorUIModel) view);
                }
            }
            while (!pending.isEmpty()) {
                int progressed = 0;
                java.util.Iterator<IConnectorUIModel> it = pending.iterator();
                while (it.hasNext()) {
                    IConnectorUIModel link = it.next();
                    IDiagramElement from = remapEnd(link.getFromShape(), link.getFromConnector(), clones);
                    IDiagramElement to = remapEnd(link.getToShape(), link.getToConnector(), clones);
                    IModelElement relModel = link.getModelElement();
                    if (from == null || to == null || relModel == null) {
                        continue;
                    }
                    IDiagramElement clone =
                            state.diagrams.createConnector(copy, relModel, from, to, link.getPoints());
                    if (clone instanceof IConnectorUIModel) {
                        IConnectorUIModel cloneLink = (IConnectorUIModel) clone;
                        if (link.getFromMemberId() != null) {
                            cloneLink.setFromMemberId(link.getFromMemberId());
                        }
                        if (link.getToMemberId() != null) {
                            cloneLink.setToMemberId(link.getToMemberId());
                        }
                    }
                    copyCaption(link, clone);
                    clones.put(link.getId(), clone);
                    it.remove();
                    connectors++;
                    progressed++;
                }
                if (progressed == 0) {
                    skipped += pending.size();
                    break;
                }
            }
        }
        compensations.add(new Compensation(id, () -> copy.delete()));
        VpLog.info("APPLY duplicate_diagram id=" + id + " vp_id=" + copy.getId()
                + " shapes=" + shapes + " connectors=" + connectors + " skipped=" + skipped);
        JsonObject entry = appliedEntry(id, "diagram", copy.getId(), name);
        entry.addProperty("copied_shapes", shapes);
        entry.addProperty("copied_connectors", connectors);
        entry.addProperty("skipped", skipped);
        return entry;
    }

    private static IDiagramElement remapEnd(IShapeUIModel shape, IConnectorUIModel connector,
            Map<String, IDiagramElement> clones) {
        if (shape != null) {
            return clones.get(shape.getId());
        }
        if (connector != null) {
            return clones.get(connector.getId());
        }
        return null;
    }

    private static void copyCaption(IDiagramElement src, IDiagramElement dst) {
        try {
            ICaptionUIModel from = src.getCaptionUIModel();
            ICaptionUIModel to = dst.getCaptionUIModel();
            if (from != null && to != null) {
                to.setBounds(from.getX(), from.getY(), from.getWidth(), from.getHeight());
            }
        } catch (RuntimeException cosmetic) {
            VpLog.info("CAPTION copy skipped: " + cosmetic);
        }
    }

    private static JsonObject deleteDiagram(State state, JsonObject op, String id) {
        IDiagramUIModel diagram = requireDiagram(state, op.get("diagram"));
        String vpId = diagram.getId();
        String name = diagram.getName();
        diagram.delete();
        VpLog.info("APPLY delete_diagram id=" + id + " vp_id=" + vpId);
        return appliedEntry(id, "deletion", vpId, name);
    }

    private static JsonObject deleteElement(State state, JsonObject op, String id) {
        Endpoint endpoint = requireEndpoint(state, op.get("element"));
        IDiagramUIModel owner = findOwner(state, endpoint.view.getId());
        String vpId = endpoint.view.getId();
        deleteView(owner, endpoint.view, endpoint.model);
        VpLog.info("APPLY delete_element id=" + id + " vp_id=" + vpId);
        return appliedEntry(id, "deletion", vpId, endpoint.model.getName());
    }

    private static void deleteView(IDiagramUIModel owner, IDiagramElement view, IModelElement model) {
        RuntimeException first = null;
        try {
            if (owner != null) {
                owner.removeDiagramElement(view);
            }
        } catch (RuntimeException failure) {
            first = failure;
        }
        try {
            if (model != null) {
                model.delete();
            }
        } catch (RuntimeException failure) {
            if (first != null) {
                throw first;
            }
            throw failure;
        }
        if (first != null) {
            throw first;
        }
    }

    private static IDiagramUIModel findOwner(State state, String viewId) {
        IDiagramUIModel planned = state.owners.get(viewId);
        if (planned != null) {
            return planned;
        }
        IDiagramUIModel[] diagrams = state.project.toDiagramArray();
        if (diagrams != null) {
            for (IDiagramUIModel diagram : diagrams) {
                if (diagram == null) {
                    continue;
                }
                IDiagramElement[] elements = diagram.toDiagramElementArray();
                if (elements == null) {
                    continue;
                }
                for (IDiagramElement element : elements) {
                    if (element != null && viewId.equals(element.getId())) {
                        return diagram;
                    }
                }
            }
        }
        return null;
    }

    private static void pinMember(IDiagramElement connector, JsonElement member, boolean from) {
        if (member == null || member.isJsonNull() || !(connector instanceof IConnectorUIModel)) {
            return;
        }
        IConnectorUIModel link = (IConnectorUIModel) connector;
        if (from) {
            link.setFromMemberId(member.getAsString());
        } else {
            link.setToMemberId(member.getAsString());
        }
    }

    /** Caption box under an actor shape; pure geometry, no VP. */
    public static int[] actorCaptionBounds(int x, int y, int width, int height) {
        return new int[] {x + (width - 50) / 2, y + height, 50, 15};
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

    private static IDiagramUIModel requireDiagram(State state, JsonElement reference) {
        if (reference != null) {
            if (reference.isJsonObject()) {
                IDiagramUIModel planned =
                        state.planDiagrams(reference.getAsJsonObject().get("ref").getAsString());
                if (planned != null) {
                    return planned;
                }
            } else if (reference.isJsonPrimitive()) {
                IDiagramUIModel[] diagrams = state.project.toDiagramArray();
                if (diagrams != null) {
                    for (IDiagramUIModel diagram : diagrams) {
                        if (diagram != null && reference.getAsString().equals(diagram.getId())) {
                            return diagram;
                        }
                    }
                }
            }
        }
        throw new IllegalStateException("Diagram reference no longer resolves; re-run preview.");
    }

    private static Endpoint requireEndpoint(State state, JsonElement reference) {
        if (reference != null) {
            if (reference.isJsonObject()) {
                String id = reference.getAsJsonObject().get("ref").getAsString();
                if (state.views.containsKey(id) && state.models.containsKey(id)) {
                    return new Endpoint(state.views.get(id), state.models.get(id));
                }
            } else if (reference.isJsonPrimitive()) {
                IDiagramElement view = state.project.getDiagramElementById(reference.getAsString());
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

    private static JsonObject compensatedEntry(String id, boolean undone, String message) {
        JsonObject entry = new JsonObject();
        entry.addProperty("id", id);
        entry.addProperty("undone", undone);
        if (message != null) {
            entry.addProperty("message", message);
        }
        return entry;
    }

    private static JsonArray copyErrors(JsonObject validation) {
        JsonArray copy = new JsonArray();
        validation.getAsJsonArray("errors").forEach(copy::add);
        return copy;
    }

    private static JsonObject result(boolean mutated, JsonObject validation, JsonArray applied,
            JsonArray compensated, JsonArray errors) {
        JsonObject result = new JsonObject();
        result.addProperty("mutated", mutated);
        result.add("validation", validation);
        result.add("applied", applied);
        result.add("compensated", compensated);
        result.add("errors", errors);
        return result;
    }

    private static final class State {
        final IProject project;
        final DiagramManager diagrams;
        final IModelElementFactory factory;
        final Map<String, IDiagramUIModel> diagramsByPlanId = new HashMap<>();
        final Map<String, IDiagramElement> views = new HashMap<>();
        final Map<String, IModelElement> models = new HashMap<>();
        final Map<String, IDiagramUIModel> owners = new HashMap<>();

        State(IProject project, DiagramManager diagrams, IModelElementFactory factory) {
            this.project = project;
            this.diagrams = diagrams;
            this.factory = factory;
        }

        IDiagramUIModel planDiagrams(String id) {
            return diagramsByPlanId.get(id);
        }
    }

    private static final class Compensation {
        final String id;
        final Runnable action;

        Compensation(String id, Runnable action) {
            this.id = id;
            this.action = action;
        }

        void run() {
            action.run();
        }
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
