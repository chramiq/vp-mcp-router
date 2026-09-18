package vpmcp.vp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.property.IDiagramElementProperty;
import com.vp.plugin.model.IAttribute;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IOperation;
import com.vp.plugin.model.IParameter;
import com.vp.plugin.model.IStereotype;
import com.vp.plugin.model.ITaggedValue;
import com.vp.plugin.model.ITaggedValueContainer;
import com.vp.plugin.model.property.IModelProperty;

/** Reads the logical side of an element: identity, stereotypes, members and raw properties. */
public final class ModelPropertiesReader {

    /** A compact {id, name, model_type} pointer used wherever one element refers to another. */
    public JsonElement reference(IModelElement model) {
        if (model == null) {
            return JsonNull.INSTANCE;
        }
        JsonObject json = new JsonObject();
        json.addProperty("id", model.getId());
        json.addProperty("name", model.getName());
        json.addProperty("model_type", model.getModelType());
        return json;
    }

    public JsonArray readStereotypes(IModelElement model) {
        JsonArray stereotypes = new JsonArray();
        if (model == null) {
            return stereotypes;
        }
        IStereotype[] applied = model.toStereotypeModelArray();
        if (applied != null) {
            for (IStereotype stereotype : applied) {
                if (stereotype != null) {
                    stereotypes.add(stereotype.getName());
                }
            }
        }
        return stereotypes;
    }

    public JsonObject readTaggedValues(IModelElement model) {
        JsonObject values = new JsonObject();
        if (model == null) {
            return values;
        }
        ITaggedValueContainer container = model.getTaggedValues();
        if (container == null) {
            return values;
        }
        ITaggedValue[] tagged = container.toTaggedValueArray();
        if (tagged != null) {
            for (ITaggedValue value : tagged) {
                if (value != null) {
                    values.addProperty(value.getName(), value.getValueAsString());
                }
            }
        }
        return values;
    }

    /** Attributes, operations and any other child elements owned by the model element. */
    public JsonArray readMembers(IModelElement model) {
        JsonArray members = new JsonArray();
        if (model == null) {
            return members;
        }
        IModelElement[] children = model.toChildArray();
        if (children == null) {
            return members;
        }
        for (IModelElement child : children) {
            if (child == null) {
                continue;
            }
            JsonObject member = reference(child).getAsJsonObject();
            member.addProperty("documentation", child.getDocumentation());
            if (child instanceof IAttribute) {
                describeAttribute((IAttribute) child, member);
            } else if (child instanceof IOperation) {
                describeOperation((IOperation) child, member);
            } else if (child instanceof com.vp.plugin.model.IDBColumn) {
                describeColumn((com.vp.plugin.model.IDBColumn) child, member);
            }
            members.add(member);
        }
        return members;
    }

    private void describeAttribute(IAttribute attribute, JsonObject member) {
        member.addProperty("type", attribute.getTypeAsString());
        member.addProperty("visibility", attribute.getVisibility());
        member.addProperty("multiplicity", attribute.getMultiplicity());
        member.addProperty("initial_value", attribute.getInitialValueAsString());
    }

    private void describeColumn(com.vp.plugin.model.IDBColumn column, JsonObject member) {
        member.addProperty("type_name", column.getTypeName());
        member.addProperty("length", column.getLength());
        member.addProperty("nullable", column.isNullable());
    }

    private void describeOperation(IOperation operation, JsonObject member) {
        member.addProperty("return_type", operation.getReturnTypeAsString());
        member.addProperty("visibility", operation.getVisibility());

        JsonArray parameters = new JsonArray();
        IParameter[] declared = operation.toParameterArray();
        if (declared != null) {
            for (IParameter parameter : declared) {
                if (parameter == null) {
                    continue;
                }
                JsonObject json = new JsonObject();
                json.addProperty("name", parameter.getName());
                json.addProperty("type", parameter.getTypeAsString());
                json.addProperty("direction", parameter.getDirection());
                parameters.add(json);
            }
        }
        member.add("parameters", parameters);
    }

    public JsonArray readSubDiagrams(IModelElement model) {
        JsonArray diagrams = new JsonArray();
        if (model == null) {
            return diagrams;
        }
        IDiagramUIModel[] subDiagrams = model.toSubDiagramArray();
        if (subDiagrams == null) {
            return diagrams;
        }
        for (IDiagramUIModel diagram : subDiagrams) {
            if (diagram == null) {
                continue;
            }
            JsonObject json = new JsonObject();
            json.addProperty("id", diagram.getId());
            json.addProperty("name", diagram.getName());
            json.addProperty("type", diagram.getType());
            diagrams.add(json);
        }
        return diagrams;
    }

    /**
     * Every property Visual Paradigm exposes on the model element. This is what keeps the
     * extraction lossless for element kinds this code knows nothing about, such as the guard
     * condition of a sequence flow, which has no getter shared across relationship types.
     */
    public JsonObject readRawModelProperties(IModelElement model) {
        JsonObject json = new JsonObject();
        if (model == null) {
            return json;
        }
        IModelProperty[] properties = model.toModelPropertyArray();
        if (properties == null) {
            return json;
        }
        for (IModelProperty property : properties) {
            if (property == null) {
                continue;
            }
            json.add(property.getName(), modelPropertyValue(property));
        }
        return json;
    }

    private JsonElement modelPropertyValue(IModelProperty property) {
        char type = property.getType();
        try {
            if (type == IModelProperty.TYPE_BOOLEAN) {
                return primitive(property.getValueAsBoolean());
            }
            if (type == IModelProperty.TYPE_INT) {
                return primitive(Integer.valueOf(property.getValueAsInt()));
            }
            if (type == IModelProperty.TYPE_STRING_ARRAY) {
                return stringArray(property.getValueAsStringArray());
            }
            if (type == IModelProperty.TYPE_MODEL || type == IModelProperty.TYPE_COMPOSITE_MODEL || type == IModelProperty.TYPE_PARENT_MODEL) {
                return reference(property.getValueAsModel());
            }
            if (type == IModelProperty.TYPE_MODEL_COLLECTION || type == IModelProperty.TYPE_COMPOSITE_MODEL_COLLECTION) {
                return referenceArray(property.getValueAsModelCollection());
            }
            return primitive(property.getValueAsString());
        } catch (RuntimeException unreadable) {
            return primitive("<unreadable: " + unreadable.getClass().getSimpleName() + ">");
        }
    }

    /** The formatting properties Visual Paradigm keeps on the view rather than on the model. */
    public JsonObject readRawViewProperties(IDiagramElement element) {
        JsonObject json = new JsonObject();
        IDiagramElementProperty[] properties = element.toDiagramElementPropertyArray();
        if (properties == null) {
            return json;
        }
        for (IDiagramElementProperty property : properties) {
            if (property == null) {
                continue;
            }
            json.add(property.getName(), viewPropertyValue(property));
        }
        return json;
    }

    private JsonElement viewPropertyValue(IDiagramElementProperty property) {
        char type = property.getType();
        try {
            if (type == IDiagramElementProperty.TYPE_BOOLEAN) {
                return primitive(property.getValueAsBoolean());
            }
            if (type == IDiagramElementProperty.TYPE_INT) {
                return primitive(Integer.valueOf(property.getValueAsInt()));
            }
            if (type == IDiagramElementProperty.TYPE_LONG) {
                return primitive(Long.valueOf(property.getValueAsLong()));
            }
            if (type == IDiagramElementProperty.TYPE_DOUBLE) {
                return primitive(Double.valueOf(property.getValueAsDouble()));
            }
            if (type == IDiagramElementProperty.TYPE_COLOR) {
                return primitive(ColorFormat.toHex(property.getValueAsColor()));
            }
            if (type == IDiagramElementProperty.TYPE_STRING_ARRAY) {
                return stringArray(property.getValueAsStringArray());
            }
            if (type == IDiagramElementProperty.TYPE_MODEL) {
                return reference(property.getValueAsModel());
            }
            return primitive(property.getValueAsString());
        } catch (RuntimeException unreadable) {
            return primitive("<unreadable: " + unreadable.getClass().getSimpleName() + ">");
        }
    }

    private JsonElement referenceArray(IModelElement[] models) {
        JsonArray array = new JsonArray();
        if (models != null) {
            for (IModelElement model : models) {
                array.add(reference(model));
            }
        }
        return array;
    }

    private JsonElement stringArray(String[] values) {
        JsonArray array = new JsonArray();
        if (values != null) {
            for (String value : values) {
                array.add(value);
            }
        }
        return array;
    }

    private JsonElement primitive(String value) {
        return value == null ? JsonNull.INSTANCE : new JsonPrimitive(value);
    }

    private JsonElement primitive(boolean value) {
        return new JsonPrimitive(Boolean.valueOf(value));
    }

    private JsonElement primitive(Number value) {
        return value == null ? JsonNull.INSTANCE : new JsonPrimitive(value);
    }
}
