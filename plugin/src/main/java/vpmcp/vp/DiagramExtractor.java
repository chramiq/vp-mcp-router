package vpmcp.vp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IProject;
import com.vp.plugin.model.IRelationship;
import java.awt.Point;
import java.util.List;

/**
 * Walks a diagram and produces a graph of nodes and edges carrying both the logical model
 * and the formatting, so a reader can tell not only what the diagram says but how it looks.
 */
public final class DiagramExtractor {

    private final VisualPropertiesReader visualReader = new VisualPropertiesReader();
    private final ModelPropertiesReader modelReader = new ModelPropertiesReader();

    private final boolean fullDetail;
    private final List<String> warnings;

    /**
     * @param fullDetail when true, every property Visual Paradigm exposes is dumped alongside
     *                   the curated fields. That is exhaustive but verbose, so it is opt-in.
     */
    public DiagramExtractor(boolean fullDetail, List<String> warnings) {
        this.fullDetail = fullDetail;
        this.warnings = warnings;
    }

    public JsonObject extract(IDiagramUIModel diagram, IProject project) {
        JsonArray nodes = new JsonArray();
        JsonArray edges = new JsonArray();

        IDiagramElement[] elements = diagram.toDiagramElementArray();
        if (elements != null) {
            for (IDiagramElement element : elements) {
                if (element == null) {
                    continue;
                }
                try {
                    if (element instanceof IConnectorUIModel) {
                        edges.add(describeConnector((IConnectorUIModel) element));
                    } else if (element instanceof IShapeUIModel) {
                        nodes.add(describeShape((IShapeUIModel) element));
                    } else {
                        nodes.add(describeCommon(element));
                    }
                } catch (RuntimeException failure) {
                    warnings.add("Skipped diagram element " + safeId(element) + ": "
                            + failure.getClass().getSimpleName() + ": " + failure.getMessage());
                }
            }
        }

        JsonArray reportedWarnings = new JsonArray();
        for (String warning : warnings) {
            reportedWarnings.add(warning);
        }

        JsonObject graph = new JsonObject();
        graph.add("diagram", describeDiagram(diagram, project));
        graph.add("nodes", nodes);
        graph.add("edges", edges);
        graph.add("warnings", reportedWarnings);
        return graph;
    }

    private JsonObject describeDiagram(IDiagramUIModel diagram, IProject project) {
        JsonObject json = new JsonObject();
        json.addProperty("id", diagram.getId());
        json.addProperty("name", diagram.getName());
        json.addProperty("type", diagram.getType());
        json.addProperty("documentation", diagram.getDocumentation());
        json.addProperty("user_id", diagram.getUserID());
        json.addProperty("background_color", ColorFormat.toHex(diagram.getDiagramBackground()));
        json.addProperty("project_name", project.getName());
        json.addProperty("element_count", diagram.diagramElementCount());
        return json;
    }

    private JsonObject describeShape(IShapeUIModel shape) {
        JsonObject json = describeCommon(shape);

        IDiagramElement parent = shape.getParent();
        json.addProperty("parent_id", parent == null ? null : parent.getId());

        JsonArray childIds = new JsonArray();
        IShapeUIModel[] children = shape.toChildArray();
        if (children != null) {
            for (IShapeUIModel child : children) {
                if (child != null) {
                    childIds.add(child.getId());
                }
            }
        }
        json.add("child_ids", childIds);

        json.addProperty("custom_text", shape.getCustomText());
        json.addProperty("display_image_path", shape.getDisplayImagePath());
        json.add("visual_properties", visualReader.readShape(shape));
        return json;
    }

    private JsonObject describeConnector(IConnectorUIModel connector) {
        JsonObject json = describeCommon(connector);

        json.addProperty("from", endpointId(connector.getFromShape(), connector.getFromConnector(), connector, "source"));
        json.addProperty("to", endpointId(connector.getToShape(), connector.getToConnector(), connector, "target"));
        json.addProperty("from_member_id", connector.getFromMemberId());
        json.addProperty("to_member_id", connector.getToMemberId());

        IModelElement model = connector.getModelElement();
        if (model instanceof IRelationship) {
            IRelationship relationship = (IRelationship) model;
            json.add("from_model", modelReader.reference(relationship.getFrom()));
            json.add("to_model", modelReader.reference(relationship.getTo()));
        }

        JsonArray waypoints = new JsonArray();
        Point[] points = connector.getPoints();
        if (points != null) {
            for (Point point : points) {
                if (point == null) {
                    continue;
                }
                JsonObject waypoint = new JsonObject();
                waypoint.addProperty("x", point.x);
                waypoint.addProperty("y", point.y);
                waypoints.add(waypoint);
            }
        }
        json.add("waypoints", waypoints);
        json.add("visual_properties", visualReader.readConnector(connector));
        return json;
    }

    /** The fields every diagram element has, whether it is a shape or a connector. */
    private JsonObject describeCommon(IDiagramElement element) {
        IModelElement model = element.getModelElement();

        JsonObject json = new JsonObject();
        json.addProperty("id", element.getId());
        json.addProperty("model_id", model == null ? null : model.getId());
        json.addProperty("name", model == null ? null : model.getName());
        json.addProperty("shape_type", element.getShapeType());
        json.addProperty("model_type", model == null ? null : model.getModelType());
        json.addProperty("documentation", model == null ? null : model.getDocumentation());
        json.addProperty("description", model == null ? null : model.getDescription());
        json.add("stereotypes", modelReader.readStereotypes(model));
        json.add("tagged_values", modelReader.readTaggedValues(model));
        json.add("members", modelReader.readMembers(model));
        json.add("sub_diagrams", modelReader.readSubDiagrams(model));

        JsonObject bounds = new JsonObject();
        bounds.addProperty("x", element.getX());
        bounds.addProperty("y", element.getY());
        bounds.addProperty("width", element.getWidth());
        bounds.addProperty("height", element.getHeight());
        json.add("bounds", bounds);
        json.addProperty("z_order", element.getZOrder());

        if (fullDetail) {
            json.add("raw_model_properties", modelReader.readRawModelProperties(model));
            json.add("raw_view_properties", modelReader.readRawViewProperties(element));
        }
        return json;
    }

    /**
     * A connector normally ends on a shape, but Visual Paradigm also lets it end on another
     * connector. Both are reported by view id; the rarer case is called out in the warnings.
     */
    private String endpointId(IShapeUIModel shape, IConnectorUIModel connector, IConnectorUIModel owner, String end) {
        if (shape != null) {
            return shape.getId();
        }
        if (connector != null) {
            warnings.add("Connector " + safeId(owner) + " has its " + end
                    + " end attached to connector " + connector.getId() + ", not to a shape.");
            return connector.getId();
        }
        warnings.add("Connector " + safeId(owner) + " has no " + end + " end.");
        return null;
    }

    private String safeId(IDiagramElement element) {
        try {
            return element.getId();
        } catch (RuntimeException unreadable) {
            return "<unknown>";
        }
    }
}
