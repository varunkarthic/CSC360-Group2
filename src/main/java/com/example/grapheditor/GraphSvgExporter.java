package com.example.grapheditor;

import java.util.Locale;
import java.util.Objects;

/**
 * Pure-function vector exporter that renders a {@link GraphModel} as a scalable
 * vector graphics (SVG) document.
 *
 * <p>Walks the model directly and mirrors the visual geometry used on the canvas
 * ({@link GeometryUtils#trimmedSegment}, arrowhead angles, node radius, and colors)
 * without depending on JavaFX, making it fast and unit-testable.</p>
 */
public final class GraphSvgExporter {

    public static final double CANVAS_WIDTH = 800.0;
    public static final double CANVAS_HEIGHT = 600.0;

    public static final double NODE_RADIUS = 20.0;

    private static final double ARROWHEAD_LENGTH = 12.0;
    private static final double ARROWHEAD_ANGLE_DEGREES = 25.0;

    private static final String NODE_FILL_COLOR = "#3b82f6";
    private static final String NODE_STROKE_COLOR = "#1d4ed8";
    private static final String CONNECTOR_COLOR = "#334155";
    private static final String ARROWHEAD_COLOR = "#dc2626";
    private static final String LABEL_COLOR = "#ffffff";

    private GraphSvgExporter() {
    }

    /**
     * Converts the given graph model to an SVG document string.
     *
     * @param model the graph model to export
     * @return the complete SVG document
     * @throws NullPointerException if model is null
     */
    public static String toSvg(GraphModel model) {
        Objects.requireNonNull(model, "model");

        StringBuilder svg = new StringBuilder("<svg width=\"800\" height=\"600\" xmlns=\"http://www.w3.org/2000/svg\">\n");

        for (GraphArrow arrow : model.getArrows()) {
            GraphNode source = model.findNode(arrow.sourceId());
            GraphNode target = model.findNode(arrow.targetId());
            if (source == null || target == null) {
                continue;
            }

            double[] segment = GeometryUtils.trimmedSegment(
                    source.x(), source.y(), target.x(), target.y(), NODE_RADIUS);
            double startX = segment[0];
            double startY = segment[1];
            double tipX = segment[2];
            double tipY = segment[3];
            double angle = segment[4];

            svg.append("  <line x1=\"").append(format(startX))
               .append("\" y1=\"").append(format(startY))
               .append("\" x2=\"").append(format(tipX))
               .append("\" y2=\"").append(format(tipY))
               .append("\" stroke=\"").append(CONNECTOR_COLOR)
               .append("\" stroke-width=\"2.5\"/>\n");

            appendArrowhead(svg, tipX, tipY, angle);
            if (arrow.bidirectional()) {
                appendArrowhead(svg, startX, startY, angle + Math.PI);
            }
        }

        for (GraphNode node : model.getNodes()) {
            svg.append("  <circle cx=\"").append(format(node.x()))
               .append("\" cy=\"").append(format(node.y()))
               .append("\" r=\"").append(format(NODE_RADIUS))
               .append("\" fill=\"").append(NODE_FILL_COLOR)
               .append("\" stroke=\"").append(NODE_STROKE_COLOR)
               .append("\" stroke-width=\"2.0\"/>\n");

            if (!node.label().isEmpty()) {
                svg.append("  <text x=\"").append(format(node.x()))
                   .append("\" y=\"").append(format(node.y()))
                   .append("\" text-anchor=\"middle\" dominant-baseline=\"central\" fill=\"")
                   .append(LABEL_COLOR).append("\" font-size=\"11\" font-family=\"sans-serif\">")
                   .append(escapeXml(node.label())).append("</text>\n");
            }
        }

        svg.append("</svg>\n");
        return svg.toString();
    }

    private static void appendArrowhead(StringBuilder svg, double tipX, double tipY, double angle) {
        double wingAngle = Math.toRadians(ARROWHEAD_ANGLE_DEGREES);
        double baseX1 = tipX - ARROWHEAD_LENGTH * Math.cos(angle + wingAngle);
        double baseY1 = tipY - ARROWHEAD_LENGTH * Math.sin(angle + wingAngle);
        double baseX2 = tipX - ARROWHEAD_LENGTH * Math.cos(angle - wingAngle);
        double baseY2 = tipY - ARROWHEAD_LENGTH * Math.sin(angle - wingAngle);

        svg.append("  <polygon points=\"")
           .append(format(tipX)).append(',').append(format(tipY)).append(' ')
           .append(format(baseX1)).append(',').append(format(baseY1)).append(' ')
           .append(format(baseX2)).append(',').append(format(baseY2))
           .append("\" fill=\"").append(ARROWHEAD_COLOR).append("\"/>\n");
    }

    private static String format(double value) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        double rounded = Math.round(value * 100.0) / 100.0;
        if (rounded == (long) rounded) {
            return String.format(Locale.ROOT, "%.1f", rounded);
        }
        return String.format(Locale.ROOT, "%s", rounded);
    }

    private static String escapeXml(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
