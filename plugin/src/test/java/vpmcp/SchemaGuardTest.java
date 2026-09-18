package vpmcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vpmcp.vp.SchemaGuard;

/**
 * Guard contracts. The pack dir is fake; the runtime side reads the real
 * openapi when present. Version verdicts never depend on the jar.
 */
class SchemaGuardTest {

    @Test
    void matchingVersionWithNoShaMatches(@TempDir File dir) {
        writeMeta(dir, "vT", "{\"vp_version\":\"18.1\"}");

        JsonObject report = SchemaGuard.check(dir, "vT", "18.1");

        assertTrue(report.get("match").getAsBoolean(), report.toString());
        assertFalse(report.has("warning"));
    }

    @Test
    void versionMismatchWarns(@TempDir File dir) {
        writeMeta(dir, "vT", "{\"vp_version\":\"18.1\"}");

        JsonObject report = SchemaGuard.check(dir, "vT", "19.0");

        assertFalse(report.get("match").getAsBoolean());
        assertTrue(report.get("warning").getAsString().contains("autovendor"));
    }

    @Test
    void missingPackWarns(@TempDir File dir) {
        JsonObject report = SchemaGuard.check(dir, "vT", "18.1");

        assertFalse(report.get("match").getAsBoolean());
        assertTrue(report.has("warning"));
    }

    @Test
    void shaMismatchWarnsWhenJarResolves(@TempDir File dir) {
        assumeTrue(SchemaGuard.openApiSha() != null, "openapi.jar not on classpath");
        writeMeta(dir, "vT", "{\"vp_version\":\"18.1\",\"openapi_sha16\":\"0000000000000000\"}");

        JsonObject report = SchemaGuard.check(dir, "vT", "18.1");

        assertFalse(report.get("match").getAsBoolean());
        assertTrue(report.get("warning").getAsString().contains("autovendor"));
    }

    @Test
    void nullRuntimeVersionNeverMatches(@TempDir File dir) {
        writeMeta(dir, "vT", "{\"vp_version\":\"18.1\"}");

        JsonObject report = SchemaGuard.check(dir, "vT", null);

        assertFalse(report.get("match").getAsBoolean());
    }

    private void writeMeta(File dir, String version, String text) {
        try {
            File pack = new File(dir, "schemas" + File.separator + version);
            pack.mkdirs();
            Files.write(new File(pack, "meta.json").toPath(), text.getBytes(StandardCharsets.UTF_8));
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
