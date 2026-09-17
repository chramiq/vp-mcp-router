package vpmcp.vp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import vpmcp.core.McpToolException;
import vpmcp.core.ResourceProvider;

/**
 * Serves the versioned schema pack from {@code <pluginDir>/schemas/<version>/}
 * plus a baked capabilities document. Only {@code .json} files inside the
 * pack directory are reachable; everything else is rejected.
 */
public final class FileResourceProvider implements ResourceProvider {

    private static final String MIME_JSON = "application/json";

    private final File packDir;
    private final String version;
    private final String capabilitiesText;

    public FileResourceProvider(File pluginDir, String version, JsonObject capabilities) {
        this.packDir = new File(pluginDir, "schemas" + File.separator + version);
        this.version = version;
        this.capabilitiesText = capabilities.toString();
    }

    @Override
    public JsonArray list() {
        JsonArray resources = new JsonArray();
        resources.add(descriptor("vp://capabilities", "capabilities"));
        resources.add(descriptor("vp://diagram-types", "diagram-types"));
        for (String file : packFiles()) {
            resources.add(descriptor("vp://schemas/" + version + "/" + file, file));
        }
        return resources;
    }

    @Override
    public JsonObject read(String uri) throws McpToolException {
        if ("vp://capabilities".equals(uri)) {
            return content(uri, capabilitiesText);
        }
        String file;
        if ("vp://diagram-types".equals(uri)) {
            file = "diagram-types.json";
        } else if (uri != null && uri.startsWith("vp://schemas/" + version + "/")) {
            file = uri.substring(("vp://schemas/" + version + "/").length());
        } else {
            throw new McpToolException("Unknown resource: " + uri);
        }
        if (!packFiles().contains(file)) {
            throw new McpToolException("Unknown resource: " + uri);
        }
        try {
            File target = new File(packDir, file).getCanonicalFile();
            if (!target.getPath().startsWith(packDir.getCanonicalPath() + File.separator)) {
                throw new McpToolException("Unknown resource: " + uri);
            }
            String text = new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8);
            JsonParser.parseString(text);
            return content(uri, text);
        } catch (IOException unreadable) {
            throw new McpToolException("Cannot read resource " + uri + ": " + unreadable.getMessage());
        }
    }

    private JsonObject descriptor(String uri, String name) {
        JsonObject descriptor = new JsonObject();
        descriptor.addProperty("uri", uri);
        descriptor.addProperty("name", name);
        descriptor.addProperty("mimeType", MIME_JSON);
        return descriptor;
    }

    private JsonObject content(String uri, String text) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("uri", uri);
        envelope.addProperty("mimeType", MIME_JSON);
        envelope.addProperty("text", text);
        return envelope;
    }

    private Set<String> packFiles() {
        String[] names = packDir.isDirectory() ? packDir.list((dir, name) -> name.endsWith(".json")) : null;
        if (names == null) {
            return new HashSet<>();
        }
        Arrays.sort(names);
        return new HashSet<>(Arrays.asList(names));
    }
}
