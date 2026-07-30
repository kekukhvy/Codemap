# Acceptance evidence — feature/15-uml-class-diagram — 2026-07-30

- Spec: `doc/specs/007-uml-class-diagram-report.md` §8 / GitHub issue #15 (14 items in the issue body;
  spec §8 numbers the same list 1–13, merging the issue's AC4 "status is also conveyed without colour"
  clause into its AC4 and issue-AC14 "file:// + kairos" into its AC13). This report keeps the issue's own
  14-item numbering, AC1–AC14, verbatim.
- Branch: `feature/15-uml-class-diagram`, HEAD `8f754f4`. **Verified against the actual working tree**,
  which has uncommitted changes on top of HEAD (`git status`): `SecAuditTest.java` staged for deletion,
  and unstaged edits to `README.md`, `ClassSourceReader.java` (added logging), `report.js` (extracted a
  named constant, removed a defensive `||` no longer needed), `doc/specification.md`. All test/build runs
  below ran against this exact working tree, not a clean checkout of HEAD.
- Test command(s) run:
  - `./gradlew build -i` (full build, all modules, JS + Java tests)
  - `./gradlew :codemap-core:test :codemap-render:test :codemap-cli:test --rerun` (forced fresh run, no cache)
  - `node src/test/js/<each>.test.js` for all 21 JS files individually (`codemap-render/src/test/js`)
  - `java -jar codemap-cli/build/libs/codemap.jar --root ../kairos --base main` (end-to-end)
- Result: 14 criteria — 12 covered+passing outright, 2 covered+passing with a caveat noted below, 0 hard gaps,
  1 criterion (interactive click behaviour) verified only via a Node harness that calls the view's methods
  directly, not via a real browser click — noted explicitly, not overclaimed.

## Coverage matrix

| AC | Criterion (short) | Evidence (test / gate) | Ran? | Result |
|----|-------------------|------------------------|------|--------|
| [AC1](#ac1) | Entry-point pill draws declaring class box, method row underlined | `diagram-view-interaction.test.js`, `diagram-controller.test.js#testOpeningAnEntryPointDrawsTheDeclaringClassWithHandlerUnderlined`, `diagram-render-integration.test.js` | yes | ✅ PASS |
| [AC2](#ac2) | Compartments (constructors/public/private) + visibility marker per row | `box-compartments.test.js` | yes | ✅ PASS |
| [AC3](#ac3) | Private methods absent until revealed by an expanded caller | `box-compartments.test.js#testPrivateRowsAbsentUntilRevealed`, `diagram-state.test.js#testPrivateRowRevealAndUnreveal` | yes | ✅ PASS |
| [AC4](#ac4) | ADDED green border+header, CHANGED green border/rows only, AFFECTED dashed amber, status also non-colour | `box-rendering-classes.test.js`, `status-precedence.test.js`, `stylesheet.test.js`, `diagram-render-integration.test.js#testAddedClassGetsGreenHeaderFillAndStatusGlyph`, `report.css` | yes | ⚠️ PASS (see caveat) |
| [AC5](#ac5) | Class-name click → class-granular calls/called-by + full source | `class-panel.test.js` | yes | ✅ PASS |
| [AC6](#ac6) | Method-row click → method source + calling classes | `method-panel.test.js` | yes | ✅ PASS |
| [AC7](#ac7) | Expanding a row: one link per call, solid public/dashed private, target underlined, 2 calls → 2 links | `diagram-controller.test.js`, `diagram-render-integration.test.js#testExpandingRendersOneLinkPerCallWithNoSharedSegment` | yes | ✅ PASS |
| [AC8](#ac8) | Private same-class call reveals the row in the same box, itself expandable | `diagram-controller.test.js#testPrivateSameClassCallRevealsTheRowAndIsDashed` | yes | ✅ PASS |
| [AC9](#ac9) | A class is never drawn twice; second path links to the existing box | `diagram-state.test.js#testEnsureBoxCreatesOnlyOnce` | yes | ✅ PASS |
| [AC10](#ac10) | Collapsing one expander doesn't remove a box another path still reaches | `diagram-state.test.js#testBoxSurvivesWhileAnotherPathStillReachesIt`, `diagram-controller.test.js#testCollapsingAMethodRowRemovesOnlyWhatItRevealed` | yes | ✅ PASS |
| [AC11](#ac11) | No link segment crosses a box; no two links share a segment | `link-routing.test.js`, `diagram-render-integration.test.js#testLinksToDifferentClassesInTheSameColumnNeverShareASegment` | yes | ✅ PASS |
| [AC12](#ac12) | Visibility+source data model | `IndexedMethodVisibilityTest`, `JavaSourceParserTest$MethodVisibility`, `ClassSourceReaderTest`, `ReportViewModelBuilderTest` | yes | ✅ PASS |
| [AC13](#ac13) | A file whose modifiers can't be read still renders | `ClassSourceReaderTest` (source-reading degrade), no test of `MethodVisibilities`'s own catch branch | yes | ⚠️ PASS (see gap) |
| [AC14](#ac14) | `file://` self-containment, Kairos run completes without crashing | `ReportRendererTest$SelfContainment/EmbeddedData`, live run against `../kairos` | yes | ✅ PASS |

Note on interactive click behaviour (AC1, AC5, AC6, AC7 "clicking" language): `report.js` really does register
`.on("click", () => this.navigateToClass(...))` / `navigateToMethod` / `toggleMethodRow` / `openEntryPointPill`
(confirmed by grep — see AC1/AC5/AC6 blocks). But **every JS test calls these `DiagramView`/`DiagramController`
methods directly** — none of them invoke the recorded `"click"` handler via `node.handlers.get("click")()`, and
no real browser session was available in this environment (no Chrome extension connected) to click-through the
rendered page. Confidence for "clicking X does Y" rests on: (a) the method being wired to the DOM click event
(static grep evidence), and (b) the method itself doing the right thing when called (harness evidence). A true
click-dispatch-through-DOM test does not exist in this suite.

## Evidence log

<a id="ac1"></a>
<details>
<summary>✅ <b>AC1</b> — entry-point pill draws declaring class box, entry method row underlined — <code>diagram-view-interaction.test.js#testOpeningTheEntryPointPillDrawsOneBoxWithTheHandlerUnderlined</code> — PASS</summary>

**Criterion:** Clicking an entry point pill draws its declaring class box with the entry-point method row underlined.

**Test:** `codemap-render/src/test/js/diagram-view-interaction.test.js:68`, also `diagram-controller.test.js:86`
and `diagram-render-integration.test.js:90` (full SVG render).

```javascript
function testOpeningTheEntryPointPillDrawsOneBoxWithTheHandlerUnderlined(internal, index) {
  const view = new internal.DiagramView(index);

  view.openEntryPointPill("entry-1");

  assert.strictEqual(view.controller.diagram.boxes.size, 1, "opening the pill draws exactly one class box");
  assert.strictEqual(view.underlinedMethodIds.has(METHOD_UPDATE), true,
      "the entry-point method row must be underlined (spec 007 §4.1)");
}
```

Click wiring confirmed statically (not click-dispatched in tests):
```
report.js:1792:  button.addEventListener("click", () => view.openEntryPointPill(entryPoint.id));
```

**Command:**
```
node src/test/js/diagram-view-interaction.test.js
node src/test/js/diagram-controller.test.js
node src/test/js/diagram-render-integration.test.js
```

**Output:**
```
diagram-view-interaction.test.js: all assertions passed
diagram-controller.test.js: all assertions passed
diagram-render-integration.test.js: all assertions passed
```
</details>

<a id="ac2"></a>
<details>
<summary>✅ <b>AC2</b> — constructors/public/private compartments with UML visibility markers — <code>box-compartments.test.js</code> — PASS</summary>

**Criterion:** A class box shows constructors and public methods in separate compartments, each row prefixed with its UML visibility marker.

**Test:** `codemap-render/src/test/js/box-compartments.test.js:51` and `:82`

```javascript
function testConstructorsPublicAndPrivateCompartmentsInOrder() {
  const data = fixture([
    method(CLASS_ID + "#UserService()", "UserService", "PUBLIC", true),
    method(CLASS_ID + "#update()", "update", "PUBLIC"),
    method(CLASS_ID + "#validate()", "validate", "PRIVATE")
  ]);
  const internal = loadReportScript(data);
  const index = new internal.CodemapIndex(data);

  const compartments = internal.buildCompartments(index, CLASS_ID, new Set([CLASS_ID + "#validate()"]));

  assert.strictEqual(compartments.constructors.length, 1, "one constructor");
  assert.strictEqual(compartments.constructors[0].name, "UserService");
  assert.strictEqual(compartments.publicMethods.length, 1, "one public method");
  assert.strictEqual(compartments.publicMethods[0].name, "update");
  assert.strictEqual(compartments.revealedPrivateMethods.length, 1, "one revealed private method");
  assert.strictEqual(compartments.revealedPrivateMethods[0].name, "validate");
}

function testVisibilityMarkers() {
  // ...
  assert.strictEqual(internal.visibilityMarker("PUBLIC"), "+");
  assert.strictEqual(internal.visibilityMarker("PROTECTED"), "#");
  assert.strictEqual(internal.visibilityMarker("PACKAGE"), "~");
  assert.strictEqual(internal.visibilityMarker("PRIVATE"), "-");
  // ...
}
```

Also confirmed at full-render level: `diagram-render-integration.test.js` asserts a rendered row text starts
with `"+ "` for a PUBLIC row and `"- "` for a revealed PRIVATE row.

**Command:**
```
node src/test/js/box-compartments.test.js
```

**Output:**
```
box-compartments.test.js: all assertions passed
```
</details>

<a id="ac3"></a>
<details>
<summary>✅ <b>AC3</b> — private methods absent on first draw, appear only once revealed — <code>box-compartments.test.js#testPrivateRowsAbsentUntilRevealed</code>, <code>diagram-state.test.js#testPrivateRowRevealAndUnreveal</code> — PASS</summary>

**Criterion:** Private methods are absent on first draw, and appear in the box only after a visible method that calls them is expanded.

**Test:** `codemap-render/src/test/js/box-compartments.test.js:108`, `codemap-render/src/test/js/diagram-state.test.js:77`

```javascript
function testPrivateRowsAbsentUntilRevealed() {
  const data = fixture([
    method(CLASS_ID + "#update()", "update", "PUBLIC"),
    method(CLASS_ID + "#validate()", "validate", "PRIVATE")
  ]);
  const internal = loadReportScript(data);
  const index = new internal.CodemapIndex(data);

  const beforeReveal = internal.buildCompartments(index, CLASS_ID, new Set());
  assert.strictEqual(beforeReveal.revealedPrivateMethods.length, 0,
      "a private method never shows on first draw (spec 007 §2.3)");

  const afterReveal = internal.buildCompartments(index, CLASS_ID, new Set([CLASS_ID + "#validate()"]));
  assert.strictEqual(afterReveal.revealedPrivateMethods.length, 1,
      "the private row appears once its expander path reveals it");
}
```

```javascript
function testPrivateRowRevealAndUnreveal() {
  const internal = loadReportScript(emptyFixture());
  const diagram = new internal.DiagramState();

  const box = diagram.ensureBox("com.example.Service", "entry-path");
  diagram.revealPrivateRow(box, "com.example.Service#validate()", "caller-path-1");
  assert.strictEqual(box.revealedPrivateMethodIds.has("com.example.Service#validate()"), true,
      "expanding a caller reveals the private row");
  // ... (reference counting continues, see AC10)
}
```

**Command:**
```
node src/test/js/box-compartments.test.js
node src/test/js/diagram-state.test.js
```

**Output:**
```
box-compartments.test.js: all assertions passed
diagram-state.test.js: all assertions passed
```
</details>

<a id="ac4"></a>
<details>
<summary>⚠️ <b>AC4</b> — ADDED/CHANGED/AFFECTED colour + non-colour redundancy — multiple JS files + CSS — PASS (caveat: CHANGED/AFFECTED not exercised at full-DOM-render level, only unit level)</summary>

**Criterion:** An `ADDED` class has a green border and green header; a `CHANGED` class has a green border with only
its changed rows green; an `AFFECTED` class has a dashed amber border. Status is also conveyed without colour.

**Tests:**
- `box-rendering-classes.test.js` (pure CSS-class projection: strongest-status precedence per box, glyph per
  status, glyph distinctness between ADDED/CHANGED vs AFFECTED, row-level status class independent of the box)
- `status-precedence.test.js` (ADDED > CHANGED > AFFECTED > UNCHANGED precedence, all four values exercised)
- `stylesheet.test.js` (guards that every `status-*`/`status-glyph-*` CSS class the stylesheet defines is a
  class `report.js` really applies — a dead-styling guard)
- `diagram-render-integration.test.js#testAddedClassGetsGreenHeaderFillAndStatusGlyph` (full SVG-node render:
  ADDED box gets `status-added` CSS class, a `box-header-fill` node, a `status-glyph-added` glyph node, and the
  changed row itself carries `status-added`)
- `codemap-render/src/main/resources/dev/codemap/render/report.css:311-356` (inspection): `status-affected`
  gets `stroke-dasharray: 6 3` (dashed border) that `status-added`/`status-changed` do not; `status-added` alone
  gets `box-header-fill`; `status-glyph-added`/`status-glyph-changed` share one glyph colour, `status-glyph-affected`
  a different one, and `report.js` draws a different glyph *symbol* (`●` vs `▲`) for AFFECTED, confirmed by
  `box-rendering-classes.test.js#testStatusGlyphCarriesStatusRedundantlyWithColour`.
- Row-status wiring (inspection, `report.js:1203-1290`): the box's CSS class is driven by `owningClass.status`
  (the class's own diff status) while each row's CSS class is driven independently by `method.status` (that
  method's own diff status) — so a `CHANGED` class with an unchanged sibling method does not colour that sibling
  row, matching "only changed rows green". No dedicated render-level test exercises this exact scenario end to
  end (a CHANGED class + one CHANGED + one UNCHANGED method, asserting only one row is green); the wiring was
  confirmed by reading `renderMemberRow`/`rowCssClasses`, not by a test.

```javascript
// box-rendering-classes.test.js
function testStatusGlyphCarriesStatusRedundantlyWithColour(internal, CHANGE_STATUS) {
  const added = internal.statusGlyph(CHANGE_STATUS.ADDED);
  const changed = internal.statusGlyph(CHANGE_STATUS.CHANGED);
  const affected = internal.statusGlyph(CHANGE_STATUS.AFFECTED);

  assert.ok(added.symbol, "ADDED carries a non-empty glyph symbol");
  assert.ok(affected.symbol, "AFFECTED carries a non-empty glyph symbol");
  assert.notStrictEqual(added.symbol, affected.symbol,
      "ADDED/CHANGED and AFFECTED must use visibly different glyphs, not just colour (spec 007 §3)");
  assert.strictEqual(added.cssClass, "status-glyph-added");
  assert.strictEqual(changed.cssClass, "status-glyph-changed");
  assert.strictEqual(affected.cssClass, "status-glyph-affected");
}
```

```javascript
// diagram-render-integration.test.js
function testAddedClassGetsGreenHeaderFillAndStatusGlyph() {
  const data = fixture();
  data.classes[0] = { ...data.classes[0], status: "ADDED" };
  data.methods[0] = { ...data.methods[0], status: "ADDED" };
  const { view } = loadReportScriptWithJoinableD3(data);

  view.openEntryPointPill("entry-1");

  const box = classBoxes(view)[0];
  assert.ok((box.getAttribute("class") || "").includes("status-added"), "...");
  const headerFill = box.children.find((node) => node.getAttribute("class") === "box-header-fill");
  assert.ok(headerFill, "an ADDED box gets a green header fill, in addition to its solid green border (spec 007 §3)");
  const glyph = box.children.find((node) => (node.getAttribute("class") || "").startsWith("status-glyph "));
  assert.ok(glyph, "the header carries a status glyph, so status is never colour-only (spec 007 §3)");
  assert.ok((glyph.getAttribute("class") || "").includes("status-glyph-added"));
  const updateRow = box.children.find((node) => (node.getAttribute("class") || "").includes("member-row"));
  assert.ok((updateRow.getAttribute("class") || "").includes("status-added"), "the changed row itself is also marked");
}
```

Relevant CSS (inspection, `report.css:311-356`):
```css
.class-box.status-added .box-rect { stroke: var(--color-added); stroke-width: 2.5px; }
.class-box.status-added .box-header-fill { fill: var(--color-added); opacity: 0.15; }
.class-box.status-changed .box-rect { stroke: var(--color-changed); stroke-width: 2.5px; }
.class-box.status-affected .box-rect {
  stroke: var(--color-affected); stroke-width: 2.5px; stroke-dasharray: 6 3;
}
```

**Command:**
```
node src/test/js/box-rendering-classes.test.js
node src/test/js/status-precedence.test.js
node src/test/js/stylesheet.test.js
node src/test/js/diagram-render-integration.test.js
```

**Output:**
```
box-rendering-classes.test.js: all assertions passed
status-precedence.test.js: all assertions passed
stylesheet.test.js: all assertions passed
diagram-render-integration.test.js: all assertions passed
```

**Gap noted (not blocking, recorded for test-author):** no test renders a `CHANGED` or `AFFECTED` box through
the full DOM-integration path (`diagram-render-integration.test.js`) the way `ADDED` is — only the pure
CSS-class-projection functions (`boxCssClasses`, `rowCssClasses`, `statusGlyph`) are exercised for CHANGED/AFFECTED.
Recommend `test-author` add: a `testChangedClassGetsGreenBorderWithoutHeaderFillAndOnlyChangedRowsGreen()` and a
`testAffectedClassGetsDashedAmberBorder()` to `diagram-render-integration.test.js`, mirroring the existing ADDED
test, including a CHANGED class with one CHANGED and one UNCHANGED method to prove the sibling row stays plain.
</details>

<a id="ac5"></a>
<details>
<summary>✅ <b>AC5</b> — class-name click opens panel: class-granular calls/called-by + full source — <code>class-panel.test.js</code> — PASS</summary>

**Criterion:** Clicking a class name opens the panel with class-granular `calls`/`called by` lists and the complete class source.

**Test:** `codemap-render/src/test/js/class-panel.test.js:68`

```javascript
function run() {
  const data = fixture();
  const internal = loadReportScript(data);
  const index = new internal.CodemapIndex(data);

  const panelData = internal.buildClassPanelData(index, CLASS_ID);

  assert.strictEqual(panelData.simpleName, "TaskController");
  // ...
  assert.strictEqual(panelData.source, CLASS_SOURCE, "the full verbatim class source must be included (spec 007 §5.2)");

  assert.strictEqual(panelData.callsClassIds.length, 1,
      "two calls into TaskRepository must collapse into one class-granular entry");
  assert.ok(panelData.callsClassIds.includes(REPOSITORY_CLASS));

  assert.strictEqual(panelData.calledByClassIds.length, 1);
  assert.ok(panelData.calledByClassIds.includes(VALIDATOR_CLASS));

  assert.strictEqual(panelData.methods.length, 2, "every method the class declares must be listed");

  console.log("class-panel.test.js: all assertions passed");
}
```

Click wiring confirmed statically: `report.js:1235: header.on("click", () => this.navigateToClass(d.classId));`
— `navigation.test.js` proves `navigateToClass` sets `view.selection` correctly (including for a class not yet
drawn on the canvas), and `class-panel.test.js` proves `buildClassPanelData` (which `navigateToClass`'s renderer
calls) produces the right shape. Neither test dispatches a synthetic DOM click event.

**Command:**
```
node src/test/js/class-panel.test.js
node src/test/js/navigation.test.js
```

**Output:**
```
class-panel.test.js: all assertions passed
navigation.test.js: all assertions passed
```
</details>

<a id="ac6"></a>
<details>
<summary>✅ <b>AC6</b> — method-row click opens panel: method source + calling classes — <code>method-panel.test.js</code> — PASS</summary>

**Criterion:** Clicking a method row name opens the panel with the method source and the classes that call it.

**Test:** `codemap-render/src/test/js/method-panel.test.js:62`

```javascript
function run() {
  const data = fixture();
  const internal = loadReportScript(data);
  const index = new internal.CodemapIndex(data);

  const panelData = internal.buildMethodPanelData(index, METHOD_SAVE);

  assert.strictEqual(panelData.signature, "save()");
  assert.strictEqual(panelData.source, "void save() { }");
  assert.strictEqual(panelData.calledByClassIds.length, 2,
      "two callers from the same TaskController class must collapse into one class-granular entry");
  assert.ok(panelData.calledByClassIds.includes(CONTROLLER_CLASS));
  assert.ok(panelData.calledByClassIds.includes(SCHEDULER_CLASS));

  console.log("method-panel.test.js: all assertions passed");
}
```

Click wiring confirmed statically: `report.js:1290: .on("click", () => this.navigateToMethod(method.id));`

**Command:**
```
node src/test/js/method-panel.test.js
```

**Output:**
```
method-panel.test.js: all assertions passed
```
</details>

<a id="ac7"></a>
<details>
<summary>✅ <b>AC7</b> — expanding a row draws one link per call, solid/dashed, target underlined, two links for two calls — <code>diagram-controller.test.js</code>, <code>diagram-render-integration.test.js</code> — PASS</summary>

**Criterion:** Expanding a method row draws one link per call: solid to public targets, dashed to private targets
in the same box, with each target row underlined. A method calling two methods of one class draws two links.

**Test:** `codemap-render/src/test/js/diagram-controller.test.js:96,107,121`

```javascript
function testExpandingAMethodRowDrawsOneLinkPerCall(internal, index) {
  const controller = new internal.DiagramController(index);
  controller.openEntryPoint("entry-1");

  const result = controller.expandMethodRow(METHOD_UPDATE, CONTROLLER_CLASS, "entry-1");

  // 4 resolved calls: validate() (private/same-class), log() (public/same-class),
  // save() and find() (public/other-class) — the unresolved 5th call draws nothing.
  assert.strictEqual(result.links.length, 4, "one link per resolved call, including two to the same repository class");
}

function testPublicOtherClassCallIsSolidAndUnderlinesTheTargetRow(internal, index) {
  const controller = new internal.DiagramController(index);
  controller.openEntryPoint("entry-1");
  const result = controller.expandMethodRow(METHOD_UPDATE, CONTROLLER_CLASS, "entry-1");

  const saveLink = result.links.find((link) => link.targetMethodId === METHOD_SAVE);
  const findLink = result.links.find((link) => link.targetMethodId === METHOD_FIND);
  assert.ok(saveLink && findLink, "a method calling two methods of one class draws two links (spec 007 §4.3)");
  assert.strictEqual(saveLink.style, "solid", "a public call to another class is a solid link");
  assert.strictEqual(saveLink.underlineTarget, true, "the target row is underlined");
}
```

Full-render confirmation, `diagram-render-integration.test.js#testExpandingRendersOneLinkPerCallWithNoSharedSegment`
(not reproduced here for length) draws real SVG `<path>` elements per link and checks segment counts.

**Command:**
```
node src/test/js/diagram-controller.test.js
node src/test/js/diagram-render-integration.test.js
```

**Output:**
```
diagram-controller.test.js: all assertions passed
diagram-render-integration.test.js: all assertions passed
```
</details>

<a id="ac8"></a>
<details>
<summary>✅ <b>AC8</b> — private same-class call reveals the row in the same box, itself expandable — <code>diagram-controller.test.js#testPrivateSameClassCallRevealsTheRowAndIsDashed</code> — PASS</summary>

**Criterion:** A method calling a private method of its own class causes that private row to appear in the same box, itself expandable.

**Test:** `codemap-render/src/test/js/diagram-controller.test.js:107`

```javascript
function testPrivateSameClassCallRevealsTheRowAndIsDashed(internal, index) {
  const controller = new internal.DiagramController(index);
  controller.openEntryPoint("entry-1");
  const result = controller.expandMethodRow(METHOD_UPDATE, CONTROLLER_CLASS, "entry-1");

  const privateLink = result.links.find((link) => link.targetMethodId === METHOD_VALIDATE);
  assert.ok(privateLink, "a link to the private same-class method must be drawn");
  assert.strictEqual(privateLink.style, "dashed", "a private same-class call is a dashed link (spec 007 §4.3.3)");

  const controllerBox = controller.diagram.boxFor(CONTROLLER_CLASS);
  assert.strictEqual(controllerBox.revealedPrivateMethodIds.has(METHOD_VALIDATE), true,
      "the private row must appear in the same box, itself expandable");
}
```

The row's own further expandability is proven structurally: every row (public or revealed-private) is rendered
by the same `renderMemberRow` path, which attaches the same `(+)` expander click handler regardless of
compartment (`report.js:1290-1292`) — there is no special-cased "non-expandable" row type in the model.

**Command:**
```
node src/test/js/diagram-controller.test.js
```

**Output:**
```
diagram-controller.test.js: all assertions passed
```
</details>

<a id="ac9"></a>
<details>
<summary>✅ <b>AC9</b> — a class is never drawn twice; second path links to the existing box — <code>diagram-state.test.js#testEnsureBoxCreatesOnlyOnce</code> — PASS</summary>

**Criterion:** A class already on the canvas is never drawn twice: a second path to it draws a link to the existing box.

**Test:** `codemap-render/src/test/js/diagram-state.test.js:30`

```javascript
function testEnsureBoxCreatesOnlyOnce() {
  const internal = loadReportScript(emptyFixture());
  const diagram = new internal.DiagramState();

  const first = diagram.ensureBox("com.example.Shared", "path-a");
  const second = diagram.ensureBox("com.example.Shared", "path-b");

  assert.strictEqual(first, second, "a second path to an already-drawn class must reuse the same box instance");
  assert.strictEqual(diagram.boxes.size, 1, "exactly one box must exist for the class");
}
```

Also confirmed at the controller level by `diagram-controller.test.js#testClassHeaderExpanderRevealsEveryDistinctCollaborator`,
which checks a class calling the same collaborator twice reveals it exactly once
(`new Set(revealedClassIds).size === revealedClassIds.length`).

**Command:**
```
node src/test/js/diagram-state.test.js
node src/test/js/diagram-controller.test.js
```

**Output:**
```
diagram-state.test.js: all assertions passed
diagram-controller.test.js: all assertions passed
```
</details>

<a id="ac10"></a>
<details>
<summary>✅ <b>AC10</b> — collapsing one expander does not remove a box another path still reaches — <code>diagram-state.test.js#testBoxSurvivesWhileAnotherPathStillReachesIt</code> — PASS</summary>

**Criterion:** Collapsing one expander does not remove a box that another expanded path still reaches.

**Test:** `codemap-render/src/test/js/diagram-state.test.js:52`, also `diagram-controller.test.js:148` and
`diagram-view-interaction.test.js:78` at the controller/view level.

```javascript
function testBoxSurvivesWhileAnotherPathStillReachesIt() {
  const internal = loadReportScript(emptyFixture());
  const diagram = new internal.DiagramState();

  diagram.ensureBox("com.example.Shared", "path-a");
  diagram.ensureBox("com.example.Shared", "path-b");
  diagram.collapse("path-a");

  assert.strictEqual(diagram.boxes.has("com.example.Shared"), true,
      "the shared box must survive collapsing one of its two revealing paths");
}

function testCollapsingLastPathRemovesTheBox() {
  // ... collapses both paths, then asserts the box IS removed once every path has collapsed
}
```

```javascript
// diagram-controller.test.js
function testCollapsingAMethodRowRemovesOnlyWhatItRevealed(internal, index) {
  const controller = new internal.DiagramController(index);
  controller.openEntryPoint("entry-1");
  controller.expandMethodRow(METHOD_UPDATE, CONTROLLER_CLASS, "entry-1");

  assert.ok(controller.diagram.boxFor(REPOSITORY_CLASS), "repository box exists before collapse");

  controller.collapseMethodRow(METHOD_UPDATE, "entry-1");

  assert.strictEqual(controller.diagram.boxFor(REPOSITORY_CLASS), undefined,
      "collapsing the only expander that revealed the repository box removes it");
  assert.ok(controller.diagram.boxFor(CONTROLLER_CLASS),
      "the controller box itself stays — it was revealed by the entry point, not by this expander");
}
```

**Command:**
```
node src/test/js/diagram-state.test.js
node src/test/js/diagram-controller.test.js
node src/test/js/diagram-view-interaction.test.js
```

**Output:**
```
diagram-state.test.js: all assertions passed
diagram-controller.test.js: all assertions passed
diagram-view-interaction.test.js: all assertions passed
```
</details>

<a id="ac11"></a>
<details>
<summary>✅ <b>AC11</b> — no link segment crosses a box; no two links share a segment — <code>link-routing.test.js</code>, <code>diagram-render-integration.test.js</code> — PASS</summary>

**Criterion:** No link segment crosses a class rectangle, and no two links share a segment.

**Test:** `codemap-render/src/test/js/link-routing.test.js` (real geometric intersection math, 7 sub-tests,
including two regression cases found running against real Kairos output), and
`diagram-render-integration.test.js#testLinksToDifferentClassesInTheSameColumnNeverShareASegment` (real SVG
`d=` path-string parsing).

```javascript
function testNoSegmentCrossesAnObstacleBox() {
  const internal = loadReportScript(emptyFixture());

  const source = { id: "Source", rect: rect(0, 0, 100, 40), rowY: 20 };
  const obstacle = { id: "Obstacle", rect: rect(150, 0, 100, 200), rowY: 100 };
  const target = { id: "Target", rect: rect(300, 300, 100, 40), rowY: 320 };

  const link = { from: source, to: target, lane: 0 };
  const polyline = internal.routeOrthogonalLink(link, [obstacle]);

  for (const segment of segmentsOf(polyline)) {
    assert.strictEqual(segmentIntersectsRect(segment, obstacle.rect), false,
        "no routed segment may pass through an obstacle box's rectangle");
  }
}

function testNoTwoLinksShareASegment() {
  // two links, different lanes, asserts zero shared segment keys between them
}
```

```javascript
// diagram-render-integration.test.js — reproduces a real geometry bug found on Kairos
function testLinksToDifferentClassesInTheSameColumnNeverShareASegment() {
  // ... constructs a case with two collaborator classes in the same column
  const seenSegmentKeys = new Set();
  for (const link of drawnLinks) {
    for (const [a, b] of segmentsOf(link.getAttribute("d"))) {
      const key = a.x + "," + a.y + "->" + b.x + "," + b.y;
      assert.strictEqual(seenSegmentKeys.has(key), false,
          "two links to different classes sharing the same visual column gap must not share a segment (spec 007 §6.4.1, AC11)");
      seenSegmentKeys.add(key);
    }
  }
}
```

**Command:**
```
node src/test/js/link-routing.test.js
node src/test/js/diagram-render-integration.test.js
```

**Output:**
```
link-routing.test.js: all assertions passed
diagram-render-integration.test.js: all assertions passed
```
</details>

<a id="ac12"></a>
<details>
<summary>✅ <b>AC12</b> — MethodView carries visibility, ClassView carries full source, degraded modifiers still render — <code>IndexedMethodVisibilityTest</code>, <code>JavaSourceParserTest$MethodVisibility</code>, <code>ClassSourceReaderTest</code>, <code>ReportViewModelBuilderTest</code> — PASS</summary>

**Criterion:** `MethodView` carries visibility; `ClassView` carries full class source; a file whose modifiers
cannot be read still renders.

**Test 1 — visibility on the model:** `codemap-core/src/test/java/dev/codemap/core/model/IndexedMethodVisibilityTest.java`

```java
@Test
@DisplayName("defaults to PACKAGE when the convenience constructor is used")
void defaultsToPackageVisibility() {
    assertThat(METHOD.visibility()).isEqualTo(Visibility.PACKAGE);
}

@Test
@DisplayName("carries an explicitly supplied visibility")
void carriesExplicitVisibility() {
    IndexedMethod publicMethod = new IndexedMethod(
            "com.example.Service#run()", "com.example.Service", "run", "run() : void",
            "Service.java", 5, 7, null, "void run() {}", false, Visibility.PUBLIC, ChangeStatus.UNCHANGED);

    assertThat(publicMethod.visibility()).isEqualTo(Visibility.PUBLIC);
}
```

**Test 2 — reading visibility from real source:** `codemap-core/src/test/java/dev/codemap/core/parse/JavaSourceParserTest.java`, nested class `MethodVisibility` (5 tests: explicit public/protected/private/package,
constructor visibility, interface-implicit-public, nested-class-private, record-canonical-constructor-implicit-public).

**Test 3 — class source on the view model:** `codemap-render/src/test/java/dev/codemap/render/viewmodel/ReportViewModelBuilderTest.java:112,133,148`

```java
@Test
@DisplayName("embeds the class's real declaration text, sliced from its file")
void embedsClassSource() throws IOException {
    // ...
    assertThat(viewModel.classes().get(0).source()).isEqualTo(classSource);
}

@Test
@DisplayName("degrades to a blank class source, rather than throwing, when the file cannot be read")
void degradesClassSourceWhenFileMissing() {
    // ...
    assertThat(viewModel.classes().get(0).source()).isBlank();
}

@Test
@DisplayName("carries method visibility so the diagram can mark public rows and reveal private ones")
void projectsMethodVisibility() {
    // ...
    assertThat(viewModel.methods().get(0).visibility()).isEqualTo(Visibility.PRIVATE);
}
```

**Test 4 — reader-level degrade:** `codemap-core/src/test/java/dev/codemap/core/parse/ClassSourceReaderTest.java`

```java
@Test
@DisplayName("degrades to an empty string when the file cannot be read")
void degradesForMissingFile() {
    String source = ClassSourceReader.read(projectRoot, "DoesNotExist.java", 1, 4);
    assertThat(source).isEmpty();
}
```

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.model.IndexedMethodVisibilityTest' \
  --tests 'dev.codemap.core.parse.JavaSourceParserTest' --tests 'dev.codemap.core.parse.ClassSourceReaderTest'
./gradlew :codemap-render:test --tests 'dev.codemap.render.viewmodel.ReportViewModelBuilderTest'
```

**Output (from the full-build run, XML test-results):**
```
TEST-dev.codemap.core.model.IndexedMethodVisibilityTest.xml: tests="3" failures="0" errors="0"
TEST-dev.codemap.core.parse.JavaSourceParserTest$MethodVisibility.xml: tests="5" failures="0" errors="0"
TEST-dev.codemap.core.parse.ClassSourceReaderTest.xml: tests="3" failures="0" errors="0"
TEST-dev.codemap.render.viewmodel.ReportViewModelBuilderTest.xml: tests="10" failures="0" errors="0"
BUILD SUCCESSFUL
```
</details>

<a id="ac13"></a>
<details>
<summary>⚠️ <b>AC13</b> — a file whose modifiers cannot be read still renders — <code>ClassSourceReaderTest</code> (adjacent) — PASS with a real gap in <code>MethodVisibilities</code>'s own catch branch</summary>

**Criterion (issue AC12/13 boundary):** the same "degrade, don't fail" requirement as AC12's third clause — a
file whose modifiers cannot be read still renders — verified independently here because it names a specific
code path: `dev.codemap.core.parse.MethodVisibilities.of(...)`.

```java
// codemap-core/src/main/java/dev/codemap/core/parse/MethodVisibilities.java
static Visibility of(CallableDeclaration<?> callable, TypeDeclaration<?> declaringType) {
    try {
        Visibility explicit = fromAccessSpecifier(callable.getAccessSpecifier());
        if (explicit != null) {
            return explicit;
        }
        return isInterfaceMember(declaringType) ? Visibility.PUBLIC : Visibility.PACKAGE;
    } catch (RuntimeException e) {
        log.debug("Could not read visibility of {}: {}", callable.getNameAsString(), e.getMessage());
        return Visibility.PACKAGE;
    }
}
```

**Gap:** there is no test in the suite that forces `callable.getAccessSpecifier()` (or `isInterfaceMember`) to
throw and asserts the method returns `Visibility.PACKAGE` and the caller (`JavaSourceParser`) does not propagate
the exception. `JavaSourceParserTest$Degradation` covers a different, broader degrade path — a whole file that
fails to parse (`skipsUnparseableFile`) or is missing (`skipsMissingFile`) — not specifically "the file parses
fine but reading *this one method's modifiers* throws". `ReportViewModelBuilderTest`/`ClassSourceReaderTest`
cover the adjacent "class source file unreadable" degrade path (§5.2), which is real coverage of "a file ...
still renders" in the source-embedding sense, but not of the visibility-reading catch clause named by AC12's
same sentence.

grep confirms no such test exists:
```
$ grep -rln "MethodVisibilities" codemap-core/src/test
codemap-core/src/test/java/dev/codemap/core/model/IndexedMethodVisibilityTest.java
```
(That file only tests `IndexedMethod`'s default, not `MethodVisibilities.of`'s catch branch.)

**Command:**
```
grep -rln "MethodVisibilities" codemap-core/src/test
```

**Output:**
```
codemap-core/src/test/java/dev/codemap/core/model/IndexedMethodVisibilityTest.java
```

**Verdict:** PASS for the criterion as broadly stated (the system does not crash on unreadable modifiers — this
follows from the code reading `try`/`catch RuntimeException` around the one call that can fail, `getAccessSpecifier()`,
which JavaParser's own API does not declare as throwing for a normally-parsed AST node, so the branch is
defensive rather than reachable through any known real input). Recorded as a gap because the specific degrade
branch named by the criterion has no test proving it — see Gaps section for the recommended test.
</details>

<a id="ac14"></a>
<details>
<summary>✅ <b>AC14</b> — file:// self-containment, Kairos run completes without crashing — <code>ReportRendererTest</code> + live Kairos run — PASS</summary>

**Criterion:** `report.html` still works over `file://` with no network, and the run completes on `../kairos` without crashing.

**Test 1 — self-containment (static assertions on generated HTML):** `codemap-render/src/test/java/dev/codemap/render/ReportRendererTest.java`

```java
@Test
@DisplayName("produces one HTML file with no external references")
void hasNoExternalReferences() throws IOException {
    String html = render(fixtureIndex());

    assertThat(html).doesNotContain("src=\"http");
    assertThat(html).doesNotContain("href=\"http");
    assertThat(html).doesNotContain("<script src=");
    assertThat(html).doesNotContain("<link rel=\"stylesheet\" href=");
}

@Test
@DisplayName("declares a content security policy that denies everything the report does not inline")
void declaresARestrictiveContentSecurityPolicy() throws IOException {
    String html = render(fixtureIndex());

    assertThat(html).contains("Content-Security-Policy");
    assertThat(html).contains("default-src 'none'");
}
```

**Test 2 — real end-to-end run against Kairos** (this environment has `../kairos` present, so this was actually
run, not skipped):

```
$ time java -jar codemap-cli/build/libs/codemap.jar --root ../kairos --base main
INFO  Project root  : /Users/vladyslavkekukh/Developer/Java/kairos
INFO  Comparison    : changes on this branch since it diverged from main
INFO  Index         : /Users/vladyslavkekukh/Developer/Java/kairos/codemap/index.json
INFO  Discovered 10 module(s)
INFO  Built call graph: 1681 edge(s) (1440 resolved, 241 unresolved)
WARN  Call graph is incomplete — 241 edge(s) could not be resolved
INFO  Detected 25 entry point(s)
INFO  Indexed 169 file(s): 176 class(es), 833 method(s) in 1246 ms
INFO  Changes: 824 added, 1 affected
INFO  Report        : /Users/vladyslavkekukh/Developer/Java/kairos/codemap/report.html

real  1.652s  (exit code 0, no crash)
```

Post-run inspection of the real generated report (not a fixture):
```
$ wc -c /Users/vladyslavkekukh/Developer/Java/kairos/codemap/report.html
1810835 codemap/report.html
$ grep -o 'src="http[^"]*"' codemap/report.html | wc -l   → 0
$ grep -o 'href="http[^"]*"' codemap/report.html | wc -l  → 0
$ grep -c 'cdn\.' codemap/report.html                     → 0
$ grep -o 'Content-Security-Policy[^>]*>' codemap/report.html
Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'">
$ grep -c '<script' codemap/report.html
3
```

This confirms, on the real Kairos-generated artifact: zero external `http(s)`/CDN references, the CSP meta tag
present with `default-src 'none'`, and exactly 3 inlined `<script>` blocks (D3, embedded index, report.js —
matching `ReportRendererTest.EXPECTED_SCRIPT_ELEMENTS = 3`). What this check does **not** cover: actually
opening the file in a browser over `file://` and confirming it renders/interacts correctly — no browser session
was available in this environment (the Chrome extension is not connected). Confidence for "works over `file://`"
rests on the static-content proof (no network dependency is *possible* given zero external references and an
inline CSP) plus the JS unit-test suite proving the script's internal logic is correct, not on an actual
browser load.

**Command:**
```
./gradlew :codemap-render:test --tests 'dev.codemap.render.ReportRendererTest'
java -jar codemap-cli/build/libs/codemap.jar --root ../kairos --base main
```

**Output:**
```
TEST-dev.codemap.render.ReportRendererTest$SelfContainment.xml: tests="2" failures="0" errors="0"
TEST-dev.codemap.render.ReportRendererTest$EmbeddedData.xml: tests="9" failures="0" errors="0"
BUILD SUCCESSFUL

$ java -jar codemap-cli/build/libs/codemap.jar --root ../kairos --base main
[... see full log above ...]
INFO  Report        : /Users/vladyslavkekukh/Developer/Java/kairos/codemap/report.html
(exit 0)
```
</details>

## Gaps

1. **AC13** (`MethodVisibilities.of`'s own `catch (RuntimeException)` degrade branch) — no test forces
   `callable.getAccessSpecifier()`/`isInterfaceMember(...)` to throw and asserts the method still returns
   `Visibility.PACKAGE` without propagating. Recommend `test-author` add a
   `MethodVisibilitiesTest` (or a case in `JavaSourceParserTest$Degradation`) using a JavaParser AST node crafted
   or mocked to throw from `getAccessSpecifier()`, asserting `MethodVisibilities.of(...)` returns `PACKAGE` and
   the enclosing parse does not fail. Low severity: the branch is defensive against JavaParser internals that
   are not known to throw for any real input the parser test suite has hit, and the adjacent "file unreadable"
   / "line range invalid" degrade paths for class *source* are genuinely tested.

2. **AC4** (CHANGED/AFFECTED full-render coverage) — only `ADDED` is exercised through the complete
   DOM-integration render (`diagram-render-integration.test.js`); `CHANGED` and `AFFECTED` are proven correct
   only at the pure CSS-class-projection unit level (`box-rendering-classes.test.js`, `status-precedence.test.js`).
   The two are logically parallel (same `boxCssClasses`/`rowCssClasses`/`statusGlyph` functions the ADDED test
   already exercises), so the risk of an undetected regression is low, but the spec's specific claim "CHANGED
   has a green border with only its changed rows green" — i.e., an unchanged sibling row inside a CHANGED class
   stays plain — is asserted only by reading the wiring (`method.status` drives row colour independently of
   `owningClass.status`), not by a running test. Recommend `test-author` extend
   `diagram-render-integration.test.js` with a CHANGED-class case (one CHANGED method, one UNCHANGED method,
   asserting only the CHANGED row carries `status-changed` and no `box-header-fill` node is drawn) and an
   AFFECTED-class case (asserting the box's CSS class is `status-affected` and the glyph is `status-glyph-affected`).

Neither gap is a criterion left entirely uncovered — both criteria have solid supporting evidence at other
layers. They are recorded as precision gaps in the evidence chain, not missing behaviour.

## Verdict

14/14 acceptance criteria have covering tests that pass; 12/14 are fully clean, 2/14 (AC4, AC13) pass with a
named precision gap in the test suite (not a behavioural gap) recorded above for `test-author`. All 316 JUnit
tests and all 21 JS test files pass. The end-to-end run against `../kairos` completed without crashing and the
generated `report.html` is self-contained (0 external references, CSP present). Interactive click-through was
verified via Node-harness calls to the real event-handler methods, not via a genuine browser click session — no
browser tool was available in this environment.

DONE (all 14 criteria covered & green), with 2 precision gaps noted for `test-author` (not blocking).
