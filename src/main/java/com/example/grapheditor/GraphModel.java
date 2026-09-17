package com.example.grapheditor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable graph state: nodes and arrows, plus proximity/hit-test queries
 * used by both creation and deletion gestures.
 */
public class GraphModel {

    private final Map<Long, GraphNode> nodes = new LinkedHashMap<>();
    private final Map<Long, GraphArrow> arrows = new LinkedHashMap<>();

    private long nextNodeId = 1;
    private long nextArrowId = 1;

    public long allocateNodeId() {
        return nextNodeId++;
    }

    public long allocateArrowId() {
        return nextArrowId++;
    }

    public void addNode(GraphNode node) {
        nodes.put(node.id(), node);
    }

    public void removeNode(long id) {
        nodes.remove(id);
    }

    public void addArrow(GraphArrow arrow) {
        arrows.put(arrow.id(), arrow);
    }

    public void removeArrow(long id) {
        arrows.remove(id);
    }

    public GraphNode findNode(long id) {
        return nodes.get(id);
    }

    public List<GraphNode> getNodes() {
        return List.copyOf(nodes.values());
    }

    public List<GraphArrow> getArrows() {
        return List.copyOf(arrows.values());
    }

    /**
     * Every arrow with this node as source or target, in a stable order,
     * used to snapshot a node deletion for undo.
     */
    public List<GraphArrow> incidentArrows(long nodeId) {
        List<GraphArrow> result = new ArrayList<>();
        for (GraphArrow arrow : arrows.values()) {
            if (arrow.sourceId() == nodeId || arrow.targetId() == nodeId) {
                result.add(arrow);
            }
        }
        return result;
    }

    public boolean arrowExists(long sourceId, long targetId) {
        for (GraphArrow arrow : arrows.values()) {
            if (arrow.sourceId() == sourceId && arrow.targetId() == targetId) {
                return true;
            }
        }
        return false;
    }

    /**
     * A node whose body (radius R) contains point (x, y), if any.
     */
    public GraphNode hitNodeBody(double x, double y, double radius) {
        for (GraphNode node : nodes.values()) {
            if (GeometryUtils.distanceSquared(x, y, node.x(), node.y()) <= radius * radius) {
                return node;
            }
        }
        return null;
    }

    /**
     * The nearest node that a new radius-R circle at (x, y) would overlap,
     * using the 2R placement-conflict radius, or null if placement is clear.
     */
    public GraphNode nearestConflictingNode(double x, double y, double radius, double epsilon) {
        double limitSquared = (2 * radius + epsilon) * (2 * radius + epsilon);
        GraphNode nearest = null;
        double nearestDistanceSquared = Double.MAX_VALUE;
        for (GraphNode node : nodes.values()) {
            double distanceSquared = GeometryUtils.distanceSquared(x, y, node.x(), node.y());
            if (distanceSquared <= limitSquared && distanceSquared < nearestDistanceSquared) {
                nearest = node;
                nearestDistanceSquared = distanceSquared;
            }
        }
        return nearest;
    }
}
