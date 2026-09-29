# Technical Details

Deep-dive into how the Graph Editor is built. For what it does and how to run it, see the [README](README.md).

**Contents**
[1. Overview](#1-overview) ·
[2. Tech stack and packages](#2-tech-stack-and-packages) ·
[3. Build and run](#3-build-and-run) ·
[4. Architecture](#4-architecture) ·
[5. Data model](#5-data-model) ·
[6. Input handling](#6-input-handling) ·
[7. Command pattern and history](#7-command-pattern-and-history) ·
[8. Geometry](#8-geometry) ·
[9. Rendering](#9-rendering) ·
[10. Drag-pull animation](#10-drag-pull-animation) ·
[11. Persistence](#11-persistence) ·
[12. Testing](#12-testing) ·
[13. Constants](#13-constants-reference) ·
[14. Design decisions](#14-design-decisions) ·
[15. Known limitations](#15-known-limitations)

---

## 1. Overview

| | |
|---|---|
| **Type** | Desktop GUI application |
| **Language** | Java 21 |
| **UI toolkit** | JavaFX 23.0.2 (`Canvas` 2D drawing) |
| **Build** | Maven 3.9.9 via the Maven Wrapper |
| **Tests** | JUnit 5.11.4, 27 tests |
| **Java package** | `com.example.grapheditor` (single flat package, 17 classes) |
| **Size** | about 1,700 lines of main code, about 550 lines of tests |
| **Module system** | Not used (no `module-info.java`); runs on the classpath |

The user edits a **directed graph** on an 800×600 canvas. All edits are wrapped in **command objects** so they can be undone and redone. The graph can be saved to and loaded from a **JSON** file. A short **animation** gives feedback while dragging a connection.

---

## 2. Tech stack and packages

### 2.1 Third-party dependencies (`pom.xml`)

| Artifact | Version | Scope | Why |
|---|---|---|---|
| `org.openjfx:javafx-controls` | 23.0.2 | compile | Buttons, alerts, layout controls |
| `org.openjfx:javafx-graphics` | 23.0.2 | compile | Stage, Scene, Canvas, input events, animation |
| `org.junit.jupiter:junit-jupiter` | 5.11.4 (via BOM) | test | Test framework and assertions |
| `org.junit.platform:junit-platform-launcher` | 5.11.4 (via BOM) | test | Lets Surefire launch JUnit 5 |
| `org.junit:junit-bom` | 5.11.4 | import | Keeps all JUnit artifacts on matching versions |

`javafx-base` comes in automatically as a dependency of the two JavaFX modules above. No JSON, logging or utility libraries are used.

### 2.2 Build plugins

| Plugin | Version | Purpose |
|---|---|---|
| `maven-surefire-plugin` | 3.5.2 | Runs the JUnit 5 tests (`mvn test`) |
| `javafx-maven-plugin` | 0.0.8 | Runs the app (`javafx:run`); resolves the correct native JavaFX jars for the current OS |

The compiler is configured with `maven.compiler.release=21` and UTF-8 source encoding. The main class is set in the `main.class` property (`EditorApplication`).

### 2.3 JavaFX packages used

| Package | Classes used | Used for |
|---|---|---|
| `javafx.application` | `Application` | App lifecycle (`start`, `launch`) |
| `javafx.stage` | `Stage`, `FileChooser` | Window; open/save dialogs |
| `javafx.scene` | `Scene` | Root scene and keyboard accelerators |
| `javafx.scene.canvas` | `Canvas`, `GraphicsContext` | All drawing |
| `javafx.scene.control` | `Button`, `Alert` | Toolbar buttons; error dialogs |
| `javafx.scene.layout` | `BorderPane`, `HBox` | Toolbar on top, canvas in centre |
| `javafx.scene.input` | `MouseEvent`, `MouseButton`, `KeyCode`, `KeyCodeCombination`, `KeyCombination` | Mouse gestures and shortcuts |
| `javafx.scene.paint` | `Color` | Palette |
| `javafx.geometry` | `Insets` | Toolbar padding |
| `javafx.animation` | `AnimationTimer` | Per-frame animation loop |

Only `EditorApplication` imports JavaFX. Every other class is plain Java, which is why the logic can be unit-tested without starting a UI.

### 2.4 Java standard library used

| Package | Used for |
|---|---|
| `java.util` | `List`, `Map`, `LinkedHashMap`, `Set`, `HashSet`, `ArrayList`, `Iterator`, `Objects`, `NoSuchElementException` |
| `java.util.function` | `BiConsumer` (JSON writer helper) |
| `java.nio.file` / `java.nio.charset` | `Files.readString` / `writeString` with UTF-8 |
| `java.io` | `File` (from `FileChooser`), `IOException` |

Java language features used: **records** (`GraphNode`, `GraphArrow`), **pattern matching for `instanceof`** (JSON reader), **generics** (`LinkedStack<T>`), lambdas and method references, `final` and static nested classes.

---

## 3. Build and run

| Task | Command (macOS / Linux) | Windows |
|---|---|---|
| Run the app | `./mvnw javafx:run` | `mvnw.cmd javafx:run` |
| Run all tests | `./mvnw test` | `mvnw.cmd test` |
| Compile only | `./mvnw compile` | `mvnw.cmd compile` |

- The wrapper (`.mvn/wrapper/maven-wrapper.properties`) downloads Apache Maven 3.9.9 on first use, so no local Maven is needed.
- `Main` is a thin launcher that calls `EditorApplication.main`. A launcher class that does **not** extend `Application` avoids the "JavaFX runtime components are missing" error when the app is started from the classpath. `javafx:run` itself uses `EditorApplication` directly.

---

## 4. Architecture

```text
                        ┌─────────────────────────────┐
   mouse / keys ───────▶│      EditorApplication      │──── draws ───▶ Canvas
                        │  (view + controller)        │
                        └───────┬─────────────┬───────┘
                   creates      │             │ owns
                                ▼             ▼
                        ┌─────────────┐   ┌───────────────┐
                        │ EditCommand │   │ LinkedStack ×2│  undo / redo
                        │ apply/undo  │   └───────────────┘
                        └──────┬──────┘
                               │ mutates
                               ▼
   ┌────────────────┐   ┌─────────────┐   reads    ┌────────────────┐
   │ GraphJsonCodec │◀─▶│ GraphModel  │◀───────────│ PullMotionModel│ (cosmetic only)
   └────────────────┘   └──────┬──────┘            └────────────────┘
                               │ uses
                        ┌──────▼──────┐
                        │GeometryUtils│  (pure static maths)
                        └─────────────┘
```

**Roles, mapped to the classes**

| Layer | Classes | Responsibility |
|---|---|---|
| View / controller | `EditorApplication`, `Main` | UI, event handling, deciding *what the user meant*, drawing |
| Commands | `EditCommand` + 7 implementations | One reversible edit each |
| Model | `GraphModel`, `GraphNode`, `GraphArrow` | Graph state and queries |
| Utilities | `GeometryUtils`, `LinkedStack` | Maths and history storage |
| Animation | `PullMotionModel` | Temporary visual offsets |
| Persistence | `GraphJsonCodec` | JSON read and write |

**Key rule:** the UI never mutates the model directly. Every change is a command run through `execute()`, so history stays correct.

---

## 5. Data model

### 5.1 `GraphNode` and `GraphArrow`

Both are immutable Java **records**.

```java
record GraphNode(long id, double x, double y)                       // x, y = circle centre
record GraphArrow(long id, long sourceId, long targetId, boolean bidirectional)
```

- Arrows refer to nodes **by id**, not by object reference. This keeps them valid when a node is replaced with a moved copy.
- `GraphNode.withPosition(x, y)` returns a new node with the same id.
- The three-argument `GraphArrow` constructor defaults `bidirectional` to `false`.
- Immutability is what makes undo simple: a command keeps the old copy and puts it back.

### 5.2 `GraphModel`

| Field | Type | Notes |
|---|---|---|
| `nodes` | `LinkedHashMap<Long, GraphNode>` | Keyed by id; insertion order preserved |
| `arrows` | `LinkedHashMap<Long, GraphArrow>` | Keyed by id; insertion order preserved |
| `nextNodeId`, `nextArrowId` | `long` | Start at 1; increase on each `allocate…Id()` |

`addNode` / `addArrow` use `Map.put`, so adding an object with an **existing id replaces it in place**. Move and upgrade commands rely on this.

| Method | Purpose |
|---|---|
| `allocateNodeId()`, `allocateArrowId()` | Next unique id |
| `addNode`, `removeNode`, `addArrow`, `removeArrow` | Mutation |
| `findNode(id)`, `getNodes()`, `getArrows()` | Lookup; lists are defensive copies (`List.copyOf`) |
| `incidentArrows(nodeId)` | Arrows touching a node (for delete/undo) |
| `findArrow(src, tgt)`, `arrowExists(src, tgt)` | Directed-pair lookup |
| `hitNodeBody(x, y, r)` | Node whose circle contains the point |
| `nearestNodeWithin(x, y, r, excludedId)` | Closest node inside a radius, skipping one |
| `nearestConflictingNode(x, y, r, eps)` | Closest node within `2r + eps` (placement conflict) |
| `loadFrom(nodes, arrows)` | Replace everything; reset id counters to `max id + 1` |

Distance checks compare **squared** distances, which avoids `sqrt`.

---

## 6. Input handling

All gesture logic lives in `EditorApplication`. Handlers are attached to the canvas: `onMousePressed`, `onMouseDragged`, `onMouseReleased`. The context menu is suppressed.

### 6.1 Secondary gesture (create / select)

A **secondary gesture** is a right-click, or a left-click with `Ctrl` held (for trackpads). It is handled on *press*:

1. Hit-test the point; if no node is hit, look for a node within `2 × radius` (`nearestConflictingNode`).
2. If a node was found: toggle it as the single selected node (`selectedNodeId`).
3. Else, if the point is closer than one radius to the canvas edge: ignore.
4. Else create a node with a new id:
   - a node is selected → `AddConnectedNodeCommand` (new node + arrow from the selected node), then clear the selection;
   - otherwise → `AddNodeCommand`.

### 6.2 Primary gesture (click, drag)

A left-button gesture is classified only when the button is **released**:

```text
press   : clear single selection; store press point; note node under cursor (if any)
drag    : track the largest distance moved from the press point;
          if pressed on a node → update preview cursor + choose node to "pull"
release : max distance > 5 px ?  → completeDrag(...)
                              no → completeClick(...)
```

**`completeDrag`** (pressed on a node; a press on empty space is ignored):

| Release position | Result |
|---|---|
| On, or within 90 px of, another node | If that direction already exists → nothing. If the reverse arrow exists → `UpgradeArrowCommand` (skipped when already bidirectional). Else → `AddArrowCommand`. |
| Empty space | `MoveNodesCommand` by `(release − press)`. Moves every multi-selected node if the pressed node is one of them, else just the pressed node. |

The 90 px fallback (`resolveConnectTarget`) matches the distance at which the pull animation starts, so a connection that *looked* successful really connects.

**`completeClick`** (movement ≤ 5 px):

| Under the cursor | Result |
|---|---|
| Node + `Shift` held | Toggle in the multi-selection (no command) |
| Node | `DeleteNodeCommand` (node and its incident arrows) |
| Arrow (both press and release hit the same arrow) | `DeleteArrowCommand` |
| Nothing | Nothing |

### 6.3 Keyboard and toolbar

| Input | Action |
|---|---|
| `Ctrl/Cmd + Z` | Undo (`SHORTCUT_DOWN` maps to Cmd on macOS, Ctrl elsewhere) |
| `Ctrl/Cmd + Shift + Z` | Redo |
| `Esc` | Clear selections, abort the current gesture, reset the pull animation |
| Undo / Redo / Save / Load buttons | Same actions as above and the file dialogs |

---

## 7. Command pattern and history

### 7.1 The interface

```java
public interface EditCommand {
    void apply(GraphModel model);   // do (or redo) the edit
    void undo(GraphModel model);    // reverse it exactly
}
```

### 7.2 The seven commands

| Command | State stored | `apply` | `undo` |
|---|---|---|---|
| `AddNodeCommand` | node | add node | remove node |
| `AddArrowCommand` | arrow | add arrow | remove arrow |
| `AddConnectedNodeCommand` | node, arrow | add node, then arrow | remove arrow, then node |
| `DeleteNodeCommand` | node, incident arrows (snapshot) | remove arrows, then node | add node, then arrows |
| `DeleteArrowCommand` | arrow | remove arrow | add arrow |
| `UpgradeArrowCommand` | `before`, `after` (bidirectional copy, same id) | put `after` | put `before` |
| `MoveNodesCommand` | original nodes, `dx`, `dy` | put copies moved by `(dx, dy)` | put originals back |

Order matters where two objects are involved: an arrow is always added *after* its nodes and removed *before* them, so the model never holds an arrow pointing at a missing node.

### 7.3 History: `LinkedStack<T>`

A generic singly linked LIFO stack built from a private static `Entry<T>(value, next)`:

| Operation | Cost | Notes |
|---|---|---|
| `push(v)` | O(1) | Rejects `null` (`Objects.requireNonNull`) |
| `pop()` / `peek()` | O(1) | Throw `NoSuchElementException` when empty |
| `isEmpty()`, `size()` | O(1) | `size` is a counter |
| `clear()` | O(1) | Drops the top pointer |

### 7.4 Flow in `EditorApplication`

```text
execute(cmd):  cmd.apply(model) → undoStack.push(cmd) → redoStack.clear() → render()
undo():        cmd = undoStack.pop() → cmd.undo(model) → redoStack.push(cmd) → render()
redo():        cmd = redoStack.pop() → cmd.apply(model) → undoStack.push(cmd) → render()
```

Undo and redo also clear the single selection. Loading a file clears both stacks.

---

## 8. Geometry

All in `GeometryUtils` (pure static methods, shared by drawing **and** hit testing so both always agree).

| Function | Formula / behaviour |
|---|---|
| `distanceSquared(x1,y1,x2,y2)` | `dx² + dy²` |
| `calculateAngle(x1,y1,x2,y2)` | `atan2(y2 − y1, x2 − x1)`; correct in every quadrant |
| `trimmedSegment(sx,sy,tx,ty,r)` | `θ = angle(s→t)`; start = `s + r·(cosθ, sinθ)`; end = `t − r·(cosθ, sinθ)`. Returns `{startX, startY, endX, endY, θ}` |
| `pointToSegmentDistance(p, a, b)` | Project `p` on line `ab`: `t = ((p−a)·(b−a)) / |b−a|²`, clamp `t` to [0, 1], distance to `a + t(b−a)`. A zero-length segment gives `t = 0` |
| `lerp(a, b, t)` | `a + (b − a)·t` |
| `clampMagnitude(dx, dy, max)` | Shortens the vector to `max` keeping its direction; unchanged if already shorter or zero |

**Arrowhead.** A filled triangle at the tip, 12 px long. Its two base corners are placed at `tip − 12·(cos(θ ± 25°), sin(θ ± 25°))`. A bidirectional arrow draws a second head at the start using `θ + π`.

**Arrow hit test** (`EditorApplication.hitArrowAt`): for each arrow, take the *clipped* segment (what is visible on screen) and measure the click's distance to it. An arrow counts as hit at ≤ 6 px; the closest wins.

---

## 9. Rendering

Rendering is **immediate mode**: `render()` clears the whole canvas and redraws everything. It is called after every state change and on every animation frame.

**Draw order** (later draws on top):

1. Background fill
2. All arrows (line + head(s))
3. Dashed drag preview line (only during a connect-drag)
4. All nodes (fill, outline, selection ring)

**Look and feel**

| Element | Style |
|---|---|
| Background | `#f8fafc` |
| Node | Fill `#3b82f6`, 2 px outline `#1d4ed8`, radius 20 |
| Single selection ring | `#f59e0b`, 3 px, inset 4 px |
| Multi-selection ring | `#8b5cf6`, 3 px, inset 4 px |
| Arrow line | `#334155`, 2.5 px |
| Arrowhead | Fill `#dc2626` |
| Preview line | `#94a3b8`, 2 px, dashes 8 px on / 6 px off |

Both nodes and arrows are drawn at `pullMotion.effectivePosition(node)`, so a tugged node and its arrows move together. The preview line starts on the source circle's edge and is skipped while the cursor is still inside the source node.

The window is 800 × 640 (canvas 800 × 600 plus a 40 px toolbar) and not resizable.

---

## 10. Drag-pull animation

While dragging from a node, the nearest other node within **90 px** of the cursor is gently pulled toward it. When the drag ends it eases back.

### 10.1 Mechanism

`PullMotionModel` keeps a `{dx, dy}` **offset** per node and one `pulledNodeId`. On every frame (`tick`):

```text
target = pulled node ?  clampMagnitude(cursor − node, 14 px)  :  (0, 0)
offset = lerp(offset, target, 0.35)          // covers 35 % of the remaining gap
if |offset| < 0.05 on both axes and node is not pulled → forget the offset
```

Using one rule for both directions gives an exponential ease: fast at first, then settling. The screen position is `true position + offset`.

### 10.2 Driver

`EditorApplication` starts a JavaFX `AnimationTimer` the first time a drag updates the pull. Each frame it calls `tick`, then `render`. When `isAtRest()` is true (nothing pulled, no offsets left) **and** no connect-drag is active, the timer stops itself, so an idle app does no work.

### 10.3 Guarantees

- Offsets are **never** written into `GraphModel`. Hit testing and commands use true coordinates.
- If a tracked node is deleted mid-animation, its offset simply decays to zero.
- `reset()` clears everything at once (used by `Esc` and after loading a file).
- The class has no JavaFX imports, so it is fully unit-tested.

---

## 11. Persistence

### 11.1 File format

```json
{
  "nodes": [
    {"id": 1, "x": 207.0, "y": 89.0}
  ],
  "arrows": [
    {"id": 1, "sourceId": 1, "targetId": 2, "bidirectional": true}
  ]
}
```

`test.json` in the repo root is a ready-made sample.

### 11.2 Writing (`toJson`)

Builds the text with a `StringBuilder`, one entry per line, in the model's insertion order. Coordinates use `Double.toString`, which is locale-independent (always a `.` decimal point). A `NaN` or infinite coordinate raises `IllegalArgumentException`.

### 11.3 Reading (`fromJson`)

A small hand-written recursive parser, `JsonScanner`, walks the text character by character. It supports exactly the subset this format needs: objects, arrays, plain string keys, numbers and `true`/`false`.

**Validation rules**

| Problem | Result |
|---|---|
| Malformed syntax (missing `:`/`,`/brackets, bad number, unterminated string) | `IllegalArgumentException` with the character offset |
| Text after the closing `}` | Rejected |
| Top-level key other than `nodes` / `arrows` | Rejected |
| Missing `nodes` or `arrows` array | Rejected |
| Missing or non-numeric `x`, `y`, ids; NaN/infinite values | Rejected |
| An id that is not a whole number ≥ 1 | Rejected |
| Duplicate node id or duplicate arrow id | Rejected |
| Arrow whose `sourceId` / `targetId` is not a node in the file | Rejected |
| `bidirectional` present but not a boolean | Rejected |
| `bidirectional` absent | Accepted, defaults to `false` (older files still load) |

Parser limits: string escape sequences (`\n`, `\"`, `\uXXXX`) are not supported. This is fine because the schema only contains fixed ASCII keys.

### 11.4 Save and load in the UI

```text
Save: FileChooser (*.json, default "graph.json") → toJson(model) → Files.writeString (UTF-8)
Load: FileChooser → Files.readString → fromJson
        ├─ IOException            → "Could not read graph" alert
        ├─ IllegalArgumentException → "Not a valid graph file" alert   (current graph untouched)
        └─ success → model.loadFrom(...), clear undo/redo, clear selection,
                     abort any gesture, reset animation, render
```

The file is fully parsed and validated into a **separate** `GraphModel` first, and only then copied in. A bad file can never leave a half-loaded graph.

---

## 12. Testing

`EditorLogicTest` (JUnit 5, 27 tests) exercises everything except the JavaFX window. No JavaFX toolkit is started; tests run headless.

| Area | Tests cover |
|---|---|
| Geometry | angle in all directions, trimmed segment endpoints, node-centring, `lerp`, `clampMagnitude` |
| `LinkedStack` | LIFO order, empty-stack behaviour |
| `GraphModel` | hit tests, nearest / conflicting node queries, `nearestNodeWithin` excluding the source |
| Commands | apply/undo round trip for every command, bidirectional upgrade, multi-node move |
| JSON | round trip (normal and empty graph), id allocation after load, `loadFrom` replacing state, real file save/load via `@TempDir`, bidirectional preserved / defaulted / rejected when not boolean, whitespace tolerance, negative and exponent numbers, invalid documents |
| Pull animation | eases toward cursor, springs back after release, does not mutate the model, reset, deleted node |

Run with `./mvnw test`. What is **not** covered by automated tests: the JavaFX gesture handling and drawing in `EditorApplication`, which need manual checking (see the [demo video](media/demo_video_1.mp4)).

---

## 13. Constants reference

| Constant | Value | Where | Meaning |
|---|---|---|---|
| `CANVAS_WIDTH` / `CANVAS_HEIGHT` | 800 / 600 | `EditorApplication` | Canvas size |
| `NODE_RADIUS` | 20 px | `EditorApplication` | Node size |
| `DRAG_THRESHOLD` | 5 px | `EditorApplication` | Click vs drag cut-off |
| `ARROW_HIT_TOLERANCE` | 6 px | `EditorApplication` | Arrow click distance |
| `GEOMETRY_EPSILON` | 1e-6 | `EditorApplication` | Float comparison tolerance |
| `ARROWHEAD_LENGTH` / `_ANGLE_DEGREES` | 12 px / 25° | `EditorApplication` | Arrowhead shape |
| `PULL_RADIUS` | 90 px | `PullMotionModel` | Distance at which a node is pulled; also the drop-to-connect tolerance |
| `MAX_PULL_OFFSET` | 14 px | `PullMotionModel` | Furthest a node is displaced |
| `EASING_FACTOR` | 0.35 | `PullMotionModel` | Share of the gap covered each frame |
| `SETTLED_EPSILON` | 0.05 px | `PullMotionModel` | Below this an offset counts as at rest |

---

## 14. Design decisions

| Decision | Reason |
|---|---|
| **Command pattern** | Uniform undo/redo; new edit types need no change to the history code |
| **Custom `LinkedStack`** | Course requirement to build our own data structure; O(1) operations |
| **Immutable records for nodes and arrows** | Commands can safely keep old copies; no hidden shared state |
| **Arrows reference node ids** | Moving a node (replacing the object) never breaks arrows |
| **`LinkedHashMap` storage** | O(1) lookup by id and stable draw / save order |
| **Logic kept out of JavaFX classes** | Everything except `EditorApplication` is unit-testable without a UI |
| **Animation offsets separate from the model** | Cosmetic motion can never corrupt data or change what a click hits |
| **Shared geometry for drawing and hit testing** | What you see is exactly what you can click |
| **Hand-written JSON** | No dependency beyond JavaFX; the schema is small and fixed |
| **Validate-then-replace on load** | A bad file cannot half-overwrite the current graph |
| **Squared-distance comparisons** | Avoids `sqrt` in hit-test loops |
| **Separate `Main` launcher** | Avoids the JavaFX classpath launch error |

---

## 15. Known limitations

Worth knowing before a demo or a change:

- **Multi-selection is not cleared** by undo, redo or load, so stale ids may remain until toggled or `Esc` is pressed. Draws and moves ignore ids that no longer exist.
- **Dropping a moved node within 90 px of another node connects instead of moving**, because the drop is treated as a connect target. Drop farther away to move.
- **Arrows are one per direction.** A second arrow in an existing direction is silently ignored.
- **Hit testing and drawing are O(n)** over nodes and arrows. This is fine for hand-drawn graphs, not for thousands of items.
- **Fixed canvas.** 800 × 600, non-resizable, no zoom or pan. New nodes must sit at least one radius from the edge, but moves are not clamped, so a node can be dragged partly or fully off-canvas.
- **JSON parser** does not support string escapes or comments (not needed by the schema).
- **No labels, weights or colours** on nodes and arrows; the data model holds only ids, positions and direction.
- **UI-layer code** (`EditorApplication`) has no automated tests.
