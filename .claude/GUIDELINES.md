# Codemap — Coding Guidelines

These rules are **mandatory** for every change. They apply to humans and to
Claude equally. If a rule conflicts with "just make it work", the rule wins —
ask before breaking one.

---

## Foundations

We follow **Clean Code** and **Clean Architecture**.

- **Clean Architecture** — dependencies point one way. `codemap-core` depends on
  nothing else in the project; `render`, `cli`, and `ai` depend inward. See
  [CLAUDE.md](./CLAUDE.md) for the module rules.
- **Clean Code** — code reads like prose. Intention-revealing names, small
  functions, no surprises.

---

## Hard rules (non-negotiable)

### 1. No literals in code — ever
Magic literals are **strictly forbidden**. Every value must live in a
**constant** or a **variable**, never inline.

- String literals → `private static final String` constants (or config).
- Numbers → named constants. The only allowed bare numbers are `0` and `1`
  in genuinely trivial idioms (loop start, `size - 1`); even then prefer a
  name if it carries meaning.
- Repeated/meaningful values → a single named constant, never duplicated.
- Configuration values (paths, timeouts, limits) → config or CLI options, not
  hardcoded.

```java
// ❌ forbidden
if (path.endsWith(".java") && lines > 40) { warn("too long"); }

// ✅ required
private static final String JAVA_EXTENSION = ".java";
private static final int MAX_METHOD_LINES = 40;
if (path.endsWith(JAVA_EXTENSION) && lines > MAX_METHOD_LINES) { ... }
```

Prefer enums over string constants where the value is a closed set
(`EntryPointKind`, `EdgeKind`, `ChangeStatus`, `Layer`, `DetectedBy`).

### 2. Methods ≤ 40 lines
No method exceeds **40 lines** (body, excluding signature and braces). If it
grows past that, extract sub-methods with intention-revealing names. A long
method is a sign of a missing abstraction.

> Codemap reports this metric about other projects. Failing it in our own code
> would be embarrassing.

### 3. SRP — Single Responsibility Principle
Every class and method does **one** thing. One reason to change. A parser
parses; a detector detects; a renderer renders. Pipeline stages must not reach
into each other's concerns — if a parser needs git, the boundary leaked.

### 4. KISS — Keep It Simple
Choose the simplest design that solves the actual problem. No speculative
generality, no abstraction without a second caller. No deep generics. This tool
exists to make code understandable — its own code must be the example.

### 5. DRY — Don't Repeat Yourself
No copy-pasted logic, no duplicated literals, no parallel implementations of the
same rule. Shared model types live in `codemap-core`.

---

## Naming

- Classes: nouns (`CallGraphBuilder`, `EntryPointDetector`, `MethodId`).
- Methods: verbs (`parse`, `resolve`, `detect`, `render`).
- Booleans: `is/has/supports` (`isResolved`, `hasCallers`).
- Constants: `UPPER_SNAKE_CASE`.
- No abbreviations or single-letter names (except trivial loop indices).
- Names reveal intent — no comments needed to explain what a name means.

---

## Methods & classes

- Small methods, small classes. Keep classes cohesive; split when a class starts
  to have multiple reasons to change.
- Minimize parameters. 3+ related parameters → a parameter object. Use the
  **Builder pattern** for complex model objects.
- No boolean flag parameters that switch behaviour — split into two methods.
- Fail fast: validate inputs at the start with guard clauses.
- Prefer immutability. Model types (`MethodId`, `ClassId`, `ModuleId`) are
  immutable; prefer records for value types.

---

## Model layer specifics (`codemap-core`)

- **No dependency on render, cli, or ai.** Enforced by a test.
- The index model is a **serialisation contract** — `index.json` is read back by
  later runs and by the report. Changing a field is a breaking change: bump
  `schemaVersion` and handle the mismatch by rebuilding.
- Keep model types free of rendering concerns. No colours, no CSS classes, no
  D3 shapes in `core` — those belong to `codemap-render`.

---

## Error handling

- No swallowed exceptions. No empty `catch` blocks.
- **Degrade, don't fail.** A malformed source file, an unresolvable symbol, a
  missing `claude` binary, a corrupt cache — each produces a warning and a
  usable result, never a crashed run. This is a hard project invariant, not a
  nicety: a tool that dies on one odd file is useless on real repositories.
- Report what was degraded. A silent partial result is worse than a loud one —
  the user must know the map is incomplete.
- Reserve exceptions for genuinely unrecoverable states (unreadable root
  directory, invalid CLI arguments).

---

## Comments

- Code should be self-explanatory; comments explain **why**, not **what**.
- No commented-out code. No noise comments restating the obvious.
- Keep doc-comments where they add real value (public API, non-obvious
  invariants, the reason a heuristic exists).

---

## Testing

- **Unit tests** for parsing, graph building, detection, and diff logic — run
  against small Java fixture sources checked into test resources, not against a
  whole project.
- **Fixtures over mocks** for parser tests: a tiny real `.java` file exercises
  JavaParser honestly; a mocked AST tests nothing.
- **End-to-end runs against `../kairos`** for anything non-trivial. Guard these
  so the suite still passes when that path is absent.
- Cover the degradation paths explicitly — malformed source, unresolved symbol,
  missing binary, corrupt cache. They are the rules most likely to regress.
- Tests follow the same rules: no magic literals, clear names, one assertion
  focus per test where reasonable.

---

## Quick checklist before finishing any change

- [ ] No inline literals — all in constants/enums/config
- [ ] No method longer than 40 lines
- [ ] Each class/method has a single responsibility
- [ ] No duplicated logic or values (DRY)
- [ ] Simplest solution that works (KISS)
- [ ] Module boundaries respected; `codemap-core` imports nothing internal
- [ ] Failure paths degrade with a warning rather than crashing
- [ ] Tests added/updated and passing
