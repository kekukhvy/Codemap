---
description: Run every review (code-review, security-review, architecture-reviewer) over the same change and merge all findings into one document under .claude/reviews/
---

Run all of Codemap's reviews over the **same** set of changes and collect every
finding into a single, structured document. This command only **gathers and
records** findings — it does not judge them (that's `/validate-findings`) and
does not fix anything (that's `/fix-findings`).

`$ARGUMENTS` is the review scope, optional:
- empty → the current branch diff vs its base (uncommitted + committed changes)
- a git range / branch / `HEAD~n` → review that range
- a path or paths → scope the reviews to those files
Resolve it once and pass the **same** scope to all three reviews so they see
identical code.

## Step 1 — Establish scope

```bash
git branch --show-current
git status --short
git diff --stat            # or the range from $ARGUMENTS
```

If there are no changes and `$ARGUMENTS` gives no explicit range/paths, report
"nothing to review" and stop.

## Step 2 — Run the three reviews over that scope

Run them independently (they don't depend on each other):

1. **`/code-review`** — correctness bugs + reuse/simplification/efficiency.
   Run at **high** effort by default (`/code-review high`) for broad coverage,
   since a validator downstream filters false positives. Do **not** pass
   `--fix` or `--comment` — we only want findings, collected here.
2. **`/security-review`** — security review of the same change.
3. **`architecture-reviewer`** subagent (via the Task tool) — Clean
   Architecture / Clean Code findings, passing the same scope as context.

Capture the full findings from each — every finding with `file`, `line`,
`summary`, the concrete cost, and the proposed fix. Do not drop or pre-filter
anything here; over-collection is intended.

## Step 3 — Write the merged findings document

Create `.claude/reviews/` if it doesn't exist. Write the document to:

```
.claude/reviews/<YYYY-MM-DD>-<branch>.md
```

(slugify the branch; if a file for today already exists, append `-2`, `-3`, …
rather than overwriting a prior run).

Structure it so the validator and fixer can process it mechanically:

```markdown
# Review findings — <branch> — <date>

- Scope: <range/paths reviewed>
- Sources run: code-review (<effort>), security-review, architecture-reviewer

## Findings

### F1 — <one-line summary>
- Source: code-review | security-review | architecture-reviewer
- File: <path>:<line>
- Severity (as reported): must-fix | suggestion
- Detail: <what's wrong / the concrete cost>
- Proposed fix: <the reviewer's suggested change>
- Verdict: _(pending validation)_

### F2 — …
```

Number findings sequentially (F1, F2, …) across all sources so later steps can
reference them by id. Keep each finding self-contained.

## Step 4 — Report

Print the document path and a short summary: how many findings from each source,
and the total. Remind the user that findings are **unvalidated** — run
`/validate-findings <path>` next (or `/review-cycle` to do the whole flow).

Do **not** edit any source code in this command.
