package vpmcp.vp;

import com.google.gson.JsonObject;

/** Latest schema-guard report, published at plugin load for tools to read. */
public final class GuardStatus {

    private static volatile JsonObject latest;

    private GuardStatus() {
    }

    public static void publish(JsonObject report) {
        latest = report;
    }

    public static JsonObject latest() {
        return latest;
    }
}
