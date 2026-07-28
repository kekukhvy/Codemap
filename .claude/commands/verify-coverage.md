---
description: Prove a change meets its acceptance criteria — map each criterion to a test, run the tests for real, and write an evidence report under .claude/reviews/
---

Produce **evidence** that the current change satisfies its acceptance criteria:
every criterion mapped to a concrete test, those tests actually executed, and
the real pass/fail output recorded. Use this before saying a slice is done, or
after `/fix-findings`, when you need proof rather than a claim.

`$ARGUMENTS` — the scope, optional:
- a spec path under `doc/specs/*.md` (the source of `## Acceptance criteria`)
- an issue number (`#31`) whose body holds the criteria
- empty → infer from the current branch (match branch slug → spec in
  `doc/specs/`, else the linked issue)

## Step 1 — Locate the acceptance criteria

```bash
git branch --show-current
ls doc/specs/*.md
```

Resolve the spec/issue from `$ARGUMENTS` or the branch. If no acceptance
criteria can be found, report that and stop — don't invent them.

## Step 2 — Delegate to the acceptance-verifier

Invoke the **acceptance-verifier** subagent via the Task tool, passing the
resolved spec path (or issue). It will:

1. Extract and number each acceptance criterion (AC1, AC2, …).
2. Map each to the specific test(s) that assert it (or the build/inspection gate
   that proves it), and flag any criterion with **no** covering test.
3. **Actually run** the mapped tests and build gates (`./gradlew … test`,
   `./gradlew … build`), capturing real pass/fail output as evidence.
4. Write an evidence report to
   `.claude/reviews/<date>-<branch>-acceptance.md` with a coverage matrix (one
   row per criterion: criterion → evidence → ran? → PASS/FAIL/GAP), an evidence
   log of the real test output, and a gaps list.

Integration/repository tests run on in-process H2 (`H2DatabaseBase`) — no Docker
needed, so they always run; a repo IT is never "not-run (environment)".

## Step 3 — Post the acceptance evidence to the issue

Post an **"Acceptance evidence"** comment to the linked issue following
`.claude/reviews/POSTING.md` (verdict `X/Y` + the coverage matrix on top, full
evidence report in a collapsed `<details>`, ask before posting). This is the
third and final history comment (review → fix → acceptance).

## Step 4 — Report

Surface the verifier's verdict: `X/Y criteria verified with passing tests`, the
report path, and — importantly — any **gaps** (criteria with no test, or with a
failing test) and any **not-run** items.

If there are gaps, recommend running **`test-author`** to add the missing tests,
then re-running `/verify-coverage` to confirm they close the gaps and pass. This
command does not write tests or edit code — it only verifies and produces the
evidence report.
