package vpmcp.vp;

import com.vp.plugin.ApplicationManager;
import com.vp.plugin.ProjectManager;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IProject;
import java.util.List;
import vpmcp.core.McpToolException;

/** Resolves a {@link VppUrl} against the project currently open in Visual Paradigm. */
public final class DiagramLocator {

    private DiagramLocator() {
    }

    public static IProject requireOpenProject() throws McpToolException {
        ApplicationManager application = ApplicationManager.instance();
        if (application == null) {
            throw new McpToolException("The Visual Paradigm Open API is unavailable; "
                    + "this code is not running inside Visual Paradigm.");
        }
        ProjectManager projectManager = application.getProjectManager();
        if (projectManager == null) {
            throw new McpToolException("Visual Paradigm has no project manager yet.");
        }
        IProject project = projectManager.getProject();
        if (project == null) {
            throw new McpToolException("No project is currently open in Visual Paradigm.");
        }
        return project;
    }

    public static IDiagramUIModel locate(VppUrl url, IProject project, List<String> warnings) throws McpToolException {
        if (url.getProjectName() != null && !url.getProjectName().equalsIgnoreCase(project.getName())) {
            warnings.add("The address refers to project \"" + url.getProjectName()
                    + "\" but the open project is \"" + project.getName()
                    + "\"; the element id was looked up in the open project anyway.");
        }

        IDiagramUIModel diagram = project.getDiagramById(url.getId());
        if (diagram != null) {
            return diagram;
        }

        IModelElement model = project.getModelElementById(url.getId());
        if (model != null) {
            throw new McpToolException("Id \"" + url.getId() + "\" is the model element \""
                    + model.getName() + "\" of type " + model.getModelType()
                    + ", not a diagram. Use the address of a diagram.");
        }
        throw new McpToolException("No diagram with id \"" + url.getId()
                + "\" exists in project \"" + project.getName() + "\".");
    }
}
