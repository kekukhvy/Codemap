# Codemap

An interactive mindmap of a Java application — organised by **entry points**, not
by folders. Open the map and you see what the application actually *does*: which
REST endpoints, jobs, and listeners exist, and what happens when each one fires.

From any method you can follow the chain in both directions — what it calls, and
who calls it — and read the real source without leaving the map. Classes and
methods touched by recent git changes are outlined in colour on top of that.

---

## Status

**In development.** The tool maps a project today: it discovers build modules,
parses production sources, resolves the call graph, detects entry points, and
overlays git change status. It writes two files — `codemap/report.html`, the
interactive map you open directly in a browser (no server, no network), and
`codemap/index.json`, the underlying index that also serves as the cache for the
next run.

Progress is tracked as milestones M1–M8; see the
[issues](https://github.com/kekukhvy/Codemap/issues).

| | Milestone | State |
|---|---|---|
| M1 | Project skeleton — modules, CLI, fat JAR | ✅ done |
| M2 | Index the code — modules, classes, methods, Javadoc | ✅ done |
| M3 | Call graph | ✅ done |
| M4 | Entry points | ✅ done |
| M5 | Git change status | ✅ done |
| M6 | Render the interactive report | ✅ done |
| M7 | Incremental re-indexing | ⏳ next |
| M8 | `codemap.yml` rules and the `--ai` fallback | planned |

The rest of this document describes the tool as specified; see
[`doc/specification.md`](doc/specification.md) for the design rationale.

---

## Why entry points

Package trees show how code was filed away. They say little about behaviour: a
`service` package tells you nothing about which of its classes is reachable from
a live HTTP request and which is dead code.

Codemap roots the map in the places the outside world can reach:

```
  [POST /api/v1/tasks]  ← entry point pill
         ↓
    ┌─ TaskHandler ─(+)─┐  ← class box; (+) expands to show collaborators
    │ + create()    (+) │  ← entry method (underlined)
    ├╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌┤
    │ - validate()      │  ← private: appeared because create() calls it
    └───────────────────┘
         ↓ calls
    ┌─ CreateTaskUseCase ─(+)─┐
    │ + execute()             │
    │ + validate()            │
    └─────────────────────────┘
         ↓ calls
    ┌─ TaskRepository ─(+)─┐
    │ + save()            │
    │ + findById()        │
    └─────────────────────┘
```

Everything under an entry point is reachable at runtime. That makes the map a
map of behaviour, and it makes dead code visible by absence. Each class box
lists its public API; nothing else is drawn until you ask for it, so the canvas
grows one deliberate step at a time rather than dumping the whole call graph.

Each build module is its own root, because that is how such systems are
deployed — separate processes, separate containers. Where modules genuinely
touch, a **connector** is drawn, and a module-level overview aggregates those
into a "which module depends on which" picture.

---

## What it does

1. **Parses** the project's production sources with JavaParser — modules,
   classes, methods, and a call graph resolved by symbol solver.
2. **Detects entry points** — REST routes, scheduled jobs, message listeners,
   UI routes, `main()` methods — and hangs them under their module.
3. **Diffs** against git to outline changed classes and methods.
4. **Renders** a self-contained `report.html` you open directly in a browser.

**Test sources are excluded entirely.** The map answers what the application does
in production; tests are not reachable from any entry point, they invert the call
direction, and they typically outnumber production code. Excluding them also
keeps chains honest — a call through a repository port leads to the real
implementation rather than forking into an in-memory test double.

---

## Requirements

- JDK 21+
- `git` on `PATH`

The analysed project needs no changes and is never built — Codemap reads source
files and git metadata only.

---

## Usage

```bash
# Build the tool (produces codemap-cli/build/libs/codemap.jar)
./gradlew build

# Map the current project, highlighting this branch's changes
java -jar codemap-cli/build/libs/codemap.jar

# Map another project
java -jar codemap-cli/build/libs/codemap.jar --root /path/to/project

# Or through Gradle
./gradlew :codemap-cli:run --args="--root /path/to/project"
```

Every option has a working default, so a bare run maps the current directory.
Use `--help` to see the flags.

### Options

| Flag | Meaning | Default |
|---|---|---|
| `--root <path>` | Java project to analyse | current directory |
| `--base <branch>` | Branch to compare against | the repository's default branch |
| `--since <commit>` | Compare against this exact commit instead of the branch point | — |
| `--out <path>` | Where to write the report | `codemap/report.html` |
| `--config <path>` | Custom entry-point rules | `codemap.yml` if present |
| `--ai` | Let an AI agent classify unresolved entry points | off |
| `--rebuild` | Ignore the cached index and reparse everything | off |

### What "changed" means

By default the map highlights **everything this branch changed since it diverged
from the default branch** — the same set of changes a pull request shows. So
during review, a bare `codemap` already highlights the right thing.

That uses the merge base, not a direct comparison against the tip. The difference
shows up as soon as the base branch moves: comparing against the tip would colour
commits other people landed after you branched as though they were yours.

```bash
codemap                     # this branch's changes (what your PR contains)
codemap --base develop      # compare against a different branch
codemap --since HEAD~5      # compare against an exact commit instead
```

Output lands in `codemap/`: `report.html` (open this in a browser to read the map)
and `index.json` (the index, and the incremental cache for the next run). Both
are build artifacts — git-ignore them.

---

## Entry-point detection

Detection is **deterministic first**. Rules cover the common frameworks:

| Kind | Recognised by |
|---|---|
| REST | `@RestController` + `@GetMapping`/`@PostMapping`/…; JAX-RS `@Path`/`@GET` |
| REST (programmatic) | Javalin/Spark route registration — `app.post(PATH, handler::method)` |
| Scheduled | `@Scheduled`, Quartz `Job.execute` |
| Messaging | `@KafkaListener`, `@RabbitListener`, `@JmsListener` |
| WebSocket | `@ServerEndpoint`, `@MessageMapping` |
| UI | Vaadin `@Route` |
| Bootstrap | `public static void main`, `CommandLineRunner` |

Programmatic registration matters as much as annotations. A route registered as
`app.post(TASKS, taskHandler::create)` carries the HTTP method, the path, and the
target method — all recoverable without a single annotation.

Projects with their own conventions add rules in `codemap.yml`:

```yaml
entryPoints:
  - kind: JOB
    label: "Engine claim loop"
    match:
      implements: dev.kairos.engine.ClaimLoop
      method: run
```

### The `--ai` fallback

Deterministic rules can miss entry points that are conventions rather than
syntax — a hand-rolled dispatcher, a worker that is a root by meaning only.

With `--ai`, the AI **does not search from scratch**. Rules run first, and only
what remains unexplained — public classes that nothing in the project calls, the
natural root candidates — is handed to an agent for classification.

Every entry point records how it was found (`detectedBy: RULE | CONFIG | AI`),
and AI verdicts are cached in the index. The map stays reproducible, you can see
which findings to trust, and a later run without `--ai` keeps what was found.

Codemap shells out to the **local `claude` CLI** rather than embedding an AI SDK:

```
claude --print --output-format json  <  candidates.json
```

So there are no API keys to manage, no SDK on the classpath, and no network code
in the default install — it reuses whatever session you are already logged into.
If the binary is missing or the call fails, the run completes with rule-detected
entry points and a warning.

**Default is off.** Rules are the product; AI is the fallback.

---

## Navigating the map

Open `report.html` in any browser (Chrome, Firefox, Safari, etc.). It works over
`file://` with no server and no network — you can copy it anywhere or attach it
to a code review and it still works. All assets (CSS, JavaScript, D3, and the
index data) are embedded in the single file.

### The diagram

The canvas displays **entry-point pills** (rounded boxes showing REST routes, scheduled
jobs, and other entry points) and **class rectangles** (UML-style boxes showing each
class touched by an entry point or any call chain from it).

Click an **entry point pill** to draw its declaring class box. The entry-point method
(the one the entry point targets) is underlined in the class box, with a link from the
pill pointing to it. From there you expand outward through collaborating classes.

### Class boxes

Each class box shows three sections (omitting empty ones):

1. **Constructors** — listed at the top
2. **Public methods** — the class's public API
3. **Private methods** — only those called by visible public methods

Each method row is prefixed with a **UML visibility marker**:
- **`+`** — public
- **`#`** — protected
- **`~`** — package-private
- **`-`** — private

The box header shows the class name and its architectural layer, inferred from the
package and class name (`…/controller/`, `…Repository`, and so on). A **`(+)` expander**
on the header reveals what classes this class collaborates with — every class it calls
at least one method on.

### Expanding calls

Click the **`(+)` expander on a method row** to see what that method calls:

- **Solid links** → calls to public methods of other classes. The target class box is
  drawn (or reused if already on canvas), and the target method row is underlined.
- **Dashed links** → calls to private methods of the same class. The private method row
  is added to the box's third compartment (becoming visible and itself expandable).
- **Self-links** (a small loop) → calls to other public methods of the same class, also
  underlined.

A method calling two methods of one class draws **two links**, one per target. This
visibility lets you follow which collaborators matter most.

### Link routing

Links between boxes are **orthogonal polylines** (horizontal and vertical segments only).
Each link gets its own **lane**, so no line crosses a class rectangle and no two lines
share a segment — where several relationships run between the same pair of columns you
can still tell which goes where. Hover a link to highlight it and both endpoint rows,
making long routes easy to follow.

### Class uniqueness

A class appears **exactly once** on the canvas, keyed by its fully-qualified name — so
two same-named classes in different packages stay distinct. If two different call
paths both reach the same class, they share the same box — you see it is a shared
collaborator, not two separate instances. Collapsing one caller does not remove a box
that another expanded path still reaches.

### Side panel

Click a **class name** to open the side panel showing:

- **Calls** — every class this one calls at least one method on (class-granular, not
  method-by-method)
- **Called by** — every class that calls at least one method of this one
- **Full class source** — the entire class body, so you can read it inline

Click a **method row name** to open the side panel showing:

- **Called by** — every class that calls this method (class names, not individual call sites)
- **Method source** — the full method body

The class names in those lists are clickable: following one swaps the panel to that
class, so you can read your way along a call chain. This never moves or redraws the
canvas — the diagram stays exactly as you arranged it.

### Change highlighting

Change status is layered on top of the structure:

| Border | Status | Meaning |
|---|---|---|
| **Solid green** | `ADDED` | Class is new in this branch; header also filled green |
| **Solid green** | `CHANGED` | Class exists in base; only changed rows are highlighted green |
| **Dashed amber** | `AFFECTED` | Class untouched, but a method it calls was changed |
| **None** | `UNCHANGED` | Untouched |

Colour is never the only signal: the border style (solid, dashed, plain) and a glyph in
the box header carry the same information, so the map reads correctly in greyscale and
for colour-blind readers. `REMOVED` methods are listed in the side panel only, since
there is no declaration left to draw a row for.

### Controls

- **Entry-point list** — pick which entry point to open on the canvas.
- **Search** — filter the entry-point list by class or method name.
- **Layer filter** — restrict the list to entry points declared in one layer.
- **Module filter** — restrict the list to one build module.
- **Light/dark theme toggle** — choose your preferred reading mode.

All three narrow the entry-point list you choose from, not the boxes already on the
canvas — once a diagram is open, only expanding and collapsing changes what it shows.

---

## Design notes

**JavaParser with symbol solver.** Symbol resolution is what makes the call graph
trustworthy: it distinguishes overloads instead of merging them by name, so two
different `requireText(...)` calls in one method stay two distinct edges. The
cost is configuration — the solver needs every source root of the project to
resolve cross-module types.

**Calls into the JDK and third-party libraries are ignored.** They add volume
without helping you understand your own system.

**Incremental by default.** Each file is cached with size, mtime, and content
hash; a rerun reparses only what changed, so the second run is substantially
faster. `--rebuild` forces a clean pass.

**Self-contained output.** `report.html` embeds everything: D3, CSS, JavaScript,
and the complete index. It renders over `file://` with no server and no network
access. That means you can copy it anywhere, attach it to a GitHub review (yes,
really), send it in Slack, or sync it to a shared drive and everyone sees the
same thing. On typical projects the file is 1–2 MB.

---

## Limitations

- Java only.
- A port with several *production* implementations still fans out to all of them —
  static analysis cannot tell which one is wired at runtime. (Test doubles are
  gone, since test sources are not indexed.)
- Classes used only by tests appear as orphans with no callers. That is accurate,
  but it can surprise.
- `removed` methods come from the git diff rather than by parsing the base
  revision, so they appear without a source body.
- Reflection, DI-by-name, and dynamic proxies are invisible to static analysis —
  chains that pass through them break.
