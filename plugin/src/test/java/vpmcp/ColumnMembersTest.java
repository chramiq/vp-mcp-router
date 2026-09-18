package vpmcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.vp.plugin.model.IDBColumn;
import com.vp.plugin.model.IModelElement;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vpmcp.vp.ModelPropertiesReader;

/** Column members serialize type detail. Fakes model the contract, not VP. */
class ColumnMembersTest {

    @Test
    void columnTypeRoundTrips() {
        Map<String, Object> values = new HashMap<>();
        values.put("getId", "c1");
        values.put("getName", "plate");
        values.put("getModelType", "DBColumn");
        values.put("getDocumentation", "");
        values.put("getTypeName", "varchar");
        values.put("getLength", 20);
        values.put("isNullable", true);
        IDBColumn column = VpFakes.of(IDBColumn.class, values);
        IModelElement table = VpFakes.of(IModelElement.class, Map.of(),
                (method, args) -> "toChildArray".equals(method.getName())
                        ? new IModelElement[] {column} : null);

        JsonArray members = new ModelPropertiesReader().readMembers(table);

        assertEquals(1, members.size());
        JsonObject member = members.get(0).getAsJsonObject();
        assertEquals("varchar", member.get("type_name").getAsString());
        assertEquals(20, member.get("length").getAsInt());
        assertTrue(member.get("nullable").getAsBoolean());
    }
}
