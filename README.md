
# CSC360 Course Project

## Group 2

> A JavaFX desktop editor for creating and connecting graph nodes.

---

## Course Information

| Field | Details |
| --- | --- |
| Course code | CSC360 |
| Course name | Computer Graphics and Digital Image Processing |
| Session | Monsoon 2026 |
| Group | 2 |

## Group Members

| No. | Name | Enrolment Number |
| --- | --- | --- |
| 1 | Varun | AU2520215 |
| 2 | Satvik | AU2520039 |
| 3 | Garv | AU2520247 |
| 4 | Shambhavee | AU2500016 |

## Running It

Requires a JDK (21+) installed and on your `PATH`. The Gradle wrapper handles everything else — no local Gradle install needed.

### macOS / Linux

```
git clone https://github.com/varunkarthic/CSC360-Group2.git
cd CSC360-Group2
./gradlew run
```

### Windows

```
git clone https://github.com/varunkarthic/CSC360-Group2.git
cd CSC360-Group2
gradlew.bat run
```

(Or double-click `gradlew.bat` from File Explorer inside the project folder.)

## Features

- Create fixed-radius nodes on the canvas.
- Overlapping placement selects the existing node instead of creating a new one.
- Select a node, then create another to auto-connect them with a directed arrow.
- Drag between two existing nodes to connect them.
- Click a node or arrow to delete it; deleting a node removes its arrows too.
- Undo / redo every change.

## Controls

| Action | Control |
| --- | --- |
| Create a node | Right-click (or `Ctrl+click`) empty space |
| Select / deselect a node | Right-click (or `Ctrl+click`) the node |
| Auto-connect new node to selection | Select a node, then right-click empty space |
| Connect two existing nodes | Left-click and drag from one node to another |
| Delete a node or arrow | Left-click it (no drag) |
| Undo | Undo button, or `Cmd+Z` / `Ctrl+Z` |
| Redo | Redo button, or `Cmd+Shift+Z` / `Ctrl+Shift+Z` |
| Cancel selection / drag | `Escape` |
