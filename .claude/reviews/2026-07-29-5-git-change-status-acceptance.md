# Acceptance evidence — feature/5-git-change-status — 2026-07-29

- Issue: [#5 — Compute git change status per method](../../../issues/5) (`gh issue view 5`)
- Spec: `doc/specification.md` §5 "Change highlighting", §5.1 "What 'changed' is measured against"
- Test command(s) run:
  - `./gradlew :codemap-core:test --tests 'dev.codemap.core.diff.*' --tests 'dev.codemap.core.model.ChangeStatusTest' --tests 'dev.codemap.core.CodemapOptionsTest' -i`
  - `./gradlew :codemap-core:test :codemap-cli:test`
  - `./gradlew build`
  - `java -jar codemap-cli/build/libs/codemap.jar --root ../kairos`
  - Three hermetic `git init` scratch repos driven through the real `codemap.jar` for the off-by-one, one-hop, and rename checks (see Evidence log)
- Result: 9 criteria — 9 covered+passing (unit-tested), 0 gaps, 0 not-run. All 260 project tests pass (0 failures, 0 errors), verified from JUnit XML (`codemap-core` + `codemap-cli`).

**Stale-wording note:** the issue's "Sources" line says `git diff --unified=0 <base>` for the working tree. §5.1 is more precise and is what's actually implemented: BRANCH mode (the default, no flag or `--base <branch>`) diffs from the **merge base** — three-dot semantics, `<merge-base>..HEAD` plus uncommitted work, computed via `git merge-base <base> HEAD` then a two-dot-style diff from that commit — specifically to exclude commits landed on the base branch after this branch forked. Only REVISION mode (`--since <commit>`) is a direct two-dot comparison against the named commit. This is confirmed in `GitChangeSource.java:11-23` and exercised by `GitChangeSourceTest#excludesCommitsLandedOnBaseAfterForking`. The issue text is a simplification, not a contradiction — no criterion's *behavior* conflicts with §5.1, but AC6's "both work" is proven against the §5.1 semantics (merge-base for BRANCH), not the issue's literal `<base>` wording.

## Coverage matrix

| AC | Criterion (short) | Evidence (test / gate) | Ran? | Result |
|----|-------------------|------------------------|------|--------|
| [AC1](#ac1) | Editing one method marks exactly that method `changed` | `GitChangeAnalyzerTest#marksEditedMethodChanged`, `MethodStatusAssignerTest$Changed`, + hermetic off-by-one repro | yes | ✅ PASS |
| [AC2](#ac2) | A new file's methods are all `added` | `MethodStatusAssignerTest$Added#everyMethodInANewFileIsAdded`, + hermetic `--since` repro | yes | ✅ PASS |
| [AC3](#ac3) | Deleting a method yields `removed` | `GitChangeAnalyzerTest#recordsRemovedMethod`, `MethodStatusAssignerTest$Removed`, + hermetic `--since` repro | yes | ✅ PASS |
| [AC4](#ac4) | Callers and callees of a changed method are `affected` | `GitChangeAnalyzerTest#marksCallerAffected`, `AffectedMethodResolverTest$OneHop`, + hermetic A→B→C repro | yes | ✅ PASS |
| [AC5](#ac5) | `affected` does not propagate past one hop | `AffectedMethodResolverTest$HopLimit#doesNotPropagatePastOneHop`, + hermetic A→B→C repro | yes | ✅ PASS |
| [AC6](#ac6) | `--base main` and `--since <commit>` both work | `GitChangeSourceTest$BranchMode`, `$RevisionMode`, `CodemapCommandTest$OptionWiring#carriesEveryFlag` | yes | ✅ PASS |
| [AC7](#ac7) | No commits / bad `--base` fails readably | `GitChangeSourceTest#failsForARepositoryWithNoCommits`, `#failsForAnUnresolvableBase`, `GitChangeAnalyzerTest$Degradation`, + hermetic CLI repro | yes | ✅ PASS |
| [AC8](#ac8) | Class status aggregates from its methods | `ClassStatusAggregatorTest` (6 cases), `GitChangeAnalyzerTest#aggregatesClassStatus` | yes | ✅ PASS |
| [AC9](#ac9) | Renamed files are handled (git rename detection) | `UnifiedDiffParserTest$FileIdentity#renamedFile`, `MethodStatusAssignerTest$Renames#rangesAreKeyedByTheNewPath`, + hermetic `git mv` repro | yes | ✅ PASS |

## Evidence log

<a id="ac1"></a>
<details>
<summary>✅ <b>AC1</b> — Editing one method marks exactly that method <code>changed</code> — <code>GitChangeAnalyzerTest#marksEditedMethodChanged</code> — PASS</summary>

**Criterion:** Editing one method marks exactly that method `changed`

**Test:** `codemap-core/src/test/java/dev/codemap/core/diff/GitChangeAnalyzerTest.java:53`

```java
@Test
@DisplayName("marks the edited method changed and leaves the rest unchanged")
void marksEditedMethodChanged() throws IOException, InterruptedException {
    initRepo();
    writeAndCommit(FILE, javaClass("void touched() {\n    }", "void untouched() {\n    }"), "init on main");
    checkoutBranch("feature");
    writeAndCommit(FILE, javaClass("void touched() {\n        int x = 1;\n    }", "void untouched() {\n    }"), "edit");

    CodeIndex index = indexWithMethods(method("touched", 2, 4), method("untouched", 5, 6));
    ChangeAnalysisResult result = analyzer.analyze(repoRoot, ComparisonMode.BRANCH, Optional.of("main"), Optional.empty(), index);

    assertThat(result.isResolved()).isTrue();
    IndexedMethod touched = methodNamed(result.index(), "touched");
    IndexedMethod untouched = methodNamed(result.index(), "untouched");
    assertThat(touched.status()).isEqualTo(ChangeStatus.CHANGED);
    assertThat(untouched.status()).isEqualTo(ChangeStatus.UNCHANGED);
}
```

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.diff.GitChangeAnalyzerTest'
```

**Output (from JUnit XML, `TEST-dev.codemap.core.diff.GitChangeAnalyzerTest$SuccessfulAnalysis.xml`):**
```
<testsuite name="successful analysis" tests="4" skipped="0" failures="0" errors="0" time="0.535">
  <testcase name="marks the edited method changed and leaves the rest unchanged" .../>
```
`BUILD SUCCESSFUL` — 0 failures, 0 errors.

**Independent judgement check — off-by-one at a method boundary (hermetic repo):** built a class with `neighbourAbove()` / `edited()` / `neighbourBelow()`, committed on `main`, then on `feature` added two lines *only inside* `edited()`. The resulting hunk sits immediately adjacent to `neighbourBelow()`:
```
@@ -10,0 +11,2 @@ class Service {
+        int extra = 2;
+        int extra2 = 3;
```
Ran the real jar (`codemap.jar --root . --base main`) against this repo; `index.json` methods:
```
neighbourAbove 5 7 UNCHANGED
edited         9 13 CHANGED
neighbourBelow 15 17 UNCHANGED
```
`edited` (lines 9-13, containing the new-file range 11-12) is CHANGED; `neighbourBelow` (15-17) is correctly UNCHANGED — not mis-attributed despite sitting right after the hunk. This confirms `UnifiedDiffParser` uses the new-file side for additions, per `UnifiedDiffParserTest$HunkHeaderMath` (`addChangedRange` uses group 3/4, the `+` side) and `addRemovedRange` uses group 1/2 (the `-` side) — `UnifiedDiffParser.java:141-155`.
</details>

<a id="ac2"></a>
<details>
<summary>✅ <b>AC2</b> — A new file's methods are all <code>added</code> — <code>MethodStatusAssignerTest$Added#everyMethodInANewFileIsAdded</code> — PASS</summary>

**Criterion:** A new file's methods are all `added`

**Test:** `codemap-core/src/test/java/dev/codemap/core/diff/MethodStatusAssignerTest.java:75`

```java
@Test
@DisplayName("every method in a brand new file is added")
void everyMethodInANewFileIsAdded() {
    IndexedMethod one = method("one", 3, 5);
    IndexedMethod two = method("two", 7, 9);
    FileDiff diff = new FileDiff(FILE, FILE, List.of(new LineRange(1, 9)), List.of(), true, false);

    MethodStatusAssignment assignment = assigner.assign(List.of(one, two), List.of(diff));

    assertThat(assignment.statusOf(one.id())).isEqualTo(ChangeStatus.ADDED);
    assertThat(assignment.statusOf(two.id())).isEqualTo(ChangeStatus.ADDED);
}
```

Also relevant: `MethodStatusAssignerTest$Added#partiallyOverlappingMethodIsChangedNotAdded` (line 88) pins the spec §5 subtlety that a fully-rewritten method in an *existing* file is `changed`, not `added` — `added` requires the whole file to be new.

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.diff.MethodStatusAssignerTest'
```

**Output (JUnit XML, `MethodStatusAssignerTest$Added.xml`):** `tests="3" failures="0" errors="0"`.

**End-to-end confirmation (hermetic repo, `--since HEAD~1`):** added `Brand.java` with `fresh()` alongside removing a method from an existing file. `index.json`:
```
fresh src/main/java/com/example/Brand.java ADDED
keep  src/main/java/com/example/Existing.java UNCHANGED
```
CLI log: `Changes: 1 added, 1 removed`.
</details>

<a id="ac3"></a>
<details>
<summary>✅ <b>AC3</b> — Deleting a method yields <code>removed</code> — <code>GitChangeAnalyzerTest#recordsRemovedMethod</code> — PASS</summary>

**Criterion:** Deleting a method yields `removed`

**Test:** `codemap-core/src/test/java/dev/codemap/core/diff/GitChangeAnalyzerTest.java:110`

```java
@Test
@DisplayName("records a deleted method as a removed node with the old line range")
void recordsRemovedMethod() throws IOException, InterruptedException {
    initRepo();
    writeAndCommit(FILE, javaClass("void touched() {\n    }", "void deleted() {\n    }"), "init on main");
    checkoutBranch("feature");
    writeAndCommit(FILE, javaClass("void touched() {\n    }"), "delete a method");

    CodeIndex index = indexWithMethods(method("touched", 2, 3));
    ChangeAnalysisResult result = analyzer.analyze(repoRoot, ComparisonMode.BRANCH, Optional.of("main"), Optional.empty(), index);

    assertThat(result.index().removedMethods()).singleElement()
            .satisfies(removed -> assertThat(removed.file()).isEqualTo(FILE));
}
```

Complementary unit coverage: `MethodStatusAssignerTest$Removed#deletedFileYieldsRemovedPlaceholder` (whole-file deletion) and `$ModificationVersusDeletion` (lines 169-202), which pin that a rewritten line is *not* also reported as removed — `--unified=0` reports a modification as a delete+add pair at the same place, and the resolver correctly distinguishes a genuine deletion (fewer replacement lines than removed) from a rewrite.

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.diff.GitChangeAnalyzerTest' --tests 'dev.codemap.core.diff.MethodStatusAssignerTest'
```

**Output:** `GitChangeAnalyzerTest$SuccessfulAnalysis`: `tests="4" failures="0" errors="0"`. `MethodStatusAssignerTest$Removed`: `tests="1" failures="0" errors="0"`. `$ModificationVersusDeletion`: `tests="2" failures="0" errors="0"`.

**End-to-end confirmation:** same hermetic repo as AC2 — `Existing.java` lost `toRemove()`. `index.json`:
```
removed: [{'file': 'src/main/java/com/example/Existing.java', 'lineStart': 8, 'lineEnd': 11}]
```
Note per the issue's "Notes" and spec §5: removed methods have no body — recovered from the diff's old-file range alone, never by parsing the base revision. `RemovedMethod` (`codemap-core/src/main/java/dev/codemap/core/model/RemovedMethod.java`) carries only `file`/`lineStart`/`lineEnd`, consistent with that.
</details>

<a id="ac4"></a>
<details>
<summary>✅ <b>AC4</b> — Callers and callees of a changed method are <code>affected</code> — <code>AffectedMethodResolverTest$OneHop</code> — PASS</summary>

**Criterion:** Callers and callees of a changed method are `affected`

**Test:** `codemap-core/src/test/java/dev/codemap/core/diff/AffectedMethodResolverTest.java:39` and `:49`

```java
@Test
@DisplayName("marks the caller of a changed method affected")
void marksCallerAffected() {
    CallGraph graph = new CallGraph(List.of(edge(CALLER, CHANGED)));

    Set<String> affected = resolver.resolve(Map.of(CHANGED, ChangeStatus.CHANGED), graph);

    assertThat(affected).containsExactly(CALLER);
}

@Test
@DisplayName("marks the callee of a changed method affected")
void marksCalleeAffected() {
    CallGraph graph = new CallGraph(List.of(edge(CHANGED, CALLEE)));

    Set<String> affected = resolver.resolve(Map.of(CHANGED, ChangeStatus.CHANGED), graph);

    assertThat(affected).containsExactly(CALLEE);
}
```

Also `GitChangeAnalyzerTest#marksCallerAffected` (line 86) exercises the same behaviour wired through the full analyzer with a real call graph and hermetic git repo.

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.diff.AffectedMethodResolverTest' --tests 'dev.codemap.core.diff.GitChangeAnalyzerTest'
```

**Output:** `AffectedMethodResolverTest$OneHop`: `tests="3" failures="0" errors="0"`. `GitChangeAnalyzerTest$SuccessfulAnalysis`: `tests="4" failures="0" errors="0"`.

**Independent judgement check — one hop only, A→B→C chain (hermetic repo):** built `a()` calls `b()` calls `c()`, edited only `c()` on `feature`. Ran the real jar (`--base main`); `index.json`:
```
a 5 7   UNCHANGED
b 9 11  AFFECTED
c 13 16 CHANGED
```
CLI log: `Changes: 1 changed, 1 affected`. `b` (direct caller of `c`) is `AFFECTED`; `a` (two hops from `c`) is correctly `UNCHANGED` — see AC5 for the hop-limit assertion this also proves.
</details>

<a id="ac5"></a>
<details>
<summary>✅ <b>AC5</b> — <code>affected</code> does not propagate past one hop — <code>AffectedMethodResolverTest$HopLimit#doesNotPropagatePastOneHop</code> — PASS</summary>

**Criterion:** `affected` does not propagate past one hop

**Test:** `codemap-core/src/test/java/dev/codemap/core/diff/AffectedMethodResolverTest.java:74`

```java
@Test
@DisplayName("does not propagate past one hop in either direction")
void doesNotPropagatePastOneHop() {
    CallGraph graph = new CallGraph(List.of(
            edge(TWO_HOPS_AWAY, CALLER),
            edge(CALLER, CHANGED)));

    Set<String> affected = resolver.resolve(Map.of(CHANGED, ChangeStatus.CHANGED), graph);

    assertThat(affected).containsExactly(CALLER);
    assertThat(affected).doesNotContain(TWO_HOPS_AWAY);
}
```

Implementation: `AffectedMethodResolver.resolve()` (`codemap-core/src/main/java/dev/codemap/core/diff/AffectedMethodResolver.java:30-41`) iterates only the *direct* `outgoingFrom`/`incomingTo` edges of each already-statused method and stops — there is no recursive step to accidentally leave enabled, per the class's own Javadoc.

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.diff.AffectedMethodResolverTest'
```

**Output:** `AffectedMethodResolverTest$HopLimit`: `tests="1" failures="0" errors="0"`.

**Independent judgement check:** same A→B→C hermetic repo as AC4 — `a` (two hops from edited `c`) is `UNCHANGED`, not `AFFECTED`, confirming the unit-level guarantee holds through the real call graph built by the parser/resolver stage, not just a synthetic `CallGraph`.
</details>

<a id="ac6"></a>
<details>
<summary>✅ <b>AC6</b> — <code>--base main</code> and <code>--since &lt;commit&gt;</code> both work — <code>GitChangeSourceTest$BranchMode</code> / <code>$RevisionMode</code> — PASS</summary>

**Criterion:** `--base main` and `--since <commit>` both work

**Test:** `codemap-core/src/test/java/dev/codemap/core/diff/GitChangeSourceTest.java:36` (BRANCH, explicit `--base`) and `:146` (REVISION, `--since`); CLI wiring in `codemap-cli/src/test/java/dev/codemap/cli/CodemapCommandTest.java:123-147`.

```java
@Test
@DisplayName("diffs from the merge base with an explicit --base, not a two-dot comparison")
void usesMergeBaseWithExplicitBase() throws IOException, InterruptedException {
    initRepo();
    commit("A.java", "class A { void m() {} }", "init on main");
    checkoutBranch("feature");
    writeAndCommit("A.java", "class A { void m() { CHANGED; } }", "change on feature");

    GitDiffOutcome outcome = changeSource.resolve(repoRoot, ComparisonMode.BRANCH, Optional.of("main"), Optional.empty());

    assertThat(outcome.isResolved()).isTrue();
    assertThat(outcome.fileDiffs()).extracting(FileDiff::path).containsExactly("A.java");
}

@Test
@DisplayName("diffs directly against the named commit with a two-dot comparison")
void diffsAgainstNamedCommit() throws IOException, InterruptedException {
    initRepo();
    commit("A.java", "class A { void m() {} }", "first");
    writeAndCommit("A.java", "class A { void m() { CHANGED; } }", "second");
    writeAndCommit("B.java", "class B {}", "third");

    GitDiffOutcome outcome = changeSource.resolve(repoRoot, ComparisonMode.REVISION, Optional.empty(), Optional.of("HEAD~2"));

    assertThat(outcome.isResolved()).isTrue();
    assertThat(outcome.fileDiffs()).extracting(FileDiff::path).contains("A.java", "B.java");
}
```

`GitChangeSourceTest$BranchMode#excludesCommitsLandedOnBaseAfterForking` (line 50) is the criterion that actually proves the §5.1 merge-base semantics rather than a naive `<base>` diff — it commits to `main` *after* the feature branch forked and asserts that commit is excluded, which a two-dot `diff main` would have wrongly included.

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.diff.GitChangeSourceTest'
./gradlew :codemap-cli:test --tests 'dev.codemap.cli.CodemapCommandTest'
```

**Output:** `GitChangeSourceTest$BranchMode`: `tests="7" failures="0" errors="0"`. `$RevisionMode`: `tests="2" failures="0" errors="0"`. `CodemapCommandTest`: part of the 80-test `codemap-cli` suite, 0 failures (see full-suite run below).

**End-to-end confirmation:** `--base main` run (AC1/AC4 scratch repos) and `--since HEAD~1` run (AC2/AC3 scratch repo) both succeeded via the real jar, producing correct status.
</details>

<a id="ac7"></a>
<details>
<summary>✅ <b>AC7</b> — No commits / bad <code>--base</code> fails readably — <code>GitChangeSourceTest#failsForARepositoryWithNoCommits</code> / <code>#failsForAnUnresolvableBase</code> — PASS</summary>

**Criterion:** A repo with no commits, or a `--base` that does not exist, fails with a readable message

**Test:** `codemap-core/src/test/java/dev/codemap/core/diff/GitChangeSourceTest.java:83` and `:95`; also `GitChangeAnalyzerTest$Degradation` (lines 128-150) proves the whole pipeline degrades instead of crashing.

```java
@Test
@DisplayName("fails readably when the named base branch does not exist")
void failsForAnUnresolvableBase() throws IOException, InterruptedException {
    initRepo();
    commit("A.java", "class A {}", "init");

    GitDiffOutcome outcome = changeSource.resolve(repoRoot, ComparisonMode.BRANCH, Optional.of("does-not-exist"), Optional.empty());

    assertThat(outcome.isResolved()).isFalse();
    assertThat(outcome.failureReason()).isNotBlank();
}

@Test
@DisplayName("fails readably, not with an exception, for a repository with no commits")
void failsForARepositoryWithNoCommits() throws IOException, InterruptedException {
    initRepo();

    GitDiffOutcome outcome = changeSource.resolve(repoRoot, ComparisonMode.BRANCH, Optional.of("main"), Optional.empty());

    assertThat(outcome.isResolved()).isFalse();
    assertThat(outcome.failureReason()).isNotBlank();
}
```

Note: this is a **degrade-don't-fail** criterion (project invariant, GUIDELINES.md "Error handling"), not a hard CLI failure — `GitChangeAnalyzer.analyze()` returns `ChangeAnalysisResult.unresolved(index, reason)` and `CodemapRunner` logs a `WARN` and still writes the plain index (`CodemapRunner.java:73-82`, `DIFF_UNRESOLVED`). "Fails readably" is read as "reports a readable reason", matching the project's hard rule that a git problem must never crash the run — confirmed both at the unit level and via the real CLI below (`EXIT=0`, readable `WARN` line, no stack trace).

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.diff.GitChangeSourceTest' --tests 'dev.codemap.core.diff.GitChangeAnalyzerTest'
```

**Output:** `GitChangeSourceTest$BranchMode`: `tests="7" failures="0" errors="0"` (includes both readability tests). `GitChangeAnalyzerTest$Degradation`: `tests="2" failures="0" errors="0"`.

**End-to-end confirmation (hermetic repos, real jar):**

No-commits repo:
```
WARN  Change status was not computed: Could not resolve a merge base with 'main'. Pass --base <branch> to name one explicitly.. The map will show no git overlay.
WARN  Rendering is not implemented yet — the index was written, but there is no report to open.
EXIT=0
```

Bad `--base`:
```
WARN  Change status was not computed: Could not resolve a merge base with 'does-not-exist'. Pass --base <branch> to name one explicitly.. The map will show no git overlay.
EXIT=0
```
Both produce a specific, readable message and a clean exit rather than a stack trace or crash.
</details>

<a id="ac8"></a>
<details>
<summary>✅ <b>AC8</b> — Class status aggregates from its methods — <code>ClassStatusAggregatorTest</code> — PASS</summary>

**Criterion:** Class status aggregates from its methods

**Test:** `codemap-core/src/test/java/dev/codemap/core/diff/ClassStatusAggregatorTest.java` (whole file, 6 cases)

```java
@Test
@DisplayName("a class with a changed method is itself changed")
void changedMethodMakesClassChanged() {
    ChangeStatus status = aggregator.aggregate(List.of(ChangeStatus.UNCHANGED, ChangeStatus.CHANGED));

    assertThat(status).isEqualTo(ChangeStatus.CHANGED);
}

@Test
@DisplayName("changed outranks added and affected when a class has a mix")
void changedOutranksAddedAndAffected() {
    ChangeStatus status = aggregator.aggregate(List.of(ChangeStatus.ADDED, ChangeStatus.AFFECTED, ChangeStatus.CHANGED));

    assertThat(status).isEqualTo(ChangeStatus.CHANGED);
}
```

Plus `GitChangeAnalyzerTest#aggregatesClassStatus` (line 71) wires this through the full analyzer against a real hermetic repo.

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.diff.ClassStatusAggregatorTest' --tests 'dev.codemap.core.diff.GitChangeAnalyzerTest'
```

**Output:** `ClassStatusAggregatorTest`: `tests="6" failures="0" errors="0"`. `GitChangeAnalyzerTest$SuccessfulAnalysis`: `tests="4" failures="0" errors="0"`.

**End-to-end confirmation:** the AC9 rename scratch repo's `index.json` `classes[0]` shows `"status": "CHANGED"` derived from its one `CHANGED` method, confirming aggregation through the real pipeline, not only the isolated aggregator.
</details>

<a id="ac9"></a>
<details>
<summary>✅ <b>AC9</b> — Renamed files are handled (git rename detection) — <code>UnifiedDiffParserTest$FileIdentity#renamedFile</code> — PASS</summary>

**Criterion:** Renamed files are handled (git rename detection)

**Test:** `codemap-core/src/test/java/dev/codemap/core/diff/UnifiedDiffParserTest.java:164` and `codemap-core/src/test/java/dev/codemap/core/diff/MethodStatusAssignerTest.java:148`

```java
@Test
@DisplayName("a rename carries the old path forward so removed methods still resolve")
void renamedFile() {
    String diff = """
            diff --git a/Old.java b/New.java
            similarity index 92%
            rename from Old.java
            rename to New.java
            --- a/Old.java
            +++ b/New.java
            @@ -5,0 +6 @@ line5
            +line6
            """;

    List<FileDiff> diffs = parser.parse(diff);

    assertThat(diffs).singleElement().satisfies(fileDiff -> {
        assertThat(fileDiff.path()).isEqualTo("New.java");
        assertThat(fileDiff.oldPath()).isEqualTo("Old.java");
        assertThat(fileDiff.isRenamed()).isTrue();
        assertThat(fileDiff.changedRanges()).containsExactly(new LineRange(6, 6));
    });
}
```

```java
@Test
@DisplayName("a renamed file's changed ranges are keyed by the new path, where the method now lives")
void rangesAreKeyedByTheNewPath() {
    String newFile = "src/main/java/com/example/Renamed.java";
    IndexedMethod method = method("method", 10, 15);
    IndexedMethod renamedMethod = new IndexedMethod(
            "com.example.Renamed#method()", CLASS_ID, "method", "method()", newFile,
            10, 15, null, "void method() {}", false);
    FileDiff diff = new FileDiff(newFile, FILE, List.of(new LineRange(12, 12)), List.of(), false, false);

    MethodStatusAssignment assignment = assigner.assign(List.of(renamedMethod), List.of(diff));

    assertThat(assignment.statusOf(renamedMethod.id())).isEqualTo(ChangeStatus.CHANGED);
}
```

**Command:**
```
./gradlew :codemap-core:test --tests 'dev.codemap.core.diff.UnifiedDiffParserTest' --tests 'dev.codemap.core.diff.MethodStatusAssignerTest'
```

**Output:** `UnifiedDiffParserTest$FileIdentity`: `tests="3" failures="0" errors="0"`. `MethodStatusAssignerTest$Renames`: `tests="1" failures="0" errors="0"`.

**Independent judgement check (hermetic repo, real `git mv`):** committed `OldName.java` on `main`, then on `feature` ran `git mv OldName.java NewName.java`, edited the class name and one method body, and committed — a genuine git-detected rename (`similarity index 74%`), not a delete+add pair:
```
diff --git a/.../OldName.java b/.../NewName.java
similarity index 74%
rename from src/main/java/com/example/OldName.java
rename to src/main/java/com/example/NewName.java
@@ -3 +3 @@ package com.example;
-class OldName {
+class NewName {
@@ -6,0 +7 @@ class OldName {
+        int extra = 99;
```
Ran the real jar (`--base main`); `index.json`:
```
method NewName.java 5 8  CHANGED
other  NewName.java 10 12 UNCHANGED
class NewName ... status: CHANGED
```
Status is correctly attached to the *new* path (`NewName.java`), and the untouched `other()` method stays `UNCHANGED` — confirms rename mapping works end-to-end through git's real `-M` similarity detection, not just the parser's handling of a synthetic rename header.
</details>

## Full-suite run (regression check)

```
./gradlew :codemap-core:test :codemap-cli:test
BUILD SUCCESSFUL in 3s
13 actionable tasks: 4 executed, 9 up-to-date
```
JUnit XML totals across both modules: **260 tests, 0 failures, 0 errors** — matches the stated "260 tests pass".

```
./gradlew build
BUILD SUCCESSFUL in 322ms
21 actionable tasks: 1 from cache, 20 up-to-date
```

## Sanity run against `../kairos`

```
java -jar codemap-cli/build/libs/codemap.jar --root ../kairos

INFO  Project root  : /Users/vladyslavkekukh/Developer/Java/kairos
INFO  Comparison    : changes on this branch since it diverged from the default branch
INFO  Discovered 10 module(s)
INFO  Built call graph: 1681 edge(s) (1440 resolved, 241 unresolved)
WARN  Call graph is incomplete — 241 edge(s) could not be resolved
INFO  Detected 25 entry point(s)
INFO  Indexed 169 file(s): 176 class(es), 833 method(s) in 1258 ms
INFO  Changes: 56 changed, 14 added, 121 affected, 72 removed
WARN  Rendering is not implemented yet — the index was written, but there is no report to open.
```
Ran clean against a real, large multi-module project (auto-detected base branch, no `--base` flag needed) and produced a full change summary with all five statuses represented. The generated `../kairos/codemap/` output directory was deleted afterward; no source file in `../kairos` was modified (confirmed via `git status --short` before/after — the only new entry was the untracked `codemap/` directory).

## Gaps

None. Every acceptance criterion has at least one unit test that asserts the specific behavior (not a neighboring one), and every criterion was additionally exercised through the real CLI jar against a hermetic repo during this verification, so none of the 9 criteria rests on end-to-end evidence alone — all are backed by fast, deterministic unit tests in `codemap-core`.

One observation, not a gap: AC7's "fails with a readable message" is implemented as the project's degrade-don't-fail pattern (`WARN` log + exit 0 + unresolved diff, not a nonzero exit code or thrown exception). This matches GUIDELINES.md's "Error handling" section exactly, but a reader expecting "fails" to mean "nonzero exit" should note the actual behavior is a readable warning with a successful exit — confirmed intentional by `GitChangeAnalyzer`'s Javadoc ("Never fails the run: a git problem degrades ... per the project's degrade-don't-fail rule").

## Verdict

9/9 acceptance criteria verified with passing tests. 0 gaps.
DONE — all criteria covered by specific unit tests, all green (260/260, 0 failures, 0 errors), full build green, and the three independent-judgement checks (off-by-one at a method boundary, one-hop-only propagation on an A→B→C chain, and real `git mv` rename detection) all confirmed correct via the actual `codemap.jar` against hermetic repos. The issue's "Sources" line (`git diff --unified=0 <base>`) is a stale simplification of §5.1's actual merge-base (three-dot) semantics for BRANCH mode; the implementation and its tests follow §5.1, not the literal issue text, and no criterion's behavior conflicts with it.

---

## Addendum — independent re-verification

The agent above completed its checks but was cut off by a session limit before it
could report. Re-run by hand against fresh hermetic repositories; results agree.

**Off-by-one at a method boundary.** Three adjacent methods, only the middle
body edited:

```
first    lines 3-5   UNCHANGED
second   lines 6-8   CHANGED
third    lines 9-11  UNCHANGED
```

**One hop only.** Chain `a() → b() → c()`, editing `c()`: `c` CHANGED, `b`
AFFECTED, `a` UNCHANGED.

**Renames, both kinds.** A pure move (content identical) is reported by git as
`similarity index 100% / rename from`, and Codemap correctly says *No changes* —
the code moved, it did not change. A move *plus* an edit falls below git's
similarity threshold, so git itself emits a delete and an add; Codemap reports
`1 added, 1 removed`, faithfully reflecting git's own judgement rather than
inventing a rename.

## Defects found during verification, now fixed

1. **Phantom removals.** `--unified=0` reports a rewritten line as a deletion plus
   an addition at the same place, and the deleted side was being counted as a
   removed method — so every ordinary edit invented one, while that method was
   already reported as `changed`. On ../kairos this inflated removals from 72 to
   125. Fixed by comparing line counts rather than mere overlap: a rewrite gets
   back at least as many lines as it removed.
2. **The result was invisible.** Only the failure path was logged; a successful
   analysis printed nothing, though change status is why many runs happen at all.
3. **Doubled period** in the degradation warning (`--base <branch>.. The map`),
   because reasons that already ended in a period had another appended.
4. **`comparison` was computed and dropped.** Spec §6.4 documents a
   `comparison{mode,base,mergeBase}` field; the merge base was resolved and then
   discarded, so a report did not record how it was produced. Now persisted.
