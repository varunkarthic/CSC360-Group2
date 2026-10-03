package com.example.grapheditor;

import java.util.Objects;

// An empty label means the node has no label.
public record GraphNode(long id, double x, double y, String label) {

    public GraphNode {
        label = Objects.requireNonNullElse(label, "");
    }

    public GraphNode(long id, double x, double y) {
        this(id, x, y, "");
    }

    public GraphNode withPosition(double newX, double newY) {
        return new GraphNode(id, newX, newY, label);
    }

    public GraphNode withLabel(String newLabel) {
        return new GraphNode(id, x, y, newLabel);
    }
}
