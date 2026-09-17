package com.example.grapheditor;

/**
 * Upgrades an existing one-way arrow to a bidirectional arrow (swaps arrow record with bidirectional = true).
 */
public class UpgradeArrowCommand implements EditCommand {

    private final GraphArrow before;
    private final GraphArrow after;

    public UpgradeArrowCommand(GraphArrow before) {
        this.before = before;
        this.after = new GraphArrow(before.id(), before.sourceId(), before.targetId(), true);
    }

    public GraphArrow getBefore() {
        return before;
    }

    public GraphArrow getAfter() {
        return after;
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
