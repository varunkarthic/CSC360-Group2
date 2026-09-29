package com.example.grapheditor;

/**
 * Picks the short instruction line shown in the top bar for the current editor
 * state. Kept free of JavaFX so the wording rules are unit-testable.
 */
public final class EditorHints {

    public static final String IDLE =
            "Right-click: add node | Drag node to node: connect | Drag: move | Click: delete";
    public static final String NODE_SELECTED =
            "Node selected: right-click empty space to add a linked node | Esc: cancel";
    public static final String MULTI_SELECTED =
            "Multi-selected: drag to move together | Shift+click: add/remove | Esc: clear";
    public static final String CONNECTING =
            "Release on a node to connect | Release on empty space to move";

    private EditorHints() {
    }

    /**
     * @param connectDragActive a left-drag that started on a node is in progress
     * @param nodeSelected      a single (orange) node is selected
     * @param multiSelectedCount how many nodes are multi-selected (purple)
     */
    public static String hintFor(boolean connectDragActive, boolean nodeSelected, int multiSelectedCount) {
        if (connectDragActive) {
            return CONNECTING;
        }
        if (nodeSelected) {
            return NODE_SELECTED;
        }
        if (multiSelectedCount > 0) {
            return MULTI_SELECTED;
        }
        return IDLE;
    }
}
