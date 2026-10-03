package com.example.grapheditor;

import java.util.List;

public class MoveNodesCommand implements EditCommand {

    private final List<GraphNode> before;
    private final double dx;
    private final double dy;

    public MoveNodesCommand(List<GraphNode> before, double dx, double dy) {
        this.before = List.copyOf(before);
        this.dx = dx;
        this.dy = dy;
    }

    @Override
    public void apply(GraphModel model) {
        for (GraphNode node : before) {
            model.addNode(node.withPosition(node.x() + dx, node.y() + dy));
        }
    }

    @Override
    public void undo(GraphModel model) {
        for (GraphNode node : before) {
            model.addNode(node);
        }
    }
}
