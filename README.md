# CSC360 Course Project

## Group 2

> A JavaFX desktop editor for creating, connecting, and editing directed graphs.

---

## Course Information

| Field | Details |
| --- | --- |
| **Course Code** | CSC360 |
| **Course Name** | Computer Graphics and Digital Image Processing |
| **Session** | Monsoon 2026 |
| **Group** | 2 |

---

## Group Members

| No. | Name | Enrolment Number |
|:---:|:---|:---|
| 1 | Varun | AU2520215 |
| 2 | Satvik | AU2520039 |
| 3 | Garv | AU2520247 |
| 4 | Shambhavee | AU2500016 |

---

## About The Project

This project is an interactive JavaFX graph editor developed for CSC360. Users can visually construct directed graphs on an 800×600 canvas through intuitive mouse gestures. The editor implements robust geometric math for boundary clipping and hit detection, and provides complete reversible history (undo/redo) via custom linked-list stacks and the Command design pattern.

### Key Features

- **Fixed-Radius Node Placement:** Place solid nodes (radius 20 px) with centered coordinates.
- **Proximity & Selection:** Right-clicking on an existing node toggles selection with a visible gold highlight.
- **One-Shot Auto-Connect:** Select an existing node and right-click elsewhere on empty canvas to spawn a new node and automatically draw a directed arrow between them.
- **Interactive Arrow Dragging:** Left-click and drag between two nodes with real-time rubber-band arrow preview.
- **Smart Boundary Clipping:** Arrows clip exactly to node perimeters using Euclidean unit vectors and render with filled directed arrowheads.
- **Single-Click Deletion:** Stationary left-click deletes the targeted node (and all incident arrows) or arrow.
- **Full Undo / Redo:** Every user action is encapsulated in a command and managed via two history stacks.

---

## Controls & Shortcuts

| Action | Control |
| --- | --- |
| **Create a node** | Right-click (or `Ctrl+click`) on empty canvas |
| **Select / deselect node** | Right-click (or `Ctrl+click`) an existing node |
| **Auto-connect new node** | Select a node, then right-click on empty canvas |
| **Connect two nodes** | Left-click and drag from source node to target node |
| **Delete a node or arrow** | Left-click on it (stationary click, no drag) |
| **Undo** | **Undo** button or `Ctrl+Z` / `Cmd+Z` |
| **Redo** | **Redo** button or `Ctrl+Y` / `Ctrl+Shift+Z` / `Cmd+Shift+Z` |
| **Cancel selection / drag** | `Escape` key |

---

## Getting Started

### Prerequisites

- **Java Development Kit (JDK):** Version 21 or later (tested up to JDK 24).
- A terminal (PowerShell, Command Prompt, or Bash).

---

### Running the Application

Both **Gradle** and **Maven** are supported.

#### Option 1: Using Gradle (Recommended)

The included Gradle wrapper handles all dependencies and tool installations automatically.

- **Windows (PowerShell or CMD):**
  ```powershell
  .\gradlew.bat run
  ```

- **macOS / Linux:**
  ```bash
  ./gradlew run
  ```

#### Option 2: Using Maven

- **All Platforms:**
  ```bash
  mvn javafx:run
  ```

---

### Running Tests

#### Using Gradle:
```powershell
.\gradlew.bat test
```
*(On macOS/Linux: `./gradlew test`)*

#### Using Maven:
```powershell
mvn test
```

---

## Architecture & Design Patterns

The application is structured into modular components adhering to object-oriented principles:

1. **Command Pattern (`EditCommand`):**
   - Every mutating user action implements `EditCommand` with `execute()` and `undo()` methods.
   - Concrete commands:
     - `AddNodeCommand`: Inserts a node.
     - `DeleteNodeCommand`: Removes a node and preserves its incident arrows for undo.
     - `AddArrowCommand`: Inserts a directed arrow between two nodes.
     - `DeleteArrowCommand`: Removes an arrow and restores it on undo.
     - `AddConnectedNodeCommand`: Atomically creates a node and connects it to the selected node.

2. **Custom Data Structure (`LinkedStack<T>`):**
   - The undo and redo stacks are powered by a custom singly-linked list stack implementation (`push`, `pop`, `peek`, `size`, `isEmpty`), fulfilling project data structure requirements without using standard Java collection stacks.

3. **Geometry Engine (`GeometryUtils`):**
   - **`calculateAngle(x1, y1, x2, y2)`:** Computes vector angle for arrowhead orientation.
   - **`trimmedSegment(x1, y1, x2, y2, radius)`:** Calculates exact intersection points where arrows meet circle boundaries.
   - **`pointToSegmentDistance(px, py, x1, y1, x2, y2)`:** Orthogonal distance testing for precise arrow hit detection.

4. **Model Layer (`GraphModel`):**
   - In-memory graph representation tracking nodes (`GraphNode`) and directed edges (`GraphArrow`) with sequential ID allocation.

---

## Repository Structure

```text
CSC360-Group2/
├── build.gradle                                Gradle build script
├── settings.gradle                             Gradle settings
├── pom.xml                                     Maven build script
├── gradlew / gradlew.bat                       Gradle wrapper scripts
├── gradle/wrapper/                             Gradle wrapper distribution files
├── README.md                                   Project documentation
└── src/
    ├── main/java/com/example/grapheditor/
    │   ├── AddArrowCommand.java                Command to add arrow
    │   ├── AddConnectedNodeCommand.java        Atomic command to add connected node
    │   ├── AddNodeCommand.java                 Command to add node
    │   ├── DeleteArrowCommand.java             Command to delete arrow
    │   ├── DeleteNodeCommand.java              Command to delete node & cascade edges
    │   ├── EditCommand.java                    Command interface
    │   ├── EditorApplication.java              JavaFX GUI application and canvas controller
    │   ├── GeometryUtils.java                  Geometry and hit-testing helpers
    │   ├── GraphArrow.java                     Directed arrow record/model
    │   ├── GraphModel.java                     Graph state and entity manager
    │   ├── GraphNode.java                      Node model (id, position, radius)
    │   ├── LinkedStack.java                    Custom linked-list stack for history
    │   └── Main.java                           JavaFX launcher class
    └── test/java/com/example/grapheditor/
        └── EditorLogicTest.java                Unit tests for geometry, models, and stack
```
