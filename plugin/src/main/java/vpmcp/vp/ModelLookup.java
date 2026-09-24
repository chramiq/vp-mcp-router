package vpmcp.vp;

import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IProject;

/**
 * Id lookup that also covers members. VP's project id index and
 * all-level array skip child models (attributes, operations, columns);
 * they are only reachable through their parent's {@code getChildById}.
 */
public final class ModelLookup {

    private ModelLookup() {
    }

    /** A top-level model by id, or a member by id through its parent. Null when absent. */
    public static IModelElement byId(IProject project, String id) {
        if (project == null || id == null) {
            return null;
        }
        try {
            IModelElement model = project.getModelElementById(id);
            if (model != null) {
                return model;
            }
        } catch (RuntimeException unreadable) {
            // fall through to the parent scan
        }
        try {
            IModelElement[] parents = project.toAllLevelModelElementArray();
            if (parents != null) {
                for (IModelElement parent : parents) {
                    if (parent == null) {
                        continue;
                    }
                    IModelElement child = parent.getChildById(id);
                    if (child != null) {
                        return child;
                    }
                }
            }
        } catch (RuntimeException unreadable) {
            return null;
        }
        return null;
    }
}
