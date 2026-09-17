package com.example.grapheditor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EditorLogicTest {

    @Test
    @DisplayName("Issue #7: Angle calculation covers horizontal, vertical, and diagonal directions")
    void testCalculateAngle() {
        assertEquals(0.0, GeometryUtils.calculateAngle(0, 0, 10, 0), 1e-9, "pointing right");
        assertEquals(Math.PI, Math.abs(GeometryUtils.calculateAngle(0, 0, -10, 0)), 1e-9, "pointing left");
        assertEquals(Math.PI / 2, GeometryUtils.calculateAngle(0, 0, 0, 10), 1e-9, "pointing down");
        assertEquals(-Math.PI / 2, GeometryUtils.calculateAngle(0, 0, 0, -10), 1e-9, "pointing up");
        assertEquals(Math.PI / 4, GeometryUtils.calculateAngle(0, 0, 10, 10), 1e-9, "diagonal down-right");
    }

    @Test
    @DisplayName("Issue #8: Trimmed connector segment starts and ends on circle boundaries")
    void testTrimmedSegment() {
        double radius = EditorApplication.NODE_RADIUS;
        double[] segment = GeometryUtils.trimmedSegment(100, 100, 220, 100, radius);

        assertEquals(120.0, segment[0], 1e-9);
        assertEquals(100.0, segment[1], 1e-9);
        assertEquals(200.0, segment[2], 1e-9);
        assertEquals(100.0, segment[3], 1e-9);
    }

    @Test
    @DisplayName("Issue #5: Bounding box offset correctly centers circle")
    void testBoundingBoxCentering() {
        double centerX = 200.0;
        double centerY = 300.0;
        double radius = EditorApplication.NODE_RADIUS;

        double topLeftX = centerX - radius;
        double topLeftY = centerY - radius;
        double diameter = radius * 2.0;

        assertEquals(180.0, topLeftX);
        assertEquals(280.0, topLeftY);
        assertEquals(40.0, diameter);
    }

    @Test
    @DisplayName("LinkedStack: LIFO push/pop order and empty-stack contract")
    void testLinkedStackOrder() {
        LinkedStack<Integer> stack = new LinkedStack<>();
        assertTrue(stack.isEmpty());

        stack.push(1);
        stack.push(2);
        stack.push(3);
        assertEquals(3, stack.size());
        assertEquals(3, stack.pop());
        assertEquals(2, stack.pop());
        assertEquals(1, stack.pop());
        assertTrue(stack.isEmpty());
        assertThrows(java.util.NoSuchElementException.class, stack::pop);
    }

    @Test
    @DisplayName("GraphModel: node/arrow add-remove and proximity/hit queries")
    void testGraphModelQueries() {
        GraphModel model = new GraphModel();
        GraphNode a = new GraphNode(model.allocateNodeId(), 100, 100);
        GraphNode b = new GraphNode(model.allocateNodeId(), 220, 100);
        model.addNode(a);
        model.addNode(b);

        assertEquals(a, model.hitNodeBody(105, 100, EditorApplication.NODE_RADIUS));
        assertNull(model.hitNodeBody(160, 100, EditorApplication.NODE_RADIUS));

        // 30px apart is within 2R (40) of a fixed-radius-20 circle at (100,100).
        GraphNode conflict = model.nearestConflictingNode(130, 100, EditorApplication.NODE_RADIUS, 1e-6);
        assertEquals(a, conflict);

        GraphArrow arrow = new GraphArrow(model.allocateArrowId(), a.id(), b.id());
        model.addArrow(arrow);
        assertTrue(model.arrowExists(a.id(), b.id()));
        assertFalse(model.arrowExists(b.id(), a.id()));
        assertEquals(List.of(arrow), model.incidentArrows(a.id()));
        assertEquals(List.of(arrow), model.incidentArrows(b.id()));
    }

    @Test
    @DisplayName("Commands: add/delete node and arrow round-trip through apply/undo")
    void testCommandUndoRoundTrip() {
        GraphModel model = new GraphModel();
        GraphNode a = new GraphNode(model.allocateNodeId(), 100, 100);

        EditCommand addA = new AddNodeCommand(a);
        addA.apply(model);
        assertEquals(List.of(a), model.getNodes());

        addA.undo(model);
        assertTrue(model.getNodes().isEmpty());

        addA.apply(model);
        GraphNode b = new GraphNode(model.allocateNodeId(), 220, 100);
        GraphArrow arrow = new GraphArrow(model.allocateArrowId(), a.id(), b.id());
        EditCommand addConnectedB = new AddConnectedNodeCommand(b, arrow);
        addConnectedB.apply(model);
        assertEquals(2, model.getNodes().size());
        assertTrue(model.arrowExists(a.id(), b.id()));

        EditCommand deleteA = new DeleteNodeCommand(a, model.incidentArrows(a.id()));
        deleteA.apply(model);
        assertNull(model.findNode(a.id()));
        assertTrue(model.getArrows().isEmpty(), "cascading delete must remove incident arrows");

        deleteA.undo(model);
        assertEquals(a, model.findNode(a.id()));
        assertTrue(model.arrowExists(a.id(), b.id()), "undo must restore the incident arrow");
    }

    @Test
    @DisplayName("Issue #11: Bidirectional arrow model, lookup, and UpgradeArrowCommand apply/undo")
    void testBidirectionalArrowAndUpgradeCommand() {
        GraphModel model = new GraphModel();
        GraphNode a = new GraphNode(model.allocateNodeId(), 100, 100);
        GraphNode b = new GraphNode(model.allocateNodeId(), 220, 100);
        model.addNode(a);
        model.addNode(b);

        GraphArrow oneWay = new GraphArrow(model.allocateArrowId(), a.id(), b.id());
        assertFalse(oneWay.bidirectional(), "default constructor must set bidirectional to false");
        model.addArrow(oneWay);

        assertEquals(oneWay, model.findArrow(a.id(), b.id()));
        assertNull(model.findArrow(b.id(), a.id()));
        assertTrue(model.arrowExists(a.id(), b.id()));
        assertFalse(model.arrowExists(b.id(), a.id()));

        UpgradeArrowCommand upgrade = new UpgradeArrowCommand(oneWay);
        upgrade.apply(model);

        GraphArrow upgraded = model.findArrow(a.id(), b.id());
        assertNotNull(upgraded);
        assertEquals(oneWay.id(), upgraded.id(), "upgraded arrow keeps the same id");
        assertTrue(upgraded.bidirectional(), "upgraded arrow must have bidirectional = true");

        upgrade.undo(model);
        GraphArrow reverted = model.findArrow(a.id(), b.id());
        assertNotNull(reverted);
        assertEquals(oneWay.id(), reverted.id());
        assertFalse(reverted.bidirectional(), "undo must restore original one-way arrow");
    }

    @Test
    @DisplayName("Issue #16: GraphNode.withPosition and MoveNodesCommand apply/undo for single and multi-selection")
    void testMoveNodesCommandRoundTrip() {
        GraphModel model = new GraphModel();
        GraphNode a = new GraphNode(model.allocateNodeId(), 100, 100);
        GraphNode b = new GraphNode(model.allocateNodeId(), 200, 250);
        model.addNode(a);
        model.addNode(b);

        // withPosition test
        GraphNode aRepositioned = a.withPosition(150, 120);
        assertEquals(a.id(), aRepositioned.id());
        assertEquals(150.0, aRepositioned.x());
        assertEquals(120.0, aRepositioned.y());

        // Move single node
        MoveNodesCommand moveSingle = new MoveNodesCommand(List.of(a), 50, -30);
        moveSingle.apply(model);
        GraphNode movedA = model.findNode(a.id());
        assertEquals(150.0, movedA.x());
        assertEquals(70.0, movedA.y());

        moveSingle.undo(model);
        assertEquals(a, model.findNode(a.id()));

        // Move multiple nodes (multi-selection)
        MoveNodesCommand moveMulti = new MoveNodesCommand(List.of(a, b), 40, 60);
        moveMulti.apply(model);
        assertEquals(140.0, model.findNode(a.id()).x());
        assertEquals(160.0, model.findNode(a.id()).y());
        assertEquals(240.0, model.findNode(b.id()).x());
        assertEquals(310.0, model.findNode(b.id()).y());

        moveMulti.undo(model);
        assertEquals(a, model.findNode(a.id()));
        assertEquals(b, model.findNode(b.id()));
    }

    // -----------------------------------------------------------------
    // Issue #13: Save/load graph as JSON
    // -----------------------------------------------------------------

    /**
     * Builds a small graph: three nodes, one one-way arrow and one bidirectional arrow.
     */
    private static GraphModel sampleGraph() {
        GraphModel model = new GraphModel();
        GraphNode a = new GraphNode(model.allocateNodeId(), 120.5, 80.25);
        GraphNode b = new GraphNode(model.allocateNodeId(), 300.0, 240.0);
        GraphNode c = new GraphNode(model.allocateNodeId(), 500.0, 90.0);
        model.addNode(a);
        model.addNode(b);
        model.addNode(c);
        model.addArrow(new GraphArrow(model.allocateArrowId(), a.id(), b.id()));
        model.addArrow(new GraphArrow(model.allocateArrowId(), b.id(), c.id(), true));
        return model;
    }

    @Test
    @DisplayName("Issue #13: JSON round-trip preserves nodes, arrows, and their ids")
    void testJsonRoundTrip() {
        GraphModel original = sampleGraph();

        GraphModel restored = GraphJsonCodec.fromJson(GraphJsonCodec.toJson(original));

        assertEquals(original.getNodes(), restored.getNodes(), "nodes must survive the round-trip");
        assertEquals(original.getArrows(), restored.getArrows(), "arrows must survive the round-trip");
    }

    @Test
    @DisplayName("Issue #13: Round-trip of an empty graph yields an empty graph")
    void testJsonRoundTripEmptyGraph() {
        GraphModel restored = GraphJsonCodec.fromJson(GraphJsonCodec.toJson(new GraphModel()));

        assertTrue(restored.getNodes().isEmpty());
        assertTrue(restored.getArrows().isEmpty());
        assertEquals(1, restored.allocateNodeId(), "empty graph must allocate from 1");
    }

    @Test
    @DisplayName("Issue #13: Ids allocated after a load cannot collide with loaded ids")
    void testLoadResetsIdAllocationAboveLoadedIds() {
        GraphModel model = new GraphModel();
        model.loadFrom(
                List.of(new GraphNode(4, 10, 10), new GraphNode(9, 50, 50)),
                List.of(new GraphArrow(7, 4, 9)));

        assertEquals(10, model.allocateNodeId(), "next node id must clear the highest loaded id");
        assertEquals(8, model.allocateArrowId(), "next arrow id must clear the highest loaded id");
    }

    @Test
    @DisplayName("Issue #13: Loading replaces any previous graph state")
    void testLoadFromReplacesExistingState() {
        GraphModel model = sampleGraph();

        model.loadFrom(List.of(new GraphNode(1, 5, 5)), List.of());

        assertEquals(List.of(new GraphNode(1, 5, 5)), model.getNodes());
        assertTrue(model.getArrows().isEmpty(), "previous arrows must be discarded");
    }

    @Test
    @DisplayName("Issue #13: Saving to a file and loading it back restores the same graph")
    void testSaveAndLoadThroughAFile(@TempDir Path directory) throws IOException {
        GraphModel original = sampleGraph();
        Path file = directory.resolve("graph.json");

        // Same I/O path the Save and Load buttons use.
        Files.writeString(file, GraphJsonCodec.toJson(original), StandardCharsets.UTF_8);
        GraphModel loaded = GraphJsonCodec.fromJson(Files.readString(file, StandardCharsets.UTF_8));

        GraphModel live = new GraphModel();
        live.loadFrom(loaded.getNodes(), loaded.getArrows());

        assertEquals(original.getNodes(), live.getNodes());
        assertEquals(original.getArrows(), live.getArrows());

        // A node added after the load must not reuse a loaded id.
        long freshId = live.allocateNodeId();
        assertNull(live.findNode(freshId), "id allocated after a load must be unused");
    }

    @Test
    @DisplayName("Issue #13: The bidirectional flag from #11 survives a round-trip")
    void testJsonPreservesBidirectionalFlag() {
        GraphModel restored = GraphJsonCodec.fromJson(GraphJsonCodec.toJson(sampleGraph()));

        List<GraphArrow> arrows = restored.getArrows();
        assertEquals(2, arrows.size());
        assertFalse(arrows.get(0).bidirectional(), "a one-way arrow must stay one-way");
        assertTrue(arrows.get(1).bidirectional(), "a bidirectional arrow must stay bidirectional");
    }

    @Test
    @DisplayName("Issue #13: An arrow with no bidirectional field loads as one-way")
    void testJsonTreatsMissingBidirectionalAsFalse() {
        // Shape written before bidirectional arrows existed.
        String legacy = "{\"nodes\": [{\"id\": 1, \"x\": 0, \"y\": 0}, {\"id\": 2, \"x\": 50, \"y\": 0}],"
                + " \"arrows\": [{\"id\": 1, \"sourceId\": 1, \"targetId\": 2}]}";

        GraphArrow arrow = GraphJsonCodec.fromJson(legacy).getArrows().get(0);

        assertFalse(arrow.bidirectional(), "absent flag must default to one-way");
    }

    @Test
    @DisplayName("Issue #13: A non-boolean bidirectional value is rejected")
    void testJsonRejectsNonBooleanBidirectional() {
        String bad = "{\"nodes\": [{\"id\": 1, \"x\": 0, \"y\": 0}, {\"id\": 2, \"x\": 50, \"y\": 0}],"
                + " \"arrows\": [{\"id\": 1, \"sourceId\": 1, \"targetId\": 2, \"bidirectional\": 3}]}";

        assertThrows(IllegalArgumentException.class, () -> GraphJsonCodec.fromJson(bad));
    }

    @Test
    @DisplayName("Issue #13: Parser tolerates arbitrary whitespace and compact formatting")
    void testJsonParserToleratesWhitespace() {
        String compact = "{\"nodes\":[{\"id\":1,\"x\":0,\"y\":0}],\"arrows\":[]}";
        String spaced = "{\n\n  \"nodes\" : [ { \"id\" : 1 , \"x\" : 0 , \"y\" : 0 } ] ,"
                + "\n  \"arrows\" : [ ]\n}\n";

        assertEquals(List.of(new GraphNode(1, 0, 0)), GraphJsonCodec.fromJson(compact).getNodes());
        assertEquals(List.of(new GraphNode(1, 0, 0)), GraphJsonCodec.fromJson(spaced).getNodes());
    }

    @Test
    @DisplayName("Issue #13: Negative and exponent-notation coordinates survive a round-trip")
    void testJsonHandlesNegativeAndExponentCoordinates() {
        GraphModel model = new GraphModel();
        model.loadFrom(List.of(new GraphNode(1, -12.5, 1.0E-3)), List.of());

        GraphModel restored = GraphJsonCodec.fromJson(GraphJsonCodec.toJson(model));

        assertEquals(List.of(new GraphNode(1, -12.5, 1.0E-3)), restored.getNodes());
    }

    @Test
    @DisplayName("Issue #13: Malformed or inconsistent JSON is rejected rather than half-loaded")
    void testJsonRejectsInvalidDocuments() {
        assertThrows(IllegalArgumentException.class,
                () -> GraphJsonCodec.fromJson("not json"),
                "garbage input");
        assertThrows(IllegalArgumentException.class,
                () -> GraphJsonCodec.fromJson("{\"nodes\": []}"),
                "missing arrows array");
        assertThrows(IllegalArgumentException.class,
                () -> GraphJsonCodec.fromJson("{\"nodes\": [{\"id\": 1, \"x\": 0}], \"arrows\": []}"),
                "node missing its y coordinate");
        assertThrows(IllegalArgumentException.class,
                () -> GraphJsonCodec.fromJson(
                        "{\"nodes\": [{\"id\": 1, \"x\": 0, \"y\": 0},"
                                + " {\"id\": 1, \"x\": 9, \"y\": 9}], \"arrows\": []}"),
                "duplicate node id");
        assertThrows(IllegalArgumentException.class,
                () -> GraphJsonCodec.fromJson(
                        "{\"nodes\": [{\"id\": 1, \"x\": 0, \"y\": 0}],"
                                + " \"arrows\": [{\"id\": 1, \"sourceId\": 1, \"targetId\": 99}]}"),
                "arrow pointing at a node that is not in the file");
        assertThrows(IllegalArgumentException.class,
                () -> GraphJsonCodec.fromJson("{\"nodes\": [{\"id\": 1.5, \"x\": 0, \"y\": 0}],"
                        + " \"arrows\": []}"),
                "non-integral id");
        assertThrows(IllegalArgumentException.class,
                () -> GraphJsonCodec.fromJson("{\"nodes\": [], \"arrows\": [], \"extra\": []}"),
                "unexpected top-level key");
    }

    // -----------------------------------------------------------------
    // Issue #12: Drag-connect preview line + pull motion animation
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Issue #12: lerp interpolates endpoints and eases monotonically toward the target")
    void testLerp() {
        assertEquals(0.0, GeometryUtils.lerp(0, 10, 0.0), 1e-9, "t=0 stays at the start");
        assertEquals(10.0, GeometryUtils.lerp(0, 10, 1.0), 1e-9, "t=1 reaches the target");
        assertEquals(5.0, GeometryUtils.lerp(0, 10, 0.5), 1e-9, "t=0.5 is the midpoint");
        assertEquals(-2.5, GeometryUtils.lerp(-10, 5, 0.5), 1e-9, "works across zero");

        // Repeated application must approach the target without overshooting it.
        double value = 0.0;
        for (int frame = 0; frame < 50; frame++) {
            double previous = value;
            value = GeometryUtils.lerp(value, 10.0, 0.35);
            assertTrue(value > previous && value <= 10.0, "frame " + frame + " must ease inward");
        }
        assertEquals(10.0, value, 1e-6, "easing converges on the target");
    }

    @Test
    @DisplayName("Issue #12: Pull offsets are clamped in length but keep their direction")
    void testClampMagnitude() {
        double[] within = GeometryUtils.clampMagnitude(3, 4, 10);
        assertEquals(3.0, within[0], 1e-9, "short vectors pass through unchanged");
        assertEquals(4.0, within[1], 1e-9);

        double[] clamped = GeometryUtils.clampMagnitude(30, 40, 10);
        assertEquals(10.0, Math.hypot(clamped[0], clamped[1]), 1e-9, "length is capped");
        assertEquals(6.0, clamped[0], 1e-9, "direction is preserved");
        assertEquals(8.0, clamped[1], 1e-9);

        double[] zero = GeometryUtils.clampMagnitude(0, 0, 10);
        assertEquals(0.0, zero[0], 1e-9, "zero-length input must not divide by zero");
        assertEquals(0.0, zero[1], 1e-9);
    }

    @Test
    @DisplayName("Issue #12: Pull target is the nearest in-range node, never the drag source")
    void testNearestNodeWithinExcludesSource() {
        GraphModel model = new GraphModel();
        GraphNode source = new GraphNode(model.allocateNodeId(), 100, 100);
        GraphNode near = new GraphNode(model.allocateNodeId(), 150, 100);
        GraphNode far = new GraphNode(model.allocateNodeId(), 700, 500);
        model.addNode(source);
        model.addNode(near);
        model.addNode(far);

        assertEquals(near, model.nearestNodeWithin(140, 100, PullMotionModel.PULL_RADIUS, source.id()),
                "nearest in-range node wins");
        assertEquals(near, model.nearestNodeWithin(100, 100, PullMotionModel.PULL_RADIUS, source.id()),
                "even with the cursor on the source node, the source is skipped as a candidate");
        assertNull(model.nearestNodeWithin(400, 300, PullMotionModel.PULL_RADIUS, source.id()),
                "nodes beyond PULL_RADIUS are ignored");

        GraphModel lone = new GraphModel();
        GraphNode only = new GraphNode(lone.allocateNodeId(), 100, 100);
        lone.addNode(only);
        assertNull(lone.nearestNodeWithin(100, 100, PullMotionModel.PULL_RADIUS, only.id()),
                "a drag from the only node has nothing to pull");
    }

    @Test
    @DisplayName("Issue #12: Pulled node eases toward the cursor, capped at MAX_PULL_OFFSET")
    void testPullMotionEasesTowardCursor() {
        GraphModel model = new GraphModel();
        GraphNode node = new GraphNode(model.allocateNodeId(), 100, 100);
        model.addNode(node);

        PullMotionModel motion = new PullMotionModel();
        assertTrue(motion.isAtRest(), "no drag means no animation");
        assertArrayEquals(new double[]{100, 100}, motion.effectivePosition(node), 1e-9,
                "an untracked node draws at its true position");

        motion.setPulledNodeId(node.id());
        assertFalse(motion.isAtRest(), "an active pull keeps the animation running");

        // Cursor far to the right: the node should drift right, but never past the cap.
        double previousOffset = 0.0;
        for (int frame = 0; frame < 60; frame++) {
            motion.tick(model, 400, 100);
            double offset = motion.effectivePosition(node)[0] - node.x();
            assertTrue(offset >= previousOffset - 1e-9, "motion must be gradual, not a snap");
            assertTrue(offset <= PullMotionModel.MAX_PULL_OFFSET + 1e-9,
                    "offset must never exceed MAX_PULL_OFFSET");
            previousOffset = offset;
        }
        assertEquals(PullMotionModel.MAX_PULL_OFFSET, previousOffset, 1e-3,
                "a sustained pull settles at the cap");
        assertEquals(100.0, motion.effectivePosition(node)[1], 1e-6,
                "a horizontal pull must not move the node vertically");

        // One frame must not jump the whole way there: this is eased, not instant.
        PullMotionModel single = new PullMotionModel();
        single.setPulledNodeId(node.id());
        single.tick(model, 400, 100);
        double firstFrame = single.effectivePosition(node)[0] - node.x();
        assertTrue(firstFrame > 0 && firstFrame < PullMotionModel.MAX_PULL_OFFSET,
                "first frame is partway, not snapped: " + firstFrame);
    }

    @Test
    @DisplayName("Issue #12: Releasing a pull springs the node back and stops the animation")
    void testPullMotionSpringsBackAfterRelease() {
        GraphModel model = new GraphModel();
        GraphNode node = new GraphNode(model.allocateNodeId(), 100, 100);
        model.addNode(node);

        PullMotionModel motion = new PullMotionModel();
        motion.setPulledNodeId(node.id());
        for (int frame = 0; frame < 30; frame++) {
            motion.tick(model, 400, 100);
        }
        assertTrue(motion.effectivePosition(node)[0] > node.x(), "node is displaced before release");

        motion.setPulledNodeId(null);
        double previousOffset = motion.effectivePosition(node)[0] - node.x();
        for (int frame = 0; frame < 60 && !motion.isAtRest(); frame++) {
            motion.tick(model, 400, 100);
            double offset = motion.effectivePosition(node)[0] - node.x();
            assertTrue(offset <= previousOffset + 1e-9, "spring-back must be gradual too");
            previousOffset = offset;
        }

        assertTrue(motion.isAtRest(), "settled offsets must let the animation timer stop");
        assertArrayEquals(new double[]{100, 100}, motion.effectivePosition(node), 1e-9,
                "node returns to its true position");
    }

    @Test
    @DisplayName("Issue #12: Pull offsets are cosmetic and never mutate the graph model")
    void testPullMotionDoesNotMutateModel() {
        GraphModel model = new GraphModel();
        GraphNode node = new GraphNode(model.allocateNodeId(), 100, 100);
        model.addNode(node);

        PullMotionModel motion = new PullMotionModel();
        motion.setPulledNodeId(node.id());
        for (int frame = 0; frame < 20; frame++) {
            motion.tick(model, 400, 300);
        }

        assertEquals(node, model.findNode(node.id()), "model coordinates must be untouched");
        assertEquals(node, model.hitNodeBody(100, 100, EditorApplication.NODE_RADIUS),
                "hit testing must still resolve against the true position");
    }

    @Test
    @DisplayName("Issue #12: Reset drops all motion at once, for cancel or load")
    void testPullMotionReset() {
        GraphModel model = new GraphModel();
        GraphNode node = new GraphNode(model.allocateNodeId(), 100, 100);
        model.addNode(node);

        PullMotionModel motion = new PullMotionModel();
        motion.setPulledNodeId(node.id());
        motion.tick(model, 400, 100);

        motion.reset();

        assertTrue(motion.isAtRest());
        assertNull(motion.getPulledNodeId());
        assertArrayEquals(new double[]{100, 100}, motion.effectivePosition(node), 1e-9);
    }

    @Test
    @DisplayName("Issue #12: A node deleted mid-pull decays instead of chasing a missing position")
    void testPullMotionHandlesDeletedNode() {
        GraphModel model = new GraphModel();
        GraphNode node = new GraphNode(model.allocateNodeId(), 100, 100);
        model.addNode(node);

        PullMotionModel motion = new PullMotionModel();
        motion.setPulledNodeId(node.id());
        motion.tick(model, 400, 100);
        model.removeNode(node.id());

        for (int frame = 0; frame < 60; frame++) {
            motion.tick(model, 400, 100);
        }

        assertArrayEquals(new double[]{100, 100}, motion.effectivePosition(node), 1e-6,
                "offset decays to zero once the node is gone");
    }
}
