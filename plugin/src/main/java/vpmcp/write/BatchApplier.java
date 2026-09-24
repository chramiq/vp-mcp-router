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
            case "add_member":
                return addMember(state, op, id, compensations);
            case "update_element":
                return updateElement(state, op, id, compensations);
            case "move_element":
                return moveElement(state, op, id, compensations);
            case "show_element":
                return showElement(state, op, id, compensations);
            case "delete_model":
                return deleteModel(state, op, id);
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
        String modelType = op.get("model_type").getAsString();
        String name = op.get("name").getAsString().trim();
        IModelElement model = newElement(state.factory, modelType);
        unsyncTable(model);
        model.setName(name);
        IDiagramElement first = state.diagrams.createDiagramElement(diagram, model);
        IModelElement placed = model;
        IDiagramElement view = first;
        if (view == null) {
            // Some types (lifelines) refuse model-first placement; the diagram
            // creates the view with its own model instead (probe-verified).
            String shape = VIEW_FIRST_SHAPES.getOrDefault(modelType, modelType);
            view = diagram.createDiagramElement(shape);
            if (view == null) {
                throw new IllegalStateException(
                        "Cannot place \"" + modelType + "\" on this diagram.");
            }
            if (view.getModelElement() != null) {
                placed = view.getModelElement();
            }
        }
        if (!name.equals(placed.getName())) {
            // Some types (DB tables) drop a pre-placement name; re-assert it.
            placed.setName(name);
        }
        int gx = number(op, "x", 10);
        int gy = number(op, "y", 10);
        int gw = number(op, "width", 80);
        int gh = number(op, "height", 40);
        view.setBounds(gx, gy, gw, gh);
        if (modelType.equals("Actor")) {
            int[] caption = actorCaptionBounds(gx, gy, gw, gh);
            view.getCaptionUIModel().setBounds(caption[0], caption[1], caption[2], caption[3]);
        } else if ("State2".equals(modelType) || "DecisionNode".equals(modelType)) {
            int[] caption = insideCaptionBounds(gx, gy, gw, gh);
            view.getCaptionUIModel().setBounds(caption[0], caption[1], caption[2], caption[3]);
        }
        state.views.put(id, view);
        state.models.put(id, placed);
        state.owners.put(view.getId(), diagram);
        final IDiagramElement target = view;
        final IModelElement kept = placed;
        compensations.add(new Compensation(id, () -> deleteView(diagram, target, kept)));
        if (placed != model) {
            dropOrphan(model);
        }
        VpLog.info("APPLY create_element id=" + id + " vp_id=" + view.getId());
        JsonObject placedEntry = appliedEntry(id, "element", view.getId(), placed.getName());
        if (!name.equals(placed.getName())) {
            placedEntry.addProperty("name_warning",
                    "VP kept \"" + placed.getName() + "\" instead of \"" + name + "\".");
        }
        return placedEntry;
    }

    /** Fresh tables follow entity naming by default; opt out so setName sticks. */
    private static void unsyncTable(IModelElement model) {
        if (model instanceof com.vp.plugin.model.IDBTable) {
            com.vp.plugin.model.IDBTable table = (com.vp.plugin.model.IDBTable) model;
            table.setSyncType(com.vp.plugin.model.IDBTable.SYNC_TYPE_NOT_SYNC);
            table.setOrmSyncState(com.vp.plugin.model.IDBTable.ORM_SYNC_STATE_NOT_SYNC);
        }
    }

    private static void dropOrphan(IModelElement model) {
        try {
            model.delete();
        } catch (RuntimeException leftover) {
            VpLog.info("ORPHAN kept: " + leftover);
        }
    }

    private static JsonObject connect(State state, JsonObject op, String id, List<Compensation> compensations) {
        IDiagramUIModel diagram = requireDiagram(state, op.get("diagram"));
        IModelElement relModel = newRelationship(state.factory, op.get("rel_type").getAsString());
        Endpoint from = requireEndpoint(state, op.get("from"));
        Endpoint to = requireEndpoint(state, op.get("to"));
        if (relModel instanceof IRelationship) {
            // Messages are not relationships; their views carry the endpoints.
            IRelationship rel = (IRelationship) relModel;
            rel.setFrom(from.model);
            rel.setTo(to.model);
        }
        if (op.has("name") && op.get("name").isJsonPrimitive()) {
            relModel.setName(op.get("name").getAsString());
        }
        IDiagramElement connector = state.diagrams.createConnector(diagram, relModel, from.view,
                to.view, toPointArray(op.get("points")));
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

    /** Waypoints for connect; null when the op carries no points. Pure JSON, no VP. */
    public static java.awt.Point[] toPointArray(JsonElement points) {
        if (points == null || points.isJsonNull()) {
            return null;
        }
        JsonArray array = points.getAsJsonArray();
        java.awt.Point[] out = new java.awt.Point[array.size()];
        for (int index = 0; index < array.size(); index++) {
            JsonObject point = array.get(index).getAsJsonObject();
            out[index] = new java.awt.Point(point.get("x").getAsInt(), point.get("y").getAsInt());
        }
        return out;
    }

    /**
     * Containment writes: attributes, operations, columns. The adder is
     * resolved from the child's interface (addAttribute, addDBColumn…),
     * so new member kinds need no new code — probe-verified.
     */
    private static JsonObject addMember(State state, JsonObject op, String id,
            List<Compensation> compensations) {
        Endpoint parent = requireEndpoint(state, op.get("parent"));
        String memberType = op.get("member_type").getAsString();
        IModelElement child = newElement(state.factory, memberType);
        String name = op.get("name").getAsString().trim();
        child.setName(name);
        setMemberType(child, op.get("type"), id);
        attachMember(parent.model, child, memberType, id);
        compensations.add(new Compensation(id, () -> detachMember(parent.model, child, memberType)));
        VpLog.info("APPLY add_member id=" + id + " child=" + child.getId());
        return appliedEntry(id, "member", child.getId(), name);
    }

    /**
     * Property mutation with an old-value snapshot: name, documentation and
     * stereotypes are restorable, unlike deletions. Stereotypes replace all:
     * current ones not kept are removed, new ones added. VP's silent name
     * vetoes (ADR-0015) surface as a name_warning on the applied entry.
     */
    private static JsonObject updateElement(State state, JsonObject op, String id,
            List<Compensation> compensations) {
        IModelElement model = requireModel(state, op.get("model"));
        String oldName = model.getName();
        String oldDocumentation = model.getDocumentation();
        List<String> oldStereotypes = stereotypeNames(model);
        compensations.add(new Compensation(id, () -> restoreSnapshot(
                model, oldName, oldDocumentation, oldStereotypes)));

        JsonObject entry = appliedEntry(id, "model", model.getId(), model.getName());
        JsonElement name = op.get("name");
        if (name != null && !name.isJsonNull()) {
            String want = name.getAsString().trim();
            model.setName(want);
            if (!want.equals(model.getName())) {
                entry.addProperty("name_warning",
                        "VP kept \"" + model.getName() + "\" instead of \"" + want + "\".");
            }
            entry.addProperty("name", model.getName());
        }
        JsonElement documentation = op.get("documentation");
        if (documentation != null && !documentation.isJsonNull()) {
            model.setDocumentation(documentation.getAsString());
            entry.addProperty("documentation", model.getDocumentation());
        }
        JsonElement stereotypes = op.get("stereotypes");
        if (stereotypes != null && !stereotypes.isJsonNull()) {
            List<String> keep = new ArrayList<>();
            for (JsonElement item : stereotypes.getAsJsonArray()) {
                keep.add(item.getAsString().trim());
            }
            for (String current : stereotypeNames(model)) {
                if (!keep.contains(current)) {
                    model.removeStereotype(current);
                }
            }
            for (String wanted : keep) {
                if (!stereotypeNames(model).contains(wanted)) {
                    model.addStereotype(wanted);
                }
            }
            JsonArray applied = new JsonArray();
            stereotypeNames(model).forEach(applied::add);
            entry.add("stereotypes", applied);
        }
        VpLog.info("APPLY update_element id=" + id + " model=" + model.getId());
        return entry;
    }

    private static List<String> stereotypeNames(IModelElement model) {
        List<String> names = new ArrayList<>();
        com.vp.plugin.model.IStereotype[] applied = model.toStereotypeModelArray();
        if (applied != null) {
            for (com.vp.plugin.model.IStereotype stereotype : applied) {
                if (stereotype != null) {
                    names.add(stereotype.getName());
                }
            }
        }
        return names;
    }

    private static void restoreSnapshot(IModelElement model, String name, String documentation,
            List<String> stereotypes) {
        model.setName(name);
        model.setDocumentation(documentation);
        for (String current : stereotypeNames(model)) {
            if (!stereotypes.contains(current)) {
                model.removeStereotype(current);
            }
        }
        for (String wanted : stereotypes) {
            if (!stereotypeNames(model).contains(wanted)) {
                model.addStereotype(wanted);
            }
        }
    }

    private static void setMemberType(IModelElement child, JsonElement type, String id) {
        if (type == null || type.isJsonNull()) {
            return;
        }
        String want = type.getAsString();
        for (String setter : new String[] {"setType", "setTypeName"}) {
            try {
                child.getClass().getMethod(setter, String.class).invoke(child, want);
                return;
            } catch (NoSuchMethodException missing) {
                continue;
            } catch (Exception failure) {
                throw new IllegalStateException("Op \"" + id + "\" could not set type: " + failure);
            }
        }
        throw new IllegalStateException(
                "Op \"" + id + "\" wants a type, but this member kind takes none as a string.");
    }

    private static void attachMember(IModelElement parent, IModelElement child, String memberType,
            String id) {
        for (Class<?> iface : child.getClass().getInterfaces()) {
            String simple = iface.getSimpleName();
            String adder = "add" + (simple.startsWith("I") ? simple.substring(1) : simple);
            try {
                parent.getClass().getMethod(adder, iface).invoke(parent, child);
                return;
            } catch (NoSuchMethodException missing) {
                continue;
            } catch (Exception failure) {
                throw new IllegalStateException("Op \"" + id + "\" failed to attach: " + failure);
            }
        }
        throw new IllegalStateException("Op \"" + id + "\" has no adder for \"" + memberType
                + "\" on " + parent.getClass().getSimpleName() + ".");
    }

    private static void detachMember(IModelElement parent, IModelElement child, String memberType) {
        for (Class<?> iface : child.getClass().getInterfaces()) {
            String simple = iface.getSimpleName();
            String remover = "remove" + (simple.startsWith("I") ? simple.substring(1) : simple);
            try {
                parent.getClass().getMethod(remover, iface).invoke(parent, child);
                return;
            } catch (NoSuchMethodException missing) {
                continue;
            } catch (Exception failure) {
                throw new IllegalStateException("Detach failed: " + failure);
            }
        }
        throw new IllegalStateException(
                "No remover for \"" + memberType + "\"; member survives compensation.");
    }

    /**
     * View-only move. Model untouched, previous bounds restore on
     * compensation. Connectors are refused: they move via waypoints.
     */
    private static JsonObject moveElement(State state, JsonObject op, String id,
            List<Compensation> compensations) {
        Endpoint endpoint = requireEndpoint(state, op.get("element"));
        IDiagramElement view = endpoint.view;
        if (view instanceof IConnectorUIModel) {
            throw new IllegalStateException("Connectors cannot move; delete and reconnect.");
        }
        int ox = view.getX();
        int oy = view.getY();
        int ow = view.getWidth();
        int oh = view.getHeight();
        view.setBounds(number(op, "x", ox), number(op, "y", oy), number(op, "width", ow),
                number(op, "height", oh));
        compensations.add(new Compensation(id, () -> view.setBounds(ox, oy, ow, oh)));
        state.views.put(id, view);
        state.models.put(id, endpoint.model);
        VpLog.info("APPLY move_element id=" + id + " vp_id=" + view.getId());
        return appliedEntry(id, "move", view.getId(), endpoint.model.getName());
    }

    /**
     * Fresh view of an existing model on another diagram. The model is
     * shared; compensation removes the view only.
     */
    private static JsonObject showElement(State state, JsonObject op, String id,
            List<Compensation> compensations) {
        IDiagramUIModel diagram = requireDiagram(state, op.get("diagram"));
        IModelElement model = requireModel(state, op.get("model"));
        IDiagramElement view = state.diagrams.createDiagramElement(diagram, model);
        if (view == null) {
            throw new IllegalStateException("This model cannot be shown on this diagram.");
        }
        if (op.has("x") || op.has("y") || op.has("width") || op.has("height")) {
            view.setBounds(number(op, "x", view.getX()), number(op, "y", view.getY()),
                    number(op, "width", view.getWidth()), number(op, "height", view.getHeight()));
        }
        state.views.put(id, view);
        state.models.put(id, model);
        state.owners.put(view.getId(), diagram);
        compensations.add(new Compensation(id, () -> removeViewOnly(diagram, view)));
        VpLog.info("APPLY show_element id=" + id + " vp_id=" + view.getId());
        return appliedEntry(id, "element", view.getId(), model.getName());
    }

    private static void removeViewOnly(IDiagramUIModel owner, IDiagramElement view) {
        if (owner != null) {
            owner.removeDiagramElement(view);
        }
    }

    /**
     * Model deletion: every view across every diagram goes first, then the
     * model. Final like other deletions; reports how many views went.
     */
    private static JsonObject deleteModel(State state, JsonObject op, String id) {
        IModelElement model = requireModel(state, op.get("model"));
        String modelId = model.getId();
        String name = model.getName();
        int removed = 0;
        IDiagramUIModel[] diagrams = state.project.toDiagramArray();
        if (diagrams != null) {
            for (IDiagramUIModel diagram : diagrams) {
                if (diagram == null || diagram.toDiagramElementArray() == null) {
                    continue;
                }
                for (IDiagramElement view : diagram.toDiagramElementArray()) {
                    if (view != null && view.getModelElement() != null
                            && modelId.equals(view.getModelElement().getId())) {
                        diagram.removeDiagramElement(view);
                        removed++;
                    }
                }
            }
        }
        model.delete();
        VpLog.info("APPLY delete_model id=" + id + " model=" + modelId + " views=" + removed);
        JsonObject entry = appliedEntry(id, "deletion", modelId, name);
        entry.addProperty("views_removed", removed);
        return entry;
    }

    private static IModelElement requireModel(State state, JsonElement reference) {
        if (reference != null) {
            if (reference.isJsonObject()) {
                IModelElement planned = state.models.get(reference.getAsJsonObject().get("ref")
                        .getAsString());
                if (planned != null) {
                    return planned;
                }
            } else if (reference.isJsonPrimitive()) {
                IModelElement found = state.project.getModelElementById(reference.getAsString());
                if (found != null) {
                    return found;
                }
            }
        }
        throw new IllegalStateException("Model reference no longer resolves; re-run preview.");
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

    /** Caption box centered inside a state/decision shape; pure geometry, no VP. */
    public static int[] insideCaptionBounds(int x, int y, int width, int height) {
        return new int[] {x + width / 2 - 40, y + height / 2 - 8, 80, 16};
    }

    /** View-first shapes for types that refuse model-first placement. */
    private static final Map<String, String> VIEW_FIRST_SHAPES = Map.of("LifeLine", "InteractionLifeLine");

    private static IModelElement newElement(IModelElementFactory factory, String modelType) {
        try {
            return (IModelElement) factory.getClass().getMethod("create" + modelType).invoke(factory);
        } catch (Exception failure) {
            throw new IllegalStateException(
                    "Unvalidated model_type \"" + modelType + "\": " + failure);
        }
    }

    private static IModelElement newRelationship(IModelElementFactory factory, String relType) {
        try {
            return (IModelElement) factory.getClass().getMethod("create" + relType).invoke(factory);
        } catch (Exception failure) {
            throw new IllegalStateException("Unvalidated rel_type \"" + relType + "\": " + failure);
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
