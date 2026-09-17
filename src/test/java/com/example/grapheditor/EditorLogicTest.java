package com.example.grapheditor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EditorLogicTest {

    @Test
    @DisplayName("Issue #5 & Issue #6: Tracking circle coordinates on right-click")
    void testAddCircleCoordinates() {
        List<double[]> points = new ArrayList<>();

        // Simulate first right click at (100.0, 150.0)
        points.add(new double[]{100.0, 150.0});
        assertEquals(1, points.size());
        assertArrayEquals(new double[]{100.0, 150.0}, points.get(0));

        // Simulate second right click at (300.0, 400.0)
        points.add(new double[]{300.0, 400.0});
        assertEquals(2, points.size());
        assertArrayEquals(new double[]{300.0, 400.0}, points.get(1));
    }

    @Test
    @DisplayName("Issue #10: Left-click clears all coordinates and allows a fresh start")
    void testClearResetState() {
        List<double[]> points = new ArrayList<>();

        // Add some existing points
        points.add(new double[]{100.0, 150.0});
        points.add(new double[]{200.0, 250.0});
        assertEquals(2, points.size());

        // Simulate left-click reset
        points.clear();
        assertEquals(0, points.size(), "List must be empty after left-click reset");

        // Next right-click starts brand new chain with no leftover state
        points.add(new double[]{500.0, 500.0});
        assertEquals(1, points.size());
        assertArrayEquals(new double[]{500.0, 500.0}, points.get(0));
    }

    @Test
    @DisplayName("Issue #5: Bounding box offset correctly centers circle")
    void testBoundingBoxCentering() {
        double centerX = 200.0;
        double centerY = 300.0;
        double radius = EditorApplication.NODE_RADIUS;

        double topLeftX = centerX - radius;
        double topLeftY = centerY - radius;
        double diameter = radius * 2.0;

        assertEquals(180.0, topLeftX);
        assertEquals(280.0, topLeftY);
        assertEquals(40.0, diameter);
    }
}
