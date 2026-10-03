package com.example.grapheditor;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

// Visual only: the node near the drag cursor is pulled toward it, then eases back.
// Offsets are never saved in GraphModel, so hit testing always uses the real positions.
final class NodePullAnimation {

    // Pull starts before the cursor reaches the node, so this is wider than the node radius.
    static final double PULL_RADIUS = 90.0;

    static final double MAX_PULL_OFFSET = 14.0;

    // Share of the remaining distance covered each frame.
    static final double EASING_FACTOR = 0.35;

    // Below this offset a node counts as back at rest.
    static final double REST_THRESHOLD = 0.05;

    private static final double[] AT_REST = {0.0, 0.0};

    // {dx, dy} per node id. A node with no entry is at its real position.
    private final Map<Long, double[]> offsets = new LinkedHashMap<>();

    private Long pulledNodeId;

    // The previously pulled node keeps its offset and eases back over the next frames.
    void setPulledNodeId(Long nodeId) {
        this.pulledNodeId = nodeId;
        if (nodeId != null) {
            offsets.computeIfAbsent(nodeId, id -> new double[]{0.0, 0.0});
        }
    }

    Long getPulledNodeId() {
        return pulledNodeId;
    }

    // Moves every offset one step toward its target. Nodes back at rest are removed.
    void tick(GraphModel model, double cursorX, double cursorY) {
        Iterator<Map.Entry<Long, double[]>> entries = offsets.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Long, double[]> entry = entries.next();
            long nodeId = entry.getKey();
            double[] offset = entry.getValue();
            double[] target = targetOffset(model, nodeId, cursorX, cursorY);

            offset[0] = GeometryUtils.lerp(offset[0], target[0], EASING_FACTOR);
            offset[1] = GeometryUtils.lerp(offset[1], target[1], EASING_FACTOR);

            boolean backAtRest = Math.abs(offset[0]) < REST_THRESHOLD
                    && Math.abs(offset[1]) < REST_THRESHOLD;
            if (backAtRest && !isPulled(nodeId)) {
                entries.remove();
            }
        }
    }

    private double[] targetOffset(GraphModel model, long nodeId, double cursorX, double cursorY) {
        if (!isPulled(nodeId)) {
            return AT_REST;
        }
        GraphNode node = model.findNode(nodeId);
        if (node == null) {
            // Node was deleted while pulled.
            return AT_REST;
        }
        return GeometryUtils.clampMagnitude(cursorX - node.x(), cursorY - node.y(), MAX_PULL_OFFSET);
    }

    private boolean isPulled(long nodeId) {
        return pulledNodeId != null && pulledNodeId == nodeId;
    }

    // Real position plus the current offset. Returns {x, y}.
    double[] effectivePosition(GraphNode node) {
        double[] offset = offsets.get(node.id());
        if (offset == null) {
            return new double[]{node.x(), node.y()};
        }
        return new double[]{node.x() + offset[0], node.y() + offset[1]};
    }

    // True when nothing is pulled and every node is back at rest.
    boolean isAtRest() {
        return pulledNodeId == null && offsets.isEmpty();
    }

    void reset() {
        pulledNodeId = null;
        offsets.clear();
    }
}
