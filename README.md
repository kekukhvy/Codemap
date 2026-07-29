# Codemap

An interactive mindmap of a Java application — organised by **entry points**, not
by folders. Open the map and you see what the application actually *does*: which
REST endpoints, jobs, and listeners exist, and what happens when each one fires.

From any method you can follow the chain in both directions — what it calls, and
who calls it — and read the real source without leaving the map. Classes and
methods touched by recent git changes are outlined in colour on top of that.

---

## Status

**In development.** The build and CLI exist: the tool runs, validates its
arguments, and reports the configuration it resolved. **No analysis happens
yet** — running it prints the resolved settings and says so.

Progress is tracked as milestones M1–M8; see the
[issues](https://github.com/kekukhvy/Codemap/issues).

| | Milestone | State |
|---|---|---|
| M1 | Project skeleton — modules, CLI, fat JAR | ✅ done |
| M2 | Index the code | ⏳ next |
| M3–M8 | Call graph → report → incremental → config/AI | planned |

The rest of this document describes the tool as specified; see
[`doc/specification.md`](doc/specification.md) for the design rationale.

---

## Why entry points

Package trees show how code was filed away. They say little about behaviour: a
`service` package tells you nothing about which of its classes is reachable from
a live HTTP request and which is dead code.

Codemap roots the map in the places the outside world can reach:

```
kairos-api                           ← module root
└── POST /api/v1/tasks               ← entry point
    └── TaskHandler.create()         ← focused method, siblings visible
        ├╌ validate()                ╌ dashed: same class
        └─→ CreateTaskUseCase        → solid: crosses into another class
            └── execute()
                └─→ TaskRepository.save()
                    ═══ common       ═ heavy: crosses a module boundary
```

Everything under an entry point is reachable at runtime. That makes the map a
map of behaviour, and it makes dead code visible by absence.

Each build module is its own root, because that is how such systems are
deployed — separate processes, separate containers. Where modules genuinely
touch, a **connector** is drawn, and a module-level overview aggregates those
into a "which module depends on which" picture.

---

## What it does

1. **Indexes** the project's production sources with JavaParser — modules,
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

Output lands in `codemap/`: `index.json` (the index, and the incremental cache)
and `report.html` (the map). Both are build artifacts — git-ignore them.

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

The tree **grows as you expand it**. Codemap never renders the whole call graph
upfront — that explodes in size and loops forever on recursion. One level is
shown, and the next is fetched when you open a node.

Clicking an entry point reveals its class with **all methods visible but the
relevant one focused**, so you see the method among its neighbours rather than in
isolation. From there:

- **`╌╌` dashed edge** — a call inside the same class; you stay in the card.
- **`──→` solid edge** — a call into another class; the node opens a fan of
  arrows out to each collaborator (validator, repository, …).
- **`═══` heavy connector** — the call crosses into another module. Collapsed by
  default so the tree stays readable; expanding it jumps to the other module.
- **Revisiting a node** already expanded higher in the branch shows it collapsed
  with a `↗ already above` badge that jumps to the original. Cycles terminate,
  and you can see that it is the same method.

Selecting a method opens the side panel:

- signature, layer, file path and line range
- the Javadoc summary
- **Called by** — every caller, as links
- **Calls** — every callee, as links
- the **real source**, sliced from the file by line range

Both lists are navigable, so a chain can be walked upward from a repository to
the endpoints that reach it, or downward from an endpoint to the database.

Types are shown too: selecting a type used in a signature (`User`) lists where it
is used and what methods it has. Note this is a *usage* relation, not a call —
a type does not call anything, so chains do not continue through it.

### Change highlighting

Layered on top of the structure, not the point of it:

| Outline | Status | Meaning |
|---|---|---|
| 🟢 Green | `changed` / `added` | Lines fall inside the git diff |
| 🔴 Red, struck through | `removed` | Existed in the base, gone now |
| 🟡 Yellow | `affected` | Not edited, but one call hop from something that was |
| ⚪ None | `unchanged` | Untouched |

`affected` surfaces the callers and callees of your edits — the code most likely
to break without appearing in the diff.

### Controls

- **Focus on changes** — collapse everything except paths to changed nodes.
- **Search** — filter by class or method name.
- **Layer filter** — show or hide architectural layers.

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

**Self-contained output.** `report.html` inlines its CSS and JavaScript and
embeds the index as JSON. It renders over `file://` with no server and no network
access, so it survives being copied around or attached to a review.

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
