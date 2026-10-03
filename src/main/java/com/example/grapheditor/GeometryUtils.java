package com.example.grapheditor;

public final class GeometryUtils {

    // A line between two nodes, trimmed to start and end on the circle edges.
    public record ArrowLine(double startX, double startY, double endX, double endY, double angle) {
    }

    private GeometryUtils() {
    }

    public static double distanceSquared(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        return dx * dx + dy * dy;
    }

    // Angle in radians from point 1 to point 2.
    public static double angleBetweenPoints(double x1, double y1, double x2, double y2) {
        return Math.atan2(y2 - y1, x2 - x1);
    }

    public static ArrowLine trimmedArrowLine(double sourceX, double sourceY,
                                             double targetX, double targetY, double radius) {
        double angle = angleBetweenPoints(sourceX, sourceY, targetX, targetY);
        double startX = sourceX + radius * Math.cos(angle);
        double startY = sourceY + radius * Math.sin(angle);
        double endX = targetX - radius * Math.cos(angle);
        double endY = targetY - radius * Math.sin(angle);
        return new ArrowLine(startX, startY, endX, endY, angle);
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    // Shortens the vector (dx, dy) to maxMagnitude if it is longer. Returns {dx, dy}.
    public static double[] clampMagnitude(double dx, double dy, double maxMagnitude) {
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= maxMagnitude * maxMagnitude || lengthSquared < 1e-12) {
            return new double[]{dx, dy};
        }
        double scale = maxMagnitude / Math.sqrt(lengthSquared);
        return new double[]{dx * scale, dy * scale};
    }

    // Shortest distance from point P to the segment A-B.
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
