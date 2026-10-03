package com.example.grapheditor;

// One edit that can be applied and undone. Used for the undo/redo stacks.
public interface EditCommand {
    void apply(GraphModel model);

    void undo(GraphModel model);
}
