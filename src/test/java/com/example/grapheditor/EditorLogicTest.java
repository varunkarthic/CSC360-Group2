package com.example.grapheditor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
