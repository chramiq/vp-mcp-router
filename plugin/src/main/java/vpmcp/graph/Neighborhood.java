package vpmcp.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * N-hop subgraph over an extractor graph ({diagram, nodes[], edges[]}).
 * Pure JSON walk, no VP: nodes keyed by view id, edges by from/to.
 */
public final class Neighborhood {

    private Neighborhood() {
    }

    public static JsonObject around(JsonObject graph, String centerId, int depth) {
        Map<String, JsonObject> nodes = new HashMap<>();
        for (int index = 0; index < graph.getAsJsonArray("nodes").size(); index++) {
            JsonObject node = graph.getAsJsonArray("nodes").get(index).getAsJsonObject();
            nodes.put(node.get("id").getAsString(), node);
        }
        Map<String, Set<String>> adjacent = new HashMap<>();
        for (int index = 0; index < graph.getAsJsonArray("edges").size(); index++) {
            JsonObject edge = graph.getAsJsonArray("edges").get(index).getAsJsonObject();
            if (!edge.has("from") || !edge.has("to") || edge.get("from").isJsonNull()
                    || edge.get("to").isJsonNull()) {
                continue;
            }
            link(adjacent, edge.get("from").getAsString(), edge.get("to").getAsString());
            link(adjacent, edge.get("to").getAsString(), edge.get("from").getAsString());
        }

        Set<String> seen = new HashSet<>();
        Deque<String> frontier = new ArrayDeque<>();
        if (nodes.containsKey(centerId)) {
            seen.add(centerId);
            frontier.add(centerId);
        }
        for (int hop = 0; hop < depth && !frontier.isEmpty(); hop++) {
            int breadth = frontier.size();
            for (int index = 0; index < breadth; index++) {
                String current = frontier.removeFirst();
                Set<String> neighbours = adjacent.get(current);
                if (neighbours == null) {
                    continue;
                }
                for (String neighbour : neighbours) {
                    if (nodes.containsKey(neighbour) && seen.add(neighbour)) {
                        frontier.addLast(neighbour);
                    }
                }
            }
        }

        JsonArray keptNodes = new JsonArray();
        for (String id : seen) {
            keptNodes.add(nodes.get(id));
        }
        JsonArray keptEdges = new JsonArray();
        for (int index = 0; index < graph.getAsJsonArray("edges").size(); index++) {
            JsonObject edge = graph.getAsJsonArray("edges").get(index).getAsJsonObject();
            if (edge.has("from") && edge.has("to") && !edge.get("from").isJsonNull()
                    && !edge.get("to").isJsonNull() && seen.contains(edge.get("from").getAsString())
                    && seen.contains(edge.get("to").getAsString())) {
                keptEdges.add(edge);
            }
        }

        JsonObject result = new JsonObject();
        result.add("diagram", graph.getAsJsonObject("diagram"));
        result.addProperty("center", centerId);
        result.addProperty("depth", depth);
        result.add("nodes", keptNodes);
        result.add("edges", keptEdges);
        return result;
    }

    private static void link(Map<String, Set<String>> adjacent, String from, String to) {
        Set<String> targets = adjacent.get(from);
        if (targets == null) {
            targets = new HashSet<>();
            adjacent.put(from, targets);
        }
        targets.add(to);
    }
}
