package com.example.grapheditor;

/**
 * Connects two already-existing nodes (primary-button drag from A to B).
 */
public class AddArrowCommand implements EditCommand {

    private final GraphArrow arrow;

    public AddArrowCommand(GraphArrow arrow) {
        this.arrow = arrow;
    }

    @Override
    public void apply(GraphModel model) {
        model.addArrow(arrow);
    }

    @Override
    public void undo(GraphModel model) {
        model.removeArrow(arrow.id());
    }
}
