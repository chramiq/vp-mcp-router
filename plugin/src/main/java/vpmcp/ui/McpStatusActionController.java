package vpmcp.ui;

import com.vp.plugin.ApplicationManager;
import com.vp.plugin.ViewManager;
import com.vp.plugin.action.VPAction;
import com.vp.plugin.action.VPActionController;
import com.vp.plugin.model.IProject;
import java.awt.Dimension;
import java.awt.Font;
import java.io.File;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import vpmcp.core.McpServer;
import vpmcp.core.McpTool;
import vpmcp.core.McpToolRegistry;
import vpmcp.vp.McpServerHandle;
import vpmcp.vp.VpLog;

/** Backs the Tools menu entry that reports whether the MCP server is up, and at what address. */
public final class McpStatusActionController implements VPActionController {

    @Override
    public void performAction(VPAction action) {
        ViewManager viewManager = ApplicationManager.instance().getViewManager();

        JTextArea report = new JTextArea(buildReport());
        report.setEditable(false);
        // Keep whatever size the look and feel already scaled to; only the family changes.
        report.setFont(new Font(Font.MONOSPACED, Font.PLAIN, report.getFont().getSize()));
        report.setCaretPosition(0);

        JScrollPane scroller = new JScrollPane(report);
        scroller.setPreferredSize(new Dimension(viewManager.getScaledInt(620), viewManager.getScaledInt(320)));

        McpServer server = McpServerHandle.getServer();
        viewManager.showMessageDialog(viewManager.getRootFrame(), scroller, "MCP Server",
                server != null ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
    }

    /** Called before the menu is shown, so the entry itself already carries the status. */
    @Override
    public void update(VPAction action) {
        McpServer server = McpServerHandle.getServer();
        action.setLabel(server != null
                ? "MCP Server: running on port " + server.getPort()
                : "MCP Server: NOT RUNNING");
    }

    private String buildReport() {
        McpServer server = McpServerHandle.getServer();
        File logFile = VpLog.getLogFile();

        StringBuilder report = new StringBuilder();
        if (server != null) {
            report.append("Status      : RUNNING\n");
            report.append("Endpoint    : ").append(server.getEndpointUrl()).append('\n');
            report.append("Tools       : ").append(toolNames()).append('\n');
        } else {
            report.append("Status      : NOT RUNNING\n");
            report.append("Reason      : ").append(McpServerHandle.getFailure() == null
                    ? "the plug-in never finished loading" : McpServerHandle.getFailure()).append('\n');
        }
        report.append("Project     : ").append(openProjectName()).append('\n');
        report.append("Plugin dir  : ").append(McpServerHandle.getPluginDir()).append('\n');
        report.append("Log file    : ").append(logFile == null ? "(none)" : logFile.getAbsolutePath()).append('\n');

        if (server != null) {
            report.append("\nConnect Claude Code with:\n");
            report.append("  claude mcp add --transport http visual-paradigm ").append(server.getEndpointUrl()).append('\n');
            report.append("\nCheck it from a terminal:\n");
            report.append("  curl.exe -s -X POST ").append(server.getEndpointUrl()).append(" ^\n");
            report.append("    -H \"Content-Type: application/json\" ^\n");
            report.append("    -d \"{\\\"jsonrpc\\\":\\\"2.0\\\",\\\"id\\\":1,\\\"method\\\":\\\"tools/list\\\"}\"\n");
        } else {
            report.append('\n').append(adviceFor(McpServerHandle.getFailure()));
            report.append("\nThe log file above records the full stack trace.\n");
        }
        return report.toString();
    }

    /** Guessing one cause for every failure sends people down the wrong path; name the real ones. */
    private String adviceFor(String failure) {
        if (failure == null) {
            return "The plug-in class loaded but never reached the point of starting the server.\n";
        }
        if (failure.contains("BindException") || failure.contains("Address already in use")) {
            return "The port is already taken. Put a free one in mcp.properties next to plugin.xml:\n"
                    + "  port=8910\n"
                    + "then restart Visual Paradigm.\n";
        }
        if (failure.contains("NoClassDefFoundError") || failure.contains("ClassNotFoundException")) {
            return "A class the plug-in needs is missing from the runtime Visual Paradigm uses.\n"
                    + "Report the class name above; the plug-in has to be rebuilt without it.\n";
        }
        return "See the reason above.\n";
    }

    private String toolNames() {
        McpToolRegistry registry = McpServerHandle.getRegistry();
        if (registry == null) {
            return "(none)";
        }
        StringBuilder names = new StringBuilder();
        for (McpTool tool : registry.list()) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(tool.getDefinition().getName());
        }
        return names.length() == 0 ? "(none)" : names.toString();
    }

    private String openProjectName() {
        try {
            IProject project = ApplicationManager.instance().getProjectManager().getProject();
            return project == null ? "(no project open)" : project.getName();
        } catch (RuntimeException unavailable) {
            return "(unknown)";
        }
    }
}
