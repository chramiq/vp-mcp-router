package vpmcp.write;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vp.plugin.diagram.format.IShapeUIModelFillColor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vpmcp.VpFakes;

/**
 * Fill-flush contracts. The renderer only picks up staged fill colors
 * after applySetting(); the recording fake fails if the flush stops
 * firing or a flush failure escapes.
 */
class StyleFillTest {

    @Test
    void fillFlushesAfterSet() {
        List<String> calls = new ArrayList<>();
        Map<String, Object> state = new HashMap<>();
        IShapeUIModelFillColor fill = VpFakes.of(IShapeUIModelFillColor.class, Map.of(),
                (method, args) -> {
                    switch (method.getName()) {
                        case "setColor1":
                            calls.add("setColor1=" + args[0]);
                            state.put("color1", args[0]);
                            return true;
                        case "getColor1":
                            return state.get("color1");
                        case "applySetting":
                            calls.add("applySetting");
                            return null;
                        default:
                            return null;
                    }
                });

        BatchApplier.setFillColor(fill, java.awt.Color.RED);

        assertTrue(calls.contains("setColor1=" + java.awt.Color.RED), calls.toString());
        assertTrue(calls.contains("applySetting"), calls.toString());
        assertTrue(calls.indexOf("applySetting") > calls.indexOf("setColor1=" + java.awt.Color.RED),
                calls.toString());
        assertEquals(java.awt.Color.RED, fill.getColor1());
    }

    @Test
    void fillFlushFailureIsBestEffort() {
        IShapeUIModelFillColor fill = VpFakes.of(IShapeUIModelFillColor.class, Map.of(),
                (method, args) -> {
                    if ("applySetting".equals(method.getName())) {
                        throw new IllegalStateException("no renderer");
                    }
                    return "setColor1".equals(method.getName()) ? true : null;
                });

        BatchApplier.setFillColor(fill, java.awt.Color.RED);
    }
}
