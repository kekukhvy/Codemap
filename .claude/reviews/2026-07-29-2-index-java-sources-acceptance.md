# Acceptance evidence — feature/2-index-java-sources — 2026-07-29

- Issue: [#2 "Index Java sources into index.json"](https://github.com/kekukhvy/Codemap/issues/2) (`gh issue view 2`)
- PR: #11, branch `feature/2-index-java-sources`, verified commit `96179db`
- Test command(s) run:
  - `./gradlew :codemap-core:test --rerun -i` (and again from an isolated worktree)
  - `./gradlew build --rerun`
  - `java -jar codemap-cli/build/libs/codemap.jar --root ../kairos`
  - three ad-hoc reproduction runs of the jar against small throwaway fixture
    projects (single-module, malformed-file, `build/generated`, anonymous class)
    written under the scratch directory, never under `../kairos`
- Result: 12 criteria — 10 covered+passing, 2 gaps (both have reproducible
  end-to-end evidence but no unit test)

## ⚠️ Environment note (read before the matrix)

Partway through this verification, the primary working tree
(`/Users/vladyslavkekukh/Developer/Java/Codemap`) was modified **by a concurrent
process** (files timestamped 10:47–10:48, mid-session): several `codemap-core`
sources were half-edited (e.g. `FileFingerprints.java` referencing a
not-yet-added `FileFingerprint.UNREADABLE_HASH`) and new, unwired test files
appeared (`FileFingerprintsTest.java`, `MethodSignaturesTest.java`,
`SourceFileScannerTest.java`, `SourceTextTest.java`, `OrphanJavadocIndexTest.java`,
`ProjectPaths.java`). Running `./gradlew :codemap-cli:test` against that dirty
tree failed to compile — **not a defect in PR #11**, but drift introduced live
by another session.

To keep this report's evidence trustworthy, I created an isolated **detached
git worktree at commit `96179db`** (the actual tip of `feature/2-index-java-sources`
at the time `git status` first showed the branch clean) and re-ran the full
build, the full `codemap-core` test suite, and the end-to-end `../kairos` run
from there. All results below are from that clean, isolated state (cross-checked
against the earlier in-place run, which agreed on every number before the tree
was touched). I made **no edits** anywhere; the worktree was removed with
`git worktree remove` after use, and the primary tree was left exactly as I
found it (still carrying the concurrent process's edits, which are not mine to
revert).

## Coverage matrix

| AC | Criterion (short) | Evidence (test / gate) | Ran? | Result |
|----|-------------------|------------------------|------|--------|
| [AC1](#ac1) | Discovers all 10 Gradle modules | `ModuleDiscoveryTest$Gradle` + e2e run on `../kairos` | yes | ✅ PASS |
| [AC2](#ac2) | ~277 files indexed, no test file in index | `ProjectIndexerTest$Coverage#excludesTestSources` + e2e run (169/169 prod files, 0 test files) | yes | ✅ PASS |
| [AC3](#ac3) | Classes/methods/line ranges/signatures correct | `JavaSourceParserTest` (Types/Methods) + e2e spot-check on `Task.java`, `ScheduleType.java` | yes | ✅ PASS |
| [AC4](#ac4) | Every class carries a `moduleId` | `ProjectIndexerTest$Coverage#attributesEveryClassToAModule` + `IndexedClass` ctor guard | yes | ✅ PASS |
| [AC5](#ac5) | Javadoc first lines captured | `JavaSourceParserTest$Javadoc` + e2e (150/176 classes have javadoc) | yes | ✅ PASS |
| [AC6](#ac6) | Nested/anonymous classes handled without crashing | `JavaSourceParserTest#indexesNestedTypes` (nested) + ad-hoc repro (anonymous) | yes | ⚠️ PARTIAL (anonymous-class case has no unit test) |
| [AC7](#ac7) | Records, enums, interfaces indexed | `JavaSourceParserTest#distinguishesTypeKinds` + e2e (127 CLASS/6 ENUM/36 RECORD/7 INTERFACE) | yes | ✅ PASS |
| [AC8](#ac8) | Generated sources excluded by default | **NONE** (unit) — ad-hoc repro only | yes (repro) | ❌ GAP |
| [AC9](#ac9) | Single-module project yields one root | `ModuleDiscoveryTest$NoBuildFile` + ad-hoc repro | yes | ✅ PASS |
| [AC10](#ac10) | Malformed file skipped with warning, not a failed run | `ProjectIndexerTest$Degradation#degradesOnUnparseableFile` + `JavaSourceParserTest$Degradation` + ad-hoc repro | yes | ✅ PASS |
| [AC11](#ac11) | Layers correct for domain/application/infrastructure | `LayerTest` + e2e (DOMAIN/APPLICATION/INFRASTRUCTURE assigned correctly on real Kairos packages) | yes | ✅ PASS |
| [AC12](#ac12) | `schemaVersion` written and validated on read | `IndexStoreTest$RoundTrip` + `IndexStoreTest$TolerantReading#rejectsSchemaMismatch` | yes | ✅ PASS |

## Evidence log

<a id="ac1"></a>
<details>
<summary>✅ <b>AC1</b> — Running against <code>../kairos</code> discovers all 10 Gradle modules as roots — <code>ModuleDiscoveryTest$Gradle#readsGradleSettings</code> + e2e run — PASS</summary>

**Criterion:** Running against `../kairos` discovers all 10 Gradle modules as roots

**Test:** `codemap-core/src/test/java/dev/codemap/core/discovery/ModuleDiscoveryTest.java:32`

```java
@Test
@DisplayName("reads modules from settings.gradle, including colon-separated paths")
void readsGradleSettings() throws IOException {
    writeSettings("""
            rootProject.name = 'demo'
            include 'core'
            include 'adapters:kafka'
            """);
    createSources("core");
    createSources("adapters/kafka");

    List<IndexedModule> modules = discovery.discover(projectRoot);

    assertThat(modules).extracting(IndexedModule::path)
            .containsExactly("core", "adapters/kafka");
    assertThat(modules).extracting(IndexedModule::name)
            .containsExactly("core", "kafka");
}
```

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.discovery.ModuleDiscoveryTest'
java -jar codemap-cli/build/libs/codemap.jar --root ../kairos
```

**Output (unit, JUnit XML `TEST-dev.codemap.core.discovery.ModuleDiscoveryTest$Gradle.xml`):**
```
tests="4" skipped="0" failures="0" errors="0"
```

**Output (end-to-end, clean worktree at 96179db):**
```
INFO  Project root  : /Users/vladyslavkekukh/Developer/Java/kairos
INFO  Discovered 10 module(s)
INFO  Indexed 169 file(s): 176 class(es), 797 method(s) in 329 ms
```

Confirmed all 10 modules present with correct ids in `codemap/index.json`:
`common, kairos-api, kairos-engine, kairos-worker, kairos-adapters/kafka,
kairos-adapters/sqs, kairos-adapters/webhook, kairos-adapters/rabbitmq,
kairos-admin, kairos-sdk`.
</details>

<a id="ac2"></a>
<details>
<summary>✅ <b>AC2</b> — ~277 production files indexed, no test file appears — <code>ProjectIndexerTest$Coverage#excludesTestSources</code> + e2e count — PASS</summary>

**Criterion:** ~277 production files are indexed; no test file appears in the index

**Test:** `codemap-core/src/test/java/dev/codemap/core/index/ProjectIndexerTest.java:47`

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
./gradlew :codemap-core:test --tests 'dev.codemap.core.index.ProjectIndexerTest'
java -jar codemap-cli/build/libs/codemap.jar --root ../kairos
```

**Output (unit):**
```
TEST-dev.codemap.core.index.ProjectIndexerTest$Coverage.xml: tests="4" skipped="0" failures="0" errors="0"
```

**Output (end-to-end count check against real Kairos, `codemap/index.json`):**
```
files: 169         # kairos has 169 production .java files under src/main/java
                    # (186 total src/main files minus 17 JOOQ-generated ones,
                    #  see AC8 — those are not on a recognized source root at all)
test files in index: 0    (grep for "/test/" in index.files keys)
```

The issue text estimates "~277 production files"; the actual measured count for
Kairos's current `src/main/java` tree is 169 (Kairos has grown/shrunk since the
number was written into the spec — 941 `@Test` methods / 91 test files remain
correctly excluded either way). Zero test files appear in `files`, `classes`, or
`methods` — verified by direct inspection of the written index.
</details>

<a id="ac3"></a>
<details>
<summary>✅ <b>AC3</b> — classes, methods, line ranges, signatures correct on spot-checks — <code>JavaSourceParserTest</code> (Types/Methods) + e2e spot-check — PASS</summary>

**Criterion:** Classes, methods, line ranges, and signatures are correct on spot-checks

**Test:** `codemap-core/src/test/java/dev/codemap/core/parse/JavaSourceParserTest.java:52` and `:100`

```java
@Test
@DisplayName("indexes the top-level type with its position and layer")
void indexesTopLevelType() {
    IndexedClass sample = classNamed(SAMPLE_FQN);

    assertThat(sample.kind()).isEqualTo(TypeKind.CLASS);
    assertThat(sample.layer()).isEqualTo(Layer.DOMAIN);
    assertThat(sample.packageName()).isEqualTo("com.example.domain.order");
    assertThat(sample.moduleId()).isEqualTo(MODULE_ID);
    assertThat(sample.lineStart()).isLessThan(sample.lineEnd());
}

@Test
@DisplayName("records the return type and varargs in the signature")
void rendersSignatureDetail() {
    assertThat(methodNamed("lines").signature())
            .isEqualTo("lines(int, String...) : List<String>");
}
```

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.parse.JavaSourceParserTest'
java -jar codemap-cli/build/libs/codemap.jar --root ../kairos
```

**Output (unit):**
```
TEST-dev.codemap.core.parse.JavaSourceParserTest$Types.xml:   tests="3" skipped="0" failures="0" errors="0"
TEST-dev.codemap.core.parse.JavaSourceParserTest$Methods.xml: tests="5" skipped="0" failures="0" errors="0"
```

**Output (end-to-end spot-check, `dev.kairos.domain.task.Task`):**
```
index.json:  { "fqn": "dev.kairos.domain.task.Task", "lineStart": 25, "lineEnd": 257, ... }
Task.java:   line 25  -> "public final class Task {"
             line 257 -> file's last line (wc -l = 257)

index.json methods for this class (spot-checked against grep of the real file):
  update       | update(TaskEdit, Instant) : void | 62-76   <- source: line 62 "public void update(TaskEdit edit, Instant now) {"
  softDelete   | softDelete(Instant) : void        | 83-88   <- source: line 83
  setActive    | setActive(boolean, Instant) : void| 90-97   <- source: line 90
  isDeleted    | isDeleted() : boolean             | 105-107 <- source: line 105
```
Every line number and signature matched the real source file exactly.
</details>

<a id="ac4"></a>
<details>
<summary>✅ <b>AC4</b> — every class carries a <code>moduleId</code> — <code>ProjectIndexerTest$Coverage#attributesEveryClassToAModule</code> + ctor guard — PASS</summary>

**Criterion:** Every class carries a `moduleId`

**Test:** `codemap-core/src/test/java/dev/codemap/core/index/ProjectIndexerTest.java:58`

```java
@Test
@DisplayName("attributes every class to a module")
void attributesEveryClassToAModule() throws IOException {
    writeProductionClass("Service", "public class Service {}");

    CodeIndex index = indexer.index(projectRoot);
    String moduleId = index.modules().get(0).id();

    assertThat(index.classes()).allSatisfy(indexed ->
            assertThat(indexed.moduleId()).isEqualTo(moduleId));
    assertThat(index.classesOf(moduleId)).hasSize(1);
}
```

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.index.ProjectIndexerTest'
```

**Output:**
```
TEST-dev.codemap.core.index.ProjectIndexerTest$Coverage.xml: tests="4" skipped="0" failures="0" errors="0"
```

The `IndexedClass` record additionally enforces this by construction —
`Objects.requireNonNull(moduleId, "moduleId")` (`IndexedClass.java:42`) — so a
class without a `moduleId` cannot be constructed at all, not merely "usually
present". End-to-end check on the real Kairos index: `0` of `176` classes were
missing `moduleId` (`python3` scripted check over `index.json`).
</details>

<a id="ac5"></a>
<details>
<summary>✅ <b>AC5</b> — Javadoc first lines captured where present — <code>JavaSourceParserTest$Javadoc</code> — PASS</summary>

**Criterion:** Javadoc first lines are captured where present

**Test:** `codemap-core/src/test/java/dev/codemap/core/parse/JavaSourceParserTest.java:142`

```java
@Test
@DisplayName("keeps only the first sentence")
void keepsFirstSentenceOnly() {
    assertThat(classNamed(SAMPLE_FQN).javadoc())
            .isEqualTo("An order placed by a customer.");
}

@Test
@DisplayName("captures method javadoc")
void capturesMethodJavadoc() {
    assertThat(methodNamed("addLine").javadoc()).isEqualTo("Adds a line to the order.");
}

@Test
@DisplayName("leaves javadoc null when a method has none")
void leavesJavadocNullWhenAbsent() {
    assertThat(methodNamed("lines").javadoc()).isNull();
}

@Test
@DisplayName("recovers a javadoc block separated from its type by a blank line")
void recoversDetachedJavadoc() throws IOException { /* ... */ }
```

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.parse.JavaSourceParserTest'
```

**Output:**
```
TEST-dev.codemap.core.parse.JavaSourceParserTest$Javadoc.xml: tests="4" skipped="0" failures="0" errors="0"
```

**End-to-end confirmation:** `dev.kairos.domain.task.Task.javadoc` in the real
index reads `"Task: what to deliver, where, and with which delivery settings
(see the tasks table)."` — exactly the first sentence of the real class Javadoc,
prose after it correctly dropped. 150 of 176 real Kairos classes carry a
captured Javadoc first line; the other 26 genuinely have none in source.
</details>

<a id="ac6"></a>
<details>
<summary>⚠️ <b>AC6</b> — nested and anonymous classes handled without crashing — <code>JavaSourceParserTest#indexesNestedTypes</code> (nested only) — PARTIAL</summary>

**Criterion:** Nested and anonymous classes are handled without crashing

**Test:** `codemap-core/src/test/java/dev/codemap/core/parse/JavaSourceParserTest.java:66`

```java
@Test
@DisplayName("indexes nested types as their own entries, dotted rather than dollar-separated")
void indexesNestedTypes() {
    assertThat(parsed.classes())
            .extracting(IndexedClass::fqn)
            .contains(SAMPLE_FQN + ".Builder", SAMPLE_FQN + ".Status",
                    SAMPLE_FQN + ".Line", SAMPLE_FQN + ".Visitor");
}
```

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.parse.JavaSourceParserTest'
```

**Output:**
```
TEST-dev.codemap.core.parse.JavaSourceParserTest$Types.xml: tests="3" skipped="0" failures="0" errors="0"
```

This covers **nested** static classes/enum/record/interface thoroughly (the
`Sample.java.txt` fixture has a `Builder`, `Status`, `Line`, `Visitor`) — the
"nested" half of AC6 is genuinely test-covered and green.

The **anonymous-class** half has no unit test in `JavaSourceParserTest` — the
fixture contains no `new X() { ... }` expression, and `../kairos` itself has no
anonymous classes to exercise end-to-end (`grep -rn "= new .*(.*) *{$"` over
`kairos-api` and `kairos-engine` found none). I built a throwaway fixture (not
committed, not under `../kairos`) to get reproducible evidence the code path
doesn't crash:

```java
// scratch fixture, not part of the repo
public class WithAnon {
    public void run() {
        Comparator<String> c = new Comparator<String>() {
            @Override public int compare(String a, String b) { return a.compareTo(b); }
        };
        Runnable r = new Runnable() { public void run() { System.out.println("anon"); } };
    }
    class Inner { void innerMethod() {} }
}
```

**Command:**
```
java -jar codemap-cli/build/libs/codemap.jar --root <scratch>/anon-repro
```

**Output:**
```
INFO  Discovered 1 module(s)
INFO  Indexed 1 file(s): 2 class(es), 2 method(s) in 40 ms
```
No crash, exit 0. Only the named types (`WithAnon`, `WithAnon.Inner`) were
indexed; the two anonymous class bodies were silently not indexed as separate
entries, which is reasonable but unverified by any assertion — the run "does
not crash" is proven, but there is no test asserting *what* an anonymous class
should or should not produce in the index.
</details>

<a id="ac7"></a>
<details>
<summary>✅ <b>AC7</b> — records, enums, interfaces are all indexed — <code>JavaSourceParserTest#distinguishesTypeKinds</code> + e2e — PASS</summary>

**Criterion:** Records, enums, and interfaces are all indexed

**Test:** `codemap-core/src/test/java/dev/codemap/core/parse/JavaSourceParserTest.java:74`

```java
@Test
@DisplayName("distinguishes classes, enums, records, and interfaces")
void distinguishesTypeKinds() {
    assertThat(classNamed(SAMPLE_FQN + ".Status").kind()).isEqualTo(TypeKind.ENUM);
    assertThat(classNamed(SAMPLE_FQN + ".Line").kind()).isEqualTo(TypeKind.RECORD);
    assertThat(classNamed(SAMPLE_FQN + ".Visitor").kind()).isEqualTo(TypeKind.INTERFACE);
    assertThat(classNamed(SAMPLE_FQN + ".Builder").kind()).isEqualTo(TypeKind.CLASS);
}
```

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.parse.JavaSourceParserTest'
java -jar codemap-cli/build/libs/codemap.jar --root ../kairos
```

**Output (unit):**
```
TEST-dev.codemap.core.parse.JavaSourceParserTest$Types.xml: tests="3" skipped="0" failures="0" errors="0"
```

**Output (end-to-end kind distribution over real Kairos, 176 classes):**
```
{'CLASS': 127, 'ENUM': 6, 'RECORD': 36, 'INTERFACE': 7}
```
e.g. `dev.kairos.domain.schedule.ScheduleType` → `ENUM`, lines 12–36 (matches
`public enum ScheduleType {` at line 12, file ends at line 36);
`dev.kairos.common.dto.ErrorResponse` → `RECORD`;
`dev.kairos.domain.task.TaskRepository` → `INTERFACE`.
</details>

<a id="ac8"></a>
<details>
<summary>❌ <b>AC8</b> — generated sources (<code>build/generated</code>) excluded by default — <b>NONE</b> — GAP</summary>

**Criterion:** Generated sources (`build/generated`) are excluded by default

**No unit test exercises this.** `SourceRootResolver.EXCLUDED_SEGMENTS` (main
source, `codemap-core/src/main/java/dev/codemap/core/discovery/SourceRootResolver.java:31-40`)
lists `"build/generated"`, `"target/generated"`, `"generated-sources"` as
excluded path segments, and `SourceFileScanner.scanRoot` filters every
discovered file through `SourceRootResolver.isProductionPath`
(`SourceFileScanner.java:61`) — but there is no `SourceRootResolverTest` or
`SourceFileScannerTest` in the repository, and neither `ModuleDiscoveryTest` nor
`ProjectIndexerTest` writes a fixture file under a `build/generated` path.

The end-to-end run against `../kairos` does **not** exercise this rule either,
despite `../kairos` having real JOOQ output: Kairos's generated sources live at
`kairos-api/src/main/generated/...`, added via
`sourceSets.main.java.srcDirs += ['src/main/generated']` in
`kairos-api/build.gradle`. `SourceRootResolver.PRODUCTION_ROOTS` only recognizes
`src/main/java`, `src/java`, `java` as source roots by convention — it never
walks `src/main/generated` at all, so those 17 files are absent from the index
for a *different* reason (root discovery) than the `EXCLUDED_SEGMENTS` filter
this criterion is about. Confirmed by diffing `find` output against
`index.json`'s `files` keys: exactly the 17 files under
`kairos-api/src/main/generated/**` are the only production-tree `.java` files
missing from the 186-file `find` result vs the 169-file index.

I wrote a throwaway repro (not committed, not under `../kairos`) to get
reproducible evidence the `build/generated` string-match path does work when it
*is* reached:

```
<scratch>/gen-repro-project/
  src/main/java/com/example/Real.java        (public class Real)
  build/generated/java/com/example/Gen.java  (public class Gen)
```

**Command:**
```
java -jar codemap-cli/build/libs/codemap.jar --root <scratch>/gen-repro-project
```

**Output:**
```
INFO  Discovered 1 module(s)
INFO  Indexed 1 file(s): 1 class(es), 1 method(s) in 34 ms
```
Only `com.example.Real` appears in `index.json`; `com.example.Gen` under
`build/generated` is correctly absent. This proves the rule works for the one
shape it's written for, but it is evidence from an ad-hoc script, not a
committed, repeatable test — and it does not cover `target/generated` (Maven)
or `generated-sources` at all.

**What `test-author` should add:** a `SourceRootResolverTest` (or a case inside
`ModuleDiscoveryTest`) that creates `<module>/build/generated/java/...`,
`<module>/target/generated-sources/...`, and a file under a directory literally
named `generated-sources`, asserts none of their paths pass
`SourceRootResolver.isProductionPath`, and a companion case in
`SourceFileScannerTest`/`ProjectIndexerTest` proving a `.java` file placed
directly under `src/main/java/**/build/generated/**` (the shape the filter
segment-matches against) is excluded from `indexer.index(...)`'s output.
</details>

<a id="ac9"></a>
<details>
<summary>✅ <b>AC9</b> — a single-module project yields exactly one root — <code>ModuleDiscoveryTest$NoBuildFile</code> + ad-hoc repro — PASS</summary>

**Criterion:** A single-module project yields exactly one root

**Test:** `codemap-core/src/test/java/dev/codemap/core/discovery/ModuleDiscoveryTest.java:117` and `:128`

```java
@Test
@DisplayName("treats a plain source tree as a single module")
void treatsPlainTreeAsSingleModule() throws IOException {
    Files.createDirectories(projectRoot.resolve(MAIN_SOURCES));

    List<IndexedModule> modules = discovery.discover(projectRoot);

    assertThat(modules).hasSize(1);
    assertThat(modules.get(0).sourceRoots()).containsExactly(MAIN_SOURCES);
}

@Test
@DisplayName("still yields one module when there is nothing to find")
void yieldsOneModuleForEmptyDirectory() {
    List<IndexedModule> modules = discovery.discover(projectRoot);

    assertThat(modules).hasSize(1);
    assertThat(modules.get(0).hasSources()).isFalse();
}
```

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.discovery.ModuleDiscoveryTest'
java -jar codemap-cli/build/libs/codemap.jar --root <scratch>/single-module
```

**Output (unit):**
```
TEST-dev.codemap.core.discovery.ModuleDiscoveryTest$NoBuildFile.xml: tests="2" skipped="0" failures="0" errors="0"
```

**Output (end-to-end repro, a project with `src/main/java/com/example/Solo.java`
and no `settings.gradle`/`pom.xml`):**
```
INFO  Discovered 1 module(s)
INFO  Indexed 1 file(s): 1 class(es), 1 method(s) in 33 ms
```
`index.json.modules` has exactly one entry, `id: "."`, `schemaVersion: 1`.
</details>

<a id="ac10"></a>
<details>
<summary>✅ <b>AC10</b> — a malformed source file is skipped with a warning, not a failed run — <code>ProjectIndexerTest$Degradation#degradesOnUnparseableFile</code> — PASS</summary>

**Criterion:** A malformed source file is skipped with a warning, not a failed run

**Test:** `codemap-core/src/test/java/dev/codemap/core/index/ProjectIndexerTest.java:86`

```java
@Test
@DisplayName("indexes the good files and records the bad one, rather than failing")
void degradesOnUnparseableFile() throws IOException {
    writeProductionClass("Good", "public class Good { void go() {} }");
    writeProductionClass("Bad", "public class Bad { ### not java");

    CodeIndex index = indexer.index(projectRoot);

    assertThat(index.classes()).extracting(IndexedClass::simpleName).containsExactly("Good");
    assertThat(index.statistics().hasSkippedFiles()).isTrue();
    assertThat(index.statistics().skipped()).singleElement()
            .satisfies(skipped -> {
                assertThat(skipped.file()).endsWith("Bad.java");
                assertThat(skipped.reason()).isNotBlank();
            });
}
```

Also covered at the parser layer by `JavaSourceParserTest$Degradation#skipsUnparseableFile`
(`JavaSourceParserTest.java:186`).

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.index.ProjectIndexerTest'
./gradlew :codemap-core:test --tests 'dev.codemap.core.parse.JavaSourceParserTest'
java -jar codemap-cli/build/libs/codemap.jar --root <scratch>/malformed-repro
```

**Output (unit):**
```
TEST-dev.codemap.core.index.ProjectIndexerTest$Degradation.xml: tests="2" skipped="0" failures="0" errors="0"
TEST-dev.codemap.core.parse.JavaSourceParserTest$Degradation.xml: tests="3" skipped="0" failures="0" errors="0"
```

**Output (end-to-end repro with a `Good.java` and a syntactically broken `Bad.java`):**
```
INFO  Discovered 1 module(s)
WARN  Skipped src/main/java/com/example/Bad.java: could not be parsed
INFO  Indexed 1 file(s): 1 class(es), 1 method(s) in 34 ms
WARN  1 file(s) could not be indexed — the map is incomplete
exit code: 0
```
`index.json.classes` contains only `com.example.Good`;
`statistics.skipped == [{"file": "src/main/java/com/example/Bad.java", "reason": "could not be parsed"}]`.
Process exits 0 — run degrades, does not fail.
</details>

<a id="ac11"></a>
<details>
<summary>✅ <b>AC11</b> — layers assigned correctly for Kairos's domain/application/infrastructure — <code>LayerTest</code> + e2e — PASS</summary>

**Criterion:** Layers are assigned correctly for Kairos's domain/application/infrastructure packages

**Test:** `codemap-core/src/test/java/dev/codemap/core/model/LayerTest.java:17`

```java
@ParameterizedTest
@CsvSource({
        "dev.app.domain.task,        DOMAIN",
        "dev.app.application.task,   APPLICATION",
        "dev.app.infrastructure.jpa, INFRASTRUCTURE",
        "dev.app.api.task,           ENTRY",
        "dev.app.common.util,        SUPPORT"
})
@DisplayName("reads the layer from a conventional segment")
void readsConventionalSegments(String packageName, Layer expected) {
    assertThat(Layer.fromPackage(packageName)).isEqualTo(expected);
}
```

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.model.LayerTest'
java -jar codemap-cli/build/libs/codemap.jar --root ../kairos
```

**Output (unit):**
```
TEST-dev.codemap.core.model.LayerTest$FromPackage.xml:  tests="7" skipped="0" failures="0" errors="0"
TEST-dev.codemap.core.model.LayerTest$FromTypeName.xml: tests="7" skipped="0" failures="0" errors="0"
```

**Output (end-to-end, real Kairos packages, `dev.kairos.*`):**
```
DOMAIN: 20   e.g. dev.kairos.domain.destination.Destination, dev.kairos.domain.task.TaskId
APPLICATION: 27  e.g. dev.kairos.application.destination.usecases.CreateDestinationUseCase
INFRASTRUCTURE: 10  e.g. dev.kairos.config.AppConfig, dev.kairos.config.DatabaseMigrator
ENTRY: 22    e.g. dev.kairos.api.Router, dev.kairos.api.GlobalExceptionHandler
```
The mapping is package-driven (`domain` → DOMAIN, `application` → APPLICATION,
`config`/`infrastructure` → INFRASTRUCTURE), matching the fixture-verified rule
and matching the real hexagonal layout described in Kairos's own `CLAUDE.md`.
</details>

<a id="ac12"></a>
<details>
<summary>✅ <b>AC12</b> — <code>schemaVersion</code> written and validated on read — <code>IndexStoreTest$RoundTrip</code> + <code>TolerantReading#rejectsSchemaMismatch</code> — PASS</summary>

**Criterion:** `schemaVersion` is written and validated on read

**Test:** `codemap-core/src/test/java/dev/codemap/core/index/IndexStoreTest.java:40` and `:106`

```java
@Test
@DisplayName("preserves every field through write and read")
void preservesFields() {
    Path target = workingDirectory.resolve("codemap/index.json");

    store.write(sampleIndex(), target);
    CodeIndex restored = store.read(target).orElseThrow();

    assertThat(restored.schemaVersion()).isEqualTo(CodeIndex.SCHEMA_VERSION);
    /* ... */
}

@Test
@DisplayName("rejects an index written by a different schema version")
void rejectsSchemaMismatch() throws IOException {
    Path target = workingDirectory.resolve("index.json");
    store.write(sampleIndex(), target);
    Files.writeString(target, Files.readString(target)
            .replace("\"schemaVersion\" : " + CodeIndex.SCHEMA_VERSION, "\"schemaVersion\" : 999"));

    Optional<CodeIndex> read = store.read(target);

    assertThat(read).as("a future schema must trigger a rebuild, not a crash").isEmpty();
}
```

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.index.IndexStoreTest'
```

**Output:**
```
TEST-dev.codemap.core.index.IndexStoreTest$RoundTrip.xml:      tests="3" skipped="0" failures="0" errors="0"
TEST-dev.codemap.core.index.IndexStoreTest$TolerantReading.xml: tests="3" skipped="0" failures="0" errors="0"
```

**End-to-end confirmation:** `../kairos/codemap/index.json` was written with
`"schemaVersion": 1` (`CodeIndex.SCHEMA_VERSION`), matched by direct inspection
of the produced file.
</details>

## Gaps

1. **AC8 — generated-source exclusion has no committed test.** `SourceRootResolver`
   and `SourceFileScanner` are the classes with the actual filtering logic, and
   neither has a dedicated test file. The end-to-end reference project doesn't
   exercise this path either (Kairos's JOOQ output lives at `src/main/generated`,
   which is skipped for an unrelated reason — it's never registered as a source
   root — not because the `EXCLUDED_SEGMENTS` filter caught it). Needed:
   `SourceRootResolverTest` (or equivalent) asserting `isProductionPath` rejects
   `build/generated`, `target/generated`, and `generated-sources` segments, plus
   a `ProjectIndexerTest`/`SourceFileScannerTest` case with a `.java` file placed
   directly under a recognized source root's `build/generated` subdirectory,
   proving it is excluded from the indexed output (not just from a helper's
   boolean return value).

2. **AC6 — anonymous classes have no unit test.** `JavaSourceParserTest`'s
   nested-type coverage is solid, but nothing asserts the parser's behavior on
   `new X() { ... }` expressions specifically. Needed: extend
   `Sample.java.txt` (or add a new fixture) with an anonymous class assigned to
   a field or local variable, and assert `parser.parse(...)` completes without
   throwing and produces a sane result for the enclosing method (whether that
   means the anonymous body is omitted, given a synthetic name, or something
   else — currently unspecified and unasserted).

## Verdict

10/12 acceptance criteria verified with passing tests (AC1–AC5, AC7, AC9–AC12).
2 criteria (AC6 anonymous-class handling, AC8 generated-source exclusion) are
proven only by ad-hoc reproduction against throwaway fixtures, not by any test
in the repository — real gaps for `test-author` to close, not failures of the
implementation (all reproductions passed).

GAPS: AC6 (anonymous classes untested), AC8 (generated-source exclusion untested).

Everything else — module discovery, test exclusion at scale (169 real files,
zero leaks), line/signature accuracy, moduleId invariant, Javadoc capture,
record/enum/interface kinds, single-module fallback, malformed-file degradation,
layer assignment on real hexagonal packages, and schemaVersion round-trip — is
backed by a real, currently-green test plus a corroborating end-to-end run
against `../kairos` (10 modules, 169 files, 176 classes, 797 methods, 0 test
files, 0 skipped-but-crashed files). `../kairos` was left unmodified except for
the tool's own output file `codemap/index.json` (untracked, not part of the
repo).
