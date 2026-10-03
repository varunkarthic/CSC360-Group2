package com.example.grapheditor;

import java.util.List;

// Adds a node and the arrows to it from the selected nodes as one undo step.
public class AddConnectedNodeCommand implements EditCommand {

    private final GraphNode node;
    private final List<GraphArrow> arrows;

    public AddConnectedNodeCommand(GraphNode node, GraphArrow arrow) {
        this(node, List.of(arrow));
    }

    public AddConnectedNodeCommand(GraphNode node, List<GraphArrow> arrows) {
        this.node = node;
        this.arrows = List.copyOf(arrows);
    }

    @Override
    public void apply(GraphModel model) {
        model.addNode(node);
        for (GraphArrow arrow : arrows) {
            model.addArrow(arrow);
        }
    }

    @Override
    public void undo(GraphModel model) {
        for (GraphArrow arrow : arrows) {
            model.removeArrow(arrow.id());
        }
        model.removeNode(node.id());
    }
}
