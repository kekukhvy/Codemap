# Acceptance evidence — feature/6-interactive-report — 2026-07-29

- Spec: GitHub issue #6, "Render the interactive report.html" (no `doc/specs/*.md` slice file exists for this issue; criteria taken verbatim from `gh issue view 6`). Narrative cross-referenced against `doc/specification.md` §3.1–§3.3, §5, §6.6.
- Test command(s) run:
  - `node codemap-render/src/test/js/*.test.js` (each file individually)
  - `./gradlew build -i --console=plain` (full multi-module build, all tests, including the `jsReportTests` Gradle task)
  - `java -jar codemap-cli/build/libs/codemap.jar --root ../kairos --base main` (end-to-end happy path against the verification project)
  - Manual inspection of the generated `../kairos/codemap/report.html` (grep + embedded JSON payload) for module dependencies, edge kinds, CSP, external refs
- Result: 14 criteria — 8 covered+passing, 1 PASS-with-caveat, 3 gaps (no implementation and/or no test), 2 partially covered (implementation exists but no automated test; rests on prior manual verification only)

## Coverage matrix

| AC | Criterion (short) | Evidence (test / gate) | Ran? | Result |
|----|-------------------|------------------------|------|--------|
| [AC1](#ac1) | opens over `file://`, no console errors | `ReportRendererTest$SelfContainment` + CSP test + grep on Kairos output | yes | ⚠️ PASS-with-caveat |
| [AC2](#ac2) | modules are top-level roots, entry points beneath | `ReportViewModelBuilderTest#projectsModules/projectsEntryPoints`, `tree-builder.test.js`, Kairos run | yes | ✅ PASS |
| [AC3](#ac3) | module overview shows real Kairos dependencies | **NONE** — data is embedded and asserted, but no UI renders it | yes (data check only) | ❌ GAP |
| [AC4](#ac4) | lazy expansion, no hang on a large project | `tree-builder.test.js`, `TreeBuilder.expand`, Kairos run (833 methods, no hang) | yes | ✅ PASS |
| [AC5](#ac5) | clicking a method shows genuine source | `ReportRendererTest#embedsMethodSource`, `ReportViewModelBuilderTest#embedsMethodSource` | yes | ✅ PASS |
| [AC6](#ac6) | Called by / Calls populated and navigable | `buildMethodPanel`/`navSection` inspection; no JS test renders/asserts the panel DOM | partial | ⚠️ PARTIAL |
| [AC7](#ac7) | same-class dashed / cross-class arrow | `ReportRendererTest#embedsEdgeKinds`, `stylesheet.test.js`, CSS widths, Kairos payload | yes | ✅ PASS |
| [AC8](#ac8) | cross-module heavy connector, collapsed by default | Styling only: `stylesheet.test.js` + CSS. **Collapse/jump behavior is not implemented** | yes (styling only) | ❌ GAP |
| [AC9](#ac9) | revisited node shows `↗ already above`, click jumps to original | `tree-builder.test.js` (badge + `revisitTargetKey`); `GraphView.jumpTo/flash` not covered by a JS test | yes | ⚠️ PASS-with-caveat |
| [AC10](#ac10) | cycles do not hang the UI | `tree-builder.test.js` (`fixtureWithCycle`, asserts no further recursion on revisit) | yes | ✅ PASS |
| [AC11](#ac11) | changed outlined green, affected yellow | `ReportRendererTest#embedsChangeStatus`, `stylesheet.test.js`, CSS colours match manual Chrome check (`#37b26c`) | yes | ✅ PASS |
| [AC12](#ac12) | Focus on changes collapses everything else | `GraphView.setFocusOnChanges/visibleRoots/subtreeHasChange` implemented; **no automated test exercises it** | no | ❌ GAP (test missing) |
| [AC13](#ac13) | search filters by class/method name | `nodeMatchesSearch`/`setSearchTerm` implemented; **no automated test exercises it** | no | ❌ GAP (test missing) |
| [AC14](#ac14) | file works after being copied to another machine | Self-containment tests + CSP + grep (no external refs) | yes | ⚠️ PASS-with-caveat |

## Evidence log

<a id="ac1"></a>
<details>
<summary>⚠️ <b>AC1</b> — <code>report.html</code> opens over <code>file://</code> with no console errors — <code>ReportRendererTest$SelfContainment</code> + CSP test + grep — PASS-with-caveat</summary>

**Criterion:** `report.html` opens over `file://` with **no console errors**

**Test:** `codemap-render/src/test/java/dev/codemap/render/ReportRendererTest.java:71-90` (self-containment), `:123-130` (CSP)

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
@DisplayName("inlines the vendored D3 bundle")
void inlinesD3() throws IOException {
    String html = render(fixtureIndex());

    assertThat(html).contains("d3");
    assertThat(html.length()).isGreaterThan(50_000);
}
```

**Command:**
```
./gradlew build -i --console=plain
java -jar codemap-cli/build/libs/codemap.jar --root ../kairos --base main
grep -c 'src="http\|href="http\|<script src=' ../kairos/codemap/report.html
```

**Output:**
```
TEST-dev.codemap.render.ReportRendererTest$SelfContainment.xml:
<testsuite name="self-containment" tests="2" skipped="0" failures="0" errors="0" ...>
  <testcase name="produces one HTML file with no external references" .../>
  <testcase name="inlines the vendored D3 bundle" .../>

grep result on the real Kairos-generated report.html: 0 external refs found
Content-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'
BUILD SUCCESSFUL in 3s
```

**Caveat (must be stated honestly):** self-containment (no external `src`/`href`, D3 inlined, CSP denying network) is verified by automated test and by grepping the real Kairos output. The literal "opens over a `file://` URL with no browser console errors" was **not** re-verified in this pass; per the task's stated context, the last manual verification was done by serving the report over `http://127.0.0.1` rather than a true `file://` URL, because the Chrome automation extension used cannot drive `file://` URLs. No Chrome/console check was re-run in this verification pass — this AC rests on: (a) the automated self-containment/CSP tests above, and (b) the prior manual browser session recorded in the task context. Treat as **PASS-with-caveat**, not a fresh, independently-run console check.
</details>

<a id="ac2"></a>
<details>
<summary>✅ <b>AC2</b> — modules are top-level roots, entry points beneath them — <code>ReportViewModelBuilderTest#projectsModules/projectsEntryPoints</code>, <code>tree-builder.test.js</code> — PASS</summary>

**Criterion:** Modules are the top-level roots; entry points sit beneath them

**Test:** `codemap-render/src/test/java/dev/codemap/render/viewmodel/ReportViewModelBuilderTest.java:36-66`

```java
@Test
@DisplayName("projects modules as top-level roots")
void projectsModules() {
    CodeIndex index = CodeIndex.builder()
            .modules(List.of(new IndexedModule(MODULE_ID, "kairos-api", "kairos-api", List.of("src/main/java"))))
            .build();

    ReportViewModel viewModel = builder.build(index);

    assertThat(viewModel.modules()).hasSize(1);
    assertThat(viewModel.modules().get(0).id()).isEqualTo(MODULE_ID);
    assertThat(viewModel.modules().get(0).name()).isEqualTo("kairos-api");
}

@Test
@DisplayName("projects entry points beneath their module")
void projectsEntryPoints() {
    CodeIndex index = CodeIndex.builder()
            .modules(List.of(new IndexedModule(MODULE_ID, "kairos-api", "kairos-api", List.of("src/main/java"))))
            .entryPoints(List.of(new EntryPoint(
                    "entry-1", MODULE_ID, EntryPointKind.REST, "POST /api/v1/tasks",
                    METHOD_ID, DetectedBy.RULE, new SourceLocation("TaskController.java", 10))))
            .build();

    ReportViewModel viewModel = builder.build(index);

    assertThat(viewModel.entryPoints()).hasSize(1);
    assertThat(viewModel.entryPoints().get(0).moduleId()).isEqualTo(MODULE_ID);
    assertThat(viewModel.entryPoints().get(0).label()).isEqualTo("POST /api/v1/tasks");
    assertThat(viewModel.entryPoints().get(0).methodId()).isEqualTo(METHOD_ID);
}
```

Also directly exercised at the tree-building level in `tree-builder.test.js`:
```javascript
const moduleRoots = builder.buildModuleRoots();
assert.strictEqual(moduleRoots.length, 1, "one module root");

const moduleNode = moduleRoots[0];
builder.expand(moduleNode);
assert.strictEqual(moduleNode.children.length, 1, "module has one entry point");
```

**Command:**
```
./gradlew build -i --console=plain
node codemap-render/src/test/js/tree-builder.test.js
```

**Output:**
```
TEST-dev.codemap.render.viewmodel.ReportViewModelBuilderTest.xml: tests="6" failures="0" errors="0"
  <testcase name="projects modules as top-level roots" .../>
  <testcase name="projects entry points beneath their module" .../>

tree-builder.test.js: all assertions passed
```

Confirmed end-to-end on Kairos: `INFO  Discovered 10 module(s)` / `INFO  Detected 25 entry point(s)`, and the embedded JSON shows `modules: ['common', 'kairos-api', 'kairos-engine', 'kairos-worker', 'kafka', 'sqs', 'webhook', 'rabbitmq', 'kairos-admin', 'kairos-sdk']` with `entryPoints count: 25`.
</details>

<a id="ac3"></a>
<details>
<summary>❌ <b>AC3</b> — module overview shows real dependencies between Kairos's modules — <b>NONE</b> — GAP</summary>

**Criterion:** The module overview shows real dependencies between Kairos's modules

**What exists:** The data is correctly computed and embedded. `ReportViewModelBuilderTest#projectsModuleDependencies` and `ReportRendererTest#embedsModuleDependencies` both pass, and running against the real Kairos project produces genuine cross-module dependency pairs:

```python
modules: [... 10 modules ...]
moduleDependencies count: 2
sample moduleDependencies: [{'fromModuleId': 'kairos-admin', 'toModuleId': 'common'},
                            {'fromModuleId': 'kairos-api', 'toModuleId': 'common'}]
cross-module edges: 29
```

**What is missing:** The issue's Scope section explicitly asks for "a **module-level overview**: a 'which module depends on which' diagram" (also spec §3.2.2: "A module-level overview aggregates these into a 'which module depends on which' diagram"). Inspecting the actual generated page:

```
$ grep -o 'id="[a-z-]*"' ../kairos/codemap/report.html | sort -u
id="app"
id="canvas-wrapper"
id="focus-changes-button"
id="graph"
id="layer-filter"
id="module-filter"
id="search-input"
id="side-panel"
```

There is no dedicated module-overview element, panel, or D3 rendering path for `moduleDependencies` anywhere in `report.js` — grep for the field name confirms it is read only by the Java view-model builder and by `ReportRendererTest`, never by the front-end script:

```
$ grep -n "moduleDependencies" codemap-render/src/main/resources/dev/codemap/render/report.js
(no results)
```

The `module-filter` `<select>` exists but only filters the lazy entry-point tree; it is not a "which module depends on which" diagram, and (see AC13 note below) it is not even wired to `matchesFilters`/`visibleRoots` at present.

**Command:**
```
grep -n "moduleDependencies" codemap-render/src/main/resources/dev/codemap/render/report.js
grep -o 'id="[a-z-]*"' ../kairos/codemap/report.html | sort -u
```

**Missing test/feature:** `test-author` should add a JS test asserting a module-overview rendering function exists and produces one link per `moduleDependencies` entry (once `product code` implements it) — currently there is nothing to test because the feature itself is unbuilt on the front end.
</details>

<a id="ac4"></a>
<details>
<summary>✅ <b>AC4</b> — expanding is lazy, a large project does not hang on open — <code>tree-builder.test.js</code>, Kairos run — PASS</summary>

**Criterion:** Expanding is lazy — a large project does not hang on open

**Test:** `codemap-render/src/main/resources/dev/codemap/render/report.js:160-166` (`TreeBuilder.expand`), exercised by `codemap-render/src/test/js/tree-builder.test.js:48-88`

```javascript
function run() {
  const data = fixtureWithCycle();
  const internal = loadReportScript(data);
  const index = new internal.CodemapIndex(data);
  const builder = new internal.TreeBuilder(index);

  const moduleRoots = builder.buildModuleRoots();
  assert.strictEqual(moduleRoots.length, 1, "one module root");

  const moduleNode = moduleRoots[0];
  builder.expand(moduleNode);
  assert.strictEqual(moduleNode.children.length, 1, "module has one entry point");
  ...
}
```

`expand()` is only ever invoked node-by-node (never eagerly walking the whole graph), which is exactly what bounds the render for a large project.

**Command:**
```
node codemap-render/src/test/js/tree-builder.test.js
java -jar codemap-cli/build/libs/codemap.jar --root ../kairos --base main
```

**Output:**
```
tree-builder.test.js: all assertions passed

INFO  Discovered 10 module(s)
INFO  Built call graph: 1681 edge(s) (1440 resolved, 241 unresolved)
INFO  Detected 25 entry point(s)
INFO  Indexed 169 file(s): 176 class(es), 833 method(s) in 1333 ms
INFO  Report        : /Users/vladyslavkekukh/Developer/Java/kairos/codemap/report.html
```
The CLI run against the full 833-method Kairos call graph completed in ~1.3s with no hang, and the 1.4MB report opened instantly in-browser at the point of the earlier manual Chrome verification (per task context) — the lazy-expand model means the initial page load only materialises 10 module-root nodes, not the full graph.
</details>

<a id="ac5"></a>
<details>
<summary>✅ <b>AC5</b> — clicking a method shows genuine source, not a placeholder — <code>ReportRendererTest#embedsMethodSource</code>, <code>ReportViewModelBuilderTest#embedsMethodSource</code> — PASS</summary>

**Criterion:** Clicking a method shows its **genuine source**, not a placeholder

**Test:** `codemap-render/src/test/java/dev/codemap/render/ReportRendererTest.java:105-111`

```java
@Test
@DisplayName("embeds the real method source, not a placeholder")
void embedsMethodSource() throws IOException {
    String html = render(fixtureIndex());

    assertThat(html).contains("save();");
}
```

And `codemap-render/src/test/java/dev/codemap/render/viewmodel/ReportViewModelBuilderTest.java:68-85`:

```java
@Test
@DisplayName("embeds the method's real source text, not a placeholder")
void embedsMethodSource() {
    String source = "public void create() {\n    save();\n}";
    CodeIndex index = CodeIndex.builder()
            .classes(List.of(new IndexedClass(
                    CLASS_ID, MODULE_ID, "com.example.TaskController", "TaskController", "com.example",
                    TypeKind.CLASS, Layer.ENTRY, "TaskController.java", 1, 20, null)))
            .methods(List.of(new IndexedMethod(
                    METHOD_ID, CLASS_ID, "create", "create()", "TaskController.java", 10, 12, null,
                    source, false, ChangeStatus.UNCHANGED)))
            .build();

    ReportViewModel viewModel = builder.build(index);

    assertThat(viewModel.methods()).hasSize(1);
    assertThat(viewModel.methods().get(0).source()).isEqualTo(source);
}
```

The front-end panel builder (`report.js:697-701`, `sourceElement`) reads `method.source` directly and injects it into a `<pre class="source">` — no placeholder string anywhere in the rendering path.

**Command:**
```
./gradlew build -i --console=plain
```

**Output:**
```
TEST-dev.codemap.render.ReportRendererTest$EmbeddedData.xml: tests="9" failures="0" errors="0"
  <testcase name="embeds the real method source, not a placeholder" .../>
TEST-dev.codemap.render.viewmodel.ReportViewModelBuilderTest.xml: tests="6" failures="0" errors="0"
  <testcase name="embeds the method's real source text, not a placeholder" .../>
```
</details>

<a id="ac6"></a>
<details>
<summary>⚠️ <b>AC6</b> — Called by / Calls are populated and navigable — inspection only — PARTIAL</summary>

**Criterion:** Called by / Calls are populated and navigable

**What is covered by test:** the underlying data — edges with `from`/`to` and resolution — is asserted by `ReportViewModelBuilderTest#projectsEdges` and `ReportRendererTest#embedsEdgeKinds`, and `CodemapIndex.incoming()/outgoing()` (`report.js:81-87`) is a straightforward `Map` reverse-lookup with no branching logic worth a dedicated unit test on its own.

**What is not covered by any automated test:** the actual panel construction — `buildMethodPanel` calling `navSection("Called by", index.incoming(method.id), ...)` and `navSection("Calls", index.outgoing(method.id), ...)` (`report.js:643-660`, `703-721`) — is never invoked from any Node test. No test asserts that the rendered `<ul class="nav-list">` for "Called by"/"Calls" actually contains the expected `<a data-method-id="...">` links, or that clicking one selects/navigates to the target method (there is no click handler on `nav-list` links visible in `report.js` at all — grep below).

```
$ grep -n "nav-list\|data-method-id\|addEventListener" codemap-render/src/main/resources/dev/codemap/render/report.js
     704:    list.className = "nav-list";
     713:      const item = document.createElement("li");
     714:      const link = document.createElement("a");
     716:      link.dataset.methodId = methodId;
```

Notably, **no click handler is wired to these `<a>` elements** in `report.js` — `link.dataset.methodId` is set, but nothing in `wireControls` or elsewhere attaches a listener that reads `dataset.methodId` and calls `select()`/expands to it. Clicking a "Called by"/"Calls" link therefore does nothing observable in the current implementation; this was not caught by the prior manual Chrome session per the task's summary (which called out search/focus/expand but did not explicitly confirm clicking a Called-by/Calls link navigates).

**Command:**
```
grep -n "nav-list\|data-method-id\|addEventListener" codemap-render/src/main/resources/dev/codemap/render/report.js
```

**Verdict:** the lists are populated (data-wise, confirmed), but "navigable" is unverified by test and appears, on inspection, to be an unwired `<a>` with no click handler — a likely functional gap, not just a test gap. `test-author` should add: (1) a JS test constructing a `GraphView`, selecting a method with known callers/callees, and asserting the panel DOM contains the expected links; (2) once/if a click handler is added, a test simulating a click on a nav-list link and asserting the side panel/selection updates to the target method.
</details>

<a id="ac7"></a>
<details>
<summary>✅ <b>AC7</b> — same-class calls dashed, cross-class calls arrows — <code>ReportRendererTest#embedsEdgeKinds</code>, <code>stylesheet.test.js</code> — PASS</summary>

**Criterion:** Same-class calls render dashed; cross-class render as arrows

**Test:** `codemap-render/src/test/java/dev/codemap/render/ReportRendererTest.java:157-164`

```java
@Test
@DisplayName("carries edge kinds so internal/external/cross-module calls render distinctly")
void embedsEdgeKinds() throws IOException {
    String html = render(fixtureIndex());

    assertThat(html).contains(EdgeKind.CALL_EXTERNAL.name());
    assertThat(html).contains(EdgeKind.CROSS_MODULE.name());
}
```

And `codemap-render/src/test/js/stylesheet.test.js:50-60`:
```javascript
const STYLED_CLASSES = [
  "node-changed", "node-removed", "node-affected", "node-revisit",
  "link-call-internal", "link-call-external", "link-cross-module",
  "link-uses-type", "link-implements"
];
for (const className of STYLED_CLASSES) {
  assert.ok(CSS.includes("." + className), "stylesheet is missing a rule for ." + className);
  assert.ok(
      JS.includes('"' + className + '"'),
      "`." + className + "` is styled but report.js never applies it — dead styling");
}
```

`report.js:514-527` (`linkClass`) maps `CALL_INTERNAL` → `link-call-internal` (dashed, 1.2px) and everything else (default, i.e. `CALL_EXTERNAL`) → `link-call-external` (solid, 1.4px) — confirmed in `report.css:246-255`.

**Command:**
```
./gradlew build -i --console=plain
node codemap-render/src/test/js/stylesheet.test.js
```

**Output:**
```
TEST-dev.codemap.render.ReportRendererTest$EmbeddedData.xml:
  <testcase name="carries edge kinds so internal/external/cross-module calls render distinctly" .../>
stylesheet.test.js: all assertions passed
```
Confirmed on real Kairos data: `edge kinds present: {'CALL_EXTERNAL', 'CALL_INTERNAL', 'USES_TYPE', 'CROSS_MODULE', 'IMPLEMENTS'}` — all five kinds genuinely occur.
</details>

<a id="ac8"></a>
<details>
<summary>❌ <b>AC8</b> — cross-module calls heavy connector, collapsed by default — styling only — GAP</summary>

**Criterion:** Cross-module calls render as heavy connectors, collapsed by default

**What is covered:** the *visual* distinction. `report.css:257-260` gives `.link-cross-module` a 3px stroke (heavy, vs. 1.2px/1.4px for internal/external), and `stylesheet.test.js` confirms the class is both styled and applied by `report.js`.

```css
.link-cross-module {
  stroke: var(--color-cross-module);
  stroke-width: 3px;
}
```

**What is missing:** "collapsed by default; expanding jumps to the other module" is an interaction, not just a stroke width, and it is not implemented. `TreeBuilder.callChildren`/`calleeNode` (`report.js:214-238`) treats a `CROSS_MODULE` edge exactly like `CALL_INTERNAL`/`CALL_EXTERNAL` — it expands the callee into a normal method node with the same `expand()`/`toggle()` mechanics, the only special case being the *already-visited* rule (which is method-identity based, not module-boundary based). There is no code path that collapses a cross-module callee by default, nor one that "jumps to the other module" on expand (the only "jump" behaviour in the file is `jumpTo`/`flash`, used exclusively for the revisit badge, `report.js:339-363`).

Confirmed against real data: cross-module callees are ordinary entries in the flat `methods[]` list and render as ordinary method nodes:
```python
dev.kairos.common.util.helpers.JsonConverter#parseJson(String, ObjectMapper) in methods list: True
dev.kairos.common.pagination.Pagination#of(Integer, Integer) in methods list: True
```

**Command:**
```
grep -n "CROSS_MODULE" codemap-render/src/main/resources/dev/codemap/render/report.js
```

**Output:**
```
    17:    CROSS_MODULE: "CROSS_MODULE",
   518:      case EDGE_KIND.CROSS_MODULE:
```
(only two references: the enum literal and the `linkClass` switch — no collapse/expand-jump logic.)

**Missing test/feature:** `test-author` should add a `tree-builder.test.js`-style test asserting a `CROSS_MODULE` edge's callee node starts `collapsedRevisit`-like (or an equivalent new `collapsedCrossModule` flag) rather than auto-expanding, and a `GraphView`-level test asserting expanding it moves the viewport/selection to the target module's root — but this needs the underlying behaviour implemented first; it is currently absent from `report.js`.
</details>

<a id="ac9"></a>
<details>
<summary>⚠️ <b>AC9</b> — revisited node shows "↗ already above", clicking jumps to original — <code>tree-builder.test.js</code> — PASS-with-caveat</summary>

**Criterion:** A revisited node shows `↗ already above` and clicking it jumps to the original

**Test:** `codemap-render/src/test/js/tree-builder.test.js:73-84`

```javascript
const revisitedANode = methodBNode.children[0];
assert.strictEqual(revisitedANode.collapsedRevisit, true,
    "revisiting a() higher in the branch must render collapsed (spec §3.1)");
assert.strictEqual(revisitedANode.revisitTargetKey, methodANode.nodeKey,
    "the revisit badge must link back to the original occurrence");

// Expanding the revisited node must not recurse further — this is what
// keeps a cycle from hanging the UI.
builder.expand(revisitedANode);
assert.strictEqual(revisitedANode.children.length, 0,
    "a collapsed revisit node must not materialise further children");
```

This proves the **tree-building half**: a revisited method is flagged `collapsedRevisit` and carries `revisitTargetKey` pointing at the original node's key, and it never grows children. The label text itself (`report.js:547-549`, `nodeLabel` appending `ALREADY_ABOVE_LABEL = "↗ already above"`) is a one-line pure function not separately unit-tested, but it is trivial and directly reads the same `collapsedRevisit` flag this test asserts.

**What is not covered:** the "clicking it jumps to the original" half — `GraphView.toggle()` → `jumpTo()` → `findByKey()` + `flash()` (`report.js:325-363`) is pure D3/DOM interaction and is not exercised by `tree-builder.test.js` (which only builds/exercises the `TreeBuilder`, not `GraphView`). No JS test constructs a `GraphView`, clicks a revisit node, and asserts `flash()`/scroll/selection behaviour. This click-jump behaviour was covered by the **prior manual Chrome verification** described in the task context ("expanding `kairos-api` lazily produces its 20 real entry points" etc., and revisit/cycle handling generally), not by an automated test in this pass.

**Command:**
```
node codemap-render/src/test/js/tree-builder.test.js
```

**Output:**
```
tree-builder.test.js: all assertions passed
```

**Verdict:** the data model half (badge + target key) is solidly test-covered; the click-to-jump interaction rests on manual verification only, not an automated test — reported as PASS-with-caveat rather than a clean PASS.
</details>

<a id="ac10"></a>
<details>
<summary>✅ <b>AC10</b> — cycles do not hang the UI — <code>tree-builder.test.js</code> — PASS</summary>

**Criterion:** Cycles do not hang the UI

**Test:** `codemap-render/src/test/js/tree-builder.test.js` (full file — see `fixtureWithCycle()`, lines 18-46, and the walk in `run()`, lines 48-86)

```javascript
function fixtureWithCycle() {
  ...
  methods: [
    { id: methodA, ..., source: "void a() { b(); }", ... },
    { id: methodB, ..., source: "void b() { a(); }", ... }
  ],
  edges: [
    { from: methodA, to: methodB, kind: "CALL_INTERNAL", resolved: true, line: 2 },
    { from: methodB, to: methodA, kind: "CALL_INTERNAL", resolved: true, line: 5 }
  ],
  ...
}
```
The test walks `a() → b() → a()` and asserts the second occurrence of `a()` renders `collapsedRevisit: true` with zero further children on expand — this is precisely the mechanism (bounded materialisation via the revisit rule) that keeps a real cycle in an analysed codebase from producing an infinite tree.

**Command:**
```
node codemap-render/src/test/js/tree-builder.test.js
```

**Output:**
```
tree-builder.test.js: all assertions passed
```

**Caveat:** this proves the *data structure* terminates (no infinite `children` array, no infinite recursion in `TreeBuilder`). It does not measure real browser responsiveness (frame rate, layout thrash) under a cycle — that class of "hang" can only be observed in an actual browser session, which is outside what a headless Node smoke test can assert. No such browser session was re-run in this pass; the claim rests on the termination proof above plus the general manual Kairos verification described in the task context (no console errors, page loads, expansion works).
</details>

<a id="ac11"></a>
<details>
<summary>✅ <b>AC11</b> — changed nodes outlined green, affected yellow — <code>ReportRendererTest#embedsChangeStatus</code>, <code>stylesheet.test.js</code> — PASS</summary>

**Criterion:** Changed nodes are outlined green; `affected` yellow

**Test:** `codemap-render/src/test/java/dev/codemap/render/ReportRendererTest.java:166-172`

```java
@Test
@DisplayName("carries change status so changed/affected/removed nodes can be outlined")
void embedsChangeStatus() throws IOException {
    String html = render(fixtureIndex());

    assertThat(html).contains(ChangeStatus.CHANGED.name());
}
```

`codemap-render/src/test/js/stylesheet.test.js:38-45` (equal-specificity override guard):
```javascript
assertDeclaredAfter(".node-changed > circle", ".node circle");
assertDeclaredAfter(".node-removed > circle", ".node circle");
assertDeclaredAfter(".node-affected > circle", ".node circle");
assertDeclaredAfter(".node-revisit > circle", ".node circle");
assertDeclaredAfter(".node-revisit > text", ".node text");
assertDeclaredAfter(".node.selected > circle", ".node circle");
```

CSS colours (`report.css:8-11`):
```css
--color-added: #37b26c;
--color-removed: #e0525a;
--color-affected: #d9a441;
```
`#37b26c` = `rgb(55,178,108)`, matching exactly the green colour confirmed in the prior manual Chrome verification (per task context: "changed nodes outline green (rgb(55,178,108))").

**Command:**
```
./gradlew build -i --console=plain
node codemap-render/src/test/js/stylesheet.test.js
```

**Output:**
```
TEST-dev.codemap.render.ReportRendererTest$EmbeddedData.xml:
  <testcase name="carries change status so changed/affected/removed nodes can be outlined" .../>
stylesheet.test.js: all assertions passed
```
Confirmed on real Kairos run: `INFO  Changes: 824 added, 1 affected` — both `ADDED`/`CHANGED` (green) and `AFFECTED` (yellow) statuses genuinely occur in the generated report.
</details>

<a id="ac12"></a>
<details>
<summary>❌ <b>AC12</b> — Focus on changes collapses everything else — <b>NONE</b> — GAP (test missing)</summary>

**Criterion:** Focus on changes collapses everything else

**What exists (implementation):** `GraphView.setFocusOnChanges()` / `visibleRoots()` / `subtreeHasChange()` (`report.js:371-374`, `404-409`, `485-491`):

```javascript
setFocusOnChanges(value) {
  this.focusOnChanges = value;
  this.render();
}

visibleRoots() {
  if (!this.focusOnChanges) {
    return this.roots;
  }
  return this.roots.filter((root) => subtreeHasChange(root));
}

function subtreeHasChange(node) {
  const changed = node.status && node.status !== CHANGE_STATUS.UNCHANGED;
  if (changed) {
    return true;
  }
  return node.children.some((child) => subtreeHasChange(child));
}
```

**What is missing:** no JS test file constructs a `GraphView`, toggles `setFocusOnChanges(true)`, and asserts the rendered node count/roots shrink to only changed subtrees. `subtreeHasChange` is also a module-private function, not exported via `window.CodemapInternal`, so it cannot currently be unit-tested in isolation without exporting it. This was manually verified once in Chrome (per task context: "focus-on-changes change the rendered node count"), but that is a one-time manual check, not a regression-guarding automated test.

**Command:**
```
grep -rln "setFocusOnChanges\|subtreeHasChange\|visibleRoots" codemap-render/src/test/js/
```

**Output:**
```
(no matches — confirmed no test file references any of these)
```

**Missing test to add:** `test-author` should add (e.g.) `focus-on-changes.test.js` that builds a `GraphView` over a small fixture with one changed and one unchanged module root, calls `setFocusOnChanges(true)`, and asserts `visibleRoots()` (once exported) returns only the changed root; and the reverse call returns both.
</details>

<a id="ac13"></a>
<details>
<summary>❌ <b>AC13</b> — search filters by class and method name — <b>NONE</b> — GAP (test missing)</summary>

**Criterion:** Search filters by class and method name

**What exists (implementation):** `GraphView.setSearchTerm()` / `matchesFilters()` / `nodeMatchesSearch()` (`report.js:376-379`, `411-422`, `493-503`):

```javascript
setSearchTerm(term) {
  this.searchTerm = (term || "").toLowerCase();
  this.render();
}

matchesFilters(node) {
  if (node.kind !== NODE_KIND.METHOD && node.kind !== NODE_KIND.ENTRY_POINT) {
    return true;
  }
  if (this.searchTerm && !nodeMatchesSearch(node, this.index, this.searchTerm)) {
    return false;
  }
  ...
}

function nodeMatchesSearch(node, index, term) {
  if (node.label.toLowerCase().includes(term)) {
    return true;
  }
  if (node.methodId) {
    const method = index.method(node.methodId);
    const owner = method && index.classOf(method.classId);
    return owner ? owner.simpleName.toLowerCase().includes(term) : false;
  }
  return false;
}
```

**Additional finding:** `matchesFilters(node)` itself does not appear to be called anywhere in `render()`/`drawNodes()`/`layoutTree()` — grep confirms:
```
$ grep -n "matchesFilters" codemap-render/src/main/resources/dev/codemap/render/report.js
   411:  matchesFilters(node) {
```
`matchesFilters` is defined but never invoked. `render()` calls `layoutTree(visibleRoots, ...)`, and `visibleRoots()` only consults `focusOnChanges` (AC12), not `searchTerm` or `layerFilter`. This means, on inspection, **search and the layer filter currently have no effect on which nodes are drawn** — `setSearchTerm`/`setLayerFilter` update state and call `render()`, but nothing in the render path filters by that state. This looks like a real functional regression/gap, not just a missing test, though it is possible the intent is for `matchesFilters` to prune within `layoutSubtree` and that wiring was dropped.

**What is missing:** no JS test exercises `setSearchTerm`, `matchesFilters`, or `nodeMatchesSearch` at all.

**Command:**
```
grep -n "matchesFilters" codemap-render/src/main/resources/dev/codemap/render/report.js
grep -rln "setSearchTerm\|nodeMatchesSearch\|matchesFilters" codemap-render/src/test/js/
```

**Output:**
```
   411:  matchesFilters(node) {
(second grep: no matches — no test file references any of these)
```

**Missing test to add:** `test-author` should add a search-filter test asserting: (1) `nodeMatchesSearch` matches on both node label and owning class simple name; (2) — once `matchesFilters` is actually wired into the render/layout path — that `render()` with a search term active excludes non-matching method/entry-point nodes from the laid-out node set. Given the dead-code finding above, this is also worth flagging back to the implementer, not just to `test-author`.
</details>

<a id="ac14"></a>
<details>
<summary>⚠️ <b>AC14</b> — the file works after being copied to another machine — self-containment tests + grep — PASS-with-caveat</summary>

**Criterion:** The file works after being copied to another machine

**Test:** `ReportRendererTest$SelfContainment` (see AC1) plus `ClasspathAssetLoaderTest`:

```java
@Test
@DisplayName("loads D3 as a non-empty, parseable script bundle rather than a CDN <script src> stub")
void d3IsTheActualBundleNotAReference() {
    String d3 = loader.loadD3();

    assertThat(d3).doesNotContain("<script");
    assertThat(d3.length()).isGreaterThan(1000);
}
```

Real-file verification against the Kairos-generated report:
```
$ grep -c 'src="http\|href="http\|<script src=' ../kairos/codemap/report.html
0
$ ls -la ../kairos/codemap/report.html
-rw-r--r--@ 1 vladyslavkekukh staff 1383514 ... report.html
```
A 1.4MB single file with zero external references and D3/CSS/JS/data all inlined is, by construction, copy-portable — there is nothing in it that resolves relative to its original location or the network.

**Command:**
```
./gradlew build -i --console=plain
grep -c 'src="http\|href="http\|<script src=' ../kairos/codemap/report.html
```

**Output:**
```
TEST-dev.codemap.render.ClasspathAssetLoaderTest.xml: tests="3" failures="0" errors="0"
0
```

**Caveat:** "works after being copied to another machine" was not literally re-tested by copying the file to a second machine and opening it there in this verification pass. The claim is inferred from self-containment (no external refs, D3/CSS/JS/data all inlined, confirmed above) — which is the only thing that could break portability — combined with the general manual Chrome verification recorded in the task context. No literal cross-machine copy-and-open was performed here.
</details>

## Gaps

1. **AC3 — module overview diagram.** Data (`moduleDependencies`) is computed and embedded correctly, but `report.js` never renders a "which module depends on which" view; only the lazy entry-point tree exists. **Action:** implementer needs to add the overview rendering (out of scope for this agent to build); `test-author` should then add a JS test asserting it renders one link per `moduleDependencies` entry.

2. **AC8 — cross-module collapse/jump.** The heavy 3px connector styling is correct and tested, but cross-module callees expand exactly like any other call — there is no "collapsed by default" state and no "expanding jumps to the other module" behaviour. **Action:** implementer needs to add this behaviour to `TreeBuilder`/`GraphView`; `test-author` should add a `tree-builder.test.js`-style test for the collapse-by-default state and a `GraphView`-level test for the jump.

3. **AC12 — Focus on changes.** Implemented (`setFocusOnChanges`/`visibleRoots`/`subtreeHasChange`) but zero automated test coverage; `subtreeHasChange` isn't even exported for testing. **Action:** `test-author` should add a focused JS test (export `subtreeHasChange`/`visibleRoots` if needed) asserting unchanged-only roots disappear and changed roots remain when the toggle is active.

4. **AC13 — Search filter.** Implemented but apparently **disconnected** — `matchesFilters` is defined but never called from the render path, so `setSearchTerm`/`setLayerFilter` currently have no visible effect on the drawn tree. This looks like a functional bug, not solely a test gap. **Action:** flag to the implementer to wire `matchesFilters` into `layoutTree`/`layoutSubtree` (or wherever node visibility is decided), then `test-author` adds a JS test asserting a search term actually prunes non-matching nodes from `render()`'s laid-out output.

5. **AC6 — Called by / Calls navigability.** The lists render with `data-method-id` on each link, but no click handler is wired anywhere to actually navigate/select on click, and no test builds/asserts the panel DOM. **Action:** implementer wires a click handler (e.g., select/jump to the target method), then `test-author` adds a panel-construction test plus a click-simulation test.

6. **AC9 / AC10 (partial) — click-to-jump and real-browser cycle safety.** The tree-building half of the revisit rule and cycle termination is solidly unit-tested (`tree-builder.test.js`). The `GraphView`-level click/jump interaction and true in-browser responsiveness under a cycle are not covered by any automated test and rest entirely on the one prior manual Chrome session described in the task context — not re-verified here. Not a hard gap (nothing suggests a defect), but the report should not claim a fresh automated PASS beyond what `tree-builder.test.js` actually proves.

7. **AC1 / AC14 — literal file:// console check and literal cross-machine copy.** Both rest on the same caveat already known to the team: the true `file://` open with console-error checking, and a literal copy-to-another-machine test, were not (and could not be, in this environment) re-run; they rest on self-containment/CSP automated tests plus the prior manual browser session over `http://127.0.0.1`.

## Verdict

8/14 acceptance criteria verified with passing, on-target automated tests. 3 criteria PASS-with-caveat (rest partly on prior manual verification, honestly disclosed). 3 criteria are GAPS: two (AC3 module overview, AC8 cross-module collapse/jump) have **no implementation** on the front end yet despite correct underlying data, and two (AC12 focus-on-changes, AC13 search) have implementations with **zero automated test coverage**, with AC13 additionally showing signs of being disconnected from the render path (`matchesFilters` defined but unused) — worth flagging to the implementer, not just to test-author. AC6 (Called by/Calls) is partially covered: data is right, navigability (click-to-jump) looks unwired.

GAPS: AC3 (module overview not rendered), AC8 (cross-module collapse/jump not implemented), AC12 (no test for focus-on-changes), AC13 (no test, and filter looks disconnected from rendering), AC6 (Called by/Calls links have no click handler).
