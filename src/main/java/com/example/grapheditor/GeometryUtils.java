package com.example.grapheditor;

/**
 * Pure geometry helpers shared by rendering and hit testing, so both agree
 * on where a node or arrow actually is on screen.
 */
public final class GeometryUtils {

    private GeometryUtils() {
    }

    public static double distanceSquared(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        return dx * dx + dy * dy;
    }

    /**
     * Angle in radians from (x1, y1) to (x2, y2), using atan2 so every quadrant
     * (including purely horizontal/vertical directions) resolves correctly.
     */
    public static double calculateAngle(double x1, double y1, double x2, double y2) {
        return Math.atan2(y2 - y1, x2 - x1);
    }

    /**
     * The visible connector segment between two circles of the given radius,
     * trimmed so it starts and ends on the circle boundaries rather than the centers.
     *
     * @return {startX, startY, endX, endY, angle}
     */
    public static double[] trimmedSegment(double sourceX, double sourceY,
                                           double targetX, double targetY, double radius) {
        double angle = calculateAngle(sourceX, sourceY, targetX, targetY);
        double startX = sourceX + radius * Math.cos(angle);
        double startY = sourceY + radius * Math.sin(angle);
        double endX = targetX - radius * Math.cos(angle);
        double endY = targetY - radius * Math.sin(angle);
        return new double[]{startX, startY, endX, endY, angle};
    }

    /**
     * Linear interpolation from {@code a} to {@code b} by fraction {@code t}.
     * Applied once per animation frame it yields exponential easing, which is how
     * the drag-pull offsets both approach the cursor and spring back to rest.
     */
    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /**
     * The vector (dx, dy) shortened to {@code maxMagnitude} if it is longer than that,
     * preserving direction. Zero-length input is returned unchanged.
     *
     * @return {dx, dy}
     */
    public static double[] clampMagnitude(double dx, double dy, double maxMagnitude) {
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= maxMagnitude * maxMagnitude || lengthSquared < 1e-12) {
            return new double[]{dx, dy};
        }
        double scale = maxMagnitude / Math.sqrt(lengthSquared);
        return new double[]{dx * scale, dy * scale};
    }

    /**
     * Shortest distance from point P to the finite segment A-B.
     */
    public static double pointToSegmentDistance(double px, double py,
                                                 double ax, double ay, double bx, double by) {
        double vx = bx - ax;
        double vy = by - ay;
        double lengthSquared = vx * vx + vy * vy;
        double t = lengthSquared < 1e-9 ? 0.0 : ((px - ax) * vx + (py - ay) * vy) / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));
        double closestX = ax + t * vx;
        double closestY = ay + t * vy;
        double dx = px - closestX;
        double dy = py - closestY;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
