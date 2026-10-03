package com.example.grapheditor;

public record GraphArrow(long id, long sourceId, long targetId, boolean bidirectional) {

    public GraphArrow(long id, long sourceId, long targetId) {
        this(id, sourceId, targetId, false);
    }
}
