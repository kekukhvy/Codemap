---
description: Triage a review-findings document with the finding-validator subagent — mark each finding VALID / INVALID / NEEDS-HUMAN so only real issues get fixed
---

Take a findings document produced by `/review-all` and decide which findings are
genuinely worth fixing. The reviews over-report on purpose; this step is the
quality gate that separates real defects from false positives, out-of-scope
noise, and taste-only nits.

`$ARGUMENTS` — path to the findings document. If empty, use the most recent file
in `.claude/reviews/` (newest by name/date). If none exists, tell the user to
run `/review-all` first and stop.

## Step 1 — Locate the document

```bash
ls -t .claude/reviews/*.md 2>/dev/null | head -5
```

Resolve the target path from `$ARGUMENTS` or the newest file. Confirm it exists
and contains findings (F1, F2, …). If it has none, report that and stop.

## Step 2 — Delegate to the validator

Invoke the **finding-validator** subagent via the Task tool, passing the
document path. It will, for each finding: read the actual code at the cited
`file:line`, check callers/helpers/docs, and assign a verdict —

- **VALID** — real defect or a genuine GUIDELINES.md rule violation (with
  severity must-fix / suggestion)
- **INVALID** — false positive, already handled, out of scope, contradicts a
  settled decision in `doc/`, or an unbacked taste nit (with the reason)
- **NEEDS-HUMAN** — genuinely uncertain; carries the specific question

The validator **rewrites the same document in place**, adding a `Verdict:`,
`Reason:`, and (for VALID) `Severity:` to each finding, plus a **Validation
summary** section at the top.

## Step 3 — Report

Surface the validator's summary: counts (VALID must-fix / VALID suggestion /
INVALID / NEEDS-HUMAN), the list of VALID must-fix items, and any NEEDS-HUMAN
questions the user must answer.

If there are NEEDS-HUMAN findings, ask the user to resolve them before fixing.
Then point them at `/fix-findings <path>` (which applies **only VALID** findings
by default), or `/review-cycle` to continue the full flow.

Do **not** edit source code in this command — validation only annotates the
findings document.
