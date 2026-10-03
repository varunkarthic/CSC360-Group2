package com.example.grapheditor;

public class AddNodeCommand implements EditCommand {

    private final GraphNode node;

    public AddNodeCommand(GraphNode node) {
        this.node = node;
    }

    @Override
    public void apply(GraphModel model) {
        model.addNode(node);
    }

    @Override
    public void undo(GraphModel model) {
        model.removeNode(node.id());
    }
}
