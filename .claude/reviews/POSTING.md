# Posting pipeline results to the GitHub issue (shared step)

Several commands (`/review-cycle`, `/fix-findings`, `/verify-coverage`,
`/sync-review`) post their result to the linked GitHub issue as a **comment**,
so the issue becomes the full history: review → fix → acceptance evidence, in
chronological order.

Any command that posts follows this shared procedure.

## 1. Resolve the issue number

- From the branch name if it starts with a number (`24-add-…` → issue `#24`).
- Else from an issue passed in `$ARGUMENTS`.
- Else `gh pr view --json ...` if a PR exists and links an issue.
- If none can be resolved, **skip posting** and say so — do not guess an issue.

Confirm it exists and is the right one:
`gh issue view <n> --json number,title,state`.

## 2. Build the comment — summary on top, full doc collapsed

```markdown
## <Stage> — <branch> — <date>

<one-paragraph summary: the counts / verdict / what happened>

- <key line 1 — e.g. must-fix: 0; VALID 2 / INVALID 1>
- <key line 2 — e.g. build+tests: green>

<details>
<summary>Full <stage> report</summary>

<the full findings / acceptance / fix document, verbatim>

</details>

<sub>Posted by Kairos AI SDLC · <command> · commit <short-sha></sub>
```

Stage label per command:
- `/review-cycle` (or `/review-all`+`/validate-findings`) → **"Code review"**
- `/fix-findings` → **"Fixes applied"**
- `/verify-coverage` → **"Acceptance evidence"**

Keep the same three stages in this order so the issue reads review → fix →
acceptance. Each run appends a new comment (history), never edits a prior one.

## 3. Confirm before posting (outward-facing)

**Exception — automated sync-agent result comments post WITHOUT confirmation.**
The post-TDD sync agents (test-author, acceptance-verifier, architecture-reviewer,
logging-instrumenter, javadoc-writer, spec-keeper, user-docs-writer) each record
their own short result to the issue automatically (see
`.claude/agents/ISSUE-POSTING.md`, ≤15 lines each) — that per-agent proof-of-run is
the whole point, so it is not gated on a prompt. This exception covers **only**
those bounded, agent-authored result comments. Everything else below still applies.

Posting to a public issue is outward-facing. For **human-authored summary
comments** (a review write-up, a rewrite of the issue body, an acceptance summary
you compose): **show the exact comment body and the target issue, and ask for
confirmation before running `gh issue comment`.** Do not post those silently. On
approval:

```bash
gh issue comment <n> --body-file <path-to-comment-md>
```

Write the comment body to a scratch file first (avoids shell-escaping the
markdown), then post from the file. Report the comment URL `gh` returns.

If the user declines, keep the report in `.claude/reviews/` and move on — the
local audit trail still exists.

## 4. Tick the acceptance-criteria checkboxes in the issue BODY (verify stage)

Comments are the *history*; the issue **body** is the live status. When
`/verify-coverage` runs, also update the issue body's `## Acceptance criteria`
checkboxes so the issue reflects reality at a glance:

- `gh issue view <n> --json body -q .body` → edit → `gh issue edit <n> --body-file <file>`.
- Tick `- [ ]` → `- [x]` **only** for criteria proven by a passing test / build /
  inspection in the acceptance evidence. Add a short `<sub>(verified by …)</sub>`
  note naming the test.
- For a criterion that is a genuine **UI-mock / environment limit** (can't be
  unit-covered), say so in the note rather than ticking it falsely.

### When the code has diverged from the criteria (spec↔code reconciliation)

If verification shows the shipped behaviour no longer matches an acceptance
criterion (the design changed, or a criterion was dropped), **do not leave a
stale/invalid criterion in the issue.** After the user confirms the code is the
source of truth (the same call `spec-keeper` acts on), rewrite the issue body so
every criterion is valid and describes what actually shipped — remove or replace
the obsolete ones, and add a short "Design note (updated <date>)" explaining the
move. An issue must never keep acceptance criteria that the code deliberately
doesn't satisfy. Editing the issue body is outward-facing — show the rewrite and
confirm before `gh issue edit`.
