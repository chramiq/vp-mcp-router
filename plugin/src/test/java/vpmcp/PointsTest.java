package vpmcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.JsonParser;
import java.awt.Point;
import org.junit.jupiter.api.Test;
import vpmcp.write.BatchApplier;

/** Waypoint conversion. Null in, null out; numbers become points. */
class PointsTest {

    @Test
    void missingPointsStayNull() {
        assertNull(BatchApplier.toPointArray(null));
    }

    @Test
    void waypointsConvert() {
        Point[] points = BatchApplier.toPointArray(
                JsonParser.parseString("[{\"x\":1,\"y\":2},{\"x\":3,\"y\":4}]"));

        assertEquals(2, points.length);
        assertEquals(new Point(1, 2), points[0]);
        assertEquals(new Point(3, 4), points[1]);
    }
}
