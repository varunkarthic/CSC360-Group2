package com.example.grapheditor;

/**
 * Deletes exactly one arrow (stationary primary click on that arrow).
 */
public class DeleteArrowCommand implements EditCommand {

    private final GraphArrow arrow;

    public DeleteArrowCommand(GraphArrow arrow) {
        this.arrow = arrow;
    }

    @Override
    public void apply(GraphModel model) {
        model.removeArrow(arrow.id());
    }

    @Override
    public void undo(GraphModel model) {
        model.addArrow(arrow);
    }
}
