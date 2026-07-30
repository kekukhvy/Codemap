# Spec 007 — UML-style class diagram report

> Replaces the collapsible method tree in `report.html` with a UML-style class
> diagram: entry points expand into class rectangles, each rectangle lists the
> class's public API, and every class appears exactly **once** on the canvas.

Status: **proposed**
Supersedes the diagram half of the feature/6 interactive report (the side panel,
filters, theme toggle, and module overview are kept).

---

## 1. Why

The current diagram is a collapsible tree of one node per method. It answers
"what calls what" but not the question a reviewer actually has in front of a pull
request: *which classes does this endpoint touch, what is their public surface,
and which parts of it did this PR change?*

A tree also forces duplication. `ProcessContext.getSender()` reached from a
service and `ProcessContext.getRequest()` reached from a repository become two
separate subtrees, so the same class is drawn twice and the reader cannot see
that it is one collaborator shared by both.

The UML class-box form fixes both: the box is the unit of reading, and box
identity is the class, so a shared collaborator is visibly shared.

---

## 2. The visual model

### 2.1 Two node kinds on the canvas

| Node | Shape | Content |
|---|---|---|
| **Entry point** | Rounded pill | `PUT /api/user`, `main()`, `@Route("/admin")` |
| **Class** | UML rectangle | Header = class name; body = member rows |

Nothing else is drawn. Methods are **rows inside a box**, never free-standing
nodes — this is the core change from the tree.

### 2.2 Anatomy of a class box

```
┌──────────────────────────────────┐
│ «service»  UserService       (+) │  ← header: stereotype, name, expander
├──────────────────────────────────┤
│ + UserService(UserRepository)    │  ← constructors first
├──────────────────────────────────┤
│ + update(UserDto): User      (+) │  ← public methods
│ + delete(long): void             │
├╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌┤  ← dashed separator
│ - validate(UserDto): void    (+) │  ← private rows, only once revealed
└──────────────────────────────────┘
```

Three compartments, in order: **constructors**, **public methods**, **revealed
private methods**. A compartment with no rows is omitted, along with its rule.

- The `«stereotype»` is the existing `Layer` (`controller`, `service`,
  `repository`, …) — reuses data already in `ClassView.layer()`.
- `+` / `-` prefix per row is the UML visibility marker.
- `(+)` is the expander affordance (§4).

### 2.3 Private rows are revealed, not listed

A class box never shows private methods on first draw. A private row appears only
when some *already-visible* method in the same box is expanded and calls it
(§4.3). Collapsing the last visible caller of a private row removes the row
again. This keeps boxes small while still letting the reader walk an
implementation downwards.

---

## 3. Change highlighting

Per the settled decision, three visual registers:

| Status | Box | Member row |
|---|---|---|
| `ADDED` | Solid **green** border, green header fill | green name |
| `CHANGED` | Solid **green** border | green name (only the changed rows) |
| `AFFECTED` | Dashed **amber** border | amber name |
| `REMOVED` | — (no declaration to draw) | listed in the side panel only |
| `UNCHANGED` | Neutral border | neutral |

A class box takes the strongest status among itself and its visible rows:
`ADDED` > `CHANGED` > `AFFECTED` > `UNCHANGED`. So a box whose border is green
tells the reader "this PR edited something in here" without opening it, and the
green rows say *what*.

Amber for `AFFECTED` is deliberate: a class dragged into the diff only because a
callee changed is not an edit, and colouring it green would overstate the PR.

Colours must satisfy the contrast requirements of the existing light/dark themes
and must not be the sole carrier of meaning — the border style (solid / dashed)
and a status glyph in the header carry it redundantly for colour-blind readers.

---

## 4. Interaction

### 4.1 Clicking an entry point

Draws the entry point's **declaring class box** and a link from the pill to the
row of the entry-point method, plus that row is <u>underlined</u> to mark it as
the target. (`PUT /api/user` → `UserController` box, `update` row underlined.)

### 4.2 Clicking a name opens code in the side panel

| Click target | Panel shows |
|---|---|
| **Class name** | `calls:` / `called by:` paragraphs listing **classes** (deduplicated, not methods), then the **full class source** verbatim |
| **Method row name** | `called by:` listing **classes**, then the method's source |

The class-level "calls / called by" lists are class-granular by design — at the
class level the reader wants collaborators, not a flat list of every call site.

Requires a new data field: **the full class source text** (§5.2).

### 4.3 The `(+)` expander

Two scopes, same affordance:

**On the class header** — reveals the classes this class collaborates with:
one box per distinct target class of any call from any method of this class,
with a link per relationship. This answers "what does this class use".

**On a method row** — reveals what *that method* calls:

1. For each call to a **public method of another class**: draw that class's box
   (or reuse it, §6) and a **solid** link from this row to the target row; the
   target row is underlined.
2. For each call to a **public method of this same class**: solid link that loops
   back to the row within the same box.
3. For each call to a **private method of this same class**: **dashed** link, and
   the private row is added to this box's third compartment (§2.3).

A method may produce several links — a service `save` calling both
`repo.findById` and `repo.update` draws **two** links to two underlined rows in
the repository box. All of them are drawn; this is information, not clutter.

Every revealed row and box is itself expandable, so the reader walks the graph
outwards at their own pace.

Clicking `(−)` collapses: the subtree of boxes revealed *only* through this
expander is removed. A box still reachable from another expanded path stays
(§6.2).

### 4.4 Fan-out bounds

Per the settled decision:

- Calls into the JDK, third-party jars, and **unresolved** targets never become
  boxes. They are listed in the side panel under the method, so the information
  is not lost. This reuses the existing third-party filter (§3.3.1 of the main
  spec).
- Nothing is drawn until the reader expands it. The tree-grows-on-expansion
  invariant is preserved — and is what terminates cycles.

---

## 5. Data model changes

Both gaps are in `codemap-core`, not only the renderer. They are additive to
`index.json`.

### 5.1 Method visibility — **required**

`IndexedMethod` has no visibility today, but the whole design depends on it:
which rows are public API, which rows are private and dashed, and the `+`/`-`
marker.

```java
public enum Visibility { PUBLIC, PROTECTED, PACKAGE, PRIVATE }
```

- Added to `IndexedMethod` and surfaced on `MethodView`.
- Read from the JavaParser `MethodDeclaration` modifiers by `JavaSourceParser`.
- Interface methods are `PUBLIC` when unqualified; `record` accessors are
  `PUBLIC`.
- Rendering: `PUBLIC` and `PROTECTED` rows are listed in the public compartment
  (`+` / `#`); `PACKAGE` and `PRIVATE` are reveal-only (`~` / `-`).

Absent visibility must not fail a run — default to `PACKAGE` if a modifier set
cannot be read, per the degrade-never-fail invariant.

### 5.2 Full class source — **required**

"Click the class name and see the whole class as it is" cannot read the file at
view time: the report runs over `file://`. The class body must be embedded.

- `ClassView.source` — the verbatim text of the class declaration, `lineStart`
  to `lineEnd`.
- It goes on the **view model only**, not `IndexedClass`, so `index.json` does
  not double in size by carrying every class body next to every method body.
  `ReportViewModelBuilder` reads it via the existing `SourceText` helper.
- Escaping is already handled — it flows through `JsonScriptEscaper` like method
  source.

### 5.3 No change needed

Class-level `calls` / `called by` are derived in the renderer by projecting the
existing `EdgeView` list onto `classId` and deduplicating. No new edge kind.

---

## 6. Layout

Per the settled decision: **layered columns with orthogonal routing**,
hand-written, no new dependency. D3 stays inline and is used for zoom/pan and
selections, not for a force simulation.

### 6.1 Uniqueness is the invariant

A class is keyed by `classId` and has **at most one box on the canvas, ever**.
This is the requirement the tree could not meet.

- When an expansion targets a class that is already drawn, no second box is
  created — a link is drawn to the existing box.
- If that box is currently collapsed (header only), it stays where it is and the
  link points at its header; the box is *not* moved. Moving a box the reader has
  already positioned in their mental model is more disorienting than a long link.
- A box's own reveal state (which private rows are showing) is shared by all
  paths that reach it — there is one box, so there is one state.

### 6.2 Reference counting for collapse

Each box records the set of expander paths that revealed it. Collapsing one
expander removes that path; the box is removed only when the set becomes empty.
This is what makes "the shared `ProcessContext` box survives collapsing the
service" work.

### 6.3 Placement

- Columns by **call depth** from the entry point: pill in column 0, its class in
  column 1, its callees in column 2, and so on. A class reached at two different
  depths sits in the **shallowest** column it was reached at.
- Within a column, boxes stack vertically, ordered to minimise link crossings —
  a barycentre pass over each column, which is the standard cheap heuristic and
  is deterministic.
- Positions are **stable**: expanding a box must not reshuffle boxes the reader
  is already looking at. New boxes take free space in their column; existing
  boxes only move if a column genuinely has to grow, and then along one axis
  only.

### 6.4 Routing

Links are **orthogonal polylines** (horizontal/vertical segments only):

1. Each box reserves a margin; the region between two columns is divided into
   vertical **lanes**.
2. A link leaves its source row's right edge, takes an assigned lane, and enters
   the target's left edge at the target row's y.
3. **No segment may pass through a box rectangle.** A link whose straight path
   would cross a box is routed around it — the lane grid guarantees a free path
   exists.
4. **No two links may share a segment.** Each gets its own lane offset, so two
   parallel relationships remain two visibly distinct lines.
5. Link styles: solid = public call, **dashed** = private call, and cross-module
   links keep the existing distinct style.
6. Arrowheads point at the callee. Hovering a link highlights it and both
   endpoints so a long route can be followed by eye.

Self-links (a row calling another row in the same box) route out and back on the
same side with a distinct small loop.

---

## 7. What is kept

Not a rewrite of the report, only the diagram:

- Side panel, `calls`/`called by` navigation, source display
- Layer and module filters, search
- Module overview and cross-module connectors
- Light/dark theme toggle
- Self-containment: inline D3, inline CSS/JS, embedded index, CSP

---

## 8. Acceptance criteria

1. Clicking an entry point pill draws its declaring class box with the
   entry-point method row underlined.
2. A class box shows constructors and public methods in separate compartments,
   each row prefixed with its UML visibility marker.
3. Private methods are absent on first draw, and appear in the box only after a
   visible method that calls them is expanded.
4. An `ADDED` class has a green border and green header; a `CHANGED` class has a
   green border with only its changed rows green; an `AFFECTED` class has a
   dashed amber border. Status is also conveyed without colour.
5. Clicking a class name opens the panel with class-granular `calls` /
   `called by` lists and the complete class source.
6. Clicking a method row name opens the panel with the method source and the
   classes that call it.
7. Expanding a method row draws one link per call: solid to public targets,
   dashed to private targets in the same box, with each target row underlined.
   A method calling two methods of one class draws two links.
8. A method calling a private method of its own class causes that private row to
   appear in the same box, itself expandable.
9. A class already on the canvas is **never** drawn twice: a second path to it
   draws a link to the existing box.
10. Collapsing one expander does not remove a box that another expanded path
    still reaches.
11. No link segment crosses a class rectangle, and no two links share a segment.
12. `MethodView` carries visibility; `ClassView` carries full class source; a
    file whose modifiers cannot be read still renders.
13. `report.html` still works over `file://` with no network, and the run
    completes on `../kairos` without crashing.

---

## 9. Out of scope

- Fields/attributes in the box body (UML would show them; the map is about
  behaviour). Deferred.
- Inheritance and interface-implementation arrows as first-class UML relations.
  Deferred — `IMPLEMENTS` edges exist in the model but are not drawn as UML
  generalisation arrows in this slice.
- Manual box dragging and layout persistence across reloads.
- Exporting the diagram as SVG/PNG.
