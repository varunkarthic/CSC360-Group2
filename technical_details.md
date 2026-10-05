# Technical Details

How the Graph Editor is built. For what it does and how to run it, see the [README](README.md).

**Contents**
[1. Quick facts](#1-quick-facts) ·
[2. Terms used in this document](#2-terms-used-in-this-document) ·
[3. Tools and libraries](#3-tools-and-libraries) ·
[4. How the code is organised](#4-how-the-code-is-organised) ·
[5. The graph data](#5-the-graph-data) ·
[6. Mouse and keyboard handling](#6-mouse-and-keyboard-handling) ·
[7. Undo and redo](#7-undo-and-redo) ·
[8. Geometry](#8-geometry) ·
[9. Drawing](#9-drawing) ·
[10. The pull animation](#10-the-pull-animation) ·
[11. Saving, loading and exporting](#11-saving-loading-and-exporting) ·
[12. Tests](#12-tests) ·
[13. Why we built it this way](#13-why-we-built-it-this-way) ·
[14. Known limitations](#14-known-limitations)

---

## 1. Quick facts

| | |
|---|---|
| **What it is** | A desktop app for drawing directed graphs (circles joined by arrows) |
| **Language** | Java 21 |
| **Window toolkit** | JavaFX 23.0.2 |
| **Build tool** | Maven 3.9.9, through the included wrapper (`mvnw`) |
| **Tests** | JUnit 5, 45 tests |
| **Code** | 21 classes in one package (`com.example.grapheditor`), about 1,900 lines, plus about 870 lines of tests |

The user clicks to place nodes and drags to connect them. Every edit can be undone and redone. The graph can be saved to a JSON file, loaded back, or exported as a PNG or SVG picture.

---

## 2. Terms used in this document

| Term | Meaning |
|---|---|
| **Node** | A circle on the canvas |
| **Arrow** | A line between two nodes. It points from a *source* node to a *target* node. |
| **Directed graph** | A graph where arrows have a direction (A to B is different from B to A) |
| **Canvas** | The rectangular area of the window we draw on |
| **JavaFX** | Java's library for building windows, buttons and drawings |
| **Maven / Maven Wrapper** | Maven downloads libraries and builds the project. The wrapper (`mvnw`) is a small script that fetches Maven itself, so nobody has to install it. |
| **JUnit** | A library for writing automated tests |
| **Record** | A short way to write a Java class that only holds data. Its fields cannot change after it is created. |
| **Immutable** | Cannot be changed once created. To "change" it, you make a new copy. |
| **Interface** | A list of method names a class promises to provide |
| **Generic (`LinkedStack<T>`)** | A class that works with any type. `T` is a placeholder for that type. |
| **Stack** | A pile where you only add to, or take from, the top (last in, first out) |
| **Linked list** | Items chained together, each one pointing to the next |
| **Hit test** | Checking what is under the mouse when the user clicks |
| **Command pattern** | A design where each edit is an object that knows how to do itself and how to undo itself |
| **Selection** | Nodes the user has marked, shown with a coloured ring |
| **JSON** | A plain-text file format for data, such as `{"nodes": [...]}` |
| **Parser** | Code that reads text and turns it into data |
| **Exception** | Java's way of reporting an error (for example `IllegalArgumentException`) |
| **Classpath / module system** | Two ways Java finds its code and libraries. This project uses the simpler classpath way (no `module-info.java`). |
| **O(1), O(n)** | How the work grows with the amount of data. O(1) is always the same. O(n) grows in step with the number of items. |
| **Easing** | Movement that starts quickly and slows down as it arrives |
| **px** | Pixels, the dots on screen |

---

## 3. Tools and libraries

### 3.1 Libraries (`pom.xml`)

| Library | Version | Used for |
|---|---|---|
| `javafx-controls` | 23.0.2 | Buttons, dialogs |
| `javafx-graphics` | 23.0.2 | Window, canvas, mouse and keyboard events, animation |
| `javafx-swing` | 23.0.2 | Converts the canvas picture to a format Java can write as PNG |
| `junit-jupiter` | 5.11.4 | Tests (test only) |
| `junit-platform-launcher` | 5.11.4 | Lets Maven start the tests (test only) |

We do not use any JSON, logging or graphics libraries. The JSON reader and writer are written by hand (see section 11).

### 3.2 Build plugins

| Plugin | Purpose |
|---|---|
| `maven-surefire-plugin` | Runs the tests with `./mvnw test` |
| `javafx-maven-plugin` | Runs the app with `./mvnw javafx:run`, and picks the right JavaFX files for your operating system |

### 3.3 Running it

| Task | macOS / Linux | Windows |
|---|---|---|
| Run the app | `./mvnw javafx:run` | `mvnw.cmd javafx:run` |
| Run the tests | `./mvnw test` | `mvnw.cmd test` |
| Compile only | `./mvnw compile` | `mvnw.cmd compile` |

`Main` is a tiny class that just calls `EditorApplication.main`. It exists because starting a JavaFX app directly from a class that extends `Application` can fail with "JavaFX runtime components are missing" when run from the classpath. Starting from a plain class avoids that.

### 3.4 Where JavaFX is used

Only `EditorApplication` imports JavaFX. The other 20 classes are plain Java, so the tests can run without opening a window.

---

## 4. How the code is organised

```text
   mouse / keys ──────▶  EditorApplication  ──── draws ───▶ Canvas
                        (window + event handling)
                          │                │
                 creates  │                │ keeps two
                          ▼                ▼
                    EditCommand        LinkedStack ×2
                    (apply / undo)     (undo list, redo list)
                          │
                          │ changes
                          ▼
   GraphJsonCodec ◀──▶ GraphModel ◀── reads ── NodePullAnimation
   (save / load)       (the graph)             (visual effect only)
                          │
                          ▼
                    GeometryUtils (maths helpers)
```

| Part | Classes | Job |
|---|---|---|
| Window and events | `EditorApplication`, `Main`, `EditorHints` | Shows the window, works out what the user meant, draws, shows the hint text |
| Edits | `EditCommand` and 8 classes that implement it | One undoable edit each |
| Graph data | `GraphModel`, `GraphNode`, `GraphArrow` | Holds the nodes and arrows and answers questions about them |
| Selection | `SelectionState` | Remembers which nodes are selected for auto-connect |
| Helpers | `GeometryUtils`, `LinkedStack` | Maths, and the undo/redo storage |
| Animation | `NodePullAnimation` | Small temporary movement while dragging |
| Files | `GraphJsonCodec`, `GraphSvgExporter` | JSON save/load, SVG export |

**The main rule:** the window code never changes the graph directly. Every change is wrapped in a command and run through `execute()`. That is what keeps undo and redo correct.

---

## 5. The graph data

### 5.1 `GraphNode` and `GraphArrow`

Both are immutable records.

```java
record GraphNode(long id, double x, double y, String label)   // x, y = centre of the circle
record GraphArrow(long id, long sourceId, long targetId, boolean bidirectional)
```

- An arrow stores the **ids** of its two nodes, not the node objects themselves. Moving a node means replacing it with a copy at a new position (same id), and the arrow still finds it.
- A node with no label has an empty string as its label.
- Because records never change, undo is simple: a command keeps the old copy and puts it back.

### 5.2 `GraphModel`

It keeps two `LinkedHashMap`s (a map is a lookup table from id to object; this kind also remembers the order items were added), one for nodes and one for arrows. It also keeps two counters that hand out the next free node id and arrow id, starting at 1.

Adding an object with an id that already exists **replaces** the old one. Move, rename and "make two-way" all rely on this.

| Method | What it does |
|---|---|
| `allocateNodeId()`, `allocateArrowId()` | Give out the next unused id |
| `addNode`, `removeNode`, `addArrow`, `removeArrow` | Change the graph |
| `findNode(id)`, `getNodes()`, `getArrows()` | Look things up. The lists returned are copies, so callers cannot change the model by accident. |
| `incidentArrows(nodeId)` | All arrows touching a node |
| `findArrow(src, tgt)`, `arrowExists(src, tgt)` | Look for an arrow in one direction |
| `hitNodeBody(x, y, r)` | The node whose circle contains the point |
| `nearestNodeWithin(x, y, r, excludedId)` | Closest node within a distance, skipping one node |
| `nearestOverlappingNode(x, y, r, eps)` | Closest node that a new node at this point would overlap |
| `loadFrom(nodes, arrows)` | Replace everything, and set the id counters to the highest id plus 1 |

Distances are compared as **squared** values (`dx² + dy²`), which skips a slow square-root step and gives the same ordering.

---

## 6. Mouse and keyboard handling

All of this is in `EditorApplication`. The handlers are attached to the canvas, and the right-click context menu is turned off.

### 6.1 Right-click (create and select)

Right-click, or `Ctrl` + left-click for trackpads. It is handled the moment the button goes down.

1. If the click is on a node, or close enough that a new node would overlap one, then select or deselect that node. `Shift` adds to the selection instead of replacing it. Selected nodes get an orange ring.
2. Otherwise, if the click is closer than one radius to the canvas edge, do nothing.
3. Otherwise create a new node.
   - If some nodes are selected, one command adds the new node plus an arrow from **each** selected node to it (`AddConnectedNodeCommand`). The selection is then cleared.
   - If nothing is selected, just add the node (`AddNodeCommand`).

### 6.2 Left-click and drag

We only decide what the gesture was when the button is **released**.

```text
press   : clear orange selection, remember the press point and any node under it
drag    : remember the furthest the mouse has moved from the press point.
          If we started on a node, show a dashed preview line and
          pick a nearby node to "pull" (see section 10)
release : moved more than 5 px?  yes → it was a drag
                                 no  → it was a click
```

**Drag** (only matters if it started on a node):

| Where it was released | What happens |
|---|---|
| On another node, or within 90 px of one | If an arrow in that direction exists: nothing. If the opposite arrow exists: make it two-way (`UpgradeArrowCommand`). Otherwise add a new arrow. |
| Empty space | Move the node by the distance dragged (`MoveNodesCommand`). If the node is part of the purple multi-selection, all of those nodes move together. |

The 90 px allowance matches the distance at which the pull animation starts. So if the animation showed a node reaching for the cursor, letting go will connect to it.

**Click** (moved 5 px or less):

| What is under the mouse | What happens |
|---|---|
| A node, with `Shift` held | Add or remove it from the purple multi-selection (no command, so it is not undoable) |
| A node | Delete it and its arrows, after a 0.3 second wait (`DeleteNodeCommand`) |
| An arrow (the press and the release must both hit it) | Delete the arrow (`DeleteArrowCommand`) |
| Nothing | Nothing |

**Double-click on a node** opens a text box to set its label (`RenameNodeCommand`). The 0.3 second wait before deleting exists so that the first click of a double-click does not delete the node before the second click arrives. The double-click cancels the waiting delete.

### 6.3 Keyboard and buttons

| Input | Action |
|---|---|
| `Ctrl/Cmd + Z` | Undo (Cmd on macOS, Ctrl elsewhere) |
| `Ctrl/Cmd + Shift + Z` | Redo |
| `Esc` | Clear both selections, cancel the current gesture, stop the animation |
| Undo, Redo, Save, Load, Export PNG, Export SVG buttons | Same actions, plus the file dialogs |

A line of hint text in the toolbar changes with the current state (`EditorHints`), for example "Node selected: right-click empty space to add a linked node".

---

## 7. Undo and redo

### 7.1 The command interface

```java
public interface EditCommand {
    void apply(GraphModel model);   // do the edit (also used to redo it)
    void undo(GraphModel model);    // reverse it exactly
}
```

### 7.2 The eight commands

| Command | What it remembers | `apply` | `undo` |
|---|---|---|---|
| `AddNodeCommand` | the node | add it | remove it |
| `AddArrowCommand` | the arrow | add it | remove it |
| `AddConnectedNodeCommand` | the node and a list of arrows | add node, then arrows | remove arrows, then node |
| `DeleteNodeCommand` | the node and its arrows | remove arrows, then node | add node, then arrows |
| `DeleteArrowCommand` | the arrow | remove it | add it back |
| `UpgradeArrowCommand` | the arrow before and after (same id, two-way) | put in the "after" copy | put back the "before" copy |
| `MoveNodesCommand` | the original nodes, and how far they moved | put in moved copies | put back the originals |
| `RenameNodeCommand` | the node before and after (new label) | put in the "after" copy | put back the "before" copy |

Order matters: an arrow is always added after its nodes and removed before them. That way the graph never contains an arrow pointing at a node that does not exist.

### 7.3 `LinkedStack<T>`

Our own stack, built from a chain of small `Entry` objects. Each entry holds a value and points to the one below it. (Building it ourselves was a course requirement.)

| Operation | Work | Notes |
|---|---|---|
| `push(v)` | O(1) | Refuses `null` |
| `pop()`, `peek()` | O(1) | Throw `NoSuchElementException` if the stack is empty |
| `isEmpty()`, `size()` | O(1) | `size` is kept in a counter |
| `clear()` | O(1) | Just forgets the top entry |

### 7.4 How the three actions work

```text
execute(cmd): run cmd.apply → push on undo stack → empty the redo stack → redraw
undo():       pop from undo stack → run cmd.undo → push on redo stack → redraw
redo():       pop from redo stack → run cmd.apply → push on undo stack → redraw
```

Making a new edit empties the redo stack, because the "future" it pointed to no longer exists. Loading a file empties both stacks.

---

## 8. Geometry

`GeometryUtils` holds small maths functions. Drawing and hit testing both use them, so what you see is what you can click.

| Function | What it does |
|---|---|
| `distanceSquared` | `dx² + dy²` |
| `angleBetweenPoints` | The direction from one point to another, using `atan2` (works in every direction) |
| `trimmedArrowLine` | Takes two node centres and shortens the line at both ends by the node radius, so the arrow starts and ends on the circle edge instead of its centre |
| `pointToSegmentDistance` | How far a point is from a line segment. Used to check whether a click landed on an arrow. |
| `lerp(a, b, t)` | A point `t` of the way from `a` to `b` (`a + (b − a)·t`) |
| `clampMagnitude` | Shortens a movement to a maximum length without changing its direction |

**Arrowhead:** a filled triangle, 12 px long, at the end of the line. Its two back corners sit 25° either side of the line. A two-way arrow gets a second head at the other end.

**Clicking an arrow:** for each arrow we measure the distance from the click to the visible part of its line. If it is 6 px or less, it counts as a hit, and the closest arrow wins.

---

## 9. Drawing

The app redraws **everything** from scratch each time. `render()` clears the canvas and draws the whole graph again. It runs after every change and on every animation frame. This is simple, and fast enough for graphs this size.

**Draw order** (later items appear on top):

1. Background
2. All arrows
3. The dashed preview line (only while dragging from a node)
4. All nodes (fill, outline, labels, selection rings)

| Element | Style |
|---|---|
| Background | `#f8fafc` |
| Node | Fill `#3b82f6`, 2 px outline `#1d4ed8`, radius 20 px |
| Selection ring (right-click) | Orange `#f59e0b` |
| Multi-selection ring (`Shift` + left-click) | Purple `#8b5cf6` |
| Arrow line | `#334155`, 2.5 px |
| Arrowhead | `#dc2626` |
| Preview line | `#94a3b8`, dashed 8 px line and 6 px gap |
| Label | White, size 11 |

Nodes and arrows are drawn using the animated position, so a pulled node and its arrows move together. The window opens wide enough for the whole toolbar, with an 800 × 600 canvas below it, and it can be resized. The canvas follows the size of the area it sits in and is redrawn whenever that changes. Toolbar buttons never shrink below their text. Only the hint label gives up space (it ends in "..."), and the window has a minimum width so every button always fits. **Full Screen** (or `F11`) toggles full screen.

---

## 10. The pull animation

While you drag from a node, the closest other node within 90 px of the mouse leans toward it a little. When you let go, it eases back. It is only for feedback.

`NodePullAnimation` keeps a small x/y **offset** for each node. On every frame (about 60 times a second):

```text
goal   = the pulled node ?  the direction to the mouse, capped at 14 px  :  (0, 0)
offset = offset + 35 % of the gap to the goal
```

Taking a fixed share of the remaining gap each frame gives easing: quick at first, then it settles. When every offset is below 0.05 px and nothing is being pulled, the offsets are dropped.

The animation is driven by a JavaFX `AnimationTimer`, which calls our code on every frame. It starts the first time a drag updates the pull, and **stops itself** once everything is at rest, so the app uses no CPU when idle.

Things that are always true:

- Offsets are never stored in `GraphModel`. Clicks and commands use the real positions.
- If a node is deleted during the animation, its offset just fades to zero.
- `reset()` clears everything at once (used by `Esc` and after loading a file).
- The class has no JavaFX code in it, so it is unit tested.

---

## 11. Saving, loading and exporting

### 11.1 The JSON file

```json
{
  "nodes": [
    {"id": 1, "x": 207.0, "y": 89.0, "label": "Start"}
  ],
  "arrows": [
    {"id": 1, "sourceId": 1, "targetId": 2, "bidirectional": true}
  ]
}
```

`label` is only written when it is not empty, and `bidirectional` may be left out (it then means `false`). `test.json` in the project root is a ready-made sample.

### 11.2 Writing (`GraphJsonCodec.toJson`)

Builds the text piece by piece with a `StringBuilder`, one entry per line, in the order items were added. Numbers use `Double.toString`, which always uses `.` as the decimal point whatever the computer's language setting. A coordinate that is `NaN` (not a number) or infinite throws an `IllegalArgumentException`. Special characters in labels (quotes, backslashes, control characters) are escaped, so any label comes back exactly as it was saved.

### 11.3 Reading (`GraphJsonCodec.fromJson`)

A small hand-written parser (`JsonScanner`) reads the text one character at a time. It only supports what this file format needs: objects, arrays, strings, numbers and `true`/`false`.

The reader is strict. A file is rejected with an `IllegalArgumentException` if any of these is true:

| Problem | Example |
|---|---|
| Broken syntax | Missing bracket, comma or colon; bad number; string never closed (the error says where) |
| Extra text after the closing `}` | `{...} garbage` |
| A top-level key other than `nodes` and `arrows` | `"color": "red"` |
| `nodes` or `arrows` is missing | |
| `x`, `y` or an id is missing, not a number, or `NaN`/infinite | |
| An id that is not a whole number of at least 1 | `0`, `1.5` |
| Two nodes or two arrows with the same id | |
| An arrow pointing at a node that is not in the file | |
| `bidirectional` is not `true` or `false` | `"yes"` |
| `label` is not a string, or has a bad escape | |

Older files without `label` or `bidirectional` still load.

### 11.4 Save and load in the app

```text
Save: file dialog (*.json, default "graph.json") → toJson → write file as UTF-8
Load: file dialog → read file → fromJson
        ├─ can't read the file     → "Could not read graph" message
        ├─ not valid               → "Not a valid graph file" message (current graph untouched)
        └─ OK → copy into the model, clear undo/redo, clear orange selection,
                cancel the current gesture, reset the animation, redraw
```

The file is read and checked into a **separate** `GraphModel` first. Only if that works is it copied into the real one, so a bad file can never leave you with half a graph.

### 11.5 Export

**PNG** (`exportPng`): takes a snapshot picture of the canvas, converts it with `SwingFXUtils` into a Java `BufferedImage`, and writes it out with `ImageIO`. This is why `javafx-swing` is a dependency.

**SVG** (`GraphSvgExporter`): SVG is a text format for vector pictures (shapes, not pixels), so it stays sharp at any zoom. We do not take a picture. We go through the `GraphModel` and write one SVG element per item:

- a `<line>` for each arrow, shortened to the circle edges
- a `<polygon>` for each arrowhead (two for a two-way arrow)
- a `<circle>` for each node (radius 20, same colours as the canvas)
- a `<text>` for each label (XML special characters escaped)

`GraphSvgExporter` has no JavaFX code, so it is unit tested.

---

## 12. Tests

`EditorLogicTest` has 45 JUnit tests. They cover everything except the JavaFX window code, and they run without opening a window.

| Area | What is checked |
|---|---|
| Geometry | Angles in all directions, shortened arrow ends, `lerp`, `clampMagnitude` |
| `LinkedStack` | Last-in-first-out order, behaviour when empty |
| `GraphModel` | Hit tests, nearest-node searches, skipping the source node |
| Commands | Apply then undo gives back the original graph, for every command; two-way upgrade; moving several nodes |
| JSON | Save then load gives the same graph (including an empty one); ids after loading; real file save and load in a temporary folder; two-way flag kept, defaulted or rejected; whitespace; negative and exponent numbers; many kinds of bad input |
| Pull animation | Leans toward the mouse, eases back, never changes the model, reset, deleted node |

Run them with `./mvnw test`.

**Not covered by automated tests:** the mouse handling and drawing inside `EditorApplication`. We checked those by hand (see the [demo video](media/demo_video_1.mp4)).

---

## 13. Why we built it this way

| Choice | Reason |
|---|---|
| **Command pattern** | Undo and redo work the same way for every edit. A new kind of edit needs no change to the history code. |
| **Our own `LinkedStack`** | Course requirement. Also gives O(1) push and pop. |
| **Records for nodes and arrows** | They never change, so commands can safely keep old copies. |
| **Arrows store node ids** | Replacing a node with a moved copy does not break its arrows. |
| **`LinkedHashMap`** | Fast lookup by id, and a steady order for drawing and saving. |
| **JavaFX kept out of everything except `EditorApplication`** | The rest can be tested without a window. |
| **Animation offsets kept out of the model** | A visual effect can never damage the data or change what a click hits. |
| **One geometry class for drawing and hit tests** | The clickable area always matches what is on screen. |
| **Hand-written JSON** | No extra library; the format is small and fixed. |
| **Check the whole file before loading** | A bad file cannot half-replace the current graph. |
| **Squared distances** | Avoids square roots in loops that run on every mouse move. |
| **Separate `Main` class** | Avoids the JavaFX "runtime components are missing" error. |

---

## 14. Known limitations

Good to know before a demo or a change:

- **Purple multi-selection is not cleared** by undo, redo or load, so it may hold ids of nodes that no longer exist. Drawing and moving skip them. `Esc` clears it.
- **Dropping a moved node within 90 px of another node connects them instead of moving.** Drop it further away to move it.
- **One arrow per direction.** Dragging a second arrow the same way is ignored.
- **Drawing and hit tests check every node and arrow**, so the time grows with the size of the graph. Fine for hand-drawn graphs, not for thousands of items.
- **No zoom or pan.** The canvas grows with the window, but there is no zoom or scrolling. Making the window smaller hides nodes past its edge instead of moving them. PNG and SVG export use the current canvas size. New nodes must be at least one radius from the edge, but dragging does not stop a node leaving the canvas.
- **No weights or colours** on nodes or arrows. A node has an id, a position and a label. An arrow has an id, two node ids and a direction setting.
- **Long labels** are not clipped or wrapped and can spill outside the 20 px circle.
- **Deleting a node by click waits 0.3 seconds** (see section 6.2).
- **The JSON parser** does not accept comments. The format does not need them.
- **No automated tests for `EditorApplication`.** See section 12.
