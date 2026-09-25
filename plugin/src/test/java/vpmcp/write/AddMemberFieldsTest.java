package vpmcp.write;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vp.plugin.model.IAttribute;
import com.vp.plugin.model.IDBColumn;
import com.vp.plugin.model.IOperation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vpmcp.VpFakes;

/**
 * add_member field wiring. applyNewMemberFields shares the update writers,
 * so a create carrying return_type/visibility must drive the same setters
 * an update would — the recording fakes fail if a setter stops firing.
 */
class AddMemberFieldsTest {

    @Test
    void operationReturnTypeAndVisibilityApplied() {
        List<String> calls = new ArrayList<>();
        Map<String, String> state = new HashMap<>();
        IOperation operation = VpFakes.of(IOperation.class, Map.of(), (method, args) -> {
            switch (method.getName()) {
                case "setReturnType":
                    calls.add("setReturnType=" + args[0]);
                    state.put("returnType", (String) args[0]);
                    return null;
                case "getReturnTypeAsString":
                    return state.get("returnType");
                case "setVisibility":
                    calls.add("setVisibility=" + args[0]);
                    state.put("visibility", (String) args[0]);
                    return null;
                case "getVisibility":
                    return state.getOrDefault("visibility", "public");
                default:
                    return null;
            }
        });
        JsonObject op = JsonParser.parseString(
                "{\"return_type\":\"int\",\"visibility\":\"private\"}").getAsJsonObject();
        JsonObject entry = new JsonObject();

        BatchApplier.applyNewMemberFields(operation, op, entry);

        assertTrue(calls.contains("setReturnType=int"), calls.toString());
        assertTrue(calls.contains("setVisibility=private"), calls.toString());
        assertEquals("int", entry.get("return_type").getAsString());
        assertEquals("private", entry.get("visibility").getAsString());
        assertFalse(entry.has("skipped_fields"), entry.toString());
    }

    @Test
    void attributeTypeFieldsApplied() {
        List<String> calls = new ArrayList<>();
        Map<String, String> state = new HashMap<>();
        IAttribute attribute = VpFakes.of(IAttribute.class, Map.of(), (method, args) -> {
            switch (method.getName()) {
                case "setType":
                    calls.add("setType=" + args[0]);
                    state.put("type", (String) args[0]);
                    return null;
                case "getTypeAsString":
                    return state.get("type");
                case "setVisibility":
                    calls.add("setVisibility=" + args[0]);
                    state.put("visibility", (String) args[0]);
                    return null;
                case "getVisibility":
                    return state.getOrDefault("visibility", "private");
                default:
                    return null;
            }
        });
        JsonObject op = JsonParser.parseString(
                "{\"type\":\"string\",\"visibility\":\"public\"}").getAsJsonObject();
        JsonObject entry = new JsonObject();

        BatchApplier.applyNewMemberFields(attribute, op, entry);

        assertTrue(calls.contains("setType=string"), calls.toString());
        assertTrue(calls.contains("setVisibility=public"), calls.toString());
        assertEquals("string", entry.get("type").getAsString());
    }

    @Test
    void inapplicableFieldSkippedWithNote() {
        IAttribute attribute = VpFakes.of(IAttribute.class,
                Map.of("getTypeAsString", "string", "getVisibility", "public"), null);
        JsonObject op = JsonParser.parseString("{\"return_type\":\"int\"}").getAsJsonObject();
        JsonObject entry = new JsonObject();

        BatchApplier.applyNewMemberFields(attribute, op, entry);

        assertFalse(entry.has("return_type"), entry.toString());
        assertTrue(entry.has("skipped_fields"), entry.toString());
        assertTrue(entry.getAsJsonArray("skipped_fields").toString().contains("return_type"),
                entry.toString());
    }

    @Test
    void columnFieldsApplied() {
        List<String> calls = new ArrayList<>();
        Map<String, Object> state = new HashMap<>();
        state.put("length", 0);
        state.put("nullable", false);
        IDBColumn column = VpFakes.of(IDBColumn.class, Map.of(), (method, args) -> {
            switch (method.getName()) {
                case "setTypeName":
                    calls.add("setTypeName=" + args[0]);
                    state.put("typeName", args[0]);
                    return null;
                case "getTypeName":
                    return (String) state.get("typeName");
                case "setLength":
                    calls.add("setLength=" + args[0]);
                    state.put("length", args[0]);
                    return null;
                case "getLength":
                    return state.get("length");
                case "setNullable":
                    calls.add("setNullable=" + args[0]);
                    state.put("nullable", args[0]);
                    return null;
                case "isNullable":
                    return state.get("nullable");
                default:
                    return null;
            }
        });
        JsonObject op = JsonParser.parseString(
                "{\"type\":\"varchar\",\"length\":20,\"nullable\":true}").getAsJsonObject();
        JsonObject entry = new JsonObject();

        BatchApplier.applyNewMemberFields(column, op, entry);

        assertTrue(calls.contains("setTypeName=varchar"), calls.toString());
        assertTrue(calls.contains("setLength=20"), calls.toString());
        assertTrue(calls.contains("setNullable=true"), calls.toString());
        assertEquals("varchar", entry.get("type").getAsString());
        assertEquals(20, entry.get("length").getAsInt());
        assertTrue(entry.get("nullable").getAsBoolean());
    }
}
