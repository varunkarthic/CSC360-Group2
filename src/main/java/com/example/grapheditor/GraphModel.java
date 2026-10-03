package com.example.grapheditor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    // Replaces the whole graph. Ids restart above the highest loaded id so new ones cannot collide.
    public void loadFrom(List<GraphNode> loadedNodes, List<GraphArrow> loadedArrows) {
        nodes.clear();
        arrows.clear();
        for (GraphNode node : loadedNodes) {
            nodes.put(node.id(), node);
        }
        for (GraphArrow arrow : loadedArrows) {
            arrows.put(arrow.id(), arrow);
        }

        nextNodeId = 1;
        for (GraphNode node : nodes.values()) {
            nextNodeId = Math.max(nextNodeId, node.id() + 1);
        }
        nextArrowId = 1;
        for (GraphArrow arrow : arrows.values()) {
            nextArrowId = Math.max(nextArrowId, arrow.id() + 1);
        }
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

    public List<GraphArrow> incidentArrows(long nodeId) {
        List<GraphArrow> result = new ArrayList<>();
        for (GraphArrow arrow : arrows.values()) {
            if (arrow.sourceId() == nodeId || arrow.targetId() == nodeId) {
                result.add(arrow);
            }
        }
        return result;
    }

    public GraphArrow findArrow(long sourceId, long targetId) {
        for (GraphArrow arrow : arrows.values()) {
            if (arrow.sourceId() == sourceId && arrow.targetId() == targetId) {
                return arrow;
            }
        }
        return null;
    }

    public boolean arrowExists(long sourceId, long targetId) {
        return findArrow(sourceId, targetId) != null;
    }

    // A node whose circle contains the point.
    public GraphNode hitNodeBody(double x, double y, double radius) {
        for (GraphNode node : nodes.values()) {
            if (GeometryUtils.distanceSquared(x, y, node.x(), node.y()) <= radius * radius) {
                return node;
            }
        }
        return null;
    }

    // Nearest node within radius, skipping excludedId (the node being dragged).
    public GraphNode nearestNodeWithin(double x, double y, double radius, long excludedId) {
        double limitSquared = radius * radius;
        GraphNode nearest = null;
        double nearestDistanceSquared = Double.MAX_VALUE;
        for (GraphNode node : nodes.values()) {
            if (node.id() == excludedId) {
                continue;
            }
            double distanceSquared = GeometryUtils.distanceSquared(x, y, node.x(), node.y());
            if (distanceSquared <= limitSquared && distanceSquared < nearestDistanceSquared) {
                nearest = node;
                nearestDistanceSquared = distanceSquared;
            }
        }
        return nearest;
    }

    // Nearest node that a new node placed at (x, y) would overlap, or null if the spot is free.
    public GraphNode nearestOverlappingNode(double x, double y, double radius, double epsilon) {
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
