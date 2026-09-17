package com.example.grapheditor;

import java.util.List;

/**
 * Deletes a node and every arrow incident on it in one reversible edit
 * (stationary primary click on that node).
 */
public class DeleteNodeCommand implements EditCommand {

    private final GraphNode node;
    private final List<GraphArrow> incidentArrows;

    public DeleteNodeCommand(GraphNode node, List<GraphArrow> incidentArrows) {
        this.node = node;
        this.incidentArrows = incidentArrows;
    }

    @Override
    public void apply(GraphModel model) {
        for (GraphArrow arrow : incidentArrows) {
            model.removeArrow(arrow.id());
        }
        model.removeNode(node.id());
    }

    @Override
    public void undo(GraphModel model) {
        model.addNode(node);
        for (GraphArrow arrow : incidentArrows) {
            model.addArrow(arrow);
        }
    }
}
