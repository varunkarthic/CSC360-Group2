package com.example.grapheditor;

/**
 * Immutable graph node: a fixed-radius circle centered at (x, y).
 */
public record GraphNode(long id, double x, double y) {
}
