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
            case "update_member":
                return updateMember(state, op, id, compensations);
            case "style_element":
                return styleElement(state, op, id, compensations);
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

    /**
     * Member mutation with an old-value snapshot: fields apply by member kind
     * (Attribute: type/visibility/multiplicity/initial_value; Operation:
     * return_type/visibility/parameters-as-full-replacement; DBColumn:
     * type/length/nullable). Kind-inapplicable fields are ignored with a
     * skipped_fields note rather than failing the batch.
     */
    private static JsonObject updateMember(State state, JsonObject op, String id,
            List<Compensation> compensations) {
        IModelElement member = requireModel(state, op.get("member"));
        List<String> skipped = new ArrayList<>();

        JsonObject entry = appliedEntry(id, "member", member.getId(), member.getName());
        JsonObject snapshot = new JsonObject();
        compensations.add(new Compensation(id, () -> restoreMember(member, snapshot)));

        applyCommonFields(member, op, entry, snapshot);
        if (member instanceof com.vp.plugin.model.IAttribute) {
            applyAttribute((com.vp.plugin.model.IAttribute) member, op, entry, snapshot);
        } else if (member instanceof com.vp.plugin.model.IOperation) {
            applyOperation((com.vp.plugin.model.IOperation) member, op, entry, snapshot);
        } else if (member instanceof com.vp.plugin.model.IDBColumn) {
            applyColumn((com.vp.plugin.model.IDBColumn) member, op, entry, snapshot);
        }
        for (String field : new String[] {"type", "visibility", "multiplicity",
                "initial_value", "return_type", "parameters", "length", "nullable"}) {
            if (op.get(field) != null && !op.get(field).isJsonNull()
                    && !entry.has(field)) {
                skipped.add(field);
            }
        }
        if (!skipped.isEmpty()) {
            JsonArray skippedFields = new JsonArray();
            skipped.forEach(skippedFields::add);
            entry.add("skipped_fields", skippedFields);
        }
        VpLog.info("APPLY update_member id=" + id + " member=" + member.getId());
        return entry;
    }

    private static void applyCommonFields(IModelElement member, JsonObject op, JsonObject entry,
            JsonObject snapshot) {
        JsonElement name = op.get("name");
        if (name != null && !name.isJsonNull()) {
            snapshot.addProperty("name", member.getName());
            String want = name.getAsString().trim();
            member.setName(want);
            entry.addProperty("name", member.getName());
            if (!want.equals(member.getName())) {
                entry.addProperty("name_warning",
                        "VP kept \"" + member.getName() + "\" instead of \"" + want + "\".");
            }
        }
    }

    private static void applyAttribute(com.vp.plugin.model.IAttribute attribute, JsonObject op,
            JsonObject entry, JsonObject snapshot) {
        JsonElement type = op.get("type");
        if (type != null && !type.isJsonNull()) {
            snapshot.addProperty("type", attribute.getTypeAsString());
            attribute.setType(type.getAsString());
            entry.addProperty("type", attribute.getTypeAsString());
        }
        JsonElement visibility = op.get("visibility");
        if (visibility != null && !visibility.isJsonNull()) {
            snapshot.addProperty("visibility", attribute.getVisibility());
            attribute.setVisibility(visibility.getAsString());
            entry.addProperty("visibility", attribute.getVisibility());
        }
        JsonElement multiplicity = op.get("multiplicity");
        if (multiplicity != null && !multiplicity.isJsonNull()) {
            snapshot.addProperty("multiplicity", attribute.getMultiplicity());
            attribute.setMultiplicity(multiplicity.getAsString());
            entry.addProperty("multiplicity", attribute.getMultiplicity());
        }
        JsonElement initialValue = op.get("initial_value");
        if (initialValue != null && !initialValue.isJsonNull()) {
            snapshot.addProperty("initial_value", attribute.getInitialValueAsString());
            attribute.setInitialValue(initialValue.getAsString());
            entry.addProperty("initial_value", attribute.getInitialValueAsString());
        }
    }

    private static void applyOperation(com.vp.plugin.model.IOperation operation, JsonObject op,
            JsonObject entry, JsonObject snapshot) {
        JsonElement returnType = op.get("return_type");
        if (returnType != null && !returnType.isJsonNull()) {
            snapshot.addProperty("return_type", operation.getReturnTypeAsString());
            operation.setReturnType(returnType.getAsString());
            entry.addProperty("return_type", operation.getReturnTypeAsString());
        }
        JsonElement visibility = op.get("visibility");
        if (visibility != null && !visibility.isJsonNull()) {
            snapshot.addProperty("visibility", operation.getVisibility());
            operation.setVisibility(visibility.getAsString());
            entry.addProperty("visibility", operation.getVisibility());
        }
        JsonElement parameters = op.get("parameters");
        if (parameters != null && !parameters.isJsonNull()) {
            JsonArray before = new JsonArray();
            com.vp.plugin.model.IParameter[] existing = operation.toParameterArray();
            if (existing != null) {
                for (com.vp.plugin.model.IParameter parameter : existing) {
                    before.add(parameterSnapshot(parameter));
                }
            }
            snapshot.add("parameters", before);

            JsonArray wanted = parameters.getAsJsonArray();
            com.vp.plugin.model.IParameter[] slots =
                    existing == null ? new com.vp.plugin.model.IParameter[0] : existing;
            for (int index = 0; index < wanted.size(); index++) {
                com.vp.plugin.model.IParameter parameter;
                if (index < slots.length) {
                    parameter = slots[index];
                } else {
                    parameter = operation.createParameter();
                }
                applyParameterFields(parameter, wanted.get(index).getAsJsonObject());
            }
            for (int index = wanted.size(); index < slots.length; index++) {
                operation.removeParameter(slots[index]);
            }
            JsonArray after = new JsonArray();
            com.vp.plugin.model.IParameter[] applied = operation.toParameterArray();
            if (applied != null) {
                for (com.vp.plugin.model.IParameter parameter : applied) {
                    after.add(parameterSnapshot(parameter));
                }
            }
            entry.add("parameters", after);
        }
    }

    private static JsonObject parameterSnapshot(com.vp.plugin.model.IParameter parameter) {
        JsonObject json = new JsonObject();
        json.addProperty("name", parameter.getName());
        json.addProperty("type", parameter.getTypeAsString());
        json.addProperty("direction", parameter.getDirection());
        return json;
    }

    private static void applyParameterFields(com.vp.plugin.model.IParameter parameter,
            JsonObject want) {
        JsonElement name = want.get("name");
        if (name != null && !name.isJsonNull()) {
            parameter.setName(name.getAsString().trim());
        }
        JsonElement type = want.get("type");
        if (type != null && !type.isJsonNull()) {
            parameter.setType(type.getAsString());
        }
        JsonElement direction = want.get("direction");
        if (direction != null && !direction.isJsonNull()) {
            parameter.setDirection(direction.getAsString());
        }
    }

    private static void applyColumn(com.vp.plugin.model.IDBColumn column, JsonObject op,
            JsonObject entry, JsonObject snapshot) {
        JsonElement type = op.get("type");
        if (type != null && !type.isJsonNull()) {
            snapshot.addProperty("type", column.getTypeName());
            column.setTypeName(type.getAsString());
            entry.addProperty("type", column.getTypeName());
        }
        JsonElement length = op.get("length");
        if (length != null && !length.isJsonNull()) {
            snapshot.addProperty("length", column.getLength());
            column.setLength(length.getAsInt());
            entry.addProperty("length", column.getLength());
        }
        JsonElement nullable = op.get("nullable");
        if (nullable != null && !nullable.isJsonNull()) {
            snapshot.addProperty("nullable", column.isNullable());
            column.setNullable(nullable.getAsBoolean());
            entry.addProperty("nullable", column.isNullable());
        }
    }

    /** Best-effort restore of every snapshotted member field, in reverse. */
    private static void restoreMember(IModelElement member, JsonObject snapshot) {
        if (snapshot.has("name")) {
            member.setName(snapshot.get("name").getAsString());
        }
        if (member instanceof com.vp.plugin.model.IAttribute) {
            com.vp.plugin.model.IAttribute attribute = (com.vp.plugin.model.IAttribute) member;
            if (snapshot.has("type")) {
                attribute.setType(snapshot.get("type").getAsString());
            }
            if (snapshot.has("visibility")) {
                attribute.setVisibility(snapshot.get("visibility").getAsString());
            }
            if (snapshot.has("multiplicity")) {
                attribute.setMultiplicity(snapshot.get("multiplicity").getAsString());
            }
            if (snapshot.has("initial_value")) {
                attribute.setInitialValue(snapshot.get("initial_value").getAsString());
            }
        } else if (member instanceof com.vp.plugin.model.IOperation) {
            com.vp.plugin.model.IOperation operation =
                    (com.vp.plugin.model.IOperation) member;
            if (snapshot.has("return_type")) {
                operation.setReturnType(snapshot.get("return_type").getAsString());
            }
            if (snapshot.has("visibility")) {
                operation.setVisibility(snapshot.get("visibility").getAsString());
            }
            if (snapshot.has("parameters")) {
                restoreParameters(operation, snapshot.getAsJsonArray("parameters"));
            }
        } else if (member instanceof com.vp.plugin.model.IDBColumn) {
            com.vp.plugin.model.IDBColumn column = (com.vp.plugin.model.IDBColumn) member;
            if (snapshot.has("type")) {
                column.setTypeName(snapshot.get("type").getAsString());
            }
            if (snapshot.has("length")) {
                column.setLength(snapshot.get("length").getAsInt());
            }
            if (snapshot.has("nullable")) {
                column.setNullable(snapshot.get("nullable").getAsBoolean());
            }
        }
    }

    private static void restoreParameters(com.vp.plugin.model.IOperation operation,
            JsonArray snapshot) {
        com.vp.plugin.model.IParameter[] current = operation.toParameterArray();
        int currentCount = current == null ? 0 : current.length;
        for (int index = 0; index < snapshot.size(); index++) {
            JsonObject want = snapshot.get(index).getAsJsonObject();
            com.vp.plugin.model.IParameter parameter;
            if (index < currentCount) {
                parameter = current[index];
            } else {
                parameter = operation.createParameter();
            }
            applyParameterFields(parameter, want);
        }
        if (current != null) {
            for (int index = snapshot.size(); index < current.length; index++) {
                operation.removeParameter(current[index]);
            }
        }
    }

    /**
     * Visual styling with an old-value snapshot: view colours, line and font.
     * Colours are hex like the reads report them ("#FF0000"); inherited
     * (null) values restore to null, best-effort. The line model's two-arg
     * setters take an undocumented boolean; false is probe-verified.
     */
    private static JsonObject styleElement(State state, JsonObject op, String id,
            List<Compensation> compensations) {
        IDiagramElement element = requireEndpoint(state, op.get("element")).view;
        JsonObject entry = appliedEntry(id, "element", element.getId(), null);
        JsonObject snapshot = new JsonObject();
        compensations.add(new Compensation(id, () -> restoreStyle(element, snapshot)));

        JsonElement background = op.get("background");
        if (background != null && !background.isJsonNull()) {
            java.awt.Color before = element.getBackground();
            snapshot.add("background", colorToJson(before));
            element.setBackground(color(background.getAsString()));
            entry.addProperty("background", hexOrNull(element.getBackground()));
        }
        JsonElement foreground = op.get("foreground");
        if (foreground != null && !foreground.isJsonNull()) {
            snapshot.add("foreground", colorToJson(element.getForeground()));
            element.setForeground(color(foreground.getAsString()));
            entry.addProperty("foreground", hexOrNull(element.getForeground()));
        }
        JsonElement fillColor = op.get("fill_color");
        if (fillColor != null && !fillColor.isJsonNull()
                && element instanceof IShapeUIModel) {
            com.vp.plugin.diagram.format.IShapeUIModelFillColor fill =
                    ((IShapeUIModel) element).getFillColor();
            if (fill != null) {
                snapshot.add("fill_color", colorToJson(fill.getColor1()));
                fill.setColor1(color(fillColor.getAsString()), false);
                entry.addProperty("fill_color", hexOrNull(fill.getColor1()));
            }
        }
        com.vp.plugin.diagram.format.IDiagramElementLineModel line = element.getLineModel();
        if (line != null) {
            JsonElement lineColor = op.get("line_color");
            if (lineColor != null && !lineColor.isJsonNull()) {
                snapshot.add("line_color", colorToJson(line.getColor()));
                line.setColor(color(lineColor.getAsString()), false);
                entry.addProperty("line_color", hexOrNull(line.getColor()));
            }
            JsonElement lineWeight = op.get("line_weight");
            if (lineWeight != null && !lineWeight.isJsonNull()) {
                snapshot.addProperty("line_weight", line.getWeight());
                line.setWeight(lineWeight.getAsFloat(), false);
                entry.addProperty("line_weight", line.getWeight());
            }
        }
        com.vp.plugin.diagram.format.IElementFont font = element.getElementFont();
        if (font != null) {
            JsonElement fontColor = op.get("font_color");
            if (fontColor != null && !fontColor.isJsonNull()) {
                snapshot.add("font_color", colorToJson(font.getColor()));
                font.setColor(color(fontColor.getAsString()));
                entry.addProperty("font_color", hexOrNull(font.getColor()));
            }
            JsonElement fontSize = op.get("font_size");
            if (fontSize != null && !fontSize.isJsonNull()) {
                snapshot.addProperty("font_size", font.getSize());
                font.setSize(fontSize.getAsInt());
                entry.addProperty("font_size", font.getSize());
            }
            JsonElement fontName = op.get("font_name");
            if (fontName != null && !fontName.isJsonNull()) {
                snapshot.addProperty("font_name", font.getName());
                font.setName(fontName.getAsString().trim());
                entry.addProperty("font_name", font.getName());
            }
            JsonElement fontBold = op.get("font_bold");
            if (fontBold != null && !fontBold.isJsonNull()) {
                snapshot.addProperty("font_bold", font.isBold());
                font.setBold(fontBold.getAsBoolean());
                entry.addProperty("font_bold", font.isBold());
            }
            JsonElement fontItalic = op.get("font_italic");
            if (fontItalic != null && !fontItalic.isJsonNull()) {
                snapshot.addProperty("font_italic", font.isItalic());
                font.setItalic(fontItalic.getAsBoolean());
                entry.addProperty("font_italic", font.isItalic());
            }
        }
        VpLog.info("APPLY style_element id=" + id + " view=" + element.getId());
        return entry;
    }

    private static java.awt.Color color(String hex) {
        String normalized = hex.startsWith("#") ? hex : "#" + hex;
        return java.awt.Color.decode(normalized);
    }

    private static String hexOrNull(java.awt.Color color) {
        return color == null ? null : vpmcp.vp.ColorFormat.toHex(color);
    }

    private static JsonElement colorToJson(java.awt.Color color) {
        if (color == null) {
            return com.google.gson.JsonNull.INSTANCE;
        }
        return new com.google.gson.JsonPrimitive(vpmcp.vp.ColorFormat.toHex(color));
    }

    private static void restoreStyle(IDiagramElement element, JsonObject snapshot) {
        try {
            if (snapshot.has("background")) {
                element.setBackground(restoreColor(snapshot.get("background")));
            }
            if (snapshot.has("foreground")) {
                element.setForeground(restoreColor(snapshot.get("foreground")));
            }
            if (snapshot.has("fill_color") && element instanceof IShapeUIModel) {
                com.vp.plugin.diagram.format.IShapeUIModelFillColor fill =
                        ((IShapeUIModel) element).getFillColor();
                if (fill != null) {
                    fill.setColor1(restoreColor(snapshot.get("fill_color")), false);
                }
            }
            com.vp.plugin.diagram.format.IDiagramElementLineModel line = element.getLineModel();
            if (line != null) {
                if (snapshot.has("line_color")) {
                    line.setColor(restoreColor(snapshot.get("line_color")), false);
                }
                if (snapshot.has("line_weight")) {
                    line.setWeight(snapshot.get("line_weight").getAsFloat(), false);
                }
            }
            com.vp.plugin.diagram.format.IElementFont font = element.getElementFont();
            if (font != null) {
                if (snapshot.has("font_color")) {
                    font.setColor(restoreColor(snapshot.get("font_color")));
                }
                if (snapshot.has("font_size")) {
                    font.setSize(snapshot.get("font_size").getAsInt());
                }
                if (snapshot.has("font_name")) {
                    font.setName(snapshot.get("font_name").getAsString());
                }
                if (snapshot.has("font_bold")) {
                    font.setBold(snapshot.get("font_bold").getAsBoolean());
                }
                if (snapshot.has("font_italic")) {
                    font.setItalic(snapshot.get("font_italic").getAsBoolean());
                }
            }
        } catch (RuntimeException bestEffort) {
            VpLog.info("STYLE restore skipped: " + bestEffort);
        }
    }

    private static java.awt.Color restoreColor(JsonElement value) {
        return value == null || value.isJsonNull() ? null : color(value.getAsString());
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
                IModelElement found = vpmcp.vp.ModelLookup.byId(state.project,
                        reference.getAsString());
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
