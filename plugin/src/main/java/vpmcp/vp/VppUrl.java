package vpmcp.vp;

import java.util.ArrayList;
import java.util.List;
import vpmcp.core.McpToolException;

/**
 * A Visual Paradigm resource address such as {@code MOM.vpp://diagram/YRvbHuaFYFAEMEOa}.
 * A bare identifier is accepted too, because that is what most of the API deals in.
 */
public final class VppUrl {

    private final String projectName;
    private final String resourceKind;
    private final String id;

    private VppUrl(String projectName, String resourceKind, String id) {
        this.projectName = projectName;
        this.resourceKind = resourceKind;
        this.id = id;
    }

    public static VppUrl parse(String raw) throws McpToolException {
        if (raw == null || raw.trim().isEmpty()) {
            throw new McpToolException("\"vpp_url\" must not be empty.");
        }
        String text = raw.trim();

        int schemeEnd = text.indexOf("://");
        if (schemeEnd < 0) {
            return new VppUrl(null, null, text);
        }

        String scheme = text.substring(0, schemeEnd);
        if (scheme.toLowerCase().endsWith(".vpp")) {
            scheme = scheme.substring(0, scheme.length() - ".vpp".length());
        }
        String projectName = scheme.isEmpty() || scheme.equalsIgnoreCase("vpp") ? null : scheme;

        String path = stripAfter(stripAfter(text.substring(schemeEnd + "://".length()), '?'), '#');
        String[] segments = splitNonEmpty(path);
        if (segments.length < 2) {
            // Visual Paradigm always writes "<kind>/<id>", so a single segment means the
            // address was truncated. Guessing an id from it would silently look up nonsense.
            throw new McpToolException("Address \"" + raw + "\" has no \"<kind>/<id>\" path; "
                    + "expected something like \"Project.vpp://diagram/YRvbHuaFYFAEMEOa\".");
        }

        StringBuilder kind = new StringBuilder(segments[0]);
        for (int index = 1; index < segments.length - 1; index++) {
            kind.append('/').append(segments[index]);
        }
        return new VppUrl(projectName, kind.toString(), segments[segments.length - 1]);
    }

    private static String stripAfter(String text, char marker) {
        int index = text.indexOf(marker);
        return index >= 0 ? text.substring(0, index) : text;
    }

    private static String[] splitNonEmpty(String path) {
        List<String> segments = new ArrayList<>();
        for (String segment : path.split("/")) {
            if (!segment.isEmpty()) {
                segments.add(segment);
            }
        }
        return segments.toArray(new String[0]);
    }

    /** The project the address was written against, or null when the address carried none. */
    public String getProjectName() {
        return projectName;
    }

    /** Usually {@code "diagram"} or {@code "model"}; null when the address carried none. */
    public String getResourceKind() {
        return resourceKind;
    }

    public String getId() {
        return id;
    }
}
