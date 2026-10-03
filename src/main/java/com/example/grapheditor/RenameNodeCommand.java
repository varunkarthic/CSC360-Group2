package com.example.grapheditor;

public class RenameNodeCommand implements EditCommand {

    private final GraphNode before;
    private final GraphNode after;

    public RenameNodeCommand(GraphNode before, String newLabel) {
        this.before = before;
        this.after = before.withLabel(newLabel);
    }

    @Override
    public void apply(GraphModel model) {
        model.addNode(after);
    }

    @Override
    public void undo(GraphModel model) {
        model.addNode(before);
    }
}
