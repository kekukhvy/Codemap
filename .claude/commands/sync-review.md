---
description: For code you wrote by hand — plan first, then write tests/docs/spec/logging/Javadoc, verify acceptance, run every review, and validate the findings. Stops before applying fixes (you fix, or ask for /fix-findings).
---

You changed code yourself (in your IDE, outside Claude), so no agent produced
tests, docs, or a review. This command runs the **whole tail** of the pipeline
over your hand-written change — everything `/ship` does *after* implementation,
**except it never edits your production code to "fix" review findings**. It
takes you up to a validated findings list and stops. You decide what to fix
(yourself, or by running `/fix-findings` after).

`$ARGUMENTS` — optional scope: paths to limit to, and/or a spec/issue for the
acceptance step. Empty = the current working-tree + branch changes.

## Step 0 — Show the plan first (no silent work)

Inspect the change and **print the plan before doing anything**, so you see
exactly what will happen:

```bash
git status
git diff            # unstaged
git diff --staged   # staged
```

Then print a plan like:

```
Plan for <scope>:
 Sync (writes artifacts):
  - spec-keeper           ✔/✖  — <reason>
  - user-docs-writer      ✔/✖  — <reason>
  - test-author           ✔/✖  — <reason>
  - javadoc-writer        ✔/✖  — <reason>
  - logging-instrumenter  ✔/✖  — <reason>
 Verify:
  - verify-coverage       ✔/✖  — <spec/issue found? else skip>
 Review (read-only, no code edits):
  - review-all (code-review, security-review, architecture-reviewer)
  - validate-findings
 NOT run: fix-findings — you apply fixes, or run /fix-findings yourself.
```

If there are no changes, report "nothing to sync/review" and stop.

## Step 1 — Sync artifacts (`/sync`)

Run **`/sync`** over the scope: it writes the tests, docs, spec updates,
logging, and Javadoc that your hand-written code needs (delegating spec-keeper,
user-docs-writer, test-author, javadoc-writer, logging-instrumenter). This is
the "write all the tests, docs, specifications" part. `/sync` does **not**
review code — the architecture review happens once in Step 3 (`/review-all`),
not here, so it isn't run twice.

## Step 2 — Verify acceptance (`/verify-coverage`) — if applicable

If a spec (`doc/specs/*.md`) or issue matches this change, run
**`/verify-coverage`** to prove each acceptance criterion is covered by a passing
test, with the evidence report. If no spec/issue exists, skip this step and say
so (nothing to verify against).

## Step 3 — Review + validate (`/review-all` → `/validate-findings`)

Run **`/review-all`** over the same scope (code-review + security-review +
architecture-reviewer → merged findings doc in `reviews/`), then
**`/validate-findings`** on that doc so each finding is marked VALID / INVALID /
NEEDS-HUMAN by the finding-validator.

**Stop here.** Do **not** run `/fix-findings`.

The `/verify-coverage` and `/review-all`+`/validate-findings` sub-steps post
their result to the linked issue (per `.claude/reviews/POSTING.md`, with
confirmation) — so the issue records the acceptance evidence and the review
even though this command stops before fixing.

## Step 4 — Report and hand back control

Summarize:
- **Synced:** which subagents ran and what they wrote (from `/sync`).
- **Verified:** X/Y acceptance criteria green (or "no spec to verify against").
- **Findings:** counts VALID (must-fix / suggestion) / INVALID / NEEDS-HUMAN,
  the list of VALID must-fix items, and the findings-doc path.
- **Next:** *you* apply fixes — either by hand, or run
  `/fix-findings <doc>` (VALID only) when you're ready. Any NEEDS-HUMAN findings
  need your decision first.

Never edit production code to fix findings in this command, and never commit or
push.
