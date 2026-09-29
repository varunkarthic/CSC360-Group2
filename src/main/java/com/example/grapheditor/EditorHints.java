package com.example.grapheditor;

/**
 * Picks the short instruction line shown in the top bar for the current editor
 * state. Kept free of JavaFX so the wording rules are unit-testable.
 */
public final class EditorHints {

    public static final String IDLE =
            "Right-click: add | Drag: connect or move | Double-click: label | Click: delete";
    public static final String NODE_SELECTED =
            "Node selected: right-click empty space to add a linked node | Esc: cancel";
    public static final String NODES_SELECTED =
            "Nodes selected: right-click empty space to link all to a new node | Esc: cancel";
    public static final String MULTI_SELECTED =
            "Multi-selected: drag to move together | Shift+click: add/remove | Esc: clear";
    public static final String CONNECTING =
            "Release on a node to connect | Release on empty space to move";

    private EditorHints() {
    }

    /**
     * @param connectDragActive a left-drag that started on a node is in progress
     * @param selectedCount     how many nodes are selected (orange); Shift+right-click adds more
     * @param multiSelectedCount how many nodes are multi-selected (purple)
     */
    public static String hintFor(boolean connectDragActive, int selectedCount, int multiSelectedCount) {
        if (connectDragActive) {
            return CONNECTING;
        }
        if (selectedCount > 1) {
            return NODES_SELECTED;
        }
        if (selectedCount == 1) {
            return NODE_SELECTED;
        }
        if (multiSelectedCount > 0) {
            return MULTI_SELECTED;
        }
        return IDLE;
    }
}
