---
description: Sync spec/docs/tests with the user's own code changes by delegating to the subagents
---

The user changed code themselves (in their IDE, outside Claude), so no pipeline
stage synced the artifacts. Bring the project's specification, user docs, and
tests back in sync with reality. (After `/implement` this is unnecessary — its
step 4 already ran these agents for the slice.)

Steps:

1. Inspect what changed:
   - `git status`
   - `git diff` (unstaged) and `git diff --staged` (staged)
   - If `$ARGUMENTS` is non-empty, scope the sync to those files/paths only.
2. If there are no changes at all, report "nothing to sync" and stop.
3. **Show the plan before doing anything.** Print a short table of which
   subagents will run and **why** (which changed file/area triggered each), and
   which are skipped and why. This is the point of the command — the user sees
   what will be synced before it happens, not a silent delegation. Example:

   ```
   Sync plan for <scope>:
   - spec-keeper       ✔ run   — Schedule.java changed schedule semantics
   - user-docs-writer  ✔ run   — new field on ScheduleResponse (contract change)
   - test-author       ✔ run   — new use case ScheduleService.create
   - javadoc-writer    ✔ run   — new public methods lack Javadoc
   - logging-instrumenter ✔ run — new application-layer code
   ```

4. **If the change is small — do it yourself.** Rule of thumb: **≤ 3 files or
   ≤ ~50 changed lines**. Write the Javadoc, logging, and tests directly in this
   context, where the diff is already read. A subagent starts from a **cold
   context** and re-reads the project from scratch, so several cold starts to
   service a 20-line diff cost more than the work itself. Still show the plan
   from step 3 — just mark the rows as "inline" rather than a subagent. Delegate
   only when the change is big enough that the re-reading pays for itself.

5. Otherwise decide which subagents are relevant to the actual changes (skip the
   ones that don't apply):
   - **spec-keeper** — if the domain model, entities, schedule/execution
     semantics, API surface, DB schema, or architecture changed → updates `doc/`.
   - **user-docs-writer** — if endpoints, DTO/contract fields, or client-facing
     behavior changed → updates `README.md`.
   - **test-author** — if domain logic, use cases, repositories, or endpoints
     changed → adds/updates unit + integration tests.
   - **javadoc-writer** — if Java types/methods were added or changed and lack
     Javadoc → adds Javadoc to public/protected types and members (skipping
     trivial getters and noise).
   - **logging-instrumenter** — if Java code outside `domain` was added or
     changed → adds/tunes SLF4J logging at the right levels (DEBUG/INFO/WARN/
     ERROR) for audit and analysis. Never touches `domain` (framework-free).
6. Delegate to each relevant subagent via the Task tool. **Put the scope in the
   prompt**: the concrete list of changed files and the diff (or the relevant
   slice of it). A subagent inherits **none** of this conversation, so without it
   it burns its first dozen tool calls rediscovering what you already know. It
   still reads the code itself, so accuracy does not depend on your summary. Run
   independent delegations in parallel.
7. Summarize what each subagent updated (per the plan in step 3), and note
   anything skipped and why.

This command **only writes/syncs artifacts** — it does not review code. For a
Clean Architecture / Clean Code review of your change, run `/review-all` (or
`/sync-review` to sync *and* review + validate in one pass). Keeping the review
out of `/sync` means `architecture-reviewer` runs exactly once, in the review
stage, not twice.

A pure refactor with no API/schema/behavior change may still need javadoc-writer,
logging-instrumenter, or test-author — but don't invoke a subagent whose output
wouldn't change. javadoc-writer and logging-instrumenter apply to almost any Java
change; spec-keeper and user-docs-writer only when contracts/docs move.
