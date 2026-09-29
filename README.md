<h1 align="center">Graph Editor</h1>

<p align="center">
  A JavaFX desktop app for drawing, connecting and editing directed graphs.
</p>

<p align="center">
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-437291?style=flat-square">
  <img alt="JavaFX 23" src="https://img.shields.io/badge/JavaFX-23-2b6cb0?style=flat-square">
  <img alt="Maven" src="https://img.shields.io/badge/build-Maven-c71a36?style=flat-square">
  <img alt="27 unit tests" src="https://img.shields.io/badge/tests-27%20JUnit-2e8b57?style=flat-square">
  <img alt="Zero dependencies beyond JavaFX" src="https://img.shields.io/badge/deps-JavaFX%20only-444?style=flat-square">
</p>

<p align="center">
  <a href="#demo">Demo</a> ·
  <a href="#quick-start">Quick start</a> ·
  <a href="#how-to-use">How to use</a> ·
  <a href="#how-it-works">How it works</a> ·
  <a href="#features-in-detail">Features</a> ·
  <a href="technical_details.md">Technical details</a> ·
  <a href="#team">Team</a>
</p>

---

Click to place nodes, drag to connect them, and the editor handles the rest:
arrows clip cleanly to node edges, every action can be undone, and the whole
graph saves to a plain JSON file.

- **Build graphs by mouse.** Place nodes, drag arrows between them, move them around.
- **Bidirectional arrows.** Drag back along an existing arrow to make it two-way.
- **Undo / redo everything.** Each action is a reversible command.
- **Save / load JSON.** Human-readable files, no external libraries.

> **Next read:** [technical_details.md](technical_details.md) covers the packages used, architecture, algorithms, file format and limitations in depth.

## Team

**CSC360 · Computer Graphics and Digital Image Processing · Monsoon 2026 · Group 2**

| No. | Name | Enrolment No. |
|:---:|:---|:---|
| 1 | Varun | AU2520215 |
| 2 | Satvik | AU2520039 |
| 3 | Garv | AU2520247 |
| 4 | Shambhavee | AU2500016 |

## Demo

<p align="center">
  <video src="https://github.com/varunkarthic/CSC360-Group2/raw/master/media/demo_video_1.mp4" controls muted width="100%"></video>
</p>

<p align="center"><sub>Video not playing? <a href="media/demo_video_1.mp4">Open the demo file</a>.</sub></p>

## Quick start

**Requirement:** JDK 21. Maven is not needed; the included wrapper downloads it.

| | macOS / Linux | Windows |
|---|---|---|
| **Run** | `./mvnw javafx:run` | `mvnw.cmd javafx:run` |
| **Test** | `./mvnw test` | `mvnw.cmd test` |

## How to use

| To… | Do this |
|---|---|
| **Create a node** | Right-click (or `Ctrl`+click) empty canvas |
| **Select a node** | Right-click it again to deselect |
| **Create a node and connect it** | Select a node, then right-click empty canvas |
| **Connect two nodes** | Left-drag from one node onto another |
| **Make an arrow two-way** | Left-drag along an existing arrow in reverse |
| **Move node(s)** | Left-drag a node to empty space |
| **Multi-select** | `Shift` + left-click nodes (purple ring); drag one to move all |
| **Delete a node or arrow** | Left-click it without dragging |
| **Undo / Redo** | Buttons, or `Ctrl/Cmd+Z` and `Ctrl/Cmd+Shift+Z` |
| **Save / Load** | **Save** / **Load** buttons (`.json`) |
| **Cancel** | `Esc` |

## How it works

Four small layers. The UI never edits the graph directly; it wraps every change in a command.

```text
 mouse / keyboard
        │
        ▼
 EditorApplication ── draws ──▶ canvas
        │  creates
        ▼
   EditCommand ──apply / undo──▶ GraphModel ◀── GraphJsonCodec (save / load)
        │                            ▲
   LinkedStack (undo, redo)          └── GeometryUtils (hit tests, arrow math)
```

| Part | Role |
|---|---|
| **`EditorApplication`** | JavaFX window. Turns mouse gestures into commands and draws the canvas. |
| **`EditCommand`** | Interface with `apply()` and `undo()`. Seven implementations cover every edit. |
| **`LinkedStack<T>`** | Hand-written linked-list stack holding undo and redo history. |
| **`GraphModel`** | Nodes, arrows, ID allocation and hit-test queries. |
| **`GeometryUtils`** | Angles, boundary clipping, point-to-segment distance, easing maths. |
| **`EditorHints`** | Chooses the short instruction line in the top bar for the current state. |
| **`PullMotionModel`** | Cosmetic drag animation. Never touches the real graph. |
| **`GraphJsonCodec`** | Writes and strictly parses the JSON format. |

**Save format**

```json
{
  "nodes":  [{"id": 1, "x": 120.0, "y": 80.0}],
  "arrows": [{"id": 1, "sourceId": 1, "targetId": 2, "bidirectional": false}]
}
```

## Features in detail

| Feature | What happens |
|---|---|
| **Fixed-size nodes** | Radius 20 px on an 800×600 canvas; a right-click too close to an existing node selects it instead of stacking a new one on top. |
| **Selection** | Single select shows an orange ring, multi-select a purple ring. |
| **Auto-connect** | Right-clicking empty space with a node selected creates the new node *and* its arrow as one undo step, and the orange ring disappears as soon as the arrow is drawn. |
| **Context hints** | The top bar shows a one-line tip that changes with the state: idle, node selected, multi-selected, or dragging a connection. |
| **Boundary clipping** | Arrows start and end on the circle edge (via `atan2`), not the centre, with filled arrowheads. |
| **Precise hit testing** | Arrows are clicked by point-to-segment distance (6 px tolerance). |
| **Drag-connect preview** | A dashed line follows the cursor and the nearest target eases toward it, then springs back. Purely visual; real coordinates are always used for hit tests. |
| **Bidirectional upgrade** | Reverse drag turns one arrow into a double-headed one instead of adding a duplicate. |
| **Safe node deletion** | Removes the node plus its arrows; undo restores all of them. |
| **Strict JSON loading** | Bad syntax, duplicate IDs, or arrows to missing nodes are rejected with a clear error. New IDs resume above the highest loaded ID. |
| **Old files still open** | `bidirectional` is optional and defaults to `false`. |

## Project structure

```text
CSC360-Group2/
├── pom.xml                  Maven build
├── mvnw, mvnw.cmd           Maven wrapper
├── technical_details.md     In-depth technical documentation
├── test.json                Sample graph to try Load
├── media/demo_video_1.mp4   Demo recording
└── src/
    ├── main/java/com/example/grapheditor/
    │   ├── Main.java                  Launcher
    │   ├── EditorApplication.java     UI, gestures, rendering
    │   ├── EditCommand.java           Command interface
    │   ├── Add{Node,Arrow,ConnectedNode}Command.java
    │   ├── Delete{Node,Arrow}Command.java
    │   ├── UpgradeArrowCommand.java
    │   ├── MoveNodesCommand.java
    │   ├── LinkedStack.java           Undo / redo stack
    │   ├── GraphModel.java            Graph state and queries
    │   ├── GraphNode.java             Node record
    │   ├── GraphArrow.java            Arrow record
    │   ├── GeometryUtils.java         Geometry helpers
    │   ├── EditorHints.java           Top-bar hint text per state
    │   ├── PullMotionModel.java       Drag-pull animation state
    │   └── GraphJsonCodec.java        JSON save / load
    └── test/java/com/example/grapheditor/
        └── EditorLogicTest.java       27 unit tests
```

## Team

**CSC360 · Computer Graphics and Digital Image Processing · Monsoon 2026 · Group 2**

| No. | Name | Enrolment No. |
|:---:|:---|:---|
| 1 | Varun | AU2520215 |
| 2 | Satvik | AU2520039 |
| 3 | Garv | AU2520247 |
| 4 | Shambhavee | AU2500016 |
