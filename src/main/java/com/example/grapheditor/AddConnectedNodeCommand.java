package com.example.grapheditor;

/**
 * Creates a new node together with the automatic arrow from the previously
 * selected node (right-click on free space while a node is selected).
 */
public class AddConnectedNodeCommand implements EditCommand {

    private final GraphNode node;
    private final GraphArrow arrow;

    public AddConnectedNodeCommand(GraphNode node, GraphArrow arrow) {
        this.node = node;
        this.arrow = arrow;
    }

    @Override
    public void apply(GraphModel model) {
        model.addNode(node);
        model.addArrow(arrow);
    }

    @Override
    public void undo(GraphModel model) {
        model.removeArrow(arrow.id());
        model.removeNode(node.id());
    }
}
