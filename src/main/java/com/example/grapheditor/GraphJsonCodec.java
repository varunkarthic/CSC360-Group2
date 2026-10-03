package com.example.grapheditor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

// Saves a graph to JSON and loads it back. Format:
// {"nodes": [{"id": 1, "x": 120.0, "y": 80.0, "label": "Start"}],
//  "arrows": [{"id": 1, "sourceId": 1, "targetId": 2, "bidirectional": false}]}
// "label" and "bidirectional" are optional so older files still load.
// The format is small, so the JSON is read and written by hand (no library).
// Bad input throws IllegalArgumentException instead of loading a partial graph.
public final class GraphJsonCodec {

    private static final String NODES_KEY = "nodes";
    private static final String ARROWS_KEY = "arrows";

    private GraphJsonCodec() {
    }

    public static String toJson(GraphModel model) {
        Objects.requireNonNull(model, "model");

        StringBuilder json = new StringBuilder("{\n");
        appendArray(json, NODES_KEY, model.getNodes(), (out, node) -> {
            out.append("{\"id\": ").append(node.id())
                .append(", \"x\": ").append(formatCoordinate(node.x()))
                .append(", \"y\": ").append(formatCoordinate(node.y()));
            if (!node.label().isEmpty()) {
                out.append(", \"label\": ").append(quote(node.label()));
            }
            out.append('}');
        });
        json.append(",\n");
        appendArray(json, ARROWS_KEY, model.getArrows(), (out, arrow) -> out
                .append("{\"id\": ").append(arrow.id())
                .append(", \"sourceId\": ").append(arrow.sourceId())
                .append(", \"targetId\": ").append(arrow.targetId())
                .append(", \"bidirectional\": ").append(arrow.bidirectional())
                .append('}'));
        return json.append("\n}\n").toString();
    }

    private static <T> void appendArray(StringBuilder json, String name, List<T> items,
                                        BiConsumer<StringBuilder, T> writeItem) {
        json.append("  \"").append(name).append("\": [");
        if (items.isEmpty()) {
            json.append(']');
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            json.append(i == 0 ? "\n    " : ",\n    ");
            writeItem.accept(json, items.get(i));
        }
        json.append("\n  ]");
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    // Double.toString always uses '.', whatever the machine's locale.
    private static String formatCoordinate(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Cannot write non-finite coordinate: " + value);
        }
        return Double.toString(value);
    }

    // Throws IllegalArgumentException for bad JSON, duplicate ids, or arrows pointing to missing nodes.
    public static GraphModel fromJson(String json) {
        Objects.requireNonNull(json, "json");

        Map<String, List<Map<String, Object>>> document = readDocument(new JsonScanner(json));
        for (String key : document.keySet()) {
            if (!NODES_KEY.equals(key) && !ARROWS_KEY.equals(key)) {
                throw new IllegalArgumentException("Unexpected top-level key \"" + key + "\"");
            }
        }
        List<Map<String, Object>> nodeObjects = requireArray(document, NODES_KEY);
        List<Map<String, Object>> arrowObjects = requireArray(document, ARROWS_KEY);

        List<GraphNode> nodes = new ArrayList<>(nodeObjects.size());
        Set<Long> nodeIds = new HashSet<>();
        for (Map<String, Object> fields : nodeObjects) {
            long id = requireId(fields, "id", NODES_KEY);
            if (!nodeIds.add(id)) {
                throw new IllegalArgumentException("Duplicate node id " + id);
            }
            nodes.add(new GraphNode(id,
                    requireNumber(fields, "x", NODES_KEY),
                    requireNumber(fields, "y", NODES_KEY),
                    optionalString(fields, "label", NODES_KEY)));
        }

        List<GraphArrow> arrows = new ArrayList<>(arrowObjects.size());
        Set<Long> arrowIds = new HashSet<>();
        for (Map<String, Object> fields : arrowObjects) {
            long id = requireId(fields, "id", ARROWS_KEY);
            if (!arrowIds.add(id)) {
                throw new IllegalArgumentException("Duplicate arrow id " + id);
            }
            long sourceId = requireId(fields, "sourceId", ARROWS_KEY);
            long targetId = requireId(fields, "targetId", ARROWS_KEY);
            if (!nodeIds.contains(sourceId)) {
                throw new IllegalArgumentException(
                        "Arrow " + id + " references unknown source node " + sourceId);
            }
            if (!nodeIds.contains(targetId)) {
                throw new IllegalArgumentException(
                        "Arrow " + id + " references unknown target node " + targetId);
            }
            arrows.add(new GraphArrow(id, sourceId, targetId,
                    optionalBoolean(fields, "bidirectional", ARROWS_KEY)));
        }

        GraphModel model = new GraphModel();
        model.loadFrom(nodes, arrows);
        return model;
    }

    private static Map<String, List<Map<String, Object>>> readDocument(JsonScanner scanner) {
        Map<String, List<Map<String, Object>>> document = new LinkedHashMap<>();
        scanner.expect('{');
        if (!scanner.tryConsume('}')) {
            do {
                String key = scanner.readString();
                scanner.expect(':');
                document.put(key, readObjectArray(scanner));
            } while (scanner.tryConsume(','));
            scanner.expect('}');
        }
        scanner.expectEndOfInput();
        return document;
    }

    private static List<Map<String, Object>> readObjectArray(JsonScanner scanner) {
        List<Map<String, Object>> objects = new ArrayList<>();
        scanner.expect('[');
        if (scanner.tryConsume(']')) {
            return objects;
        }
        do {
            objects.add(readFlatObject(scanner));
        } while (scanner.tryConsume(','));
        scanner.expect(']');
        return objects;
    }

    private static Map<String, Object> readFlatObject(JsonScanner scanner) {
        Map<String, Object> fields = new LinkedHashMap<>();
        scanner.expect('{');
        if (scanner.tryConsume('}')) {
            return fields;
        }
        do {
            String key = scanner.readString();
            scanner.expect(':');
            fields.put(key, scanner.readValue());
        } while (scanner.tryConsume(','));
        scanner.expect('}');
        return fields;
    }

    private static List<Map<String, Object>> requireArray(
            Map<String, List<Map<String, Object>>> document, String key) {
        List<Map<String, Object>> array = document.get(key);
        if (array == null) {
            throw new IllegalArgumentException("Missing \"" + key + "\" array");
        }
        return array;
    }

    private static double requireNumber(Map<String, Object> fields, String key, String owner) {
        Object value = fields.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing \"" + key + "\" in " + owner + " entry");
        }
        if (!(value instanceof Double number)) {
            throw new IllegalArgumentException(
                    "Expected a number for \"" + key + "\" in " + owner + " entry, got " + value);
        }
        if (!Double.isFinite(number)) {
            throw new IllegalArgumentException(
                    "Non-finite \"" + key + "\" in " + owner + " entry: " + number);
        }
        return number;
    }

    private static boolean optionalBoolean(Map<String, Object> fields, String key, String owner) {
        Object value = fields.get(key);
        if (value == null) {
            return false;
        }
        if (!(value instanceof Boolean flag)) {
            throw new IllegalArgumentException(
                    "Expected true or false for \"" + key + "\" in " + owner + " entry, got " + value);
        }
        return flag;
    }

    private static String optionalString(Map<String, Object> fields, String key, String owner) {
        Object value = fields.get(key);
        if (value == null) {
            return "";
        }
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(
                    "Expected a string for \"" + key + "\" in " + owner + " entry, got " + value);
        }
        return text;
    }

    // Ids are whole numbers starting from 1.
    private static long requireId(Map<String, Object> fields, String key, String owner) {
        double value = requireNumber(fields, key, owner);
        long id = (long) value;
        if (id != value || id < 1) {
            throw new IllegalArgumentException(
                    "Invalid \"" + key + "\" in " + owner + " entry: " + value
                            + " (expected a whole number of at least 1)");
        }
        return id;
    }

    // Reads JSON text one token at a time. Only supports what this format uses.
    private static final class JsonScanner {

        private final String source;
        private int index;

        JsonScanner(String source) {
            this.source = source;
        }

        void expect(char expected) {
            if (!tryConsume(expected)) {
                throw error("expected '" + expected + "'");
            }
        }

        boolean tryConsume(char expected) {
            skipWhitespace();
            if (index < source.length() && source.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        void expectEndOfInput() {
            skipWhitespace();
            if (index < source.length()) {
                throw error("expected end of input");
            }
        }

        String readString() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (index < source.length() && source.charAt(index) != '"') {
                char c = source.charAt(index++);
                if (c != '\\') {
                    value.append(c);
                    continue;
                }
                if (index >= source.length()) {
                    break;
                }
                char escaped = source.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> value.append(escaped);
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'u' -> value.append(readUnicodeEscape());
                    default -> throw error("unknown string escape \\" + escaped);
                }
            }
            if (index >= source.length()) {
                throw error("unterminated string");
            }
            index++;
            return value.toString();
        }

        private char readUnicodeEscape() {
            if (index + 4 > source.length()) {
                throw error("truncated \\u escape");
            }
            try {
                char c = (char) Integer.parseInt(source.substring(index, index + 4), 16);
                index += 4;
                return c;
            } catch (NumberFormatException e) {
                throw error("malformed \\u escape");
            }
        }

        Object readValue() {
            skipWhitespace();
            if (index < source.length() && source.charAt(index) == '"') {
                return readString();
            }
            if (tryConsumeLiteral("true")) {
                return Boolean.TRUE;
            }
            if (tryConsumeLiteral("false")) {
                return Boolean.FALSE;
            }
            return readNumber();
        }

        private boolean tryConsumeLiteral(String literal) {
            if (source.startsWith(literal, index)) {
                index += literal.length();
                return true;
            }
            return false;
        }

        double readNumber() {
            skipWhitespace();
            int start = index;
            if (index < source.length() && (source.charAt(index) == '-' || source.charAt(index) == '+')) {
                index++;
            }
            while (index < source.length() && isNumberBodyChar(source.charAt(index))) {
                index++;
            }
            String text = source.substring(start, index);
            if (text.isEmpty()) {
                throw error("expected a number");
            }
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException e) {
                throw error("malformed number \"" + text + "\"");
            }
        }

        private static boolean isNumberBodyChar(char c) {
            return (c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '-' || c == '+';
        }

        private void skipWhitespace() {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) {
                index++;
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(
                    "Malformed graph JSON at offset " + index + ": " + message);
        }
    }
}
