package vpmcp;

import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IProject;
import com.vp.plugin.model.IRelationship;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Proxy fakes for VP interfaces. Canned values by method name; everything
 * else defaults by return type (false, 0, empty arrays). A fake models an
 * arbitrary diagram state, never VP internals, so tests assert extractor
 * contracts without a running VP.
 */
public final class VpFakes {

    private VpFakes() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T of(Class<T> iface, Map<String, Object> values) {
        Map<String, Object> canned = new HashMap<>(values);
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                switch (method.getName()) {
                    case "toString":
                        return "Fake(" + iface.getSimpleName() + ")";
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "equals":
                        return proxy == args[0];
                    default:
                        throw new UnsupportedOperationException(method.getName());
                }
            }
            if (canned.containsKey(method.getName())) {
                return canned.get(method.getName());
            }
            return defaultFor(method.getReturnType());
        };
        return (T) Proxy.newProxyInstance(VpFakes.class.getClassLoader(), new Class<?>[] {iface}, handler);
    }

    private static Object defaultFor(Class<?> returns) {
        if (returns == boolean.class) {
            return false;
        }
        if (returns == int.class) {
            return 0;
        }
        if (returns == long.class) {
            return 0L;
        }
        if (returns == double.class) {
            return 0.0;
        }
        if (returns == float.class) {
            return 0.0f;
        }
        if (returns.isArray()) {
            return Array.newInstance(returns.getComponentType(), 0);
        }
        if (returns == java.util.List.class || returns == java.util.Collection.class
                || returns == java.util.Iterator.class) {
            return Collections.emptyList();
        }
        return null;
    }

    public static IModelElement model(String id, String name, String type) {
        Map<String, Object> values = new HashMap<>();
        values.put("getId", id);
        values.put("getName", name);
        values.put("getModelType", type);
        return of(IModelElement.class, values);
    }

    public static IModelElement relationship(String id, String name, String type, IModelElement from,
            IModelElement to) {
        Map<String, Object> values = new HashMap<>();
        values.put("getId", id);
        values.put("getName", name);
        values.put("getModelType", type);
        values.put("getFrom", from);
        values.put("getTo", to);
        return of(IRelationship.class, values);
    }

    public static IShapeUIModel shape(String id, IModelElement model, String shapeType, int x, int y, int width,
            int height) {
        return shape(id, model, shapeType, x, y, width, height, null, null);
    }

    public static IShapeUIModel shape(String id, IModelElement model, String shapeType, int x, int y, int width,
            int height, IDiagramElement parent, IShapeUIModel[] children) {
        Map<String, Object> values = new HashMap<>();
        values.put("getId", id);
        values.put("getModelElement", model);
        values.put("getShapeType", shapeType);
        values.put("getX", x);
        values.put("getY", y);
        values.put("getWidth", width);
        values.put("getHeight", height);
        values.put("getParent", parent);
        values.put("toChildArray", children);
        return of(IShapeUIModel.class, values);
    }

    public static IConnectorUIModel connector(String id, IModelElement model, IShapeUIModel from,
            IShapeUIModel to, java.awt.Point[] points) {
        Map<String, Object> values = new HashMap<>();
        values.put("getId", id);
        values.put("getModelElement", model);
        values.put("getShapeType", model == null ? "Association" : model.getModelType());
        values.put("getFromShape", from);
        values.put("getToShape", to);
        values.put("getPoints", points);
        return of(IConnectorUIModel.class, values);
    }

    public static IDiagramUIModel diagram(String id, String name, String type, IDiagramElement... elements) {
        Map<String, Object> values = new HashMap<>();
        values.put("getId", id);
        values.put("getName", name);
        values.put("getType", type);
        values.put("toDiagramElementArray", elements);
        return of(IDiagramUIModel.class, values);
    }

    public static IProject project(String name) {
        Map<String, Object> values = new HashMap<>();
        values.put("getName", name);
        return of(IProject.class, values);
    }
}
