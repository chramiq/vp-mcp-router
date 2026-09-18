package vpmcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vpmcp.graph.Neighborhood;

/**
 * Neighborhood contracts over hand-built graphs. Sizes derive from the
 * chain built per test; membership is asserted by id, never by position.
 */
class NeighborhoodTest {

    @Test
    void depthZeroKeepsOnlyCenter() {
        JsonObject result = Neighborhood.around(chain("a", "b", "c"), "b", 0);

        assertEquals(names(result, "nodes"), set("b"));
        assertEquals(0, result.getAsJsonArray("edges").size());
    }

    @Test
    void depthOneKeepsDirectNeighbours() {
        JsonObject result = Neighborhood.around(chain("a", "b", "c"), "b", 1);

        assertEquals(names(result, "nodes"), set("a", "b", "c"));
        assertEquals(2, result.getAsJsonArray("edges").size());
    }

    @Test
    void depthLimitsTheWalk() {
        JsonObject result = Neighborhood.around(chain("a", "b", "c", "d"), "a", 1);

        assertEquals(names(result, "nodes"), set("a", "b"));
        assertEquals(1, result.getAsJsonArray("edges").size());
    }

    @Test
    void disconnectedNodesStayOut() {
        JsonObject graph = chain("a", "b");
        graph.getAsJsonArray("nodes").add(node("lonely"));

        JsonObject result = Neighborhood.around(graph, "a", 5);

        assertEquals(names(result, "nodes"), set("a", "b"));
    }

    @Test
    void unknownCenterYieldsEmpty() {
        JsonObject result = Neighborhood.around(chain("a", "b"), "ghost", 2);

        assertEquals(0, result.getAsJsonArray("nodes").size());
        assertEquals(0, result.getAsJsonArray("edges").size());
    }

    @Test
    void danglingEdgesNeverLeakOutside() {
        JsonObject graph = chain("a", "b");
        JsonObject dangling = new JsonObject();
        dangling.addProperty("id", "e9");
        dangling.addProperty("from", "a");
        dangling.addProperty("to", "missing");
        graph.getAsJsonArray("edges").add(dangling);

        JsonObject result = Neighborhood.around(graph, "a", 3);

        assertEquals(names(result, "nodes"), set("a", "b"));
        assertEquals(1, result.getAsJsonArray("edges").size());
    }

    private JsonObject chain(String... ids) {
        JsonObject diagram = new JsonObject();
        diagram.addProperty("name", "fake");
        JsonArray nodes = new JsonArray();
        for (String id : ids) {
            nodes.add(node(id));
        }
        JsonArray edges = new JsonArray();
        for (int index = 0; index + 1 < ids.length; index++) {
            JsonObject edge = new JsonObject();
            edge.addProperty("id", "e" + index);
            edge.addProperty("from", ids[index]);
            edge.addProperty("to", ids[index + 1]);
            edges.add(edge);
        }
        JsonObject graph = new JsonObject();
        graph.add("diagram", diagram);
        graph.add("nodes", nodes);
        graph.add("edges", edges);
        return graph;
    }

    private JsonObject node(String id) {
        JsonObject node = new JsonObject();
        node.addProperty("id", id);
        return node;
    }

    private Set<String> names(JsonObject result, String array) {
        Set<String> names = new HashSet<>();
        result.getAsJsonArray(array)
                .forEach(item -> names.add(item.getAsJsonObject().get("id").getAsString()));
        return names;
    }

    private Set<String> set(String... ids) {
        Set<String> values = new HashSet<>();
        for (String id : ids) {
            values.add(id);
        }
        return values;
    }
}
