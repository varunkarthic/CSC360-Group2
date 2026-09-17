package com.example.grapheditor;

/**
 * A reversible graph edit stored on the undo/redo stacks.
 */
public interface EditCommand {
    void apply(GraphModel model);

    void undo(GraphModel model);
}
