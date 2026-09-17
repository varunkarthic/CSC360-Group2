package com.example.grapheditor;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JavaFX Application for drawing circles on right-click and clearing on left-click.
 *
 * Implements:
 * - Issue #5: Right-Click Detection and Circle Drawing
 * - Issue #10: Clear/Reset Function via Left-Click
 */
public class EditorApplication extends Application {

    // Canvas dimensions (800x600 window)
    public static final double CANVAS_WIDTH = 800.0;
    public static final double CANVAS_HEIGHT = 600.0;

    // Shared radius constant for consistent circle sizing (Issue #5)
    public static final double NODE_RADIUS = 20.0;

    // Color definitions
    public static final Color BACKGROUND_COLOR = Color.web("#f8fafc"); // Clean off-white canvas
    public static final Color NODE_FILL_COLOR = Color.web("#3b82f6");   // Pleasant modern blue
    public static final Color NODE_STROKE_COLOR = Color.web("#1d4ed8"); // Darker blue outline

    // Coordinate-tracking list to store circle centers in click order (Issue #6 / Issue #10)
    // Each entry is a double array: [x, y]
    private final List<double[]> circlePoints = new ArrayList<>();

    @Override
    public void start(Stage primaryStage) {
        // Create the canvas for drawing
        Canvas canvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Initial background fill so the screen is clean on launch
        clearCanvas(gc);

        // Attach mouse click listener to handle both right-click and left-click
        canvas.setOnMouseClicked(event -> handleCanvasClick(event, gc));

        // Place canvas inside a container and set up the window
        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root, CANVAS_WIDTH, CANVAS_HEIGHT);

        primaryStage.setTitle("CSC360 Group 2 - Node Editor");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    /**
     * Central mouse click handler.
     *
     * @param event The mouse click event containing button type and (x, y) coordinates.
     * @param gc The GraphicsContext used for 2D drawing on the canvas.
     */
    public void handleCanvasClick(MouseEvent event, GraphicsContext gc) {
        // -----------------------------------------------------------------
        // ISSUE #5: Right-Click Detection and Circle Drawing
        // -----------------------------------------------------------------
        if (event.getButton() == MouseButton.SECONDARY) {
            double clickX = event.getX();
            double clickY = event.getY();

            // 1. Draw a filled circle with outline centered at (clickX, clickY)
            drawCircle(gc, clickX, clickY);

            // 2. Add circle center coordinates to the tracking list
            circlePoints.add(new double[]{clickX, clickY});
        }

        // -----------------------------------------------------------------
        // ISSUE #10: Clear/Reset Function via Left-Click
        // -----------------------------------------------------------------
        else if (event.getButton() == MouseButton.PRIMARY) {
            // 1. Repaint the canvas background to wipe away all circles and arrows
            clearCanvas(gc);

            // 2. Reset the coordinate-tracking list so no old state remains
            circlePoints.clear();
        }
    }

    /**
     * Fills the canvas with the background color to clear all drawings (Issue #10).
     */
    public void clearCanvas(GraphicsContext gc) {
        gc.setFill(BACKGROUND_COLOR);
        gc.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    /**
     * Draws a filled circle with an outline centered at (centerX, centerY) (Issue #5).
     *
     * In computer graphics, fillOval and strokeOval require the top-left bounding
     * box coordinates. Subtracting the radius centers the circle on the mouse pointer.
     */
    public void drawCircle(GraphicsContext gc, double centerX, double centerY) {
        double diameter = NODE_RADIUS * 2;
        double topLeftX = centerX - NODE_RADIUS;
        double topLeftY = centerY - NODE_RADIUS;

        // Draw solid fill
        gc.setFill(NODE_FILL_COLOR);
        gc.fillOval(topLeftX, topLeftY, diameter, diameter);

        // Draw outline stroke
        gc.setStroke(NODE_STROKE_COLOR);
        gc.setLineWidth(2.0);
        gc.strokeOval(topLeftX, topLeftY, diameter, diameter);
    }

    /**
     * Read-only view of recorded circle coordinates (useful for testing and inspection).
     */
    public List<double[]> getCirclePoints() {
        return Collections.unmodifiableList(circlePoints);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
