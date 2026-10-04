package com.example.grapheditor;

import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.WritableImage;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;

public class EditorApplication extends Application {

    // Initial canvas size. The canvas then follows the window as it is resized.
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

    private static final Color LABEL_COLOR = Color.WHITE;
    private static final Font LABEL_FONT = Font.font(11.0);
    // Wait this long before deleting a clicked node, in case it is a double-click (rename).
    private static final double DOUBLE_CLICK_DELAY_MS = 300.0;

    private static final double PREVIEW_DASH_LENGTH = 8.0;
    private static final double PREVIEW_DASH_GAP = 6.0;

    private static final String GRAPH_FILE_EXTENSION = "*.json";
    private static final String DEFAULT_GRAPH_FILE_NAME = "graph.json";

    private final GraphModel model = new GraphModel();
    private final LinkedStack<EditCommand> undoStack = new LinkedStack<>();
    private final LinkedStack<EditCommand> redoStack = new LinkedStack<>();

    // Orange selection: each of these nodes gets an arrow to the next node created.
    private final SelectionState selection = new SelectionState();
    private final Set<Long> multiSelectedNodeIds = new HashSet<>();

    // State of the current left-button press.
    private boolean primaryGestureActive;
    private double pressX;
    private double pressY;
    private Long pressedNodeId;
    private double maxDragDistanceSquared;

    // Cursor position while dragging, and the pull animation.
    private double dragCursorX;
    private double dragCursorY;
    private final NodePullAnimation pullAnimation = new NodePullAnimation();
    private AnimationTimer animationTimer;

    private PauseTransition pendingDelete;

    private Stage mainStage;
    private Canvas canvas;
    private GraphicsContext gc;
    private Label hintLabel;

    @Override
    public void start(Stage primaryStage) {
        mainStage = primaryStage;
        canvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
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
        Button exportPngButton = new Button("Export PNG");
        exportPngButton.setOnAction(event -> exportPng());
        Button exportSvgButton = new Button("Export SVG");
        exportSvgButton.setOnAction(event -> exportSvg());
        Button fullScreenButton = new Button("Full Screen");
        fullScreenButton.setOnAction(event -> toggleFullScreen());
        List<Button> buttons = List.of(undoButton, redoButton, saveButton, loadButton,
                exportPngButton, exportSvgButton, fullScreenButton);
        // Buttons never shrink below their text; only the hint gives up space (with an ellipsis).
        for (Button button : buttons) {
            button.setMinWidth(Region.USE_PREF_SIZE);
        }
        hintLabel = new Label(EditorHints.IDLE);
        hintLabel.setTextFill(Color.web("#475569"));
        hintLabel.setMinWidth(0);
        HBox.setHgrow(hintLabel, Priority.ALWAYS);
        HBox toolbar = new HBox(8);
        toolbar.getChildren().addAll(buttons);
        toolbar.getChildren().add(hintLabel);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(8));

        // The canvas fills whatever space the window gives it and redraws when that changes.
        Pane canvasHolder = new Pane(canvas);
        canvasHolder.setPrefSize(CANVAS_WIDTH, CANVAS_HEIGHT);
        canvasHolder.setMinSize(0, 0);
        canvas.widthProperty().bind(canvasHolder.widthProperty());
        canvas.heightProperty().bind(canvasHolder.heightProperty());
        canvas.widthProperty().addListener((obs, oldValue, newValue) -> render());
        canvas.heightProperty().addListener((obs, oldValue, newValue) -> render());

        BorderPane root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(canvasHolder);
        root.setFocusTraversable(true);

        // No fixed size: the window opens wide enough for the full toolbar and hint.
        Scene scene = new Scene(root);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN), this::undo);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
                this::redo);
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                cancelInteraction();
            } else if (event.getCode() == KeyCode.F11) {
                toggleFullScreen();
            }
        });

        primaryStage.setTitle("CSC360 Group 2 - Node Editor");
        primaryStage.setScene(scene);
        primaryStage.setResizable(true);
        primaryStage.fullScreenProperty().addListener((obs, wasFullScreen, isFullScreen) ->
                fullScreenButton.setText(isFullScreen ? "Exit Full Screen" : "Full Screen"));
        primaryStage.show();
        // Never let the window get narrower than the buttons need.
        double decorationWidth = primaryStage.getWidth() - scene.getWidth();
        double decorationHeight = primaryStage.getHeight() - scene.getHeight();
        primaryStage.setMinWidth(toolbar.minWidth(-1) + decorationWidth);
        primaryStage.setMinHeight(toolbar.prefHeight(-1) + 4 * NODE_RADIUS + decorationHeight);

        root.requestFocus();
        render();
    }

    private void handleMousePressed(MouseEvent event) {
        double x = event.getX();
        double y = event.getY();

        boolean secondaryGesture = event.getButton() == MouseButton.SECONDARY
                || (event.getButton() == MouseButton.PRIMARY && event.isControlDown());
        if (secondaryGesture) {
            handleSecondaryClick(x, y, event.isShiftDown());
            return;
        }

        if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
            GraphNode labelTarget = model.hitNodeBody(x, y, NODE_RADIUS);
            if (labelTarget != null) {
                // Double-click: rename, and cancel the delete the first click scheduled.
                cancelPendingDelete();
                editLabel(labelTarget);
                return;
            }
        }

        if (event.getButton() == MouseButton.PRIMARY) {
            selection.clear();
            primaryGestureActive = true;
            pressX = x;
            pressY = y;
            // Start the cursor at the press point so no old preview line is drawn.
            dragCursorX = x;
            dragCursorY = y;
            GraphNode hit = model.hitNodeBody(x, y, NODE_RADIUS);
            pressedNodeId = hit == null ? null : hit.id();
            maxDragDistanceSquared = 0.0;
            render();
        }
    }

    private void handleMouseDragged(MouseEvent event) {
        if (!primaryGestureActive) {
            return;
        }
        double x = event.getX();
        double y = event.getY();
        maxDragDistanceSquared = Math.max(maxDragDistanceSquared,
                GeometryUtils.distanceSquared(pressX, pressY, x, y));

        if (pressedNodeId == null) {
            return;
        }
        dragCursorX = x;
        dragCursorY = y;
        GraphNode candidate = model.nearestNodeWithin(
                x, y, NodePullAnimation.PULL_RADIUS, pressedNodeId);
        pullAnimation.setPulledNodeId(candidate == null ? null : candidate.id());
        startAnimationTimer();
    }

    private void handleMouseReleased(MouseEvent event) {
        if (!primaryGestureActive) {
            return;
        }
        primaryGestureActive = false;

        double x = event.getX();
        double y = event.getY();
        maxDragDistanceSquared = Math.max(maxDragDistanceSquared,
                GeometryUtils.distanceSquared(pressX, pressY, x, y));

        boolean dragged = maxDragDistanceSquared > DRAG_THRESHOLD * DRAG_THRESHOLD;
        if (dragged) {
            completeDrag(x, y);
        } else {
            completeClick(x, y, event.isShiftDown());
        }
        pressedNodeId = null;
        pullAnimation.setPulledNodeId(null);
    }

    private void completeDrag(double releaseX, double releaseY) {
        if (pressedNodeId == null) {
            return;
        }
        GraphNode target = resolveConnectTarget(releaseX, releaseY);
        if (target != null && target.id() != pressedNodeId) {
            // Dragged onto another node: connect, or make an existing arrow two-way.
            if (model.arrowExists(pressedNodeId, target.id())) {
                return;
            }
            GraphArrow reverse = model.findArrow(target.id(), pressedNodeId);
            if (reverse != null) {
                if (!reverse.bidirectional()) {
                    execute(new UpgradeArrowCommand(reverse));
                }
                return;
            }
            GraphArrow arrow = new GraphArrow(model.allocateArrowId(), pressedNodeId, target.id());
            execute(new AddArrowCommand(arrow));
        } else if (target == null) {
            // Dragged onto empty space: move the node(s).
            double dx = releaseX - pressX;
            double dy = releaseY - pressY;
            if (Math.abs(dx) > GEOMETRY_EPSILON || Math.abs(dy) > GEOMETRY_EPSILON) {
                List<GraphNode> nodesToMove = new ArrayList<>();
                if (multiSelectedNodeIds.contains(pressedNodeId)) {
                    for (Long id : multiSelectedNodeIds) {
                        GraphNode node = model.findNode(id);
                        if (node != null) {
                            nodesToMove.add(node);
                        }
                    }
                } else {
                    GraphNode pressedNode = model.findNode(pressedNodeId);
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

    // A release near a node (inside the pull radius) still connects to it, so it matches what the animation showed.
    private GraphNode resolveConnectTarget(double releaseX, double releaseY) {
        GraphNode hit = model.hitNodeBody(releaseX, releaseY, NODE_RADIUS);
        if (hit != null) {
            return hit;
        }
        return model.nearestNodeWithin(releaseX, releaseY, NodePullAnimation.PULL_RADIUS, pressedNodeId);
    }

    private void completeClick(double x, double y, boolean isShiftDown) {
        GraphNode releaseNode = model.hitNodeBody(x, y, NODE_RADIUS);
        if (releaseNode != null) {
            if (pressedNodeId != null && releaseNode.id() == pressedNodeId) {
                if (isShiftDown) {
                    if (multiSelectedNodeIds.contains(releaseNode.id())) {
                        multiSelectedNodeIds.remove(releaseNode.id());
                    } else {
                        multiSelectedNodeIds.add(releaseNode.id());
                    }
                    render();
                    return;
                }
                scheduleNodeDelete(releaseNode.id());
            }
            return;
        }

        if (pressedNodeId != null) {
            return;
        }

        GraphArrow releaseArrow = hitArrowAt(x, y);
        GraphArrow pressArrow = hitArrowAt(pressX, pressY);
        if (releaseArrow != null && pressArrow != null && releaseArrow.id() == pressArrow.id()) {
            execute(new DeleteArrowCommand(releaseArrow));
        }
    }

    private void scheduleNodeDelete(long nodeId) {
        cancelPendingDelete();
        pendingDelete = new PauseTransition(Duration.millis(DOUBLE_CLICK_DELAY_MS));
        pendingDelete.setOnFinished(event -> {
            pendingDelete = null;
            GraphNode node = model.findNode(nodeId);
            if (node != null) {
                multiSelectedNodeIds.remove(nodeId);
                execute(new DeleteNodeCommand(node, model.incidentArrows(nodeId)));
            }
        });
        pendingDelete.play();
    }

    private void cancelPendingDelete() {
        if (pendingDelete != null) {
            pendingDelete.stop();
            pendingDelete = null;
        }
    }

    private void editLabel(GraphNode node) {
        TextInputDialog dialog = new TextInputDialog(node.label());
        dialog.initOwner(mainStage);
        dialog.setTitle("Node label");
        dialog.setHeaderText("Label for this node");
        dialog.setContentText("Text:");
        dialog.showAndWait().ifPresent(text -> {
            String label = text.strip();
            if (!label.equals(node.label())) {
                execute(new RenameNodeCommand(node, label));
            }
        });
    }

    private void handleSecondaryClick(double x, double y, boolean additive) {
        GraphNode hit = model.hitNodeBody(x, y, NODE_RADIUS);
        GraphNode target = hit != null ? hit : model.nearestOverlappingNode(x, y, NODE_RADIUS, GEOMETRY_EPSILON);

        if (target != null) {
            if (additive) {
                selection.toggleAdditive(target.id());
            } else {
                selection.toggleSingle(target.id());
            }
            render();
            return;
        }

        if (x < NODE_RADIUS || x > canvas.getWidth() - NODE_RADIUS
                || y < NODE_RADIUS || y > canvas.getHeight() - NODE_RADIUS) {
            return;
        }

        GraphNode newNode = new GraphNode(model.allocateNodeId(), x, y);
        if (!selection.isEmpty()) {
            List<GraphArrow> arrows = new ArrayList<>();
            for (Long sourceId : selection.ids()) {
                arrows.add(new GraphArrow(model.allocateArrowId(), sourceId, newNode.id()));
            }
            // Clear first, because execute() redraws the canvas.
            selection.clear();
            execute(new AddConnectedNodeCommand(newNode, arrows));
        } else {
            execute(new AddNodeCommand(newNode));
        }
    }

    private void toggleFullScreen() {
        mainStage.setFullScreen(!mainStage.isFullScreen());
    }

    private void cancelInteraction() {
        cancelPendingDelete();
        selection.clear();
        multiSelectedNodeIds.clear();
        primaryGestureActive = false;
        pressedNodeId = null;
        pullAnimation.setPulledNodeId(null);
        render();
    }

    // True while dragging from a node, which is when the preview line is shown.
    private boolean connectDragActive() {
        return primaryGestureActive && pressedNodeId != null;
    }

    private void startAnimationTimer() {
        if (animationTimer != null) {
            return;
        }
        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                onAnimationFrame();
            }
        };
        animationTimer.start();
    }

    private void stopAnimationTimer() {
        if (animationTimer == null) {
            return;
        }
        animationTimer.stop();
        animationTimer = null;
    }

    private void onAnimationFrame() {
        pullAnimation.tick(model, dragCursorX, dragCursorY);
        render();
        if (pullAnimation.isAtRest() && !connectDragActive()) {
            stopAnimationTimer();
        }
    }

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
        selection.clear();
        EditCommand command = undoStack.pop();
        command.undo(model);
        redoStack.push(command);
        render();
    }

    private void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        selection.clear();
        EditCommand command = redoStack.pop();
        command.apply(model);
        undoStack.push(command);
        render();
    }

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

        cancelPendingDelete();
        model.loadFrom(loaded.getNodes(), loaded.getArrows());
        undoStack.clear();
        redoStack.clear();
        selection.clear();
        primaryGestureActive = false;
        pressedNodeId = null;
        pullAnimation.reset();
        render();
    }

    private void exportPng() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export PNG");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PNG image (*.png)", "*.png"));
        chooser.setInitialFileName("graph.png");
        File file = chooser.showSaveDialog(mainStage);
        if (file == null) {
            return;
        }
        try {
            WritableImage img = canvas.snapshot(null, null);
            BufferedImage buffered = SwingFXUtils.fromFXImage(img, null);
            ImageIO.write(buffered, "png", file);
        } catch (IOException e) {
            showError("Could not export PNG", describe(e));
        }
    }

    private void exportSvg() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export SVG");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("SVG image (*.svg)", "*.svg"));
        chooser.setInitialFileName("graph.svg");
        File file = chooser.showSaveDialog(mainStage);
        if (file == null) {
            return;
        }
        try {
            Files.writeString(file.toPath(), GraphSvgExporter.toSvg(model, canvas.getWidth(), canvas.getHeight()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            showError("Could not export SVG", describe(e));
        }
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

    private GraphArrow hitArrowAt(double x, double y) {
        GraphArrow best = null;
        double bestDistance = Double.MAX_VALUE;
        for (GraphArrow arrow : model.getArrows()) {
            GraphNode source = model.findNode(arrow.sourceId());
            GraphNode target = model.findNode(arrow.targetId());
            if (source == null || target == null) {
                continue;
            }
            GeometryUtils.ArrowLine line = GeometryUtils.trimmedArrowLine(
                    source.x(), source.y(), target.x(), target.y(), NODE_RADIUS);
            double distance = GeometryUtils.pointToSegmentDistance(
                    x, y, line.startX(), line.startY(), line.endX(), line.endY());
            if (distance <= ARROW_HIT_TOLERANCE && distance < bestDistance) {
                best = arrow;
                bestDistance = distance;
            }
        }
        return best;
    }

    // Draws use effectivePosition so a pulled node and its arrows move together. Hit tests use real positions.
    private void render() {
        if (hintLabel != null) {
            hintLabel.setText(EditorHints.hintFor(
                    connectDragActive(), selection.size(), multiSelectedNodeIds.size()));
        }
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
        GraphNode source = model.findNode(pressedNodeId);
        if (source == null) {
            return;
        }
        double[] origin = pullAnimation.effectivePosition(source);
        if (GeometryUtils.distanceSquared(origin[0], origin[1], dragCursorX, dragCursorY)
                <= NODE_RADIUS * NODE_RADIUS) {
            return;
        }

        double angle = GeometryUtils.angleBetweenPoints(origin[0], origin[1], dragCursorX, dragCursorY);
        double startX = origin[0] + NODE_RADIUS * Math.cos(angle);
        double startY = origin[1] + NODE_RADIUS * Math.sin(angle);

        gc.setStroke(PREVIEW_COLOR);
        gc.setLineWidth(2.0);
        gc.setLineDashes(PREVIEW_DASH_LENGTH, PREVIEW_DASH_GAP);
        gc.strokeLine(startX, startY, dragCursorX, dragCursorY);
        // Back to solid lines for the nodes and arrows drawn after this.
        gc.setLineDashes();
    }

    private void clearCanvas() {
        gc.setFill(BACKGROUND_COLOR);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    private void drawNode(GraphNode node) {
        double[] center = pullAnimation.effectivePosition(node);
        double diameter = NODE_RADIUS * 2;
        double topLeftX = center[0] - NODE_RADIUS;
        double topLeftY = center[1] - NODE_RADIUS;

        gc.setFill(NODE_FILL_COLOR);
        gc.fillOval(topLeftX, topLeftY, diameter, diameter);

        gc.setStroke(NODE_STROKE_COLOR);
        gc.setLineWidth(2.0);
        gc.strokeOval(topLeftX, topLeftY, diameter, diameter);

        drawLabel(node, center);

        if (multiSelectedNodeIds.contains(node.id())) {
            double ringInset = 4.0;
            gc.setStroke(MULTI_SELECT_COLOR);
            gc.setLineWidth(3.0);
            gc.strokeOval(topLeftX + ringInset, topLeftY + ringInset,
                    diameter - 2 * ringInset, diameter - 2 * ringInset);
        } else if (selection.contains(node.id())) {
            double ringInset = 4.0;
            gc.setStroke(SELECTION_COLOR);
            gc.setLineWidth(3.0);
            gc.strokeOval(topLeftX + ringInset, topLeftY + ringInset,
                    diameter - 2 * ringInset, diameter - 2 * ringInset);
        }
    }

    private void drawLabel(GraphNode node, double[] center) {
        if (node.label().isEmpty()) {
            return;
        }
        gc.setFill(LABEL_COLOR);
        gc.setFont(LABEL_FONT);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText(node.label(), center[0], center[1]);
    }

    private void drawArrow(GraphArrow arrow) {
        GraphNode source = model.findNode(arrow.sourceId());
        GraphNode target = model.findNode(arrow.targetId());
        if (source == null || target == null) {
            return;
        }

        double[] sourceCenter = pullAnimation.effectivePosition(source);
        double[] targetCenter = pullAnimation.effectivePosition(target);
        GeometryUtils.ArrowLine line = GeometryUtils.trimmedArrowLine(
                sourceCenter[0], sourceCenter[1], targetCenter[0], targetCenter[1], NODE_RADIUS);
        double startX = line.startX();
        double startY = line.startY();
        double tipX = line.endX();
        double tipY = line.endY();
        double angle = line.angle();

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
