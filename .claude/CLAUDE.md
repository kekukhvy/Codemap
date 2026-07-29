# Codemap — Project Instructions for Claude

> Interactive mindmap of a Java application, rooted in **entry points**.
> `Discover → Parse → Resolve → Detect → Diff → Render`. Codemap analyses code
> statically — it never executes, instruments, or builds the project it maps.

Read this file first. Code rules live in [GUIDELINES.md](./GUIDELINES.md) and
**must** be followed for every change. The design source of truth is
[`doc/specification.md`](../doc/specification.md).

---

## Source documents (read before non-trivial work)

| File | What it covers |
|---|---|
| `README.md` | What the tool is, how to run it, how to read the map |
| `doc/specification.md` | The *why*: core model, entry-point detection, index format, architecture, acceptance criteria |
| `doc/specs/` | Per-slice specs written by `/specification` |

When a task touches the graph model, entry-point detection, or the index format,
re-read the relevant section of `doc/specification.md` rather than relying on
memory — several decisions there are deliberate and should not be re-litigated.

---

## The four decisions that shape everything

These were settled deliberately. Don't quietly design around them.

1. **Entry points are the roots, not packages.** The map answers "what can this
   application do, and what happens when each runs". Package trees describe
   filing, not behaviour.
2. **Each build module is its own root.** Runnable modules deploy as separate
   processes; one merged root would depict a monolith that doesn't exist.
   Cross-module calls draw connectors (§3.2.2).
3. **Test sources are never indexed** — not parsed, not shown, not filterable
   back in (§3.6). They're unreachable from entry points and invert call
   direction. This also removes the port fan-out problem.
4. **Change highlighting is an overlay**, not the organising principle. The map
   must be worth opening with no diff at all.

---

## Architecture

```
codemap-core/      # model, parser, call graph, entry-point rules, diff
codemap-render/    # index.json → report.html
codemap-cli/       # picocli entry point, config loading
codemap-ai/        # optional AI classifier
```

Dependencies point one way: `cli` → `render` → `core`. **`codemap-core` depends
on nothing else in the project.** `codemap-ai` is isolated so the default path
carries no AI dependency at all.

Package root: `dev.codemap`.

### Pipeline stages are independently testable

Each stage has one responsibility and communicates through the index model:
parsing needs no git, diffing needs no AST. Keep it that way — a test that needs
a git repo to check a parser is a sign the boundary leaked.

---

## Tech stack

- **Java 21+** — plain `main()`, **no Spring** (a short-lived CLI earns nothing
  from a DI container)
- **JavaParser + symbol solver** — real AST; resolves overloads. Not optional:
  it's what makes the call graph trustworthy
- **picocli** — CLI arguments
- **Jackson** — `index.json`
- **SnakeYAML** — `codemap.yml`
- **D3.js** — vendored **inline**, never from CDN
- **JUnit 5** — tests
- **Gradle** — fat JAR

---

## Invariants (do not break these)

- **Never build or execute the analysed project.** Codemap reads source files and
  git metadata. Nothing else.
- **`report.html` must work over `file://`** with no server and no network. That
  means D3 inlined, CSS/JS inlined, index embedded, and method source text
  embedded (the browser can't read local files).
- **Unparseable input degrades, never fails.** A real repo always contains
  something odd; one bad file must not cost the whole run. Same for unresolved
  symbols (`resolved: false`) and every `--ai` failure mode.
- **The tree grows on expansion.** Never materialise the full reachable set —
  that's what bounds size and terminates cycles.
- **`detectedBy` is always recorded** (`RULE | CONFIG | AI`). A reader must be
  able to tell a syntax-derived fact from a model's guess.

---

## Build / run

```bash
./gradlew build                                    # build + tests
java -jar build/libs/codemap.jar --root <path> --base main
./gradlew run --args="--root <path> --base main"
```

**Verification project:** `../kairos` — 169 production Java files, 10 Gradle
modules, 941 `@Test` methods. It exercises every detection style at once:
programmatic Javalin routes (`kairos-api`), Vaadin `@Route` (`kairos-admin`),
`main()` in both, and real cross-module dependencies on `common`. Verify
non-trivial changes against it, not just unit tests.

Definition of Done for a slice: it runs on Kairos without crashing, the
acceptance criteria in the issue hold, and tests cover the edge cases.

---

## Git workflow

- `main` — stable. `feature/xxx` — one branch per slice, branched from `main`.
- Flow: Issue → branch → PR → merge.
- Commit/push only when the user explicitly asks. Never commit directly to
  `main`; branch first.
- Do **not** commit `.gradle/`, `.idea/`, or generated `codemap/` output.
- Watch out: a stray `[submodule] active = .` in `.git/config` (sometimes added
  by the IDE) makes git treat every nested module as a submodule and silently
  stops tracking their contents. Remove it with
  `git config --unset submodule.active`.

---

## AI SDLC — commands & subagents

The `.claude/` directory holds an issue → PR pipeline. The map of every command
and agent lives in [.claude/README.md](./README.md) (`/specification` →
`/create-issue` → `/implement` → `/review-cycle` → `/verify-coverage` →
`/create-pr`, or `/ship` for the whole slice).

Sync subagents keep artifacts aligned with the code:

- **spec-keeper** — updates `doc/specification.md` when the model, index format,
  detection rules, or architecture change.
- **user-docs-writer** — updates `README.md` when CLI flags, config, or output
  change.
- **test-author** — writes unit and fixture-based tests.
- **javadoc-writer** — Javadoc for public/protected types and methods.
- **logging-instrumenter** — SLF4J logging at the right levels.
- **architecture-reviewer** — read-only review for Clean Architecture + Clean
  Code (layer boundaries, SRP, DRY, KISS, methods ≤40 lines). Guards against
  over-engineering — readability wins.

These are invoked **explicitly**, once per slice when the whole thing is ready to
sync — not per file edit. Each starts from a cold context, so re-running them
mid-slice is the single biggest driver of usage.

For code written by hand outside Claude, **`/sync`** diffs the changes and
delegates to the right subagents.

## When writing code

1. Follow [GUIDELINES.md](./GUIDELINES.md) — no exceptions.
2. Respect the module boundaries above. `codemap-core` imports nothing from
   `render`, `cli`, or `ai`.
3. Re-read the relevant `doc/specification.md` section for the area you touch.
4. Test: unit tests for parsing/graph/diff logic against small fixture sources;
   end-to-end runs against `../kairos` for anything non-trivial.
