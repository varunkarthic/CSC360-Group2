package com.example.grapheditor;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * JavaFX node/arrow graph editor.
 *
 * Right-click creates a node, or selects/deselects an existing one; creating a
 * node while another is selected connects them automatically. A primary-button
 * drag between two nodes creates a directed arrow, with a dashed preview line
 * following the cursor and the nearest candidate target easing toward it. A
 * stationary primary click deletes the node or arrow under the pointer. Every
 * edit is undoable/redoable through two linked-list history stacks, and a graph
 * can be saved to and reloaded from a JSON file.
 */
public class EditorApplication extends Application {

    public static final double CANVAS_WIDTH = 800.0;
    public static final double CANVAS_HEIGHT = 600.0;

    public static final double NODE_RADIUS = 20.0;
    private static final double DRAG_THRESHOLD = 5.0;
    private static final double ARROW_HIT_TOLERANCE = 6.0;
    private static final double GEOMETRY_EPSILON = 0.000001;

    private static final double ARROWHEAD_LENGTH = 12.0;
    private static final double ARROWHEAD_ANGLE_DEGREES = 25.0;

    private static final Color BACKGROUND_COLOR = Color.web("#f8fafc");
    private static final Color NODE_FILL_COLOR = Color.web("#3b82f6");
    private static final Color NODE_STROKE_COLOR = Color.web("#1d4ed8");
    private static final Color SELECTION_COLOR = Color.web("#f59e0b");
    private static final Color CONNECTOR_COLOR = Color.web("#334155");
    private static final Color ARROWHEAD_COLOR = Color.web("#dc2626");
    private static final Color MULTI_SELECT_COLOR = Color.web("#8b5cf6");
    private static final Color PREVIEW_COLOR = Color.web("#94a3b8");

    private static final double PREVIEW_DASH_LENGTH = 8.0;
    private static final double PREVIEW_DASH_GAP = 6.0;

    private static final String GRAPH_FILE_EXTENSION = "*.json";
    private static final String DEFAULT_GRAPH_FILE_NAME = "graph.json";

    private final GraphModel model = new GraphModel();
    private final LinkedStack<EditCommand> undoStack = new LinkedStack<>();
    private final LinkedStack<EditCommand> redoStack = new LinkedStack<>();

    private Long selectedNodeId;
    private final Set<Long> multiSelectedNodeIds = new HashSet<>();

    // Primary-button gesture tracking (press -> drag? -> release classification).
    private boolean primaryGestureActive;
    private double pressX;
    private double pressY;
    private Long pressHitNodeId;
    private double maxMovementSquared;

    // Drag-connect feedback: last cursor position plus the cosmetic pull offsets.
    private double dragCursorX;
    private double dragCursorY;
    private final PullMotionModel pullMotion = new PullMotionModel();
    private AnimationTimer motionTimer;

    private Stage mainStage;
    private GraphicsContext gc;

    @Override
    public void start(Stage primaryStage) {
        mainStage = primaryStage;
        Canvas canvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        gc = canvas.getGraphicsContext2D();

        canvas.setOnMousePressed(this::handleMousePressed);
        canvas.setOnMouseDragged(this::handleMouseDragged);
        canvas.setOnMouseReleased(this::handleMouseReleased);
        canvas.setOnContextMenuRequested(event -> event.consume());

        Button undoButton = new Button("Undo");
        undoButton.setOnAction(event -> undo());
        Button redoButton = new Button("Redo");
        redoButton.setOnAction(event -> redo());
        Button saveButton = new Button("Save");
        saveButton.setOnAction(event -> saveGraph());
        Button loadButton = new Button("Load");
        loadButton.setOnAction(event -> loadGraph());
        HBox toolbar = new HBox(8, undoButton, redoButton, saveButton, loadButton);
        toolbar.setPadding(new Insets(8));

        BorderPane root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(canvas);
        root.setFocusTraversable(true);

        Scene scene = new Scene(root, CANVAS_WIDTH, CANVAS_HEIGHT + 40);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN), this::undo);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
                this::redo);
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                cancelInteraction();
            }
        });

        primaryStage.setTitle("CSC360 Group 2 - Node Editor");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();

        root.requestFocus();
        render();
    }

    // -----------------------------------------------------------------
    // Mouse gesture handling
    // -----------------------------------------------------------------

    private void handleMousePressed(MouseEvent event) {
        double x = event.getX();
        double y = event.getY();

        boolean secondaryGesture = event.getButton() == MouseButton.SECONDARY
                || (event.getButton() == MouseButton.PRIMARY && event.isControlDown());
        if (secondaryGesture) {
            handleSecondaryClick(x, y);
            return;
        }

        if (event.getButton() == MouseButton.PRIMARY) {
            selectedNodeId = null;
            primaryGestureActive = true;
            pressX = x;
            pressY = y;
            // Seed the cursor at the press point so the first render of this gesture
            // cannot preview a line toward a stale position from an earlier drag.
            dragCursorX = x;
            dragCursorY = y;
            GraphNode hit = model.hitNodeBody(x, y, NODE_RADIUS);
            pressHitNodeId = hit == null ? null : hit.id();
            maxMovementSquared = 0.0;
            render();
        }
    }

    private void handleMouseDragged(MouseEvent event) {
        if (!primaryGestureActive) {
            return;
        }
        double x = event.getX();
        double y = event.getY();
        maxMovementSquared = Math.max(maxMovementSquared,
                GeometryUtils.distanceSquared(pressX, pressY, x, y));

        if (pressHitNodeId == null) {
            // Not a connect-drag, so there is no preview line or target to tug.
            return;
        }
        dragCursorX = x;
        dragCursorY = y;
        GraphNode candidate = model.nearestNodeWithin(
                x, y, PullMotionModel.PULL_RADIUS, pressHitNodeId);
        pullMotion.setPulledNodeId(candidate == null ? null : candidate.id());
        startMotionTimer();
    }

    private void handleMouseReleased(MouseEvent event) {
        if (!primaryGestureActive) {
            return;
        }
        primaryGestureActive = false;

        double x = event.getX();
        double y = event.getY();
        maxMovementSquared = Math.max(maxMovementSquared,
                GeometryUtils.distanceSquared(pressX, pressY, x, y));

        boolean dragged = maxMovementSquared > DRAG_THRESHOLD * DRAG_THRESHOLD;
        if (dragged) {
            completeDrag(x, y);
        } else {
            completeClick(x, y, event.isShiftDown());
        }
        pressHitNodeId = null;
        // Releasing the pull lets the offset ease back to zero on following frames.
        pullMotion.setPulledNodeId(null);
    }

    private void completeDrag(double releaseX, double releaseY) {
        if (pressHitNodeId == null) {
            return;
        }
        GraphNode target = model.hitNodeBody(releaseX, releaseY, NODE_RADIUS);
        if (target != null && target.id() != pressHitNodeId) {
            // Drag to another node -> connect or upgrade to bidirectional
            if (model.arrowExists(pressHitNodeId, target.id())) {
                return;
            }
            GraphArrow reverse = model.findArrow(target.id(), pressHitNodeId);
            if (reverse != null) {
                if (!reverse.bidirectional()) {
                    execute(new UpgradeArrowCommand(reverse));
                }
                return;
            }
            GraphArrow arrow = new GraphArrow(model.allocateArrowId(), pressHitNodeId, target.id());
            execute(new AddArrowCommand(arrow));
        } else if (target == null) {
            // Drag to empty canvas -> move
            double dx = releaseX - pressX;
            double dy = releaseY - pressY;
            if (Math.abs(dx) > GEOMETRY_EPSILON || Math.abs(dy) > GEOMETRY_EPSILON) {
                List<GraphNode> nodesToMove = new ArrayList<>();
                if (multiSelectedNodeIds.contains(pressHitNodeId)) {
                    for (Long id : multiSelectedNodeIds) {
                        GraphNode node = model.findNode(id);
                        if (node != null) {
                            nodesToMove.add(node);
                        }
                    }
                } else {
                    GraphNode pressedNode = model.findNode(pressHitNodeId);
                    if (pressedNode != null) {
                        nodesToMove.add(pressedNode);
                    }
                }
                if (!nodesToMove.isEmpty()) {
                    execute(new MoveNodesCommand(nodesToMove, dx, dy));
                }
            }
        }
    }

    private void completeClick(double x, double y, boolean isShiftDown) {
        GraphNode releaseNode = model.hitNodeBody(x, y, NODE_RADIUS);
        if (releaseNode != null) {
            if (pressHitNodeId != null && releaseNode.id() == pressHitNodeId) {
                if (isShiftDown) {
                    if (multiSelectedNodeIds.contains(releaseNode.id())) {
                        multiSelectedNodeIds.remove(releaseNode.id());
                    } else {
                        multiSelectedNodeIds.add(releaseNode.id());
                    }
                    render();
                    return;
                }
                multiSelectedNodeIds.remove(releaseNode.id());
                List<GraphArrow> incident = model.incidentArrows(releaseNode.id());
                execute(new DeleteNodeCommand(releaseNode, incident));
            }
            return;
        }

        if (pressHitNodeId != null) {
            // Press started on a node but released on empty space/arrow: no edit.
            return;
        }

        GraphArrow releaseArrow = hitArrowAt(x, y);
        GraphArrow pressArrow = hitArrowAt(pressX, pressY);
        if (releaseArrow != null && pressArrow != null && releaseArrow.id() == pressArrow.id()) {
            execute(new DeleteArrowCommand(releaseArrow));
        }
    }

    private void handleSecondaryClick(double x, double y) {
        GraphNode hit = model.hitNodeBody(x, y, NODE_RADIUS);
        GraphNode target = hit != null ? hit : model.nearestConflictingNode(x, y, NODE_RADIUS, GEOMETRY_EPSILON);

        if (target != null) {
            selectedNodeId = (selectedNodeId != null && selectedNodeId == target.id()) ? null : target.id();
            render();
            return;
        }

        if (x < NODE_RADIUS || x > CANVAS_WIDTH - NODE_RADIUS
                || y < NODE_RADIUS || y > CANVAS_HEIGHT - NODE_RADIUS) {
            return;
        }

        GraphNode newNode = new GraphNode(model.allocateNodeId(), x, y);
        if (selectedNodeId != null) {
            GraphArrow arrow = new GraphArrow(model.allocateArrowId(), selectedNodeId, newNode.id());
            execute(new AddConnectedNodeCommand(newNode, arrow));
            selectedNodeId = null;
        } else {
            execute(new AddNodeCommand(newNode));
        }
    }

    private void cancelInteraction() {
        selectedNodeId = null;
        multiSelectedNodeIds.clear();
        primaryGestureActive = false;
        pressHitNodeId = null;
        pullMotion.setPulledNodeId(null);
        render();
    }

    // -----------------------------------------------------------------
    // Drag-pull animation driver
    // -----------------------------------------------------------------

    /**
     * True while a primary drag that started on a node is in progress, which is
     * exactly when the preview line and pull feedback are shown.
     */
    private boolean connectDragActive() {
        return primaryGestureActive && pressHitNodeId != null;
    }

    private void startMotionTimer() {
        if (motionTimer != null) {
            return;
        }
        motionTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                onMotionFrame();
            }
        };
        motionTimer.start();
    }

    private void stopMotionTimer() {
        if (motionTimer == null) {
            return;
        }
        motionTimer.stop();
        motionTimer = null;
    }

    private void onMotionFrame() {
        pullMotion.tick(model, dragCursorX, dragCursorY);
        render();
        if (pullMotion.isAtRest() && !connectDragActive()) {
            stopMotionTimer();
        }
    }

    // -----------------------------------------------------------------
    // History
    // -----------------------------------------------------------------

    private void execute(EditCommand command) {
        command.apply(model);
        undoStack.push(command);
        redoStack.clear();
        render();
    }

    private void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        selectedNodeId = null;
        EditCommand command = undoStack.pop();
        command.undo(model);
        redoStack.push(command);
        render();
    }

    private void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        selectedNodeId = null;
        EditCommand command = redoStack.pop();
        command.apply(model);
        undoStack.push(command);
        render();
    }

    // -----------------------------------------------------------------
    // Persistence
    // -----------------------------------------------------------------

    private void saveGraph() {
        File file = chooseGraphFile(true);
        if (file == null) {
            return;
        }
        try {
            Files.writeString(file.toPath(), GraphJsonCodec.toJson(model), StandardCharsets.UTF_8);
        } catch (IOException e) {
            showError("Could not save graph", describe(e));
        }
    }

    private void loadGraph() {
        File file = chooseGraphFile(false);
        if (file == null) {
            return;
        }

        GraphModel loaded;
        try {
            loaded = GraphJsonCodec.fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            showError("Could not read graph", describe(e));
            return;
        } catch (IllegalArgumentException e) {
            showError("Not a valid graph file", describe(e));
            return;
        }

        model.loadFrom(loaded.getNodes(), loaded.getArrows());
        // A loaded graph has no meaningful history back to the previous in-memory graph.
        undoStack.clear();
        redoStack.clear();
        selectedNodeId = null;
        primaryGestureActive = false;
        pressHitNodeId = null;
        pullMotion.reset();
        render();
    }

    private File chooseGraphFile(boolean forSaving) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(forSaving ? "Save Graph" : "Open Graph");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Graph JSON", GRAPH_FILE_EXTENSION));
        if (forSaving) {
            chooser.setInitialFileName(DEFAULT_GRAPH_FILE_NAME);
            return chooser.showSaveDialog(mainStage);
        }
        return chooser.showOpenDialog(mainStage);
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private void showError(String header, String detail) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(mainStage);
        alert.setTitle("Graph Editor");
        alert.setHeaderText(header);
        alert.setContentText(detail);
        alert.showAndWait();
    }

    // -----------------------------------------------------------------
    // Hit testing (shares geometry with rendering, see GeometryUtils)
    // -----------------------------------------------------------------

    private GraphArrow hitArrowAt(double x, double y) {
        GraphArrow best = null;
        double bestDistance = Double.MAX_VALUE;
        for (GraphArrow arrow : model.getArrows()) {
            GraphNode source = model.findNode(arrow.sourceId());
            GraphNode target = model.findNode(arrow.targetId());
            if (source == null || target == null) {
                continue;
            }
            double[] segment = GeometryUtils.trimmedSegment(source.x(), source.y(), target.x(), target.y(), NODE_RADIUS);
            double distance = GeometryUtils.pointToSegmentDistance(x, y, segment[0], segment[1], segment[2], segment[3]);
            if (distance <= ARROW_HIT_TOLERANCE && distance < bestDistance) {
                best = arrow;
                bestDistance = distance;
            }
        }
        return best;
    }

    // -----------------------------------------------------------------
    // Rendering
    // -----------------------------------------------------------------

    /**
     * Draws from {@link PullMotionModel#effectivePosition} rather than raw model
     * coordinates, so a node being tugged and the arrows attached to it move together.
     * Hit testing deliberately stays on the real coordinates.
     */
    private void render() {
        clearCanvas();
        for (GraphArrow arrow : model.getArrows()) {
            drawArrow(arrow);
        }
        if (connectDragActive()) {
            drawDragPreview();
        }
        for (GraphNode node : model.getNodes()) {
            drawNode(node);
        }
    }

    private void drawDragPreview() {
        GraphNode source = model.findNode(pressHitNodeId);
        if (source == null) {
            return;
        }
        double[] origin = pullMotion.effectivePosition(source);
        if (GeometryUtils.distanceSquared(origin[0], origin[1], dragCursorX, dragCursorY)
                <= NODE_RADIUS * NODE_RADIUS) {
            // Cursor still inside the source node: nothing meaningful to preview yet.
            return;
        }

        double angle = GeometryUtils.calculateAngle(origin[0], origin[1], dragCursorX, dragCursorY);
        double startX = origin[0] + NODE_RADIUS * Math.cos(angle);
        double startY = origin[1] + NODE_RADIUS * Math.sin(angle);

        gc.setStroke(PREVIEW_COLOR);
        gc.setLineWidth(2.0);
        gc.setLineDashes(PREVIEW_DASH_LENGTH, PREVIEW_DASH_GAP);
        gc.strokeLine(startX, startY, dragCursorX, dragCursorY);
        // Restore solid strokes for the nodes and arrows drawn after this.
        gc.setLineDashes();
    }

    private void clearCanvas() {
        gc.setFill(BACKGROUND_COLOR);
        gc.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    private void drawNode(GraphNode node) {
        double[] center = pullMotion.effectivePosition(node);
        double diameter = NODE_RADIUS * 2;
        double topLeftX = center[0] - NODE_RADIUS;
        double topLeftY = center[1] - NODE_RADIUS;

        gc.setFill(NODE_FILL_COLOR);
        gc.fillOval(topLeftX, topLeftY, diameter, diameter);

        gc.setStroke(NODE_STROKE_COLOR);
        gc.setLineWidth(2.0);
        gc.strokeOval(topLeftX, topLeftY, diameter, diameter);

        if (multiSelectedNodeIds.contains(node.id())) {
            double ringInset = 4.0;
            gc.setStroke(MULTI_SELECT_COLOR);
            gc.setLineWidth(3.0);
            gc.strokeOval(topLeftX + ringInset, topLeftY + ringInset,
                    diameter - 2 * ringInset, diameter - 2 * ringInset);
        } else if (selectedNodeId != null && selectedNodeId == node.id()) {
            double ringInset = 4.0;
            gc.setStroke(SELECTION_COLOR);
            gc.setLineWidth(3.0);
            gc.strokeOval(topLeftX + ringInset, topLeftY + ringInset,
                    diameter - 2 * ringInset, diameter - 2 * ringInset);
        }
    }

    private void drawArrow(GraphArrow arrow) {
        GraphNode source = model.findNode(arrow.sourceId());
        GraphNode target = model.findNode(arrow.targetId());
        if (source == null || target == null) {
            return;
        }

        double[] sourceCenter = pullMotion.effectivePosition(source);
        double[] targetCenter = pullMotion.effectivePosition(target);
        double[] segment = GeometryUtils.trimmedSegment(
                sourceCenter[0], sourceCenter[1], targetCenter[0], targetCenter[1], NODE_RADIUS);
        double startX = segment[0];
        double startY = segment[1];
        double tipX = segment[2];
        double tipY = segment[3];
        double angle = segment[4];

        gc.setStroke(CONNECTOR_COLOR);
        gc.setLineWidth(2.5);
        gc.strokeLine(startX, startY, tipX, tipY);

        drawArrowhead(tipX, tipY, angle);
        if (arrow.bidirectional()) {
            drawArrowhead(startX, startY, angle + Math.PI);
        }
    }

    private void drawArrowhead(double tipX, double tipY, double angle) {
        double wingAngle = Math.toRadians(ARROWHEAD_ANGLE_DEGREES);
        double baseX1 = tipX - ARROWHEAD_LENGTH * Math.cos(angle + wingAngle);
        double baseY1 = tipY - ARROWHEAD_LENGTH * Math.sin(angle + wingAngle);
        double baseX2 = tipX - ARROWHEAD_LENGTH * Math.cos(angle - wingAngle);
        double baseY2 = tipY - ARROWHEAD_LENGTH * Math.sin(angle - wingAngle);

        gc.setFill(ARROWHEAD_COLOR);
        gc.fillPolygon(new double[]{tipX, baseX1, baseX2}, new double[]{tipY, baseY1, baseY2}, 3);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
