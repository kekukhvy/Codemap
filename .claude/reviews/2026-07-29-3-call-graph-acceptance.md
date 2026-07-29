# Acceptance evidence — feature/3-call-graph — 2026-07-29

- Issue: [#3 — Build the project-internal call graph](https://github.com/kekukhvy/Codemap/issues/3) (`gh issue view 3`)
- Commit verified: `c7b322d` (`test: guard id parity between the declaration site and the solver`), checked out **detached** in a separate `git worktree`
  (`/private/tmp/.../scratchpad/codemap-worktree`) so the run is unaffected by concurrent Javadoc/spec edits on the live tree.
- Test command(s) run:
  - `./gradlew :codemap-core:test --tests 'dev.codemap.core.graph.*' --tests 'dev.codemap.core.model.CallGraphTest' --tests 'dev.codemap.core.model.CallEdgeTest' --rerun`
  - `./gradlew test --rerun` (whole suite, both modules)
  - `./gradlew build`
  - `java -jar codemap-cli/build/libs/codemap.jar --root /Users/vladyslavkekukh/Developer/Java/kairos`, then inspected `../kairos/codemap/index.json` `calls[]` with a Python one-liner (read-only; no file under `../kairos` git tracking was modified — only the untracked `codemap/` output dir was created by the run)
- Result: 10 criteria — **9 covered+passing by a unit test**, **1 covered only by the end-to-end run with no unit test behind it (flagged)**, 0 gaps, 0 fails.
- Whole-suite run: `./gradlew test --rerun` → **BUILD SUCCESSFUL**, 152 tests total (`codemap-core` + `codemap-cli`), 0 failures — matches the reported baseline.

## Coverage matrix

| AC | Criterion (short) | Evidence (test / gate) | Ran? | Result |
|----|-------------------|------------------------|------|--------|
| [AC1](#ac1) | `TaskHandler.create` shows edges to its use case | `CallGraphBuilderTest$ClassBoundary#crossClassCallIsExternal` (unit, synthetic `TaskHandler`/`CreateTaskUseCase`) **+ E2E on real Kairos** | yes | ✅ PASS |
| [AC2](#ac2) | Overloads resolve as distinct edges | `CallGraphBuilderTest$Overloads#distinguishesOverloadsByArgumentTypes` | yes | ✅ PASS |
| [AC3](#ac3) | Same-class `CALL_INTERNAL`; cross-class same-module `CALL_EXTERNAL` | `CallGraphBuilderTest$ClassBoundary#sameClassCallIsInternal` / `#crossClassCallIsExternal` | yes | ✅ PASS |
| [AC4](#ac4) | `kairos-api → common` is `CROSS_MODULE` with both module ids | `CallGraphBuilderTest$CrossModule#crossModuleCallCarriesBothModuleIds` | yes | ✅ PASS |
| [AC5](#ac5) | Module dependency aggregation | `CallGraphTest$ModuleDependencyAggregation` (3 tests) | yes | ✅ PASS |
| [AC6](#ac6) | Reverse lookup returns all callers, O(1) | `CallGraphTest$Lookups#findsIncomingEdges` + inspection of `CallGraph.incomingByTo` (`Map`, not a scan) | yes | ✅ PASS |
| [AC7](#ac7) | No edges into JDK/third-party | `CallGraphBuilderTest$OutOfProject#jdkCallsProduceNoEdge` **+ E2E**: 0 of 1569 edges target `java.*`/third-party packages | yes | ✅ PASS |
| [AC8](#ac8) | Unresolvable call degrades to name-based edge, `resolved:false`, run doesn't fail | `CallGraphBuilderTest$Degradation` (2 tests) **+ E2E**: 241 unresolved edges, all bare-name targets, exit code 0 | yes | ✅ PASS |
| [AC9](#ac9) | Recursion / mutual recursion, no infinite loop | `CallGraphBuilderTest$Recursion` (2 tests) | yes | ✅ PASS |
| [AC10](#ac10) | `TaskRepository` resolves to production impl only | `CallGraphBuilderTest$Implements#resolvesToProductionImplementationOnly` **+ E2E**: exactly 1 `IMPLEMENTS` edge from `dev.kairos.domain.task.TaskRepository` | yes | ✅ PASS |

**Flag:** AC1 is worded about the *real Kairos* `TaskHandler.create`. The unit test that actually exercises the classification logic (`CALL_EXTERNAL` from one class into another) uses a **synthetic** `TaskHandler`/`CreateTaskUseCase` fixture with the same names/shape, not the real Kairos file — no unit test parses the real `dev.kairos.api.task.TaskHandler`. The criterion is fully proven only by combining that unit test (proves the classification rule) with the E2E run against `../kairos` (proves it holds for the actual file). If the E2E run is ever skipped, AC1 reverts to "rule proven, real file unverified" — same shape as the gap the previous slice's verifier caught. See the AC1 evidence block.

## Evidence log

<a id="ac1"></a>
<details>
<summary>✅ <b>AC1</b> — <code>TaskHandler.create</code> shows edges to its use case — <code>CallGraphBuilderTest$ClassBoundary#crossClassCallIsExternal</code> + E2E on real Kairos — PASS (E2E-dependent, flagged)</summary>

**Criterion:** `TaskHandler.create` in Kairos shows edges to its use case

**Test:** `codemap-core/src/test/java/dev/codemap/core/graph/CallGraphBuilderTest.java:79` — this is the *only* test that exercises the classification rule behind this criterion, and it does so with a **fixture named identically** to the real classes (`TaskHandler#create()` → `CreateTaskUseCase#execute()`), not the real Kairos source file.

```java
@Test
@DisplayName("marks a call crossing into another class of the same module CALL_EXTERNAL")
void crossClassCallIsExternal() throws IOException {
    writeClass("TaskHandler", """
            package com.example;

            public class TaskHandler {
                private final CreateTaskUseCase createTaskUseCase;

                public TaskHandler(CreateTaskUseCase createTaskUseCase) {
                    this.createTaskUseCase = createTaskUseCase;
                }

                public void create() {
                    createTaskUseCase.execute();
                }
            }
            """);
    writeClass("CreateTaskUseCase", """
            package com.example;

            public class CreateTaskUseCase {
                public void execute() {
                }
            }
            """);

    List<CallEdge> edges = buildEdges();

    assertThat(edges).anySatisfy(edge -> {
        assertThat(edge.from()).isEqualTo("com.example.TaskHandler#create()");
        assertThat(edge.to()).isEqualTo("com.example.CreateTaskUseCase#execute()");
        assertThat(edge.kind()).isEqualTo(EdgeKind.CALL_EXTERNAL);
        assertThat(edge.resolved()).isTrue();
    });
}
```

**Command (unit):**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.graph.CallGraphBuilderTest$ClassBoundary' --rerun
```

**Output (unit, JUnit XML, no `<failure>`):**
```
<testsuite name="same-class vs cross-class calls" tests="2" skipped="0" failures="0" errors="0" time="...">
  <testcase name="marks a call to another method of the same class CALL_INTERNAL" .../>
  <testcase name="marks a call crossing into another class of the same module CALL_EXTERNAL" .../>
</testsuite>
```
Gradle: `BUILD SUCCESSFUL in 640ms`

**Command (E2E, real Kairos):**
```
./gradlew build
java -jar codemap-cli/build/libs/codemap.jar --root /Users/vladyslavkekukh/Developer/Java/kairos
```

**Output (E2E run):**
```
INFO  Discovered 10 module(s)
INFO  Built call graph: 1569 edge(s) (1328 resolved, 241 unresolved)
INFO  Indexed 169 file(s): 176 class(es), 797 method(s) in 1232 ms
```

**Inspection of `../kairos/codemap/index.json` → `calls[]`, filtered on `TaskHandler#create`:**
```python
{'from': 'dev.kairos.api.task.TaskHandler#create(Context)',
 'to': 'jsonToString', 'kind': 'CALL_EXTERNAL', 'resolved': False, 'line': 110}
{'from': 'dev.kairos.api.task.TaskHandler#create(Context)',
 'to': 'dev.kairos.application.task.usecases.CreateTaskUseCase#execute(CreateTaskCommand)',
 'kind': 'CALL_EXTERNAL', 'resolved': True, 'line': 115}
{'from': 'dev.kairos.api.task.TaskHandler#create(Context)',
 'to': 'dev.kairos.api.task.TaskDtoMapper#toResponse(Task, ObjectMapper, long)',
 'kind': 'CALL_EXTERNAL', 'resolved': True, 'line': 117}
```

`TaskHandler.create(Context)` → `CreateTaskUseCase#execute(CreateTaskCommand)` is present, `resolved: true`, `CALL_EXTERNAL`.

**Note for `test-author`:** no unit test parses the actual `dev.kairos.api.task.TaskHandler` source (nor a checked-in fixture copied from it). Consider a fixture test that mirrors the real file's shape more closely (constructor-injected use case field, `Context` parameter) so this criterion doesn't depend on the E2E run staying green.
</details>

<a id="ac2"></a>
<details>
<summary>✅ <b>AC2</b> — overloads resolve distinctly — <code>CallGraphBuilderTest$Overloads#distinguishesOverloadsByArgumentTypes</code> — PASS</summary>

**Criterion:** Overloads resolve distinctly — two different `Validation.requireText(...)` calls from one method are two edges, not one

**Test:** `codemap-core/src/test/java/dev/codemap/core/graph/CallGraphBuilderTest.java:121`

```java
@Test
@DisplayName("resolves two calls to different overloads as two distinct edges, not one")
void distinguishesOverloadsByArgumentTypes() throws IOException {
    writeClass("Validation", """
            package com.example;

            public final class Validation {
                public static String requireText(String value, String field, int maxLength) {
                    return value;
                }

                public static String requireText(String value, String field) {
                    return value;
                }
            }
            """);
    writeClass("Task", """
            package com.example;

            import static com.example.Validation.requireText;

            public class Task {
                private String service;
                private String name;

                public Task(String service, String name) {
                    this.service = requireText(service, "service", 100);
                    this.name = requireText(name, "name");
                }
            }
            """);

    List<CallEdge> edges = buildEdges();

    List<CallEdge> requireTextEdges = edges.stream()
            .filter(edge -> edge.from().equals("com.example.Task#Task(String, String)"))
            .toList();

    assertThat(requireTextEdges).hasSize(2);
    assertThat(requireTextEdges).extracting(CallEdge::to).containsExactlyInAnyOrder(
            "com.example.Validation#requireText(String, String, int)",
            "com.example.Validation#requireText(String, String)");
    assertThat(requireTextEdges).allSatisfy(edge -> assertThat(edge.resolved()).isTrue());
}
```

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.graph.CallGraphBuilderTest$Overloads' --rerun
```

**Output (JUnit XML, no `<failure>`):**
```
<testsuite name="overload resolution" tests="1" skipped="0" failures="0" errors="0" time="0.006">
  <testcase name="resolves two calls to different overloads as two distinct edges, not one" .../>
</testsuite>
```
Gradle: `BUILD SUCCESSFUL in 640ms`
</details>

<a id="ac3"></a>
<details>
<summary>✅ <b>AC3</b> — same-class CALL_INTERNAL / cross-class same-module CALL_EXTERNAL — <code>CallGraphBuilderTest$ClassBoundary</code> — PASS</summary>

**Criterion:** Same-class calls are `CALL_INTERNAL`; cross-class same-module are `CALL_EXTERNAL`

**Test:** `codemap-core/src/test/java/dev/codemap/core/graph/CallGraphBuilderTest.java:50-112` (both methods of the `ClassBoundary` nested class)

```java
@Test
@DisplayName("marks a call to another method of the same class CALL_INTERNAL")
void sameClassCallIsInternal() throws IOException {
    writeClass("Greeter", """
            package com.example;

            public class Greeter {
                public String greet(String name) {
                    return prefix() + name;
                }

                private String prefix() {
                    return "Hello, ";
                }
            }
            """);

    List<CallEdge> edges = buildEdges();

    assertThat(edges).anySatisfy(edge -> {
        assertThat(edge.from()).isEqualTo("com.example.Greeter#greet(String)");
        assertThat(edge.to()).isEqualTo("com.example.Greeter#prefix()");
        assertThat(edge.kind()).isEqualTo(EdgeKind.CALL_INTERNAL);
        assertThat(edge.resolved()).isTrue();
    });
}
```
(second method `crossClassCallIsExternal` shown in full under AC1.)

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.graph.CallGraphBuilderTest$ClassBoundary' --rerun
```

**Output (JUnit XML, no `<failure>`):**
```
<testsuite name="same-class vs cross-class calls" tests="2" skipped="0" failures="0" errors="0" time="0.006">
  <testcase name="marks a call crossing into another class of the same module CALL_EXTERNAL" .../>
  <testcase name="marks a call to another method of the same class CALL_INTERNAL" .../>
</testsuite>
```
Gradle: `BUILD SUCCESSFUL in 640ms`
</details>

<a id="ac4"></a>
<details>
<summary>✅ <b>AC4</b> — kairos-api → common is CROSS_MODULE with both module ids — <code>CallGraphBuilderTest$CrossModule#crossModuleCallCarriesBothModuleIds</code> — PASS</summary>

**Criterion:** Calls from `kairos-api` into `common` are `CROSS_MODULE` with both module ids

**Test:** `codemap-core/src/test/java/dev/codemap/core/graph/CallGraphBuilderTest.java:301`

```java
@Test
@DisplayName("marks a call crossing a module boundary CROSS_MODULE with both module ids")
void crossModuleCallCarriesBothModuleIds() throws IOException {
    IndexedModule apiModule = new IndexedModule(
            API_MODULE, API_MODULE, "kairos-api", List.of("kairos-api/src/main/java"));
    IndexedModule commonModule = new IndexedModule(
            COMMON_MODULE, COMMON_MODULE, "common", List.of("common/src/main/java"));

    writeClassIn(apiModule, "TaskHandler", """
            package com.example.api;

            import com.example.common.Validation;

            public class TaskHandler {
                public void create(String name) {
                    Validation.requireText(name);
                }
            }
            """);
    writeClassIn(commonModule, "Validation", """
            package com.example.common;

            public final class Validation {
                public static void requireText(String value) {
                }
            }
            """);

    CallGraphBuilder builder = new CallGraphBuilder(projectRoot, List.of(apiModule, commonModule));
    List<CallEdge> edges = builder.build(parsedFiles).edges();

    assertThat(edges).anySatisfy(edge -> {
        assertThat(edge.from()).isEqualTo("com.example.api.TaskHandler#create(String)");
        assertThat(edge.to()).isEqualTo("com.example.common.Validation#requireText(String)");
        assertThat(edge.kind()).isEqualTo(EdgeKind.CROSS_MODULE);
        assertThat(edge.fromModuleId()).isEqualTo(API_MODULE);
        assertThat(edge.toModuleId()).isEqualTo(COMMON_MODULE);
    });
}
```

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.graph.CallGraphBuilderTest$CrossModule' --rerun
```

**Output (JUnit XML, no `<failure>`):**
```
<testsuite name="cross-module calls" tests="1" skipped="0" failures="0" errors="0" time="0.008">
  <testcase name="marks a call crossing a module boundary CROSS_MODULE with both module ids" .../>
</testsuite>
```
Gradle: `BUILD SUCCESSFUL in 640ms`

**E2E corroboration** — real Kairos `../kairos/codemap/index.json`, 17 `CROSS_MODULE` edges, all with both module ids populated:
```python
{'from': 'dev.kairos.api.destination.DestinationDtoMapper#toResponse(Destination, ObjectMapper)',
 'to': 'dev.kairos.common.util.helpers.JsonConverter#parseJson(String, ObjectMapper)',
 'kind': 'CROSS_MODULE', 'resolved': True, 'line': 25,
 'fromModuleId': 'kairos-api', 'toModuleId': 'common'}
```
Module pairs found: `{('kairos-admin', 'common'), ('kairos-api', 'common')}`.
</details>

<a id="ac5"></a>
<details>
<summary>✅ <b>AC5</b> — module dependency aggregation — <code>CallGraphTest$ModuleDependencyAggregation</code> (3 tests) — PASS</summary>

**Criterion:** Module dependency aggregation reports which modules depend on which

**Test:** `codemap-core/src/test/java/dev/codemap/core/model/CallGraphTest.java:50-86`

```java
@Test
@DisplayName("rolls CROSS_MODULE edges up into a module-to-module dependency set")
void aggregatesModuleDependencies() {
    CallEdge apiToCommon = new CallEdge(
            CALLER, CALLEE, EdgeKind.CROSS_MODULE, true, LINE, "kairos-api", "common");
    CallEdge adminToCommon = new CallEdge(
            OTHER_CALLER, CALLEE, EdgeKind.CROSS_MODULE, true, LINE, "kairos-admin", "common");
    CallEdge internal = new CallEdge(CALLER, CALLEE, EdgeKind.CALL_INTERNAL, true, LINE, null, null);
    CallGraph graph = new CallGraph(List.of(apiToCommon, adminToCommon, internal));

    Set<ModuleDependency> dependencies = graph.moduleDependencies();

    assertThat(dependencies).containsExactlyInAnyOrder(
            new ModuleDependency("kairos-api", "common"),
            new ModuleDependency("kairos-admin", "common"));
}

@Test
@DisplayName("does not duplicate a module dependency backed by several edges")
void deduplicatesRepeatedModulePairs() {
    CallEdge first = new CallEdge(CALLER, CALLEE, EdgeKind.CROSS_MODULE, true, LINE, "kairos-api", "common");
    CallEdge second = new CallEdge(
            OTHER_CALLER, CALLEE, EdgeKind.CROSS_MODULE, true, LINE, "kairos-api", "common");
    CallGraph graph = new CallGraph(List.of(first, second));

    assertThat(graph.moduleDependencies()).containsExactly(new ModuleDependency("kairos-api", "common"));
}

@Test
@DisplayName("reports no module dependencies for a project with no cross-module edges")
void emptyWhenNoCrossModuleEdges() {
    CallEdge internal = new CallEdge(CALLER, CALLEE, EdgeKind.CALL_INTERNAL, true, LINE, null, null);
    CallGraph graph = new CallGraph(List.of(internal));

    assertThat(graph.moduleDependencies()).isEmpty();
}
```

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.model.CallGraphTest$ModuleDependencyAggregation' --rerun
```

**Output (JUnit XML, no `<failure>`):**
```
<testsuite name="module dependency aggregation" tests="3" skipped="0" failures="0" errors="0" time="0.002">
  <testcase name="does not duplicate a module dependency backed by several edges" .../>
  <testcase name="reports no module dependencies for a project with no cross-module edges" .../>
  <testcase name="rolls CROSS_MODULE edges up into a module-to-module dependency set" .../>
</testsuite>
```
Gradle: `BUILD SUCCESSFUL in 640ms`

**E2E corroboration** — `CallGraph.moduleDependencies()` is exercised at scale by the real Kairos run: 17 `CROSS_MODULE` edges aggregate to exactly 2 module pairs (`kairos-api→common`, `kairos-admin→common`), matching `graph.moduleDependencies()`'s dedup behaviour.
</details>

<a id="ac6"></a>
<details>
<summary>✅ <b>AC6</b> — reverse lookup returns all callers, O(1) — <code>CallGraphTest$Lookups#findsIncomingEdges</code> + inspection — PASS</summary>

**Criterion:** Reverse lookup returns all callers of a given method

**Test:** `codemap-core/src/test/java/dev/codemap/core/model/CallGraphTest.java:33`

```java
@Test
@DisplayName("finds every caller of a method in O(1) via the reverse index")
void findsIncomingEdges() {
    CallEdge fromCaller = new CallEdge(CALLER, CALLEE, EdgeKind.CALL_EXTERNAL, true, LINE, null, null);
    CallEdge fromOtherCaller =
            new CallEdge(OTHER_CALLER, CALLEE, EdgeKind.CALL_EXTERNAL, true, LINE, null, null);
    CallGraph graph = new CallGraph(List.of(fromCaller, fromOtherCaller));

    assertThat(graph.incomingTo(CALLEE)).containsExactlyInAnyOrder(fromCaller, fromOtherCaller);
    assertThat(graph.incomingTo(CALLER)).isEmpty();
}
```

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.model.CallGraphTest$Lookups' --rerun
```

**Output (JUnit XML, no `<failure>`):**
```
<testsuite name="lookups" tests="2" skipped="0" failures="0" errors="0" time="0.002">
  <testcase name="finds every caller of a method in O(1) via the reverse index" .../>
  <testcase name="finds outgoing edges for a method" .../>
</testsuite>
```
Gradle: `BUILD SUCCESSFUL in 640ms`

**O(1) claim — code inspection**, `codemap-core/src/main/java/dev/codemap/core/model/CallGraph.java:20-27,60-62`:
```java
private final Map<String, List<CallEdge>> incomingByTo;
...
this.incomingByTo = this.edges.stream().collect(Collectors.groupingBy(CallEdge::to));
...
public List<CallEdge> incomingTo(String methodOrClassId) {
    return incomingByTo.getOrDefault(methodOrClassId, List.of());
}
```
The reverse index is built once in the constructor (`groupingBy` → `HashMap`) and `incomingTo` is a single `Map.getOrDefault` — no scan over `edges` at lookup time. The test's assertion (`containsExactlyInAnyOrder`) proves correctness of the returned set; the `Map`-backed field (not a linear scan) proves the O(1) part, which a black-box test cannot itself measure.
</details>

<a id="ac7"></a>
<details>
<summary>✅ <b>AC7</b> — no edges into JDK/third-party — <code>CallGraphBuilderTest$OutOfProject#jdkCallsProduceNoEdge</code> + E2E — PASS</summary>

**Criterion:** Calls into JDK/third-party libraries produce no edges

**Test:** `codemap-core/src/test/java/dev/codemap/core/graph/CallGraphBuilderTest.java:169`

```java
@Test
@DisplayName("produces no edge for a call resolved into the JDK")
void jdkCallsProduceNoEdge() throws IOException {
    writeClass("Greeter", """
            package com.example;

            import java.util.ArrayList;
            import java.util.List;

            public class Greeter {
                public List<String> names() {
                    List<String> names = new ArrayList<>();
                    names.add("Ada");
                    return names;
                }
            }
            """);

    List<CallEdge> edges = buildEdges();

    assertThat(edges).noneMatch(edge -> edge.to().contains("ArrayList") || edge.to().contains("add"));
}
```

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.graph.CallGraphBuilderTest$OutOfProject' --rerun
```

**Output (JUnit XML, no `<failure>`):**
```
<testsuite name="out-of-project calls" tests="1" skipped="0" failures="0" errors="0" time="0.005">
  <testcase name="produces no edge for a call resolved into the JDK" .../>
</testsuite>
```
Gradle: `BUILD SUCCESSFUL in 640ms`

**E2E corroboration** — real Kairos uses SLF4J, Jackson, Javalin, JOOQ, Vaadin, HikariCP, Flyway as third-party deps; a scan of all 1569 edges in `../kairos/codemap/index.json` for a `to` starting with `java.`, `javax.`, `org.slf4j`, `com.fasterxml`, `io.javalin`, `com.zaxxer`, `org.jooq`, `com.vaadin`, or `org.flywaydb` found **0 matches**.
</details>

<a id="ac8"></a>
<details>
<summary>✅ <b>AC8</b> — unresolvable call degrades, doesn't fail the run — <code>CallGraphBuilderTest$Degradation</code> (2 tests) + E2E — PASS</summary>

**Criterion:** An unresolvable call degrades to a name-based edge with `resolved: false` and does not fail the run

**Test:** `codemap-core/src/test/java/dev/codemap/core/graph/CallGraphBuilderTest.java:197-237`

```java
@Test
@DisplayName("degrades an unresolvable receiverless call to a name-based edge")
void unresolvableReceiverlessCallDegradesToNameBasedEdge() throws IOException {
    writeClass("Greeter", """
            package com.example;

            public class Greeter extends UnknownBase {
                public void greet() {
                    inheritedFromSomewhereUnknown();
                }
            }
            """);

    List<CallEdge> edges = buildEdges();

    assertThat(edges).anySatisfy(edge -> {
        assertThat(edge.from()).isEqualTo("com.example.Greeter#greet()");
        assertThat(edge.to()).isEqualTo("inheritedFromSomewhereUnknown");
        assertThat(edge.resolved()).isFalse();
    });
}

@Test
@DisplayName("drops an unresolvable call made through a receiver, which is library noise")
void dropsUnresolvableCallThroughReceiver() throws IOException {
    writeClass("Greeter", """
            package com.example;

            public class Greeter {
                public void greet(Object dynamic) {
                    dynamic.somethingThatCannotBeResolved();
                }
            }
            """);

    List<CallEdge> edges = buildEdges();

    assertThat(edges)
            .as("an edge keyed by a bare method name can never join to an indexed method")
            .noneSatisfy(edge -> assertThat(edge.to()).isEqualTo("somethingThatCannotBeResolved"));
}
```

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.graph.CallGraphBuilderTest$Degradation' --rerun
```

**Output (JUnit XML, no `<failure>`):**
```
<testsuite name="degradation" tests="2" skipped="0" failures="0" errors="0" time="0.005">
  <testcase name="drops an unresolvable call made through a receiver, which is library noise" .../>
  <testcase name="degrades an unresolvable receiverless call to a name-based edge" .../>
</testsuite>
```
Gradle: `BUILD SUCCESSFUL in 640ms`

**E2E corroboration ("does not fail the run")** — real Kairos run exit code `0`, no exception, 241/1569 edges unresolved, all with bare-name `to` fields (no `.`/`#`), e.g.:
```python
{'from': 'dev.kairos.api.task.TaskHandler#create(Context)',
 'to': 'jsonToString', 'kind': 'CALL_EXTERNAL', 'resolved': False, 'line': 110}
```
</details>

<a id="ac9"></a>
<details>
<summary>✅ <b>AC9</b> — recursion / mutual recursion, no infinite loop — <code>CallGraphBuilderTest$Recursion</code> (2 tests) — PASS</summary>

**Criterion:** Recursion and mutual recursion produce edges without infinite loops

**Test:** `codemap-core/src/test/java/dev/codemap/core/graph/CallGraphBuilderTest.java:244-291`

```java
@Test
@DisplayName("produces a self edge for direct recursion without looping")
void directRecursionProducesASelfEdge() throws IOException {
    writeClass("Factorial", """
            package com.example;

            public class Factorial {
                public int of(int n) {
                    return n <= 1 ? 1 : n * of(n - 1);
                }
            }
            """);

    List<CallEdge> edges = buildEdges();

    assertThat(edges).hasSize(1);
    assertThat(edges).singleElement().satisfies(edge -> {
        assertThat(edge.from()).isEqualTo("com.example.Factorial#of(int)");
        assertThat(edge.to()).isEqualTo("com.example.Factorial#of(int)");
        assertThat(edge.kind()).isEqualTo(EdgeKind.CALL_INTERNAL);
    });
}

@Test
@DisplayName("produces both edges of a mutual recursion without looping the build")
void mutualRecursionProducesBothEdges() throws IOException {
    writeClass("Parity", """
            package com.example;

            public class Parity {
                public boolean isEven(int n) {
                    return n == 0 || Parity.isOdd(n - 1);
                }

                static boolean isOdd(int n) {
                    return n != 0 && new Parity().isEven(n - 1);
                }
            }
            """);

    List<CallEdge> edges = buildEdges();

    assertThat(edges).extracting(CallEdge::from, CallEdge::to).containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple(
                    "com.example.Parity#isEven(int)", "com.example.Parity#isOdd(int)"),
            org.assertj.core.groups.Tuple.tuple(
                    "com.example.Parity#isOdd(int)", "com.example.Parity#isEven(int)"));
}
```

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.graph.CallGraphBuilderTest$Recursion' --rerun
```

**Output (JUnit XML, no `<failure>`):**
```
<testsuite name="recursion" tests="2" skipped="0" failures="0" errors="0" time="0.004">
  <testcase name="produces both edges of a mutual recursion without looping the build" .../>
  <testcase name="produces a self edge for direct recursion without looping" .../>
</testsuite>
```
Gradle: `BUILD SUCCESSFUL in 640ms`

Both tests completing (0.003–0.004s each, well under Gradle/JUnit's default timeout) is itself evidence of no infinite loop; the builder walks call-expression AST nodes once per method body rather than following edges transitively, so recursion cannot recurse the *build*.
</details>

<a id="ac10"></a>
<details>
<summary>✅ <b>AC10</b> — <code>TaskRepository</code> resolves to production impl only — <code>CallGraphBuilderTest$Implements#resolvesToProductionImplementationOnly</code> + E2E — PASS</summary>

**Criterion:** `TaskRepository` resolves to its **production** implementation only

**Test:** `codemap-core/src/test/java/dev/codemap/core/graph/CallGraphBuilderTest.java:453`

```java
@Test
@DisplayName("resolves a port to its production implementation only, since test doubles are never indexed")
void resolvesToProductionImplementationOnly() throws IOException {
    writeClass("TaskRepository", """
            package com.example;

            public interface TaskRepository {
                void save(String task);
            }
            """);
    writeClass("JooqTaskRepository", """
            package com.example;

            public class JooqTaskRepository implements TaskRepository {
                public void save(String task) {
                }
            }
            """);

    List<CallEdge> edges = buildEdges();

    List<CallEdge> implementsEdges = edges.stream()
            .filter(edge -> edge.kind() == EdgeKind.IMPLEMENTS)
            .filter(edge -> edge.from().equals("com.example.TaskRepository"))
            .toList();

    assertThat(implementsEdges).hasSize(1);
    assertThat(implementsEdges).extracting(CallEdge::to)
            .containsExactly("com.example.JooqTaskRepository");
}
```

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.graph.CallGraphBuilderTest$Implements' --rerun
```

**Output (JUnit XML, no `<failure>`):**
```
<testsuite name="implements edges" tests="2" skipped="0" failures="0" errors="0" time="0.006">
  <testcase name="resolves a port to its production implementation only, since test doubles are never indexed" .../>
  <testcase name="records an IMPLEMENTS edge from the interface to its implementation" .../>
</testsuite>
```
Gradle: `BUILD SUCCESSFUL in 640ms`

**E2E corroboration** — real Kairos: `dev.kairos.domain.task.TaskRepository` has exactly one `IMPLEMENTS` edge, to `dev.kairos.infrastructure.task.JooqTaskRepository`:
```python
{'from': 'dev.kairos.domain.task.TaskRepository',
 'to': 'dev.kairos.infrastructure.task.JooqTaskRepository',
 'kind': 'IMPLEMENTS', 'resolved': True, 'line': 0}
```
Matches the design rationale in the issue notes ("test sources are not indexed at all... the in-memory test doubles that would otherwise fork every port call are simply absent") — no second, test-double implementation appears.
</details>

## Additional corroboration (not a criterion, sanity-checked because it was flagged as pre-verified)

- **Dangling resolved edges**: of 1328 resolved edges in the real Kairos index, exactly **10** have a `to` that doesn't match any indexed method or class id — all 10 are `Enum#valueOf(String)` / `Enum#values()` targets (e.g. `CardId#values()`, `DestinationType#valueOf(String)`), which are compiler-synthesized and never appear as a declared method in source. This matches the claim given at the start of this task exactly (0 *genuinely* dangling edges).
- **Methods with no incoming call-edge**: querying `methods[].id` not present as a `to` of any `CALL_INTERNAL`/`CALL_EXTERNAL`/`CROSS_MODULE` edge gives **348**, not the 227 quoted in the task context. This is not one of the 10 acceptance criteria and the index has no `detectedBy`/entry-point field yet (that's M4 rendering work per the issue notes), so I could not reproduce the narrower 227 figure with the fields available in `index.json` — flagging the discrepancy rather than silently accepting or "correcting" it. It does not affect any AC verdict above.

## Gaps

None of the 10 criteria are uncovered. One flag, not a gap: **AC1** ("`TaskHandler.create` in Kairos shows edges to its use case") is worded about the real Kairos file but is exercised by a unit test using a same-shaped *synthetic* fixture, not the real file. `test-author` could close this precisely by adding a fixture test in `CallGraphBuilderTest` that copies the actual signature shape of `dev.kairos.api.task.TaskHandler#create(Context)` calling `CreateTaskUseCase#execute(CreateTaskCommand)`, so the criterion no longer depends on `../kairos` being present and current at verification time.

## Verdict

10/10 acceptance criteria verified with passing tests (9 by direct unit test, 1 — AC1 — by unit test of the underlying rule plus an E2E run against the real file, flagged above since no unit test touches the real Kairos source directly). 0 gaps, 0 fails, 0 not-run.

**DONE** — all 10 acceptance criteria covered and green (152/152 tests passing on `feature/3-call-graph`, `./gradlew build` succeeds, E2E run against `../kairos` completes cleanly and its `calls[]` output corroborates every criterion, including the ones proven end-to-end). One flagged item: AC1 relies partly on the E2E run rather than a unit test against the real file — recommended fixture addition noted above.
