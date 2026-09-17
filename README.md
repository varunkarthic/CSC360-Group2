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
- **Proximity & Single-Selection:** Right-clicking on an existing node toggles selection with a visible gold highlight.
- **Multi-Selection:** Shift + left-click on nodes toggles multi-selection with a distinct purple highlight ring.
- **One-Shot Auto-Connect:** Select an existing node and right-click elsewhere on empty canvas to spawn a new node and automatically draw a directed arrow between them.
- **Interactive Arrow Dragging:** Left-click and drag between two nodes to connect them.
- **Bidirectional Arrows:** Dragging between two nodes with an existing reverse arrow upgrades it into a double-headed bidirectional arrow.
- **Drag-to-Move Nodes:** Drag a node (or a multi-selection of nodes) to empty canvas space to reposition smoothly with exact coordinate translations.
- **Drag-Connect Feedback:** While dragging, a dashed preview line tracks the cursor and the nearest candidate target node is visibly *pulled* toward it with eased, frame-by-frame motion, springing back when the drag ends. The displacement is purely cosmetic — hit detection always uses true node coordinates.
- **Smart Boundary Clipping:** Arrows clip exactly to node perimeters using Euclidean unit vectors and render with filled directed arrowheads.
- **Single-Click Deletion:** Stationary left-click deletes the targeted node (and all incident arrows) or arrow.
- **Full Undo / Redo:** Every user action is encapsulated in a command and managed via two history stacks.
- **Save / Load as JSON:** Persist a graph to a `.json` file and reopen it later, with id allocation resuming safely above every loaded id. The format is plain JSON written and parsed in-project, so the build stays dependency-free.

---

## Controls & Shortcuts

| Action | Control |
| --- | --- |
| **Create a node** | Right-click (or `Ctrl+click`) on empty canvas |
| **Select / deselect single node** | Right-click (or `Ctrl+click`) an existing node |
| **Multi-select / deselect node** | `Shift` + Left-click on node |
| **Auto-connect new node** | Select a node, then right-click on empty canvas |
| **Connect two nodes** | Left-click and drag from source node to target node |
| **Upgrade to bidirectional arrow** | Left-click and drag in reverse direction of existing arrow |
| **Move node(s)** | Left-click and drag a node (or multi-selected nodes) to empty space |
| **Preview a connection** | While dragging, a dashed line follows the cursor and the nearest target node is pulled toward it; it springs back on release |
| **Delete a node or arrow** | Left-click on it (stationary click, no drag) |
| **Undo** | **Undo** button or `Ctrl+Z` / `Cmd+Z` |
| **Redo** | **Redo** button or `Ctrl+Y` / `Ctrl+Shift+Z` / `Cmd+Shift+Z` |
| **Save graph to a file** | **Save** button (choose a `.json` destination) |
| **Load graph from a file** | **Load** button (clears undo/redo history) |
| **Cancel selection / drag** | `Escape` key |

---

## Getting Started

### Prerequisites

- **Java Development Kit (JDK):** Version 21 (LTS) is recommended and is what the build targets. Tested on JDK 21–24.
- A terminal (PowerShell, Command Prompt, or Bash).

> **Note on very new JDKs.** Gradle must itself run on a JDK it supports. On a newer JDK than the
> bundled Gradle recognises (for example JDK 26), the build fails before compiling with
> `Unsupported class file major version 70`. Point the build at a JDK 21 installation instead —
> either by setting `JAVA_HOME`, or by creating a local `gradle.properties` (untracked, since the
> path is machine-specific):
>
> ```properties
> org.gradle.java.home=/path/to/jdk-21
> ```

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
     - `UpgradeArrowCommand`: Reversibly upgrades a directed arrow to bidirectional.
     - `MoveNodesCommand`: Reversibly translates one or more nodes by (dx, dy).

2. **Custom Data Structure (`LinkedStack<T>`):**
   - The undo and redo stacks are powered by a custom singly-linked list stack implementation (`push`, `pop`, `peek`, `size`, `isEmpty`), fulfilling project data structure requirements without using standard Java collection stacks.

3. **Geometry Engine (`GeometryUtils`):**
   - **`calculateAngle(x1, y1, x2, y2)`:** Computes vector angle for arrowhead orientation.
   - **`trimmedSegment(x1, y1, x2, y2, radius)`:** Calculates exact intersection points where arrows meet circle boundaries.
   - **`pointToSegmentDistance(px, py, x1, y1, x2, y2)`:** Orthogonal distance testing for precise arrow hit detection.
   - **`lerp(a, b, t)`:** Linear interpolation; applied once per frame it yields the eased pull and spring-back motion.
   - **`clampMagnitude(dx, dy, max)`:** Caps a displacement vector's length while preserving its direction.

4. **Model Layer (`GraphModel`):**
   - In-memory graph representation tracking nodes (`GraphNode`) and directed edges (`GraphArrow`) with sequential ID allocation.
   - **`loadFrom(nodes, arrows)`:** Replaces all state when opening a file and restarts ID allocation above the highest loaded ID, so nodes created afterward cannot collide with loaded ones.

5. **Animation Layer (`PullMotionModel`):**
   - Holds per-node cosmetic `{dx, dy}` displacements driven by an `AnimationTimer`, easing the pulled node toward the cursor and back to rest. Kept separate from `GraphModel` so the graph itself is never mutated by animation, and so the easing logic is unit-testable without starting a JavaFX toolkit.

6. **Persistence Layer (`GraphJsonCodec`):**
   - Serializes a `GraphModel` to JSON and parses it back. Both directions are hand-written against the fixed, flat schema below, which keeps the project free of third-party dependencies. The reader is strict: malformed documents, duplicate IDs, and arrows referencing absent nodes raise `IllegalArgumentException` instead of loading a partial graph.

   ```json
   {
     "nodes": [
       {"id": 1, "x": 120.0, "y": 80.0}
     ],
     "arrows": [
       {"id": 1, "sourceId": 1, "targetId": 2, "bidirectional": false}
     ]
   }
   ```

   `bidirectional` is optional on read and defaults to `false`, so graph files written before bidirectional arrows existed still open.

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
    │   ├── GeometryUtils.java                  Geometry, easing, and hit-testing helpers
    │   ├── GraphArrow.java                     Directed/bidirectional arrow record
    │   ├── GraphJsonCodec.java                 JSON save/load serializer and parser
    │   ├── GraphModel.java                     Graph state and entity manager
    │   ├── GraphNode.java                      Node model (id, position, radius)
    │   ├── LinkedStack.java                    Custom linked-list stack for history
    │   ├── Main.java                           JavaFX launcher class
    │   ├── MoveNodesCommand.java               Command to translate one or more nodes
    │   ├── PullMotionModel.java                Cosmetic drag-pull offsets and easing
    │   └── UpgradeArrowCommand.java            Command to upgrade arrow to bidirectional
    └── test/java/com/example/grapheditor/
        └── EditorLogicTest.java                Unit tests for geometry, models, and stack
```
