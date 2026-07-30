# Codemap — Specification

> An interactive mindmap of a Java application, rooted in **entry points**, with
> call-chain navigation in both directions and git change highlighting layered on
> top.

This document is the source of truth for *why* Codemap is built the way it is.
Per-slice specs live in [`specs/`](specs/). User-facing instructions live in the
[README](../README.md).

---

## 1. Problem

Reading an unfamiliar Java service is slow. The tools available answer the wrong
question:

- **Package trees** show how files were filed away. A `service` package does not
  say which of its classes is reachable from a live request and which is dead.
- **IDE "find usages"** answers one hop at a time. Reconstructing "what actually
  happens when this endpoint is hit" means dozens of jumps, held in your head.
- **Diff views** show what changed, but not what the changed code is *part of*.

The question a developer actually asks on joining a codebase — or returning to
one after a month — is:

> What can this application do, and what happens when each of those things runs?

Codemap answers that by rooting the map in the places the outside world can
reach, and letting the reader walk the chain from there.

**Audience:** developers reading or reviewing a Java service — onboarding,
reviewing a PR, or reorienting before a change.

---

## 2. Scope

### In

- Static analysis of Java **production** source (JavaParser + symbol solver).
- Entry-point detection: REST (annotated and programmatic), scheduled jobs,
  message listeners, WebSocket endpoints, UI routes, `main()`.
- A project-internal call graph, navigable in both directions.
- Git-derived change status per class and method.
- A single self-contained `report.html`.
- Incremental re-indexing.
- Optional AI classification of entry points the rules could not resolve.

### Out

- **Test sources.** `src/test/java` and equivalents are not indexed at all — not
  parsed, not shown, not counted. See §3.6.
- **Languages other than Java.** Not Kotlin, not polyglot repos.
- **Runtime tracing.** Codemap never executes or instruments the analysed
  project. Everything is derived from source and git.
- **Editing.** The map is read-only; it is not an IDE.
- **Hosting.** Output is a local file. No server, no CI integration, no
  dashboard.
- **Build integration.** Codemap never compiles the analysed project and adds
  nothing to its build.
- **Metrics.** No complexity scores, coverage, or quality gates.

---

## 3. Core model

### 3.1 The graph is the structure; the diagram is a view

The underlying data is a **directed graph** of methods connected by calls. It is
not a tree: a method is reachable from several entry points, and cycles occur
through recursion and mutual calls.

The report presents this graph as a **UML class diagram** where the unit of the
diagram is the **class box**, not the method. Methods are rows inside a box;
classes are keyed by identity, so a shared collaborator is visibly shared and
never appears twice on the canvas. The diagram grows on expansion: each box
expander reveals its callees, and the tree-grows-on-expansion invariant is
preserved — it is what terminates cycles and keeps the map tractable.

**Uniqueness invariant:** A class is drawn at most once on the canvas, keyed by
`classId`. When expansion reaches a class already drawn, a link is drawn to the
existing box rather than creating a duplicate. This solves the core problem of
the old tree form: a shared collaborator (e.g., `ProcessContext`) reached from
multiple callers now visibly sits in one box, with connectors from each caller.

**Private rows are reveal-only:** The box initially shows constructors and public
methods. Private and package-private methods appear only when a visible method in
the same box is expanded and calls them, joined by a dashed link. Collapsing an
expander removes only the private rows that expander revealed; the box stays if
other expanded paths still reach it. This keeps boxes readable while allowing the
reader to walk implementations downwards on demand.

**Layered column layout:** Boxes are positioned in columns by call depth from the
entry point (depth 0 is the entry point's declaring class, depth 1 is its
callees, etc.). Within a column, boxes are ordered to minimise link crossings.
An expander always positions new boxes in free space; collapsing does not move
boxes already on the canvas. Links are drawn as orthogonal polylines with
deterministic routing that avoids crossing box rectangles and does not share
segments between parallel edges.

### 3.2 Node kinds

| Kind | Meaning |
|---|---|
| `MODULE` | Root: one build module of the analysed project |
| `ENTRY_POINT` | An externally reachable trigger |
| `CLASS` | A class, interface, enum, or record |
| `METHOD` | A method or constructor |
| `TYPE_REF` | A type appearing in a signature |

### 3.2.1 Modules are the top-level roots

A multi-module project is **not** flattened into one application root. Each build
module (Gradle subproject or Maven module) is its own root, and its entry points
hang beneath it:

```
kairos-api          ← module root
├── POST /api/v1/tasks
├── GET  /api/v1/tasks
└── main()
kairos-admin        ← module root
├── @Route /tasks
└── main()
```

This matches how such systems are actually deployed: in the reference project
each runnable module is a separate process and a separate container. Collapsing
them into one root would draw a picture of a monolith that does not exist.

Modules are discovered from `settings.gradle`, `pom.xml`, or by locating
`src/main/java` roots when neither is present. A single-module project yields one
root and the distinction costs nothing.

### 3.2.2 Cross-module connections

Modules are roots, but they are not isolated. When a call, type reference, or
port implementation crosses a module boundary, the map draws a **connector** —
visually distinct from an in-module edge, and collapsed by default so the tree
stays readable.

This is what makes the API surface between modules visible: shared contracts, a
port declared in one module and implemented in another, an SDK consumed by a
service. A **module-level overview** aggregates these into a
"which module depends on which" diagram, which is often the first thing a reader
wants and the last thing a package tree can show.

Connectors are `CROSS_MODULE` edges carrying the source and target module ids.

### 3.3 Edge kinds

| Edge | Rendering | Meaning |
|---|---|---|
| `CALL_INTERNAL` | Dashed `╌╌` | Call to a method of the same class |
| `CALL_EXTERNAL` | Solid `──→` | Call crossing into another class, same module |
| `CROSS_MODULE` | Heavy, collapsed | Call or reference crossing a module boundary; carries `fromModuleId`/`toModuleId` |
| `USES_TYPE` | Thin | A type used in a signature; does not continue a call chain |
| `IMPLEMENTS` | Hollow | Interface implemented by a class |

The internal/external distinction is deliberate. A call within a class is a
local detail and should not pull the reader's eye out of the current card; a call
that crosses a class boundary is an architectural fact and deserves a visible
arrow to a named collaborator.

**`USES_TYPE` does not continue a chain.** A type is not an invocation. Selecting
a type shows where it is used and what methods it declares, but chains do not
propagate *through* it. Conflating the two would imply calls that do not exist.

### 3.3.1 Third-party call filtering

Library calls are kept out of the graph through two mechanisms:

1. **Symbol resolution via dependencies.** A `DependencyClasspath` discovers the
   project's compiled dependencies from the local Gradle/Maven cache
   (no build step). These jars are fed to JavaParser's symbol solver alongside
   the project's own source roots. When a call to a library method resolves
   successfully, it is recognised as external to the indexed project and is
   dropped by the same filter that excludes JDK calls.

2. **Receiver-based degradation.** Symbol resolution failures are expected in real
   projects. An unresolvable call is kept as a degraded edge (`resolved: false`)
   **only when it has no receiver** — e.g., `validate()` rather than
   `grid.validate()` — because a no-receiver call targets the enclosing type or
   something it inherits (worth a reader following up). A call through an
   unidentifiable receiver — e.g., `logger.info()` when the logger's type is
   unknown — is almost always a library call and is dropped to avoid inflating the
   map with bare method names that can never join to indexed methods.

**Result on the reference project:** the kairos codebase (169 production files)
initially produced 3325 edges with 2049 unresolved; after applying these filters
this dropped to 1569 edges with 241 unresolved, losing no in-project calls.

### 3.4 Layers

Each class is assigned a layer from its package path, configurable per project:

`ENTRY` · `APPLICATION` · `DOMAIN` · `INFRASTRUCTURE` · `SUPPORT` · `UNKNOWN`

Layers drive the side panel badge and the layer filter. They also make
architectural violations visible: a `DOMAIN` node with a solid edge into
`INFRASTRUCTURE` is a dependency pointing the wrong way, and the map shows it
without any dedicated rule.

### 3.5 Module dependency aggregation

Module dependencies (§3.2.2) are exposed as `CallGraph.moduleDependencies()`,
computed in-memory by rolling up every `CROSS_MODULE` edge into a set of
module-to-module relationships. This derives the "which module depends on which"
overview.

**Module dependencies are not persisted as a separate array in `index.json`.** They
are fully reconstructible from the `CROSS_MODULE` edges, and a duplicated field
could drift; the edges are the single source of truth.

### 3.6 Test sources are excluded entirely

Test sources are **not indexed** — not parsed, not stored, not rendered, and not
filterable back in.

The map answers "what does this application do in production". Tests are not part
of that: they are not reachable from any entry point, they invert the call
direction (a test calls production code, so every method gains callers that never
run in production), and in a well-tested project they outnumber production code —
in the reference project, 941 `@Test` methods against 169 production files.

This also **dissolves the interface fan-out problem**. A port like
`TaskRepository` has a real implementation and an in-memory test double; indexing
both means every call through the port forks into a path that cannot execute in
production. Excluding test sources leaves the real implementation only, and the
chain stays true.

Concretely: skip `src/test/java`, `src/it/java`, `src/integrationTest/java`, and
any source root a build file marks as a test source set. Consequence to accept:
a class used *only* by tests appears as an orphan with no callers — correct, and
in fact useful, since that is what it is in production.

---

## 4. Entry-point detection

### 4.1 Deterministic first

Detection is rule-based by default. AI is a fallback, never the primary path —
rules are reproducible, free, and instant.

**Annotation rules:**

| Kind | Recognised by |
|---|---|
| `REST` | `@RestController`/`@Controller` + `@GetMapping`/`@PostMapping`/`@RequestMapping`; JAX-RS `@Path` + `@GET`/`@POST` |
| `JOB` | `@Scheduled`; Quartz `Job.execute` |
| `MESSAGE` | `@KafkaListener`, `@RabbitListener`, `@JmsListener` |
| `SOCKET` | `@ServerEndpoint`, `@MessageMapping` |
| `UI` | Vaadin `@Route` |
| `BOOTSTRAP` | `public static void main`; `CommandLineRunner.run` |

**Programmatic registration rules.** Annotations are not the only form, and in
some codebases not the dominant one. Codemap recognises route registration
expressions:

```java
app.post(TASKS, taskHandler::create);
```

This yields the HTTP method (`post`), the path (resolved by constant-folding
`TASKS` → `/api/v1/tasks`), and the target method (`TaskHandler.create`) — a
complete entry point with no annotation present.

Supported: Javalin, Spark. The matcher keys on a call whose receiver is a known
server type, whose first argument resolves to a string constant, and whose second
argument is a method reference or lambda.

> **Grounding note.** This rule is not hypothetical. The reference project
> (Kairos) registers every one of its REST routes this way, and uses annotations
> only in its Vaadin admin module. A detector that handled annotations alone
> would find `main()` and the UI, and miss the entire API.

### 4.1.1 Constant folding across files

Route paths are rarely literals. Both detection paths have to resolve them:

```java
app.post(TASKS, taskHandler::create);          // TASKS is a private static final field
@Route(value = DashboardRoutes.HOME)           // HOME lives in another class entirely
```

So folding follows a constant into the class that declares it, not just within
the current file. What cannot be resolved statically degrades to the expression
text rather than dropping the entry point — a route labelled `ExternalPaths.THINGS`
is less useful than `/things`, but far more useful than a missing root.

One case is worth naming: a landing page is declared `@Route("")`, and an empty
label would leave the map's most-looked-for node blank. It renders as `/ (root)`.

**Measured on the reference project:** 25 entry points — 19 REST, 4 UI,
2 BOOTSTRAP. All 19 REST routes come from programmatic registration; the project
uses annotations only for its Vaadin views. An annotation-only detector would
have found six of twenty-five.

### 4.2 Project rules (`codemap.yml`)

Projects with their own conventions extend detection declaratively:

```yaml
entryPoints:
  - kind: JOB
    label: "Engine claim loop"
    match:
      implements: dev.kairos.engine.ClaimLoop
      method: run

layers:
  DOMAIN: ["**.domain.**"]
  APPLICATION: ["**.application.**"]
```

Match predicates: `annotation`, `implements`, `extends`, `classNamePattern`,
`method`, `inPackage`. Predicates in one rule are ANDed.

### 4.3 The `--ai` fallback

Some entry points are conventions rather than syntax — a hand-rolled dispatcher,
a worker that is a root by meaning only. No static rule catches these without
being written for that specific project.

With `--ai`, the AI **does not search from scratch**. The pipeline is:

1. Rules and config run to completion.
2. Codemap computes **orphan roots**: public classes that nothing else in the
   project calls, and that no rule claimed. These are natural root candidates.
3. Only those, with their signatures and Javadoc, are sent for classification.
4. The agent returns a kind and label per candidate, or "not an entry point".

Constraints:

- Every entry point records `detectedBy: RULE | CONFIG | AI`, surfaced in the UI.
- AI results are **cached in the index**. A later run without `--ai` keeps them
  rather than losing them.
- AI never overrides a rule-detected entry point.
- `--ai` is **off by default**.

Rationale: the map must stay reproducible and auditable. A reader has to be able
to tell a fact derived from syntax from a guess made by a model.

#### Invocation: shell out to the local `claude` CLI

Codemap does **not** embed an AI SDK or manage API keys. It shells out to the
`claude` binary already installed on the developer's machine:

```
claude --print --output-format json  <  <candidates.json>
```

| Consequence | Detail |
|---|---|
| No key management | Codemap never stores, reads, or transmits credentials |
| No SDK dependency | `codemap-ai` shells out; nothing is linked in |
| Uses the existing session | Whatever the developer is already authenticated with |
| Degrades cleanly | Binary absent → warning, rule-only results, exit 0 |

The classifier detects the binary on `PATH`, sends candidates as JSON on stdin,
and parses JSON from stdout. A non-zero exit, a parse failure, or a timeout is
**never fatal** — the run completes with rule-detected entry points and a warning.

Trade-off accepted: this requires `claude` to be installed, and it makes Codemap
dependent on an interface it does not control. In exchange, the default install
carries no AI dependency, no key handling, and no network code at all — which is
what keeps `--ai` a genuine fallback rather than a second product.

---

## 5. Change highlighting

Change status is an **overlay**, not the organising principle. The map is worth
opening with no diff at all.

| Status | Definition |
|---|---|
| `added` | The method exists now and its lines are all new in the diff |
| `changed` | Some of the method's lines fall inside a diff hunk |
| `removed` | Present in the base revision, absent now |
| `affected` | Unchanged, but one call hop from a changed method (either direction) |
| `unchanged` | Everything else |

`affected` is the highest-value status: it names the code most likely to break
without appearing in the diff. It is deliberately **one hop only** — two hops
marks most of the codebase and stops meaning anything.

### 5.1 What "changed" is measured against

The default answers **"what is in my pull request"**, because that is the question
being asked when the map is opened during review.

| Mode | Selected by | Git equivalent |
|---|---|---|
| `BRANCH` (default) | nothing, or `--base <branch>` | `git diff --unified=0 <base>...HEAD` plus uncommitted work |
| `REVISION` | `--since <commit>` | `git diff --unified=0 <commit>` |

`BRANCH` mode uses the **merge base** — the point this branch diverged from the
base branch — not a direct two-dot comparison. The difference matters as soon as
the base branch moves: a two-dot diff would colour commits other people landed
after this branch forked as though they were part of it.

The base branch is **auto-detected** from `origin/HEAD`, so the common case needs
no flag. `--base` overrides it; if detection fails and no `--base` was given, the
diff stage reports that it could not resolve a base rather than guessing.

Line ranges from the resulting hunks are mapped onto the method ranges in the
index.

`removed` methods are recovered from diff hunks rather than by parsing the base
revision, so they have no body to display. Parsing the base tree would double
indexing cost for a rarely used status.

---

## 6. Architecture

### 6.1 Pipeline

```
Discover → Parse → Resolve → Detect → Diff → Render
 sources   AST    call graph  entry    git    HTML
                              points  status
```

Each stage has one responsibility and communicates through the index model.
Stages are independently testable: parsing needs no git, diffing needs no AST.

### 6.2 Modules

```
codemap-core/      # model, parser, call graph, entry-point rules, diff
codemap-render/    # index.json → report.html
codemap-cli/       # picocli entry point, config loading
codemap-ai/        # optional AI classifier (isolated so core never depends on it)
```

`codemap-core` has no dependency on the renderer or the CLI. The AI classifier is
its own module so that the default path carries no AI dependency at all.

### 6.3 Stack

| Concern | Choice | Rationale |
|---|---|---|
| Language | Java 21+ | Matches the analysed domain |
| Parsing | JavaParser + symbol solver | Real AST; resolves overloads |
| CLI | picocli | Standard, annotation-driven |
| JSON | Jackson | Index serialisation |
| Config | SnakeYAML | `codemap.yml` |
| Rendering | D3.js v7.9.0 vendored, inlined | Collapsible tree, no CDN dependency |
| Build | Gradle, fat JAR | Single-artifact distribution |

**No Spring.** Codemap is a short-lived CLI process: start, analyse, write, exit.
A DI container costs startup time and adds a dependency without earning anything
in return.

**Symbol solver is not optional.** It is what makes the call graph trustworthy —
it distinguishes overloads instead of merging them by name, so two different
`requireText(...)` calls from one method remain two distinct edges. The cost is
configuration: the solver needs every source root, and unresolvable symbols must
degrade to a name-based edge marked `resolved: false` rather than failing the run.

### 6.4 Index format

`codemap/index.json`:

```jsonc
{
  "schemaVersion": 2,
  "generatedAt": "2026-07-29T21:00:00Z",
  "root": "/path/to/project",
  "comparison": { "mode": "BRANCH", "base": "main", "mergeBase": "83eb2f8" },
  "modules":  [ { "id","name","path","sourceRoots" } ],
  "classes":  [ { "id","moduleId","fqn","simpleName","packageName","kind",
                  "layer","file","lineStart","lineEnd","javadoc","status" } ],
  "methods":  [ { "id","classId","name","signature","file","lineStart",
                  "lineEnd","javadoc","source","constructor","visibility","status" } ],
  "calls":    [ { "from","to","kind","resolved","line","fromModuleId","toModuleId" } ],
  "entryPoints": [ { "id","moduleId","kind","label","methodId",
                     "detectedBy","source" } ],
  "removedMethods": [ { "file","lineStart","lineEnd" } ],
  "files":    { "<path>": { "hash","size","modifiedAtMillis" } },
  "statistics": { "filesScanned","filesParsed","classesIndexed",
                  "methodsIndexed","skipped": [ { "file","reason" } ] }
}
```

Each method in the `methods` array carries:
- `id`: method id including parameter types, e.g., `com.example.Task#update(TaskEdit, Instant)`
- `visibility`: Java access level (`PUBLIC`, `PROTECTED`, `PACKAGE`, or `PRIVATE`), read from
  the declaration's modifiers (spec 007 §5.1). Interface methods are implicitly `PUBLIC` when
  unqualified; record canonical constructors are `PUBLIC` (JLS 8.10.4); anything unreadable
  defaults to `PACKAGE` rather than failing the run, per the degrade-never-fail invariant.
  This field defaults to `PACKAGE` for indices written without it, so older indices still deserialize.

Each edge in the `calls` array carries:
- `from`: source method id (e.g., `com.example.Task#update(TaskEdit, Instant)`)
- `to`: target method id or bare name if unresolved
- `kind`: one of `CALL_INTERNAL`, `CALL_EXTERNAL`, `CROSS_MODULE`, `USES_TYPE`, `IMPLEMENTS`
- `resolved`: `true` if the symbol solver confirmed the target; `false` if degraded to a name match
- `line`: call-site line number, 1-based; 0 for type references (which have no single site)
- `fromModuleId`, `toModuleId`: set only for `CROSS_MODULE` edges, omitted otherwise

**`removedMethods` carries line ranges, not methods.** A deleted method has no
declaration left to name it: its identity lived in the base revision, which §5
deliberately does not parse. So the entry is a placeholder — the old path and the
lines that went away — and the report shows it as a gap rather than as a node
with a signature.

**`comparison` records how the run was produced.** Green means different things
measured from a branch point and from an arbitrary revision, so two reports of
the same project are only comparable if each says what it was measured against.
Absent when no diff could be resolved.

`statistics.skipped` is what makes a degraded run honest: a file Codemap could
not parse is named there and reported on the console, rather than silently
missing from the map.

Method `id` includes parameter types (`com.example.Task#update(TaskEdit, Instant)`)
so overloads stay distinct. The return type is excluded deliberately — Java does
not overload on it, and including it would churn ids when a return type widens.

The index doubles as the incremental cache: `files` carries hash, mtime, and size
so a rerun reparses only what changed.

### 6.5 Incremental re-indexing

1. Load `index.json` if present and `schemaVersion` matches.
2. Compare each file by size and mtime; on a match assume unchanged, otherwise
   compare content hash.
3. Reparse only changed files; drop their classes, methods, and outgoing calls.
4. Recompute the call graph and entry points (cheap relative to parsing).

Call edges *into* a reparsed file from unchanged files must be revalidated — a
method may have been renamed or deleted. Edges whose target no longer exists are
dropped and reported as unresolved.

`--rebuild` discards the cache.

### 6.6 Report

`report.html` is a single self-contained file that must work over `file://` with
no server and no network access, so it can be copied or attached to a review and
still work.

**Page structure.** The HTML carries three inline `<script>` elements:

1. The vendored D3 v7.9.0 bundle (inlined from `codemap-render` resources)
2. An assignment of the escaped view model to `window.__CODEMAP_DATA__`
3. The interactive report script that populates the UI and wires up navigation

CSS is similarly inlined. Method source is embedded in the index; class source
is embedded in the view model only (not in `index.json`), so the index does not
double in size carrying class bodies next to method bodies. Both are necessary
because the browser cannot read local files under `file://`.

**Security model.** The page declares a Content-Security-Policy of
`default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'`.
Inline script and style must be allowed — the whole point of the report is that
it carries its own — but all network access is denied by the browser itself. This
enforces the self-containment invariant at runtime rather than relying on
convention alone, and means that even if an escaping bug let untrusted markup out,
it could not reach the network to exfiltrate the source text the report embeds.

**Escaping embedded source.** The view model embeds untrusted Java source text
(method bodies, class bodies, comments, javadoc) into a `<script>` element as
part of a JSON object.
Characters `<`, `>`, `&`, U+2028 (line separator), and U+2029 (paragraph
separator) are escaped as JSON `\uXXXX` escapes. This is character-level escaping
of the *characters themselves*, not sequence matching, because the HTML tokenizer
ends a script element on `</script` followed by whitespace, `/`, or `>`,
case-insensitively — so `</script foo>`, `</SCRIPT>`, and `</script/>` all
terminate the element, and attempting to match any one literal is bypassable.
Every replacement is itself valid JSON, so the source text remains parseable and
round-trips losslessly.

---

## 7. Acceptance criteria

Derived from the original brief; all must hold before the tool is considered
done.

- [ ] Runs on a real project with one command and does not crash.
- [ ] `report.html` opens in a browser with no server and no console errors.
- [ ] The diagram shows class boxes, each with constructors and public methods in compartments.
- [ ] Each build module is its own root; entry points hang beneath their module.
- [ ] Cross-module connectors are drawn and a module-level overview is available.
- [ ] No test source appears anywhere in the map.
- [ ] Entry points are detected and are the roots of the map.
- [ ] Programmatic (non-annotated) REST routes are detected with method and path.
- [ ] Clicking a method shows its genuine source, sliced from the file.
- [ ] The side panel lists **Called by** and **Calls**, both navigable.
- [ ] A class appears at most once on the canvas; expansion links to the existing box.
- [ ] Private methods are absent initially and appear only when a visible method calls them.
- [ ] After a test edit, changed and affected rows are coloured; status is visible without colour.
- [ ] A rerun after a one-file edit is measurably faster than the first run.
- [ ] `--ai` is off by default; with it on, results are cached and labelled.

### Verification project

Correctness is verified against **Kairos** (`../kairos`, 169 production Java
files across 10 Gradle modules), which exercises every detection style in one
codebase:

| Style | Where |
|---|---|
| Programmatic REST | `kairos-api` — Javalin method references |
| Annotations | `kairos-admin` — Vaadin `@Route` |
| Bootstrap | `main()` in both `kairos-api` and `kairos-admin` |
| Multi-module | 10 Gradle subprojects, deployed as separate processes |
| Cross-module | `common` contracts shared by `kairos-api` and `kairos-admin` |

It is also a genuine hexagonal codebase, so layer assignment and port/adapter
structure can be judged against real architecture — and it has 941 `@Test`
methods, which makes it a real test of the exclusion rule in §3.6.

---

## 8. Known limitations

- **Java only.**
- **Interface dispatch still fans out when a port has several production
  implementations.** Excluding test sources (§3.6) removes the common case — the
  in-memory test double — but a port with two real adapters (e.g. Kafka and
  webhook delivery) legitimately reaches both, and static analysis cannot say
  which is wired at runtime. Both are shown.
- **Framework registration breaks call chains.** Methods reached only through
  framework reflection (HTTP route registration, Vaadin `@Route` instantiation,
  dependency injection by name) have no incoming call edge, because static
  analysis cannot see the registration code. These are entry points (detected in
  §4), but if one is called from production code *without* going through the
  framework's entry point, that edge is invisible. On the reference project
  (kairos, 797 methods) this affects 227 methods in this position. Workaround: such
  methods are flagged as `ENTRY_POINT`, which explains their apparent lack of
  callers.
- **Reflection, DI-by-name, and dynamic proxies are invisible.** Chains passing
  through them break; nothing in static analysis can recover them.
- **Test-only classes appear as orphans**, since nothing in production calls them
  (§3.6). This is accurate rather than a defect, but it can surprise.
- **`removed` nodes have no body** (see §5).
- **Generated sources** (JOOQ, MapStruct) inflate the map. Excluded by default
  via a `build/generated` path filter, overridable in config.

---

## 9. Deferred

Deliberately out of the diagram implementation (spec 007), recorded so the design
does not preclude them:

- **Fields and attributes in class boxes.** The map is about behaviour, not data
  structure. UML would show them; they are left out.
- **Inheritance and interface-implementation arrows as UML generalisations.** The
  `IMPLEMENTS` edges exist in the call graph but are not drawn as first-class UML
  relations. They live in the index but not yet in the diagram.
- **Manual box dragging and layout persistence.** Boxes position automatically by
  call depth and barycentre ordering. Saving and restoring user-chosen positions
  across reloads is deferred.
- **SVG/PNG export.** The diagram renders to HTML/CSS/SVG but no export format
  is exposed.

Earlier deferrals:

- Kotlin support.
- A `--serve` mode with live re-indexing on file change.
- Cross-module maps spanning several repositories.
- Export to Mermaid or Graphviz.
- Per-node history (how often a method changes).
