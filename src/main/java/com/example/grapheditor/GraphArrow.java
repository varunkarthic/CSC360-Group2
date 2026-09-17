package com.example.grapheditor;

/**
 * Immutable directed arrow between two node IDs.
 */
public record GraphArrow(long id, long sourceId, long targetId) {
}
