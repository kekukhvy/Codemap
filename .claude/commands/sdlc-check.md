---
description: Pre-PR SDLC gate — verify every sync subagent the slice needed (spec-keeper, user-docs-writer, test-author, javadoc-writer, logging-instrumenter) actually ran, run the missing ones, and record a short "what each agent did" report. Blocks the PR until the pipeline is whole.
---

Run the **SDLC integrity gate** before a PR is opened: confirm the AI-SDLC pipeline
was not shortcut. `/implement` step 4 is supposed to run the applicable sync
subagents once per slice, but they are invoked by hand — so one is easily skipped
(a new endpoint shipped with no `user-docs-writer`, new use-case logic with no
`logging-instrumenter`). This command is the check that catches it, **blocks**, and
closes the gap before the work is frozen into a PR.

`$ARGUMENTS` (optional):
- an issue number / branch to scope the slice (defaults to the current branch)
- `--report-only` — run the gate and report, but do **not** run the missing agents
  or block (surface the gaps and let the user decide)

This is **`/ship` Stage 3.5** — it runs after `/verify-coverage` and before
`/create-pr`. It can also be run standalone anytime you want to prove the pipeline
is whole.

## Step 1 — Run the gate (delegate)

Invoke the **sdlc-gate** subagent via the Task tool. Pass it the slice (branch/issue)
and remind it: read-only, decide which of the five sync agents the **diff** requires,
cross-check both signals (the `.claude/sdlc/<slice>.md` run markers **and** whether the
diff shows each agent actually had an effect), and return a **Verdict** (PASS/BLOCK)
with the per-agent table + a "Missing / suspect" list + a "what each ran agent did"
summary.

Wait for its report. Surface the table and the verdict to the user.

## Step 2 — Gate decision

- **PASS** → the pipeline is whole. Print the "what each agent did" summary (this is
  the report the pipeline owes the user) and hand back to `/ship` to proceed to
  `/create-pr`. Done.
- **BLOCK** and `--report-only` → stop, show the gaps, and tell the user which agents
  to run (or to re-run `/sdlc-check` without `--report-only` to auto-close them). Do
  not open the PR.
- **BLOCK** (default) → **close the gaps**, per Step 3.

## Step 3 — Run the missing agents (only on BLOCK, default mode)

For each REQUIRED agent the gate marked ❌ (missing or suspect), run it over the
**slice diff** — the same way `/implement` step 4 would have, handing it the concrete
changed files and the diff so it doesn't start cold and rediscover the slice:

- **spec-keeper** — update `doc/` (specification / database / plan) for the domain,
  API, schema, or lifecycle change.
- **user-docs-writer** — update `README.md` for the CLI flag / config / output change.
- **test-author** — backfill the edge-case tests the TDD cycles missed.
- **javadoc-writer** — Javadoc the new public/protected types & members.
- **logging-instrumenter** — SLF4J logging (INFO on state changes, WARN on rejections,
  ERROR on failures, DEBUG for detail) across the new **non-`domain`** code — not just
  one method: the whole slice diff outside `domain`.

Small, mechanical gaps you may close **inline** (you already hold the diff) rather than
spawning a cold subagent — the same cost rule as `/implement` step 4 (≤3 files / ~50
lines → do it yourself). Match the project's existing logging/doc/test conventions.

After running each, **append a marker line** to `.claude/sdlc/<slice>.md`:

```
- <agent-name> | <ISO-date> | <one-line what it did> | files: <n>
```

(Create the file and the `.claude/sdlc/` directory if absent.)

## Step 4 — Re-gate and build

Re-invoke **sdlc-gate** to confirm the gaps are closed → it must now return **PASS**.
Then run the build (`./gradlew build`, or the affected modules) to prove the newly
added logging/docs/tests compile and pass. If red, fix and re-run; do not hand back a
red build.

## Step 5 — Report

Print the final gate summary the user asked for:

- **Verdict:** PASS (pipeline whole)
- **Per-agent:** which of the five were required, which already ran, which this
  command had to run now, and **one line on what each did** on this slice
- **Build:** green
- Point back to `/ship` (Stage 4, `/create-pr`) or tell the user they can open the PR.

Never open the PR, commit, or push here — that stays with `/create-pr` and the user.
This command only guarantees the pipeline is whole before that gate.
