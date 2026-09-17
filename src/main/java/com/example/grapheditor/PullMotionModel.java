package com.example.grapheditor;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-node visual displacement driving the connect-drag "pull" feedback: the node a
 * drag is hovering near eases toward the cursor, and springs back once it stops being
 * the pulled node.
 *
 * <p>These offsets are <strong>cosmetic only</strong>. They are never written back to
 * {@link GraphModel}, and hit testing always uses real model coordinates, so the
 * animation cannot change which node or arrow a gesture resolves to.</p>
 *
 * <p>Motion comes from applying {@link GeometryUtils#lerp} once per frame toward a
 * target offset: the clamped vector to the cursor for the pulled node, and zero for
 * everything else. That single rule produces both the tug and the spring-back.</p>
 */
final class PullMotionModel {

    /**
     * How close the cursor must come to a node for it to be tugged. Wider than
     * {@link EditorApplication#NODE_RADIUS} so the pull begins before the cursor
     * actually reaches the node.
     */
    static final double PULL_RADIUS = 90.0;

    /** Ceiling on how far a node may be displaced from its true position. */
    static final double MAX_PULL_OFFSET = 14.0;

    /** Fraction of the remaining distance covered each frame. */
    static final double EASING_FACTOR = 0.35;

    /** Below this displacement a settled node is dropped and treated as at rest. */
    static final double SETTLED_EPSILON = 0.05;

    private static final double[] AT_REST = {0.0, 0.0};

    /** Mutable {dx, dy} per node id; absent means the node is at its true position. */
    private final Map<Long, double[]> offsets = new LinkedHashMap<>();

    private Long pulledNodeId;

    /**
     * Marks which node is being tugged, or null for none. The previously pulled node
     * keeps its offset and eases back to zero over subsequent frames.
     */
    void setPulledNodeId(Long nodeId) {
        this.pulledNodeId = nodeId;
        if (nodeId != null) {
            offsets.computeIfAbsent(nodeId, id -> new double[]{0.0, 0.0});
        }
    }

    Long getPulledNodeId() {
        return pulledNodeId;
    }

    /**
     * Advances every tracked offset one frame toward its target and forgets those that
     * have settled back at rest.
     *
     * @param model    source of true node positions
     * @param cursorX  current drag cursor x
     * @param cursorY  current drag cursor y
     */
    void tick(GraphModel model, double cursorX, double cursorY) {
        Iterator<Map.Entry<Long, double[]>> entries = offsets.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Long, double[]> entry = entries.next();
            long nodeId = entry.getKey();
            double[] offset = entry.getValue();
            double[] target = targetOffset(model, nodeId, cursorX, cursorY);

            offset[0] = GeometryUtils.lerp(offset[0], target[0], EASING_FACTOR);
            offset[1] = GeometryUtils.lerp(offset[1], target[1], EASING_FACTOR);

            boolean settled = Math.abs(offset[0]) < SETTLED_EPSILON
                    && Math.abs(offset[1]) < SETTLED_EPSILON;
            if (settled && !isPulled(nodeId)) {
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
            // Node deleted while tracked: let its offset decay rather than chase a ghost.
            return AT_REST;
        }
        return GeometryUtils.clampMagnitude(cursorX - node.x(), cursorY - node.y(), MAX_PULL_OFFSET);
    }

    private boolean isPulled(long nodeId) {
        return pulledNodeId != null && pulledNodeId == nodeId;
    }

    /**
     * The node's on-screen position: its true position plus any current displacement.
     *
     * @return {x, y}
     */
    double[] effectivePosition(GraphNode node) {
        double[] offset = offsets.get(node.id());
        if (offset == null) {
            return new double[]{node.x(), node.y()};
        }
        return new double[]{node.x() + offset[0], node.y() + offset[1]};
    }

    /**
     * True once nothing is being pulled and every node has sprung back, meaning the
     * animation driver can stop until the next drag.
     */
    boolean isAtRest() {
        return pulledNodeId == null && offsets.isEmpty();
    }

    /** Drops all motion immediately, for a cancelled gesture or a freshly loaded graph. */
    void reset() {
        pulledNodeId = null;
        offsets.clear();
    }
}
