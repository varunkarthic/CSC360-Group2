package com.example.grapheditor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The orange auto-connect selection: the nodes that will each get an arrow to
 * the next node created by a right-click on free space. Plain JavaFX-free
 * logic so the toggle rules are unit-testable.
 */
public final class SelectionState {

    private final Set<Long> ids = new LinkedHashSet<>();

    /** Plain right-click: select only this node, or deselect it if it is the sole selection. */
    public void toggleSingle(long id) {
        boolean onlySelected = ids.size() == 1 && ids.contains(id);
        ids.clear();
        if (!onlySelected) {
            ids.add(id);
        }
    }

    /** Shift + right-click: add the node to the selection, or remove it if already in it. */
    public void toggleAdditive(long id) {
        if (!ids.remove(id)) {
            ids.add(id);
        }
    }

    public boolean contains(long id) {
        return ids.contains(id);
    }

    public int size() {
        return ids.size();
    }

    public boolean isEmpty() {
        return ids.isEmpty();
    }

    /** Selected ids in selection order. */
    public List<Long> ids() {
        return new ArrayList<>(ids);
    }

    public void clear() {
        ids.clear();
    }
}
