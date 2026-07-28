---
description: End-to-end review pipeline — run all reviews, validate which findings are real, then apply the valid fixes. Orchestrates review-all → validate-findings → fix-findings.
---

Run the full review-and-fix pipeline over a change, in order, stopping at the
right points for human input. This is the one-shot command that ties together
`/review-all`, `/validate-findings`, and `/fix-findings`.

`$ARGUMENTS` (all optional):
- **scope** — passed to `/review-all` (empty = current branch diff; or a git
  range / paths).
- **`--all`** — passed through to `/fix-findings` (apply every finding, not just
  VALID). Off by default.
- **`--dry-run`** — run review + validation only, stop before fixing, and show
  what *would* be fixed.

## Step 1 — Review (gather)

Run **`/review-all`** with the scope. It runs `/code-review`,
`/security-review`, and the `architecture-reviewer` subagent over the same
change and writes one merged findings document to `.claude/reviews/`. Capture
the document path — every later step operates on it.

If `/review-all` reports nothing to review, stop here.

## Step 2 — Validate (judge)

Run **`/validate-findings <path>`** on that document. The `finding-validator`
subagent reads the real code behind each finding and marks it VALID / INVALID /
NEEDS-HUMAN, rewriting the document in place with a validation summary.

**Gate:** if any findings are **NEEDS-HUMAN**, stop and surface them — ask the
user to resolve each (VALID or INVALID) before fixing. Do not proceed with
unresolved NEEDS-HUMAN findings.

If `--dry-run` was passed, stop here and report the validation summary (what
would be fixed vs skipped) without touching code.

## Step 3 — Fix (apply)

Run **`/fix-findings <path>`** (add `--all` only if it was passed to this
command). It applies the selected findings (VALID-only by default, must-fix
first), builds/tests the affected code, and records each finding's outcome in
the document.

## Step 3.5 — Post the review to the issue

After validation (and fixes), post a **"Code review"** comment to the linked
issue following `.claude/reviews/POSTING.md` (summary + collapsed full findings
doc, ask before posting). This is the first of the three history comments
(review → fix → acceptance).

## Step 4 — Final report

Summarize the whole cycle end-to-end:

- **Reviewed:** scope + how many findings each source produced
- **Validated:** VALID (must-fix / suggestion) vs INVALID vs NEEDS-HUMAN
- **Fixed:** fixed / skipped / needs-manual, files touched, build+test result
- **Document:** the findings file (the audit trail of the run)

This command stops at the review→validate→fix loop; it does **not** verify
acceptance criteria. Run **`/verify-coverage`** next to prove the change meets
its acceptance criteria (or let `/ship` / `/sync-review` run it as their next
stage). Do not commit or push — leave that to the user or `/create-pr`.
