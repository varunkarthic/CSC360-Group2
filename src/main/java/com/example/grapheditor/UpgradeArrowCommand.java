package com.example.grapheditor;

// Turns a one-way arrow into a two-way arrow.
public class UpgradeArrowCommand implements EditCommand {

    private final GraphArrow before;
    private final GraphArrow after;

    public UpgradeArrowCommand(GraphArrow before) {
        this.before = before;
        this.after = new GraphArrow(before.id(), before.sourceId(), before.targetId(), true);
    }

    @Override
    public void apply(GraphModel model) {
        model.addArrow(after);
    }

    @Override
    public void undo(GraphModel model) {
        model.addArrow(before);
    }
}
