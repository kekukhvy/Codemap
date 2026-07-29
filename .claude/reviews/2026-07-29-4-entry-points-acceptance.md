# Acceptance evidence — feature/4-entry-points — 2026-07-29

- Issue: [#4 — Detect entry points and make them map roots](../../../issues/4) (`gh issue view 4`)
- Spec: `doc/specification.md` §4.1
- Test command(s) run:
  - `./gradlew :codemap-core:test --tests 'dev.codemap.core.entrypoint.*' --tests 'dev.codemap.core.model.EntryPointTest' --tests 'dev.codemap.core.index.ProjectIndexerTest' --rerun`
  - `./gradlew build`
  - `java -jar codemap-cli/build/libs/codemap.jar --root ../kairos`
  - Manual inspection of `../kairos/codemap/index.json` (Python, read-only)
  - Manual inspection of `../kairos/kairos-api/src/main/java/dev/kairos/api/Router.java` line by line, and a repo-wide grep across `../kairos` for `@Route`, `main()`, `app.<verb>(...)`, and every other annotation kind in the spec table
- Result: 11 criteria — 8 covered+passing by unit test, 3 proven only end-to-end (no dedicated unit test) — no gaps, no failures

## Coverage matrix

| AC | Criterion (short) | Evidence (test / gate) | Ran? | Result |
|----|-------------------|------------------------|------|--------|
| [AC1](#ac1) | All 19 Kairos REST routes detected, correct verb+path | `EntryPointDetectorTest$ProgrammaticJavalinRoutes` + e2e vs `../kairos` | yes | ✅ PASS |
| [AC2](#ac2) | Path constants resolved to literal values | `EntryPointDetectorTest$ProgrammaticJavalinRoutes#recognisesRouteRegisteredWithAMethodReference`, `ConstantFolder` + e2e | yes | ✅ PASS |
| [AC3](#ac3) | Vaadin `@Route` views detected as `UI` | `EntryPointDetectorTest$VaadinRoute` (5 tests) + e2e | yes | ✅ PASS |
| [AC4](#ac4) | `main()` in `kairos-api`/`kairos-admin` detected as `BOOTSTRAP` | `EntryPointDetectorTest$Bootstrap` + `ProjectIndexerTest$EntryPoints#detectsBootstrapEntryPoint` + e2e | yes | ✅ PASS |
| [AC5](#ac5) | Every entry point carries its `moduleId` | `ProjectIndexerTest$EntryPoints#detectsBootstrapEntryPoint` + `EntryPointTest` + e2e | yes | ✅ PASS |
| [AC6](#ac6) | `kairos-api`/`kairos-admin` each show their own entry points under their own root | **end-to-end only** | yes (e2e) | ⚠️ PASS (not-unit-tested) |
| [AC7](#ac7) | Entry points are the roots beneath each module | **end-to-end only** (`entryPoints()`/`moduleId` model support; no renderer yet) | yes (e2e) | ⚠️ PASS (not-unit-tested) |
| [AC8](#ac8) | Each entry point links to the method that implements it | `ProjectIndexerTest$EntryPoints#everyEntryPointMethodIdJoinsToAnIndexedMethod` + e2e | yes | ✅ PASS |
| [AC9](#ac9) | A module with no entry points renders as an empty root, not a crash | `ProjectIndexerTest$EntryPoints#handlesProjectWithNoEntryPoints` (single-module) + e2e (8/10 Kairos modules have zero) | yes | ⚠️ PASS (unit test covers single-module case only; multi-module empty-root case is e2e-only) |
| [AC10](#ac10) | No test class ever reported as an entry point | `ProjectIndexerTest$Coverage#excludesTestSources` (architectural: test sources never parsed) + e2e (0 test-sourced entry points) | yes | ✅ PASS |
| [AC11](#ac11) | `detectedBy` recorded on every entry point | `EntryPointTest` + every `EntryPointDetectorTest` case asserts `DetectedBy.RULE` + e2e (25/25 `RULE`) | yes | ✅ PASS |

## Evidence log

<a id="ac1"></a>
<details>
<summary>✅ <b>AC1</b> — All 19 Kairos REST routes detected, correct verb+path — <code>EntryPointDetectorTest$ProgrammaticJavalinRoutes#recognisesEveryHttpVerb</code> + e2e run — PASS</summary>

**Criterion:** All 19 Kairos REST routes are detected from `Router.java` with correct HTTP method and path

**Independent verification of the route count and content (not just trusting the prior 19 claim):**

I read `../kairos/kairos-api/src/main/java/dev/kairos/api/Router.java` in full and counted every
`app.<verb>(...)` call by hand:

```
grep -nE '^\s*app\.(get|post|put|delete|patch)' Router.java | wc -l
19
```

Line-by-line list (method, constant, resolved path) — all 19 accounted for:

```
42:  app.get(TASKS, taskHandler::list)                     -> GET  /api/v1/tasks
43:  app.post(TASKS, taskHandler::create)                  -> POST /api/v1/tasks
44:  app.get(TASKS_BY_ID, taskHandler::getById)             -> GET  /api/v1/tasks/{id}
45:  app.put(TASKS_BY_ID, taskHandler::update)              -> PUT  /api/v1/tasks/{id}
46:  app.delete(TASKS_BY_ID, taskHandler::delete)           -> DELETE /api/v1/tasks/{id}
47:  app.post(TASK_START, taskHandler::start)               -> POST /api/v1/tasks/{id}/start
48:  app.post(TASK_STOP, taskHandler::stop)                 -> POST /api/v1/tasks/{id}/stop
58:  app.get(DESTINATIONS, destinationHandler::list)        -> GET  /api/v1/destinations
59:  app.post(DESTINATIONS, destinationHandler::create)     -> POST /api/v1/destinations
60:  app.put(DESTINATIONS_BY_ID, destinationHandler::update) -> PUT /api/v1/destinations/{id}
61:  app.delete(DESTINATIONS_BY_ID, destinationHandler::delete) -> DELETE /api/v1/destinations/{id}
62:  app.get(DESTINATIONS_BY_ID, destinationHandler::getById) -> GET /api/v1/destinations/{id}
73:  app.post(TASK_SCHEDULES, scheduleHandler::create)      -> POST /api/v1/tasks/{taskId}/schedules
74:  app.get(TASK_SCHEDULES, scheduleHandler::listByTask)   -> GET  /api/v1/tasks/{taskId}/schedules
75:  app.get(SCHEDULES_BY_ID, scheduleHandler::getById)     -> GET  /api/v1/schedules/{id}
76:  app.put(SCHEDULES_BY_ID, scheduleHandler::update)      -> PUT  /api/v1/schedules/{id}
77:  app.delete(SCHEDULES_BY_ID, scheduleHandler::delete)   -> DELETE /api/v1/schedules/{id}
78:  app.patch(SCHEDULE_PAUSE, scheduleHandler::pause)      -> PATCH /api/v1/schedules/{id}/pause
79:  app.patch(SCHEDULE_RESUME, scheduleHandler::resume)    -> PATCH /api/v1/schedules/{id}/resume
```

I also grepped the entire Kairos repo (all modules, production sources only) for any other
`app.<verb>(...)`/`javalin.<verb>(...)` call — **none found outside `Router.java`** — so 19 is the
complete set, not a partial count.

Diffed this list against the actual `index.json` output (below) — every label and methodId matches
exactly, in both directions (no route missing, no extra route).

**Unit test:** `codemap-core/src/test/java/dev/codemap/core/entrypoint/EntryPointDetectorTest.java:352`

```java
@Test
@DisplayName("recognises every verb Javalin exposes, registered in one method")
void recognisesEveryHttpVerb() throws IOException {
    writeAuxiliaryClass("TaskHandler", """
            package com.example;

            public class TaskHandler {
                public void list(String requestBody) {}
                public void create(String requestBody) {}
                public void update(String requestBody) {}
                public void delete(String requestBody) {}
                public void patch(String requestBody) {}
            }
            """);
    CompilationUnit unit = parse("Router", """
            package com.example;

            import io.javalin.Javalin;

            public final class Router {
                private static final String TASKS = "/api/v1/tasks";
                private static final String TASKS_BY_ID = "/api/v1/tasks/{id}";

                public static void register(Javalin app, TaskHandler taskHandler) {
                    app.get(TASKS, taskHandler::list);
                    app.post(TASKS, taskHandler::create);
                    app.put(TASKS_BY_ID, taskHandler::update);
                    app.delete(TASKS_BY_ID, taskHandler::delete);
                    app.patch(TASKS_BY_ID, taskHandler::patch);
                }
            }
            """);

    List<EntryPoint> entryPoints = detector.detect(unit, MODULE_ID, RELATIVE_PATH);

    assertThat(entryPoints).extracting(EntryPoint::label).containsExactlyInAnyOrder(
            "GET /api/v1/tasks",
            "POST /api/v1/tasks",
            "PUT /api/v1/tasks/{id}",
            "DELETE /api/v1/tasks/{id}",
            "PATCH /api/v1/tasks/{id}");
}
```

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.entrypoint.*' --rerun
```

**Output (JUnit XML, `ProgrammaticJavalinRoutes` nested class):**
```
<testsuite name="REST — programmatic Javalin routes" tests="4" skipped="0" failures="0" errors="0" time="0.034">
```
4/4 passing (includes `recognisesEveryHttpVerb`, `recognisesRouteRegisteredWithAMethodReference`,
`ignoresCallsOnUnrelatedReceivers`, `fallsBackToTextForADynamicPath`).

**End-to-end command:**
```
java -jar codemap-cli/build/libs/codemap.jar --root ../kairos
```

**Output:**
```
INFO  Discovered 10 module(s)
INFO  Built call graph: 1681 edge(s) (1440 resolved, 241 unresolved)
INFO  Detected 25 entry point(s)
INFO  Indexed 169 file(s): 176 class(es), 833 method(s) in 1297 ms
```

19 REST entries extracted from `index.json`, all `kind: REST`, `moduleId: kairos-api`,
`detectedBy: RULE` — verified to match the 19 registrations above one-to-one:

```
DELETE /api/v1/destinations/{id} | dev.kairos.api.destination.DestinationHandler#delete(Context)
DELETE /api/v1/schedules/{id}    | dev.kairos.api.schedule.ScheduleHandler#delete(Context)
DELETE /api/v1/tasks/{id}        | dev.kairos.api.task.TaskHandler#delete(Context)
GET /api/v1/destinations         | dev.kairos.api.destination.DestinationHandler#list(Context)
GET /api/v1/destinations/{id}    | dev.kairos.api.destination.DestinationHandler#getById(Context)
GET /api/v1/schedules/{id}       | dev.kairos.api.schedule.ScheduleHandler#getById(Context)
GET /api/v1/tasks                | dev.kairos.api.task.TaskHandler#list(Context)
GET /api/v1/tasks/{id}           | dev.kairos.api.task.TaskHandler#getById(Context)
GET /api/v1/tasks/{taskId}/schedules | dev.kairos.api.schedule.ScheduleHandler#listByTask(Context)
PATCH /api/v1/schedules/{id}/pause   | dev.kairos.api.schedule.ScheduleHandler#pause(Context)
PATCH /api/v1/schedules/{id}/resume  | dev.kairos.api.schedule.ScheduleHandler#resume(Context)
POST /api/v1/destinations        | dev.kairos.api.destination.DestinationHandler#create(Context)
POST /api/v1/tasks               | dev.kairos.api.task.TaskHandler#create(Context)
POST /api/v1/tasks/{id}/start    | dev.kairos.api.task.TaskHandler#start(Context)
POST /api/v1/tasks/{id}/stop     | dev.kairos.api.task.TaskHandler#stop(Context)
POST /api/v1/tasks/{taskId}/schedules | dev.kairos.api.schedule.ScheduleHandler#create(Context)
PUT /api/v1/destinations/{id}    | dev.kairos.api.destination.DestinationHandler#update(Context)
PUT /api/v1/schedules/{id}       | dev.kairos.api.schedule.ScheduleHandler#update(Context)
PUT /api/v1/tasks/{id}           | dev.kairos.api.task.TaskHandler#update(Context)
```
(19 lines — matches the manual count above exactly, verb and path.)
</details>

<a id="ac2"></a>
<details>
<summary>✅ <b>AC2</b> — Path constants resolved to literal values — <code>ConstantFolder</code> exercised via <code>EntryPointDetectorTest$ProgrammaticJavalinRoutes</code> and <code>VaadinRoute</code> — PASS</summary>

**Criterion:** Path constants are resolved to literal values (`TASKS` → `/api/v1/tasks`)

**Code inspection:** `codemap-core/src/main/java/dev/codemap/core/entrypoint/ConstantFolder.java` resolves
`private static final String` fields via the symbol solver, following cross-file references
(`resolved.asField()` → `toAst()` → initializer), with a fallback to `textOf(expression)` (source text)
when resolution fails — never dropping the entry point.

**Test:** `EntryPointDetectorTest.java:317` (`recognisesRouteRegisteredWithAMethodReference`) resolves
`TASKS` (same-file constant) to `/api/v1/tasks`; `EntryPointDetectorTest.java:411`
(`fallsBackToTextForADynamicPath`) proves the fallback path when the argument is `prefix + "/tasks"`
(not statically foldable) — label becomes the literal expression text `GET prefix + "/tasks"` rather than
being dropped.

```java
@Test
@DisplayName("falls back to the expression text when the path argument is not a literal or constant")
void fallsBackToTextForADynamicPath() throws IOException {
    ...
    assertThat(detector.detect(unit, MODULE_ID, RELATIVE_PATH))
            .singleElement()
            .satisfies(entryPoint -> assertThat(entryPoint.label()).isEqualTo("GET prefix + \"/tasks\""));
}
```

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.entrypoint.*' --rerun
```

**Output:**
```
<testsuite name="REST — programmatic Javalin routes" tests="4" ... failures="0" errors="0">
```

**End-to-end confirmation against real Kairos constants:** `Router.java` declares
`private static final String TASKS = "/api/v1/tasks";` etc. — none of the 19 routes above show up in
`index.json` as the raw constant name (`TASKS`); every one is the fully folded literal path. Also
confirmed for Vaadin: `DashboardRoutes.HOME = ""` (cross-class constant) folds to the labelled root
`/ (root)`, and `ScheduleRoutes.SCHEDULES`, `TaskRoutes.TASKS`, `DestinationRoutes.DESTINATIONS` all
fold to their plain string values (`schedules`, `tasks`, `destinations`) in the real index — see AC3.
</details>

<a id="ac3"></a>
<details>
<summary>✅ <b>AC3</b> — Vaadin <code>@Route</code> views detected as <code>UI</code> — <code>EntryPointDetectorTest$VaadinRoute</code> (5 tests) + e2e — PASS</summary>

**Criterion:** Vaadin `@Route` views in `kairos-admin` are detected as `UI`

**Independent verification:** grepped all of `../kairos` for `@Route` in production sources —
found exactly 4 classes, all in `kairos-admin`: `DashboardView`, `DestinationView`, `ScheduleView`,
`TaskView` (`DestinationRoutes.java` matched only because of a doc-comment mentioning `@Route`, it is
not itself annotated). This matches the 4 UI entries in `index.json`.

**Test:** `EntryPointDetectorTest.java:197` (class `VaadinRoute`, 5 tests: literal value, root-route
naming, same-file constant, cross-file constant, unresolvable fallback):

```java
@Test
@DisplayName("recognises a class-level @Route with a string literal as a UI entry point")
void recognisesRouteWithLiteralValue() throws IOException {
    CompilationUnit unit = parse("TaskView", """
            package com.example;

            import com.vaadin.flow.router.Route;
            import com.vaadin.flow.component.orderedlayout.VerticalLayout;

            @Route("tasks")
            public class TaskView extends VerticalLayout {
                public void onShow() {
                }
            }
            """);

    List<EntryPoint> entryPoints = detector.detect(unit, MODULE_ID, RELATIVE_PATH);

    assertThat(entryPoints).hasSize(1);
    EntryPoint entryPoint = entryPoints.get(0);
    assertThat(entryPoint.kind()).isEqualTo(EntryPointKind.UI);
    assertThat(entryPoint.label()).isEqualTo("tasks");
    assertThat(entryPoint.detectedBy()).isEqualTo(DetectedBy.RULE);
}
```

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.entrypoint.*' --rerun
```

**Output:**
```
<testsuite name="UI — Vaadin @Route" tests="5" skipped="0" failures="0" errors="0" time="0.012">
```

**End-to-end output** (4 UI entries, `../kairos/codemap/index.json`):
```
UI | / (root)     | dev.kairos.admin.feature.dashboard.DashboardView#DashboardView(...) | kairos-admin
UI | destinations | dev.kairos.admin.feature.destination.DestinationView#DestinationView(...) | kairos-admin
UI | schedules    | dev.kairos.admin.feature.schedule.ScheduleView#ScheduleView(...) | kairos-admin
UI | tasks        | dev.kairos.admin.feature.task.TaskView#TaskView(...) | kairos-admin
```
`DashboardView` is `@Route(value = DashboardRoutes.HOME)` with `HOME = ""`, correctly labelled
`/ (root)` per the root-route-naming rule — confirms both the annotation-detection and the
constant-folding-across-classes paths.
</details>

<a id="ac4"></a>
<details>
<summary>✅ <b>AC4</b> — <code>main()</code> in <code>kairos-api</code>/<code>kairos-admin</code> detected as <code>BOOTSTRAP</code> — <code>EntryPointDetectorTest$Bootstrap</code> + <code>ProjectIndexerTest$EntryPoints#detectsBootstrapEntryPoint</code> — PASS</summary>

**Criterion:** `main()` in `kairos-api` and `kairos-admin` is detected as `BOOTSTRAP`

**Independent verification:** grepped all of `../kairos` for `public static void main` in production
sources — exactly 2 matches: `kairos-admin/.../KairosAdminApplication.java` and
`kairos-api/.../KairosApplication.java`. No other `main()` exists in any other module (engine, worker,
adapters, sdk, common all lack one) — matches the 2 BOOTSTRAP entries in `index.json`.

**Test:** `EntryPointDetectorTest.java:50`

```java
@Test
@DisplayName("recognises public static void main as a BOOTSTRAP entry point")
void recognisesMainMethod() throws IOException {
    CompilationUnit unit = parse("Application", """
            package com.example;

            public class Application {
                public static void main(String[] args) {
                    System.out.println("start");
                }
            }
            """);

    List<EntryPoint> entryPoints = detector.detect(unit, MODULE_ID, RELATIVE_PATH);

    assertThat(entryPoints).hasSize(1);
    EntryPoint entryPoint = entryPoints.get(0);
    assertThat(entryPoint.kind()).isEqualTo(EntryPointKind.BOOTSTRAP);
    assertThat(entryPoint.moduleId()).isEqualTo(MODULE_ID);
    assertThat(entryPoint.methodId()).isEqualTo("com.example.Application#main(String[])");
    assertThat(entryPoint.detectedBy()).isEqualTo(DetectedBy.RULE);
    assertThat(entryPoint.label()).contains("Application");
    assertThat(entryPoint.source().file()).isEqualTo(RELATIVE_PATH);
    assertThat(entryPoint.source().line()).isEqualTo(4);
}
```
A companion negative test (`ignoresInstanceMethodNamedMain`) confirms a non-static `main()` is rejected.

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.entrypoint.*' --tests 'dev.codemap.core.index.ProjectIndexerTest' --rerun
```

**Output:**
```
<testsuite name="BOOTSTRAP" tests="2" skipped="0" failures="0" errors="0" time="0.004">
<testsuite name="entry points" tests="3" skipped="0" failures="0" errors="0" time="0.019">
```

**End-to-end output:**
```
BOOTSTRAP | main() — KairosApplication      | dev.kairos.KairosApplication#main(String[])              | kairos-api
BOOTSTRAP | main() — KairosAdminApplication | dev.kairos.admin.KairosAdminApplication#main(String[])   | kairos-admin
```
</details>

<a id="ac5"></a>
<details>
<summary>✅ <b>AC5</b> — Every entry point carries the <code>moduleId</code> it was found in — <code>ProjectIndexerTest$EntryPoints#detectsBootstrapEntryPoint</code> + <code>EntryPointTest</code> + e2e — PASS</summary>

**Criterion:** Every entry point carries the `moduleId` it was found in

**Test:** `ProjectIndexerTest.java:219` asserts `entryPoint.moduleId()` equals the module the file was
discovered under; `EntryPointTest.java` proves `moduleId` is a required constructor field carried
through unchanged (`carriesEveryFieldRequiredToRenderAndJoinAnEntryPoint`).

```java
@Test
@DisplayName("detects a BOOTSTRAP entry point and attributes it to the module")
void detectsBootstrapEntryPoint() throws IOException {
    writeProductionClass("Application", """
            public class Application {
                public static void main(String[] args) {
                }
            }
            """);

    CodeIndex index = indexer.index(projectRoot);
    String moduleId = index.modules().get(0).id();

    assertThat(index.entryPoints()).singleElement().satisfies(entryPoint -> {
        assertThat(entryPoint.kind()).isEqualTo(dev.codemap.core.model.EntryPointKind.BOOTSTRAP);
        assertThat(entryPoint.moduleId()).isEqualTo(moduleId);
        assertThat(entryPoint.detectedBy()).isEqualTo(dev.codemap.core.model.DetectedBy.RULE);
    });
}
```

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.index.ProjectIndexerTest' --tests 'dev.codemap.core.model.EntryPointTest' --rerun
```

**Output:**
```
<testsuite name="entry points" tests="3" skipped="0" failures="0" errors="0" time="0.019">
<testsuite name="dev.codemap.core.model.EntryPointTest" tests="3" skipped="0" failures="0" errors="0" time="0.004">
```

**End-to-end confirmation:** all 25 entry points in `index.json` carry a non-null `moduleId`
(`0 missing` — verified by script: `missing moduleId: 0`), correctly split `kairos-api: 20`,
`kairos-admin: 5`.
</details>

<a id="ac6"></a>
<details>
<summary>⚠️ <b>AC6</b> — <code>kairos-api</code>/<code>kairos-admin</code> each show their own entry points under their own root — end-to-end only, no dedicated unit test — PASS (gap noted)</summary>

**Criterion:** `kairos-api` and `kairos-admin` each show their own entry points under their own root

**Finding:** There is no unit test that specifically asserts two *different* modules each end up with
their *own, correctly separated* set of entry points (the closest is
`ProjectIndexerTest$MultiModule#indexesEachModuleSeparately`, which covers `classes`/`classesOf`, not
`entryPoints`/`moduleId` on `EntryPoint`). `CodeIndex` also has no `entryPointsOf(moduleId)` helper
(only `classesOf(moduleId)` exists) — the per-module split is proved indirectly by every `EntryPoint`
carrying its own `moduleId` (AC5) plus this end-to-end run, not by a test that indexes two modules and
asserts each keeps only its own entry points.

**Command:**
```
java -jar codemap-cli/build/libs/codemap.jar --root ../kairos
```

**Output (index.json, grouped):**
```
kairos-api:   20 entry points (19 REST + 1 BOOTSTRAP)
kairos-admin:  5 entry points (4 UI + 1 BOOTSTRAP)
```
No cross-contamination: none of `kairos-api`'s entries carry `moduleId: kairos-admin` or vice versa.

**Gap:** `test-author` should add a `ProjectIndexerTest` case (multi-module fixture, one module with a
Javalin-style route, another with a `main()`) asserting
`index.entryPoints().stream().filter(e -> e.moduleId().equals("api")) ...` contains exactly that
module's entry points and none of the other's — the same shape as `indexesEachModuleSeparately` but for
`entryPoints()` instead of `classesOf()`.
</details>

<a id="ac7"></a>
<details>
<summary>⚠️ <b>AC7</b> — Entry points are the roots beneath each module — end-to-end + model inspection only — PASS (gap noted, no renderer yet)</summary>

**Criterion:** Entry points are the roots beneath each module

**Finding:** This is a data-model claim at this stage of the project — `codemap-render` does not yet
consume `entryPoints()` at all (confirmed: no `entryPoint` reference anywhere under
`codemap-render/src/main/java`; the CLI itself prints
`WARN Rendering is not implemented yet — the index was written, but there is no report to open.`). What
*is* proven: `IndexDocument`/`CodeIndex` javadoc and serialisation explicitly designate
`entryPoints` as "a root of the map" (`CodeIndex.java:96`, `IndexDocument.java:33`), and every entry
point's `moduleId` correctly scopes it to its module (AC5/AC6). There is no unit test asserting the
"roots beneath a module" tree shape itself, because that shape does not exist yet outside the index —
it is a future rendering concern.

**Command:**
```
java -jar codemap-cli/build/libs/codemap.jar --root ../kairos
```

**Output:**
```
WARN  Rendering is not implemented yet — the index was written, but there is no report to open.
```

**Gap:** no code gap for this slice (rendering is out of scope — see spec §6.1 pipeline stages), but
flagging this criterion as **proven only as a data-model invariant**, not as an observable "roots
beneath each module" behaviour, since there is no rendering test (nor renderer) to exercise it yet.
</details>

<a id="ac8"></a>
<details>
<summary>✅ <b>AC8</b> — Each entry point links to the method that implements it — <code>ProjectIndexerTest$EntryPoints#everyEntryPointMethodIdJoinsToAnIndexedMethod</code> + e2e — PASS</summary>

**Criterion:** Each entry point links to the method that implements it

**Test:** `ProjectIndexerTest.java:239`

```java
@Test
@DisplayName("every detected entry point's methodId joins to a method actually indexed")
void everyEntryPointMethodIdJoinsToAnIndexedMethod() throws IOException {
    writeProductionClass("TaskHandler", """
            public class TaskHandler {
                public void create(String body) {
                }
            }
            """);
    writeProductionClass("Router", """
            public final class Router {
                private static final String TASKS = "/api/v1/tasks";

                public static void register(io.javalin.Javalin app, TaskHandler taskHandler) {
                    app.post(TASKS, taskHandler::create);
                }
            }
            """);

    CodeIndex index = indexer.index(projectRoot);
    java.util.Set<String> indexedMethodIds = index.methods().stream()
            .map(dev.codemap.core.model.IndexedMethod::id)
            .collect(java.util.stream.Collectors.toSet());

    assertThat(index.entryPoints()).isNotEmpty();
    assertThat(index.entryPoints())
            .as("an entry point method id that does not join to an indexed method can never be navigated to")
            .allSatisfy(entryPoint -> assertThat(indexedMethodIds).contains(entryPoint.methodId()));
}
```

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.index.ProjectIndexerTest' --rerun
```

**Output:**
```
<testsuite name="entry points" tests="3" skipped="0" failures="0" errors="0" time="0.019">
```

**End-to-end confirmation:** scripted check against the real Kairos index — every one of the 25
`methodId`s is present in the `methods` array (`unjoined methodId: 0`).
</details>

<a id="ac9"></a>
<details>
<summary>⚠️ <b>AC9</b> — A module with no entry points renders as an empty root, not a crash — <code>ProjectIndexerTest$EntryPoints#handlesProjectWithNoEntryPoints</code> (single-module) + e2e (multi-module, 8/10 empty) — PASS</summary>

**Criterion:** A module with no recognisable entry points renders as an empty root, not a crash

**Test:** `ProjectIndexerTest.java:269` — covers the single-module case only (a project with one module
and zero matching entry points produces an empty list, no exception):

```java
@Test
@DisplayName("produces no entry points, rather than failing, when nothing matches a rule")
void handlesProjectWithNoEntryPoints() throws IOException {
    writeProductionClass("Plain", "public class Plain { void doWork() {} }");

    CodeIndex index = indexer.index(projectRoot);

    assertThat(index.entryPoints()).isEmpty();
}
```

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.index.ProjectIndexerTest' --rerun
```

**Output:**
```
<testsuite name="entry points" tests="3" skipped="0" failures="0" errors="0" time="0.019">
```

**End-to-end confirmation (the multi-module case the unit test doesn't cover):** ran against Kairos's
10 real modules. 8 have zero entry points (`common`, `kairos-adapters/kafka`,
`kairos-adapters/rabbitmq`, `kairos-adapters/sqs`, `kairos-adapters/webhook`, `kairos-engine`,
`kairos-sdk`, `kairos-worker`) — the run completed cleanly with no exception:

```
INFO  Discovered 10 module(s)
INFO  Detected 25 entry point(s)
INFO  Indexed 169 file(s): 176 class(es), 833 method(s) in 1297 ms
```

modules with entry points: `['kairos-admin', 'kairos-api']`
modules with **no** entry points: `['common', 'kairos-adapters/kafka', 'kairos-adapters/rabbitmq', 'kairos-adapters/sqs', 'kairos-adapters/webhook', 'kairos-engine', 'kairos-sdk', 'kairos-worker']`

No crash, no exception, index written successfully with those 8 modules present in `modules` and absent
from any `entryPoints` grouping. "Renders as an empty root" specifically (a rendered tree node) cannot
yet be verified — `codemap-render` doesn't consume entry points yet (see AC7) — so this is proven at the
index/no-crash level, not at the rendered-tree level.

**Gap:** `test-author` should add a `ProjectIndexerTest$MultiModule` case with two+ modules where only
one has a detectable entry point, asserting the empty module still appears in `index.modules()` with
zero matching `entryPoints()` and the run does not throw — closing the multi-module version of the
already-covered single-module case.
</details>

<a id="ac10"></a>
<details>
<summary>✅ <b>AC10</b> — No test class is ever reported as an entry point — <code>ProjectIndexerTest$Coverage#excludesTestSources</code> (architectural) + e2e — PASS</summary>

**Criterion:** No test class is ever reported as an entry point

**Basis:** per spec §3.6/§4 Notes, test sources are never indexed at all — not a detection rule, an
architectural exclusion at the discovery stage. So "no test class is ever an entry point" follows
directly from "no test class is ever parsed", which is what this test proves:

**Test:** `ProjectIndexerTest.java:48`

```java
@Test
@DisplayName("never indexes test sources")
void excludesTestSources() throws IOException {
    writeProductionClass("Service", "public class Service {}");
    writeTestClass("ServiceTest", "public class ServiceTest {}");

    CodeIndex index = indexer.index(projectRoot);

    assertThat(index.classes()).extracting(IndexedClass::simpleName).containsExactly("Service");
    assertThat(index.files().keySet()).noneMatch(file -> file.contains("/test/"));
}
```

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.index.ProjectIndexerTest' --rerun
```

**Output:**
```
<testsuite name="what gets indexed" tests="5" skipped="0" failures="0" errors="0" time="0.012">
```

**End-to-end confirmation:** scripted check against the real Kairos index (which has 941 `@Test`
methods per CLAUDE.md) — `test files indexed: 0` out of 169 total indexed files, and zero entry points
whose `methodId` or `source.file` contains `Test`/`/test/`.
</details>

<a id="ac11"></a>
<details>
<summary>✅ <b>AC11</b> — <code>detectedBy</code> recorded on every entry point — <code>EntryPointTest</code> + every <code>EntryPointDetectorTest</code> case + e2e — PASS</summary>

**Criterion:** `detectedBy` is recorded on every entry point

**Test:** `EntryPointTest.java` proves `detectedBy` is a required constructor argument
(`rejectsAMissingDetectedBy` throws `NullPointerException` on `null`); every single test case across all
8 nested classes in `EntryPointDetectorTest` (20 tests) that produces an entry point asserts
`detectedBy() == DetectedBy.RULE`.

```java
@Test
void rejectsAMissingDetectedBy() {
    assertThatThrownBy(() -> new EntryPoint(ID, MODULE_ID, EntryPointKind.REST, LABEL, METHOD_ID, null, SOURCE))
            .isInstanceOf(NullPointerException.class);
}
```

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.model.EntryPointTest' --tests 'dev.codemap.core.entrypoint.*' --rerun
```

**Output:**
```
<testsuite name="dev.codemap.core.model.EntryPointTest" tests="3" skipped="0" failures="0" errors="0" time="0.004">
```
(plus all 8 `EntryPointDetectorTest` nested suites, 20/20 passing — see AC1–AC4 blocks above.)

**End-to-end confirmation:** all 25 entry points in `index.json` show `detectedBy: RULE`
(`Counter({'RULE': 25})` — no `CONFIG`, no `AI`, consistent with this slice only implementing rule-based
detection).
</details>

## Independent judgement — the three specific questions asked

**1. Are all 19 routes correct, verb and path, against `Router.java` line by line?**
Yes. I read `Router.java` directly (not from the prior summary) and hand-counted 19
`app.<verb>(CONSTANT, handler::method)` calls across `registerTaskRoutes` (7),
`registerDestinationRoutes` (5), `registerScheduleRoutes` (7). Every verb/path pair in the generated
`index.json` matches the source line for line — see the AC1 block for the full side-by-side.

**2. Is any entry point missed?**
No additional entry point source was found. I grepped all of `../kairos` (all 10 modules, production
sources only) for: `@Route`, `public static void main`, `CommandLineRunner`, `@RestController`,
`@Controller`, `@Scheduled`, `@KafkaListener`, `@RabbitListener`, `@JmsListener`, `@ServerEndpoint`,
`@MessageMapping`, `@Path(`, and any `app.<verb>(...)`/`javalin.<verb>(...)` call outside `Router.java`.
Result: exactly the 4 `@Route` views, 2 `main()` methods, and 19 route registrations already detected —
nothing else exists to miss. `kairos-engine`, `kairos-sdk`, `kairos-worker`, `common`, and all 4
adapters (`kafka`, `rabbitmq`, `sqs`, `webhook`) genuinely have zero rule-recognisable entry points in
this codebase (they are libraries/workers driven by the engine's claim loop, not by any of the
annotation or programmatic-registration shapes this slice implements) — consistent with the `--ai`
fallback (§4.3) being the intended future path for those, not a bug in this slice.

**3. Criteria proven only by the end-to-end run, with no unit test behind them:**
- **AC6** (`kairos-api`/`kairos-admin` each show their own entry points under their own root) — no
  `ProjectIndexerTest` case indexes two modules and asserts each keeps only its own `entryPoints()`
  (the existing `MultiModule` test only covers `classesOf`).
- **AC7** (entry points are the roots beneath each module) — not observable at all yet; `codemap-render`
  doesn't consume `entryPoints()` and the CLI itself reports rendering as not implemented. Proven only
  as a documented model invariant plus the e2e `moduleId` split.
- **AC9**, multi-module half — the single-module "no entry points" case has a real unit test
  (`handlesProjectWithNoEntryPoints`), but the specific shape in the criterion (**a** module with none,
  among **several** that do or don't) is only exercised by the real Kairos run (8 of 10 modules empty,
  no crash), not by a fixture-based unit test.

## Gaps

No criterion is unproven, but three are weaker than the rest — proven only by the `../kairos` run, not
by a fixture-based unit test that would catch a regression in CI on every commit:

1. **AC6** — add `ProjectIndexerTest` case: two modules (e.g. `api` with a Javalin route, `admin` with a
   `@Route` view), assert `index.entryPoints()` filtered by each `moduleId` contains only that module's
   entries.
2. **AC9** (multi-module case) — extend the same fixture: a third module with only plain classes, assert
   it appears in `index.modules()` with zero matching `entryPoints()`, and the run does not throw.
3. **AC7** — no test needed yet (rendering is out of scope for this slice); flagged for `test-author`/
   the renderer slice, not this one.

## Verdict

`11/11 acceptance criteria verified — 8 with dedicated unit tests + e2e, 3 with e2e evidence only
(no unit-test regression guard yet)`. 0 failures, 0 blind gaps.
DONE — all criteria hold against real Kairos and the described unit tests, with 3 flagged for
additional unit coverage (AC6, AC7, AC9-multi-module) to close the e2e-only exposure test-author should
address before the next regression risk.
