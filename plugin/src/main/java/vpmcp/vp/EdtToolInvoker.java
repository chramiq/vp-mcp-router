package vpmcp.vp;

import com.google.gson.JsonObject;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import vpmcp.core.McpTool;
import vpmcp.core.ToolInvoker;

/**
 * Runs tools on the Swing event dispatch thread. HTTP requests arrive on pool threads, but
 * the Visual Paradigm Open API touches the same model the UI is editing, so every call has
 * to be marshalled onto the thread Visual Paradigm itself uses.
 */
public final class EdtToolInvoker implements ToolInvoker {

    @Override
    public JsonObject invoke(McpTool tool, JsonObject arguments) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            return tool.execute(arguments);
        }

        AtomicReference<JsonObject> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    result.set(tool.execute(arguments));
                } catch (Throwable thrown) {
                    failure.set(thrown);
                }
            });
        } catch (InvocationTargetException | InterruptedException dispatchFailure) {
            if (dispatchFailure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw dispatchFailure;
        }

        Throwable thrown = failure.get();
        if (thrown instanceof Exception) {
            throw (Exception) thrown;
        }
        if (thrown instanceof Error) {
            throw (Error) thrown;
        }
        return result.get();
    }
}
