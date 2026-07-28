---
description: Apply the fixes from a validated review-findings document — by default only findings marked VALID — then update each finding's outcome
---

Apply the code changes described in a findings document. By default this fixes
**only findings marked `Verdict: VALID`** by `/validate-findings` — INVALID
findings are skipped, NEEDS-HUMAN findings are left for the user.

`$ARGUMENTS`:
- **path** (optional) — the findings document. If empty, use the most recent
  file in `.claude/reviews/`.
- **`--all`** (optional) — apply every finding regardless of verdict (use only
  when the document was not validated, or the user explicitly wants everything).

## Step 1 — Load and check the document

```bash
ls -t .claude/reviews/*.md 2>/dev/null | head -5
```

Resolve the path. Read the document. Then:

- If it has **no verdicts** (never validated) and `--all` was **not** passed,
  stop and tell the user to run `/validate-findings <path>` first — we don't
  apply raw, unvalidated findings by default. (With `--all`, proceed anyway.)
- If it has `NEEDS-HUMAN` findings still unresolved, list them and ask the user
  to resolve them (mark VALID or INVALID in the doc) before continuing. Do not
  guess.

## Step 2 — Select the findings to fix

- Default: every finding with `Verdict: VALID`.
- `--all`: every finding in the document.

Order them **must-fix before suggestion**, and group by file so related edits
land together. If the selected set is empty, report "nothing to fix" and stop.

## Step 3 — Apply the fixes

For each selected finding, make the change the finding describes, honoring
`.claude/GUIDELINES.md` and `.claude/CLAUDE.md` (no framework imports in
`codemap-core`, methods ≤40 lines, no inline literals, respect module boundaries).
Prefer the smallest edit that resolves the finding.

- If applying a fix reveals the finding is wrong or no longer applicable, **skip
  it** and note why — do not force a bad change.
- Do not fix INVALID findings (unless `--all`).
- If the fixes change behavior the artifacts document (spec, user docs, Javadoc,
  logging, tests), delegate those follow-ups **once at the end** — not per edit.

## Step 4 — Verify

- Build / run the affected tests to confirm the fixes hold:
  `./gradlew build` (or the narrowest module/test that covers the change).
- If a fix breaks the build or tests, fix-forward if it's small and obvious;
  otherwise revert that finding's change and mark it as needing manual work.

## Step 5 — Post the fixes to the issue

Post a **"Fixes applied"** comment to the linked issue following
`.claude/reviews/POSTING.md` (summary on top, full outcome list in a collapsed
`<details>`, ask before posting). This continues the issue history after the
"Code review" comment.

## Step 6 — Record outcomes and report

Update the findings document: set each processed finding's `Outcome:` to
**fixed** | **skipped** (with reason) | **needs-manual**. Then report:

- how many findings were fixed, skipped, left for manual work
- the files touched
- the build/test result
- remaining NEEDS-HUMAN or INVALID items that were intentionally not addressed

Never commit or push automatically — leave that to the user (or `/create-pr`).
