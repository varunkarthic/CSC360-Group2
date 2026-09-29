package com.example.grapheditor;

import java.util.Objects;

/**
 * Immutable graph node: a fixed-radius circle centered at (x, y) with an optional
 * text label (empty means unlabeled).
 */
public record GraphNode(long id, double x, double y, String label) {

    public GraphNode {
        label = Objects.requireNonNullElse(label, "");
    }

    /** An unlabeled node. */
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
