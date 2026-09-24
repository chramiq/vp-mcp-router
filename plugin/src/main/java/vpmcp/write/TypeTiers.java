package vpmcp.write;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.Reader;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import vpmcp.vp.VpLog;

/**
 * The two write tiers. Verified families are the probe-tested ADR-0014 set:
 * they behave exactly as documented. Pack families come from the schema
 * pack (diagram types, factory-creatable model types) and are accepted
 * but flagged unverified — VP may veto silently or misplace. Types in
 * neither tier are rejected as impossible. A missing or unreadable pack
 * degrades to verified-only, warn-never-fail (SchemaGuard philosophy).
 */
public final class TypeTiers {

    private final Set<String> verifiedDiagrams;
    private final Set<String> verifiedElements;
    private final Set<String> verifiedMembers;
    private final Set<String> verifiedRelationships;
    private final Set<String> packDiagrams;
    private final Set<String> packCreatable;

    public TypeTiers(Set<String> packDiagrams, Set<String> packCreatable) {
        this.verifiedDiagrams = new HashSet<>(java.util.Arrays.asList("ClassDiagram",
                "UseCaseDiagram", "ActivityDiagram", "StateDiagram", "ERDiagram",
                "InteractionDiagram", "DeploymentDiagram"));
        this.verifiedElements = new HashSet<>(java.util.Arrays.asList("Actor", "UseCase",
                "Class", "Activity", "InitialNode", "DecisionNode", "ActivityFinalNode",
                "State2", "DBTable", "Component", "Node", "LifeLine"));
        this.verifiedMembers =
                new HashSet<>(java.util.Arrays.asList("Attribute", "Operation", "DBColumn"));
        this.verifiedRelationships = new HashSet<>(java.util.Arrays.asList("Association",
                "Include", "Extend", "Generalization", "Dependency", "Message", "Transition2"));
        this.packDiagrams = new HashSet<>(packDiagrams);
        this.packCreatable = new HashSet<>(packCreatable);
    }

    /** Verified only, empty pack: the pre-tier behavior, for tests and fallbacks. */
    public static TypeTiers verifiedOnly() {
        return new TypeTiers(new HashSet<>(), new HashSet<>());
    }

    /** Loads the pack from {@code <pluginDir>/schemas/<version>/}; degrades on any failure. */
    public static TypeTiers load(File pluginDir, String schemaVersion) {
        Set<String> diagrams = new HashSet<>();
        Set<String> creatable = new HashSet<>();
        File packDir = new File(pluginDir, "schemas" + File.separator + schemaVersion);
        try (Reader reader = Files.newBufferedReader(new File(packDir, "diagram-types.json").toPath())) {
            JsonArray types = JsonParser.parseReader(reader).getAsJsonArray();
            for (JsonElement item : types) {
                JsonElement value = item.getAsJsonObject().get("value");
                if (value != null && !value.isJsonNull()) {
                    diagrams.add(value.getAsString());
                }
            }
        } catch (Exception unreadable) {
            VpLog.info("TYPE TIERS: diagram-types.json unavailable (" + unreadable + ")");
        }
        try (Reader reader = Files.newBufferedReader(new File(packDir, "factory-creates.json").toPath())) {
            JsonArray creates = JsonParser.parseReader(reader).getAsJsonArray();
            for (JsonElement item : creates) {
                String method = item.getAsString();
                if (method.length() > "create".length()) {
                    creatable.add(method.substring("create".length()));
                }
            }
        } catch (Exception unreadable) {
            VpLog.info("TYPE TIERS: factory-creates.json unavailable (" + unreadable + ")");
        }
        if (diagrams.isEmpty() && creatable.isEmpty()) {
            VpLog.info("TYPE TIERS: no pack data, falling back to verified-only");
        }
        return new TypeTiers(diagrams, creatable);
    }

    public boolean isVerifiedDiagram(String type) {
        return verifiedDiagrams.contains(type);
    }

    /** Verified or in the pack; false means VP has no such diagram type at all. */
    public boolean isKnownDiagram(String type) {
        return isVerifiedDiagram(type) || packDiagrams.contains(type);
    }

    public boolean isVerifiedElement(String type) {
        return verifiedElements.contains(type);
    }

    public boolean isVerifiedMember(String type) {
        return verifiedMembers.contains(type);
    }

    public boolean isVerifiedRelationship(String type) {
        return verifiedRelationships.contains(type);
    }

    /** The factory has a create method for this model type. */
    public boolean isCreatable(String type) {
        return packCreatable.contains(type);
    }

    /** The fixed text plan entries carry for pack-tier types. */
    public static String unverifiedNote() {
        return "unverified family, VP may veto or misplace";
    }
}
