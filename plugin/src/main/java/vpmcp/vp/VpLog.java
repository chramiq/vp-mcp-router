package vpmcp.vp;

import com.vp.plugin.ApplicationManager;
import com.vp.plugin.ViewManager;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Writes to a log file inside the plug-in directory, and mirrors everything to Visual
 * Paradigm's message pane and the console.
 *
 * <p>The file is the channel that matters for diagnosis: the message pane opens a separate
 * tab that is easy to miss, and the console goes wherever Visual Paradigm sends it. If
 * {@code vpmcp.log} does not exist at all, Visual Paradigm never loaded the plug-in.
 */
public final class VpLog {

    private static final String CHANNEL = "MCP Server";
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static volatile File logFile;

    private VpLog() {
    }

    /** Points the log at {@code vpmcp.log} in the plug-in directory. Call this first. */
    public static void useLogFile(File pluginDir) {
        logFile = pluginDir == null ? null : new File(pluginDir, "vpmcp.log");
    }

    public static File getLogFile() {
        return logFile;
    }

    public static void info(String message) {
        write("INFO ", message, null);
        show(message);
    }

    public static void error(String message, Throwable failure) {
        write("ERROR", message, failure);
        show(failure == null ? message : message + " (" + failure + ")");
    }

    private static void write(String level, String message, Throwable failure) {
        String line = TIMESTAMP.format(LocalDateTime.now()) + "  " + level + "  " + message;
        System.out.println("[vpmcp] " + line);
        if (failure != null) {
            failure.printStackTrace();
        }

        File target = logFile;
        if (target == null) {
            return;
        }
        try (PrintWriter writer = new PrintWriter(new FileWriter(target, true))) {
            writer.println(line);
            if (failure != null) {
                failure.printStackTrace(writer);
            }
        } catch (IOException unwritable) {
            System.err.println("[vpmcp] Could not write " + target + ": " + unwritable);
        }
    }

    private static void show(String message) {
        try {
            ApplicationManager application = ApplicationManager.instance();
            if (application == null) {
                return;
            }
            ViewManager viewManager = application.getViewManager();
            if (viewManager != null) {
                viewManager.showMessage(message, CHANNEL);
            }
        } catch (RuntimeException | LinkageError unavailable) {
            // Running outside Visual Paradigm; the file and console copies are the whole log.
        }
    }
}
