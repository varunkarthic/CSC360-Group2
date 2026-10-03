package com.example.grapheditor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// Nodes selected for auto-connect: each gets an arrow to the next node created by right-click.
public final class SelectionState {

    private final Set<Long> ids = new LinkedHashSet<>();

    // Right-click: select only this node, or deselect it if it is the only one selected.
    public void toggleSingle(long id) {
        boolean onlySelected = ids.size() == 1 && ids.contains(id);
        ids.clear();
        if (!onlySelected) {
            ids.add(id);
        }
    }

    // Shift + right-click: add or remove this node.
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

    public List<Long> ids() {
        return new ArrayList<>(ids);
    }

    public void clear() {
        ids.clear();
    }
}
