---
description: Full AI-SDLC pipeline for one issue — implement (TDD) → sync → review → validate → fix → verify acceptance → open PR → review the PR. Orchestrates the whole slice from issue number to reviewed pull request.
---

Run the **entire** Codemap delivery pipeline for a single issue, end to end, with
human gates at the points that need a decision. This is the top-level
orchestrator: it chains the stage commands rather than re-implementing them, so
each stage stays independently runnable.

`$ARGUMENTS`:
- **issue** (required) — number (`31` / `#31`) to deliver.
- **`--dry-run`** — run through verification but stop before opening the PR.
- **`--from <stage>`** — resume at a stage (`implement` | `review` | `verify` |
  `pr`), e.g. after fixing a gap manually.

The pipeline, in order. After each stage, report a one-line status; stop at any
**gate** that needs the user.

## Stage 1 — Implement (`/implement <issue>`)
TDD implementation on a feature branch + artifact sync (spec/docs/logging/
Javadoc/tests). **Gate:** if `tdd-implementer` reports an acceptance criterion it
couldn't satisfy, or the build is red, stop and resolve with the user before
continuing.

## Stage 2 — Review + validate + fix (`/review-cycle`)
Runs `/review-all` (code-review, security-review, architecture-reviewer) →
`/validate-findings` → `/fix-findings` (VALID only) over the change.
**Gate:** if validation flags **NEEDS-HUMAN** findings, stop and let the user
resolve them before fixes are applied.

## Stage 3 — Verify acceptance (`/verify-coverage <issue|spec>`)
Proves every acceptance criterion is covered by a passing test and writes the
evidence report (coverage matrix + collapsible per-criterion test/output).
**Gate:** if there are **gaps** or **failing** tests, stop — recommend
`test-author` to close gaps, then resume with `--from verify`. Do not ship with
unmet acceptance criteria.

If `--dry-run`, stop here and report: implemented, reviewed, fixed, verified —
with the two report paths. Nothing is pushed.

## Stage 3.5 — SDLC integrity gate (`/sdlc-check`)
Verify the pipeline was not shortcut before the work is frozen into a PR: that
every sync subagent the slice actually needed (`spec-keeper`, `user-docs-writer`,
`test-author`, `javadoc-writer`, `logging-instrumenter`) ran and left real
evidence on this diff. **Gate:** if any REQUIRED agent is missing or its artifact
doesn't reflect the change, `/sdlc-check` **blocks**, runs the missing agents over
the slice diff, records what each did, and re-gates to PASS (re-building green).
Do not proceed to the PR until the gate is PASS. This is the check that stops a
slice shipping with, e.g., a new endpoint but no user docs, or new use-case logic
but no logging.

## Stage 4 — Open the PR (`/create-pr <issue>`)
Opens a PR from the feature branch to `main`, with a diff-based description,
correct labels, and `Closes #<issue>`. **Gate:** commit/push are outward-facing —
`/create-pr` shows what will happen and asks before pushing (don't bypass that).

## Stage 5 — Review the PR (`/review <PR#>`)
Once the PR exists, run a review pass over it as a final check. Surface its
findings to the user; if it turns up must-fix issues, they can loop back through
`/fix-findings` (or a `--from review` resume) before merge.

## Final report

A single summary of the whole run:
- **Issue / branch / PR** links
- **Implemented:** cycles, files, build result
- **Reviewed:** findings by source; validated VALID/INVALID/NEEDS-HUMAN; fixed
- **Verified:** X/Y acceptance criteria green; gaps if any
- **SDLC gate:** which sync agents were required, which ran during `/implement`,
  which `/sdlc-check` had to run, and one line on what each did
- **Artifacts:** the findings doc, the acceptance evidence doc, the PR URL

Never merge automatically — merging to `main`/`main` stays a human action.
Codemap's rule: commit/push/PR only when the user asks; this pipeline asks at the
push and PR gates rather than acting silently.
