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
    void testAngleBetweenPoints() {
        assertEquals(0.0, GeometryUtils.angleBetweenPoints(0, 0, 10, 0), 1e-9, "pointing right");
        assertEquals(Math.PI, Math.abs(GeometryUtils.angleBetweenPoints(0, 0, -10, 0)), 1e-9, "pointing left");
        assertEquals(Math.PI / 2, GeometryUtils.angleBetweenPoints(0, 0, 0, 10), 1e-9, "pointing down");
        assertEquals(-Math.PI / 2, GeometryUtils.angleBetweenPoints(0, 0, 0, -10), 1e-9, "pointing up");
        assertEquals(Math.PI / 4, GeometryUtils.angleBetweenPoints(0, 0, 10, 10), 1e-9, "diagonal down-right");
    }

    @Test
    @DisplayName("Issue #8: Trimmed connector segment starts and ends on circle boundaries")
    void testTrimmedArrowLine() {
        double radius = EditorApplication.NODE_RADIUS;
        GeometryUtils.ArrowLine line = GeometryUtils.trimmedArrowLine(100, 100, 220, 100, radius);

        assertEquals(120.0, line.startX(), 1e-9);
        assertEquals(100.0, line.startY(), 1e-9);
        assertEquals(200.0, line.endX(), 1e-9);
        assertEquals(100.0, line.endY(), 1e-9);
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
        GraphNode conflict = model.nearestOverlappingNode(130, 100, EditorApplication.NODE_RADIUS, 1e-6);
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

    // Issue #13: Save/load graph as JSON

    // Three nodes, one one-way arrow and one two-way arrow.
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

    // Issue #12: Drag-connect preview line + pull motion animation

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

        assertEquals(near, model.nearestNodeWithin(140, 100, NodePullAnimation.PULL_RADIUS, source.id()),
                "nearest in-range node wins");
        assertEquals(near, model.nearestNodeWithin(100, 100, NodePullAnimation.PULL_RADIUS, source.id()),
                "even with the cursor on the source node, the source is skipped as a candidate");
        assertNull(model.nearestNodeWithin(400, 300, NodePullAnimation.PULL_RADIUS, source.id()),
                "nodes beyond PULL_RADIUS are ignored");

        GraphModel lone = new GraphModel();
        GraphNode only = new GraphNode(lone.allocateNodeId(), 100, 100);
        lone.addNode(only);
        assertNull(lone.nearestNodeWithin(100, 100, NodePullAnimation.PULL_RADIUS, only.id()),
                "a drag from the only node has nothing to pull");
    }

    @Test
    @DisplayName("Issue #12: Pulled node eases toward the cursor, capped at MAX_PULL_OFFSET")
    void testPullAnimationEasesTowardCursor() {
        GraphModel model = new GraphModel();
        GraphNode node = new GraphNode(model.allocateNodeId(), 100, 100);
        model.addNode(node);

        NodePullAnimation motion = new NodePullAnimation();
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
            assertTrue(offset <= NodePullAnimation.MAX_PULL_OFFSET + 1e-9,
                    "offset must never exceed MAX_PULL_OFFSET");
            previousOffset = offset;
        }
        assertEquals(NodePullAnimation.MAX_PULL_OFFSET, previousOffset, 1e-3,
                "a sustained pull settles at the cap");
        assertEquals(100.0, motion.effectivePosition(node)[1], 1e-6,
                "a horizontal pull must not move the node vertically");

        // One frame must not jump the whole way there: this is eased, not instant.
        NodePullAnimation single = new NodePullAnimation();
        single.setPulledNodeId(node.id());
        single.tick(model, 400, 100);
        double firstFrame = single.effectivePosition(node)[0] - node.x();
        assertTrue(firstFrame > 0 && firstFrame < NodePullAnimation.MAX_PULL_OFFSET,
                "first frame is partway, not snapped: " + firstFrame);
    }

    @Test
    @DisplayName("Issue #12: Releasing a pull springs the node back and stops the animation")
    void testPullAnimationSpringsBackAfterRelease() {
        GraphModel model = new GraphModel();
        GraphNode node = new GraphNode(model.allocateNodeId(), 100, 100);
        model.addNode(node);

        NodePullAnimation motion = new NodePullAnimation();
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
    void testPullAnimationDoesNotMutateModel() {
        GraphModel model = new GraphModel();
        GraphNode node = new GraphNode(model.allocateNodeId(), 100, 100);
        model.addNode(node);

        NodePullAnimation motion = new NodePullAnimation();
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
    void testPullAnimationReset() {
        GraphModel model = new GraphModel();
        GraphNode node = new GraphNode(model.allocateNodeId(), 100, 100);
        model.addNode(node);

        NodePullAnimation motion = new NodePullAnimation();
        motion.setPulledNodeId(node.id());
        motion.tick(model, 400, 100);

        motion.reset();

        assertTrue(motion.isAtRest());
        assertNull(motion.getPulledNodeId());
        assertArrayEquals(new double[]{100, 100}, motion.effectivePosition(node), 1e-9);
    }

    @Test
    @DisplayName("Issue #12: A node deleted mid-pull decays instead of chasing a missing position")
    void testPullAnimationHandlesDeletedNode() {
        GraphModel model = new GraphModel();
        GraphNode node = new GraphNode(model.allocateNodeId(), 100, 100);
        model.addNode(node);

        NodePullAnimation motion = new NodePullAnimation();
        motion.setPulledNodeId(node.id());
        motion.tick(model, 400, 100);
        model.removeNode(node.id());

        for (int frame = 0; frame < 60; frame++) {
            motion.tick(model, 400, 100);
        }

        assertArrayEquals(new double[]{100, 100}, motion.effectivePosition(node), 1e-6,
                "offset decays to zero once the node is gone");
    }

    @Test
    @DisplayName("Hints: each editor state maps to its own short instruction, drag taking priority")
    void testHintSelection() {
        assertEquals(EditorHints.IDLE, EditorHints.hintFor(false, 0, 0));
        assertEquals(EditorHints.NODE_SELECTED, EditorHints.hintFor(false, 1, 0));
        assertEquals(EditorHints.MULTI_SELECTED, EditorHints.hintFor(false, 0, 2));
        assertEquals(EditorHints.NODE_SELECTED, EditorHints.hintFor(false, 1, 2),
                "orange selection is the one right-click acts on");
        assertEquals(EditorHints.CONNECTING, EditorHints.hintFor(true, 1, 1));
        for (String hint : new String[]{EditorHints.IDLE, EditorHints.NODE_SELECTED,
                EditorHints.MULTI_SELECTED, EditorHints.CONNECTING}) {
            assertTrue(hint.length() <= 80, "hint must fit the toolbar: " + hint);
        }
    }

    @Test
    @DisplayName("Auto-connect: selection cleared after creation returns the hint to idle; command is atomic")
    void testAutoConnectClearsSelectionState() {
        GraphModel model = new GraphModel();
        GraphNode source = new GraphNode(model.allocateNodeId(), 100, 100);
        model.addNode(source);
        Long selected = source.id();
        assertEquals(EditorHints.NODE_SELECTED, EditorHints.hintFor(false, selected != null ? 1 : 0, 0));

        GraphNode created = new GraphNode(model.allocateNodeId(), 250, 100);
        GraphArrow arrow = new GraphArrow(model.allocateArrowId(), source.id(), created.id());
        selected = null; // the controller clears selection before rendering
        AddConnectedNodeCommand command = new AddConnectedNodeCommand(created, arrow);
        command.apply(model);

        assertEquals(EditorHints.IDLE, EditorHints.hintFor(false, selected != null ? 1 : 0, 0));
        assertEquals(2, model.getNodes().size());
        assertEquals(1, model.getArrows().size());
        command.undo(model);
        assertEquals(1, model.getNodes().size());
        assertTrue(model.getArrows().isEmpty());
    }

    @Test
    @DisplayName("Multi-select: plain right-click replaces the selection, Shift+right-click toggles membership")
    void testSelectionStateToggles() {
        SelectionState selection = new SelectionState();
        selection.toggleSingle(1);
        assertEquals(List.of(1L), selection.ids());
        selection.toggleSingle(2);
        assertEquals(List.of(2L), selection.ids(), "plain click replaces");
        selection.toggleAdditive(3);
        selection.toggleAdditive(1);
        assertEquals(List.of(2L, 3L, 1L), selection.ids(), "additive keeps order");
        selection.toggleAdditive(3);
        assertFalse(selection.contains(3));
        assertEquals(2, selection.size());
        selection.toggleSingle(2);
        assertEquals(List.of(2L), selection.ids(), "plain click on one of several keeps only it");
        selection.toggleSingle(2);
        assertTrue(selection.isEmpty(), "plain click on the sole selection deselects");
        selection.toggleAdditive(5);
        selection.clear();
        assertTrue(selection.isEmpty());
    }

    @Test
    @DisplayName("Multi-select: a new node gets one arrow from every selected node, undone in one step")
    void testMultiSourceAutoConnect() {
        GraphModel model = new GraphModel();
        SelectionState selection = new SelectionState();
        long[] sourceIds = new long[3];
        for (int i = 0; i < 3; i++) {
            GraphNode n = new GraphNode(model.allocateNodeId(), 100 + 100 * i, 100);
            model.addNode(n);
            sourceIds[i] = n.id();
            selection.toggleAdditive(n.id());
        }
        assertEquals(EditorHints.NODES_SELECTED, EditorHints.hintFor(false, selection.size(), 0));

        GraphNode created = new GraphNode(model.allocateNodeId(), 300, 300);
        List<GraphArrow> arrows = new java.util.ArrayList<>();
        for (Long id : selection.ids()) {
            arrows.add(new GraphArrow(model.allocateArrowId(), id, created.id()));
        }
        selection.clear();
        AddConnectedNodeCommand command = new AddConnectedNodeCommand(created, arrows);
        command.apply(model);

        assertEquals(EditorHints.IDLE, EditorHints.hintFor(false, selection.size(), 0));
        assertEquals(4, model.getNodes().size());
        assertEquals(3, model.getArrows().size());
        for (long source : sourceIds) {
            assertTrue(model.arrowExists(source, created.id()), "arrow from " + source);
        }
        command.undo(model);
        assertEquals(3, model.getNodes().size());
        assertTrue(model.getArrows().isEmpty());
    }

    @Test
    @DisplayName("Multi-select hint is short enough for the toolbar")
    void testMultiSelectHintFits() {
        assertTrue(EditorHints.NODES_SELECTED.length() <= 80);
        assertEquals(EditorHints.NODES_SELECTED, EditorHints.hintFor(false, 2, 1));
        assertEquals(EditorHints.CONNECTING, EditorHints.hintFor(true, 2, 0));
    }

    // Issue #14: Editable text labels on nodes

    @Test
    @DisplayName("Issue #14: Nodes default to an empty label and keep it through move and rename copies")
    void testNodeLabelBasics() {
        assertEquals("", new GraphNode(1, 10, 20).label());
        assertEquals("", new GraphNode(1, 10, 20, null).label(), "null normalised to empty");
        GraphNode labelled = new GraphNode(1, 10, 20, "Start");
        GraphNode moved = labelled.withPosition(50, 60);
        assertEquals("Start", moved.label(), "moving must not discard the label");
        assertEquals(50.0, moved.x());
        assertEquals(20.0, labelled.withLabel("End").y());
        assertEquals("Start", labelled.label(), "records are immutable");
    }

    @Test
    @DisplayName("Issue #14: RenameNodeCommand apply/undo round-trip restores the previous label")
    void testRenameNodeCommandRoundTrip() {
        GraphModel model = new GraphModel();
        GraphNode node = new GraphNode(model.allocateNodeId(), 100, 100, "Old");
        model.addNode(node);

        RenameNodeCommand rename = new RenameNodeCommand(node, "Login");
        rename.apply(model);
        assertEquals("Login", model.findNode(node.id()).label());
        assertEquals(100.0, model.findNode(node.id()).x());
        assertEquals(1, model.getNodes().size(), "rename overwrites by id, never duplicates");

        rename.undo(model);
        assertEquals("Old", model.findNode(node.id()).label());
        rename.apply(model);
        assertEquals("Login", model.findNode(node.id()).label(), "redo");
    }

    @Test
    @DisplayName("Issue #14: Renaming works through the undo/redo stacks and clears to unlabeled")
    void testRenameThroughHistory() {
        GraphModel model = new GraphModel();
        GraphNode node = new GraphNode(model.allocateNodeId(), 5, 5);
        model.addNode(node);
        LinkedStack<EditCommand> undo = new LinkedStack<>();
        EditCommand named = new RenameNodeCommand(node, "A");
        named.apply(model);
        undo.push(named);
        EditCommand cleared = new RenameNodeCommand(model.findNode(node.id()), "");
        cleared.apply(model);
        undo.push(cleared);

        assertEquals("", model.findNode(node.id()).label());
        undo.pop().undo(model);
        assertEquals("A", model.findNode(node.id()).label());
        undo.pop().undo(model);
        assertEquals("", model.findNode(node.id()).label());
    }

    @Test
    @DisplayName("Issue #14: Moving, deleting and restoring a node keeps its label")
    void testLabelSurvivesMoveAndDelete() {
        GraphModel model = new GraphModel();
        GraphNode node = new GraphNode(model.allocateNodeId(), 100, 100, "Payment failed");
        model.addNode(node);

        MoveNodesCommand move = new MoveNodesCommand(List.of(node), 30, 40);
        move.apply(model);
        assertEquals("Payment failed", model.findNode(node.id()).label());
        move.undo(model);
        assertEquals("Payment failed", model.findNode(node.id()).label());

        DeleteNodeCommand delete = new DeleteNodeCommand(node, model.incidentArrows(node.id()));
        delete.apply(model);
        assertNull(model.findNode(node.id()));
        delete.undo(model);
        assertEquals("Payment failed", model.findNode(node.id()).label());
    }

    @Test
    @DisplayName("Issue #14: Labels survive a JSON round-trip, including quotes, backslashes and unicode")
    void testLabelJsonRoundTrip() {
        GraphModel model = new GraphModel();
        String tricky = "Say \"hi\" \\ back\nline\ttab \u00e9\u4e2d";
        model.addNode(new GraphNode(model.allocateNodeId(), 1, 2, "Start"));
        model.addNode(new GraphNode(model.allocateNodeId(), 3, 4));
        model.addNode(new GraphNode(model.allocateNodeId(), 5, 6, tricky));

        String json = GraphJsonCodec.toJson(model);
        GraphModel loaded = GraphJsonCodec.fromJson(json);

        assertEquals("Start", loaded.findNode(1).label());
        assertEquals("", loaded.findNode(2).label());
        assertEquals(tricky, loaded.findNode(3).label());
        assertFalse(json.contains("\"label\": \"\""), "empty labels are not written");
        assertEquals(json, GraphJsonCodec.toJson(loaded), "stable second round-trip");
    }

    @Test
    @DisplayName("Issue #14: Files written before labels existed still load; bad label types are rejected")
    void testLabelJsonCompatibilityAndValidation() {
        GraphModel legacy = GraphJsonCodec.fromJson(
                "{\"nodes\": [{\"id\": 1, \"x\": 0, \"y\": 0}], \"arrows\": []}");
        assertEquals("", legacy.findNode(1).label());

        GraphModel unicode = GraphJsonCodec.fromJson(
                "{\"nodes\": [{\"id\": 1, \"x\": 0, \"y\": 0, \"label\": \"\\u0041\\/\"}], \"arrows\": []}");
        assertEquals("A/", unicode.findNode(1).label());

        assertThrows(IllegalArgumentException.class, () -> GraphJsonCodec.fromJson(
                "{\"nodes\": [{\"id\": 1, \"x\": 0, \"y\": 0, \"label\": 5}], \"arrows\": []}"),
                "numeric label");
        assertThrows(IllegalArgumentException.class, () -> GraphJsonCodec.fromJson(
                "{\"nodes\": [{\"id\": 1, \"x\": 0, \"y\": 0, \"label\": \"a\\qb\"}], \"arrows\": []}"),
                "unknown escape");
        assertThrows(IllegalArgumentException.class, () -> GraphJsonCodec.fromJson(
                "{\"nodes\": [{\"id\": 1, \"x\": 0, \"y\": 0, \"label\": \"abc}], \"arrows\": []}"),
                "unterminated label");
        assertThrows(IllegalArgumentException.class, () -> GraphJsonCodec.fromJson(
                "{\"nodes\": [{\"id\": 1, \"x\": 0, \"y\": 0, \"label\": \"\\u12\"}], \"arrows\": []}"),
                "truncated unicode escape");
    }

    @Test
    @DisplayName("Issue #14: Idle hint mentions double-click labelling and fits the toolbar")
    void testIdleHintMentionsLabel() {
        assertTrue(EditorHints.IDLE.contains("Double-click"));
        assertTrue(EditorHints.IDLE.length() <= 80);
    }

    @Test
    @DisplayName("Issue #15: GraphSvgExporter exports empty model with valid SVG root and no shapes")
    void testSvgExportEmptyModel() {
        GraphModel model = new GraphModel();
        String svg = GraphSvgExporter.toSvg(model);

        assertTrue(svg.startsWith("<svg width=\"800\" height=\"600\" xmlns=\"http://www.w3.org/2000/svg\">"));
        assertTrue(svg.endsWith("</svg>\n"));
        assertFalse(svg.contains("<circle"));
        assertFalse(svg.contains("<line"));
        assertFalse(svg.contains("<polygon"));
        assertFalse(svg.contains("<text"));
    }

    @Test
    @DisplayName("Issue #15: GraphSvgExporter exports nodes, arrows, arrowheads and labels matching canvas geometry")
    void testSvgExportSmallModel() {
        GraphModel model = new GraphModel();
        long id1 = model.allocateNodeId();
        long id2 = model.allocateNodeId();
        model.addNode(new GraphNode(id1, 100.0, 100.0, "Start"));
        model.addNode(new GraphNode(id2, 300.0, 100.0));
        model.addArrow(new GraphArrow(model.allocateArrowId(), id1, id2, false));

        String svg = GraphSvgExporter.toSvg(model);

        // Expected counts
        assertEquals(2, countOccurrences(svg, "<circle"));
        assertEquals(1, countOccurrences(svg, "<line"));
        assertEquals(1, countOccurrences(svg, "<polygon"));
        assertEquals(1, countOccurrences(svg, "<text"));

        // Trimmed connector line starts at 120.0 and ends at 280.0
        assertTrue(svg.contains("<line x1=\"120.0\" y1=\"100.0\" x2=\"280.0\" y2=\"100.0\""));
        assertTrue(svg.contains("stroke=\"#334155\" stroke-width=\"2.5\""));

        // Arrowhead polygon at (280.0, 100.0)
        assertTrue(svg.contains("<polygon points=\"280.0,100.0 "));
        assertTrue(svg.contains("fill=\"#dc2626\""));

        // Circle nodes
        assertTrue(svg.contains("<circle cx=\"100.0\" cy=\"100.0\" r=\"20.0\" fill=\"#3b82f6\" stroke=\"#1d4ed8\" stroke-width=\"2.0\"/>"));
        assertTrue(svg.contains("<circle cx=\"300.0\" cy=\"100.0\" r=\"20.0\" fill=\"#3b82f6\" stroke=\"#1d4ed8\" stroke-width=\"2.0\"/>"));

        // Label text element
        assertTrue(svg.contains("<text x=\"100.0\" y=\"100.0\" text-anchor=\"middle\" dominant-baseline=\"central\" fill=\"#ffffff\" font-size=\"11\" font-family=\"sans-serif\">Start</text>"));
    }

    @Test
    @DisplayName("Issue #15: Bidirectional arrows export two arrowhead polygons")
    void testSvgExportBidirectionalArrow() {
        GraphModel model = new GraphModel();
        long id1 = model.allocateNodeId();
        long id2 = model.allocateNodeId();
        model.addNode(new GraphNode(id1, 100.0, 100.0));
        model.addNode(new GraphNode(id2, 300.0, 100.0));
        model.addArrow(new GraphArrow(model.allocateArrowId(), id1, id2, true));

        String svg = GraphSvgExporter.toSvg(model);

        assertEquals(1, countOccurrences(svg, "<line"));
        assertEquals(2, countOccurrences(svg, "<polygon"), "bidirectional arrow has arrowheads at both ends");
        assertTrue(svg.contains("<polygon points=\"280.0,100.0 "));
        assertTrue(svg.contains("<polygon points=\"120.0,100.0 "));
    }

    @Test
    @DisplayName("Issue #15: Node labels in SVG properly escape special XML characters")
    void testSvgExportLabelXmlEscaping() {
        GraphModel model = new GraphModel();
        model.addNode(new GraphNode(model.allocateNodeId(), 200.0, 200.0, "<A & B > \" '"));

        String svg = GraphSvgExporter.toSvg(model);

        assertTrue(svg.contains("&lt;A &amp; B &gt; &quot; &apos;"));
        assertFalse(svg.contains("<A & B >"));
    }

    @Test
    @DisplayName("Issue #15: GraphSvgExporter throws NullPointerException on null model")
    void testSvgExportNullModelThrows() {
        assertThrows(NullPointerException.class, () -> GraphSvgExporter.toSvg(null));
    }

    private static int countOccurrences(String text, String substring) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(substring, idx)) != -1) {
            count++;
            idx += substring.length();
        }
        return count;
    }
}
