package vpmcp.vp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.CodeSource;
import java.security.MessageDigest;

/**
 * Compares the schema pack against the actually loaded VP: pack-declared
 * VP version and openapi sha versus runtime values. Mismatches warn,
 * never fail; graceful degradation is the design.
 */
public final class SchemaGuard {

    private SchemaGuard() {
    }

    public static JsonObject check(File pluginDir, String schemaVersion, String runtimeVpVersion) {
        File meta = new File(pluginDir,
                "schemas" + File.separator + schemaVersion + File.separator + "meta.json");
        JsonObject report = new JsonObject();
        report.addProperty("schema_version", schemaVersion);
        report.addProperty("runtime_vp_version", runtimeVpVersion);
        if (!meta.isFile()) {
            report.addProperty("match", false);
            report.addProperty("warning", "No schema pack at " + meta.getPath() + ".");
            return report;
        }
        try (InputStream stream = Files.newInputStream(meta.toPath())) {
            byte[] bytes = stream.readAllBytes();
            JsonObject pack = JsonParser.parseString(new String(bytes, "UTF-8")).getAsJsonObject();
            String packVersion = pack.has("vp_version") ? pack.get("vp_version").getAsString() : null;
            String packSha = pack.has("openapi_sha16") ? pack.get("openapi_sha16").getAsString() : null;
            report.addProperty("pack_vp_version", packVersion);
            String jarSha = openApiSha();
            String jarName = openApiJarName();
            if (jarName != null) {
                report.addProperty("runtime_jar", jarName);
            }
            if (jarSha != null) {
                report.addProperty("runtime_jar_sha16", jarSha);
            }
            boolean versionMatch = packVersion != null && packVersion.equals(runtimeVpVersion);
            boolean shaMatch = packSha == null || jarSha == null || packSha.equalsIgnoreCase(jarSha);
            report.addProperty("match", versionMatch && shaMatch);
            if (!versionMatch) {
                report.addProperty("warning", "Pack targets VP " + packVersion + " but runtime is "
                        + runtimeVpVersion + "; regenerate with schemas/autovendor.py.");
            } else if (!shaMatch) {
                report.addProperty("warning", "openapi.jar differs from pack build (" + jarSha + " vs "
                        + packSha + "); regenerate with schemas/autovendor.py.");
            }
            return report;
        } catch (Exception unreadable) {
            report.addProperty("match", false);
            report.addProperty("warning", "Cannot read " + meta.getPath() + ": " + unreadable.getMessage());
            return report;
        }
    }

    /** First 16 hex chars of the loaded openapi.jar sha, or null when unresolvable. */
    public static String openApiSha() {
        File jar = openApiJar();
        if (jar == null) {
            return null;
        }
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            try (InputStream stream = Files.newInputStream(jar.toPath())) {
                byte[] buffer = new byte[65536];
                int read;
                while ((read = stream.read(buffer)) >= 0) {
                    sha.update(buffer, 0, read);
                }
            }
            StringBuilder hex = new StringBuilder();
            for (byte digit : sha.digest()) {
                hex.append(Character.forDigit((digit >> 4) & 0xF, 16));
                hex.append(Character.forDigit(digit & 0xF, 16));
            }
            return hex.substring(0, 16);
        } catch (Exception unavailable) {
            return null;
        }
    }

    static String openApiJarName() {
        File jar = openApiJar();
        return jar == null ? null : jar.getName();
    }

    private static File openApiJar() {
        try {
            CodeSource source = com.vp.plugin.model.factory.IModelElementFactory.class.getProtectionDomain()
                    .getCodeSource();
            if (source == null || source.getLocation() == null) {
                return null;
            }
            File jar = new File(source.getLocation().toURI());
            return jar.isFile() ? jar : null;
        } catch (Exception unavailable) {
            return null;
        }
    }
}
