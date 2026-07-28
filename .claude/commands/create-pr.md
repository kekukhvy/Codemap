---
description: Open a well-formed GitHub pull request for Codemap — description from the diff, the right labels, and a linked issue to auto-close on merge
---

Open a pull request in `kekukhvy/Codemap` from the current branch, with a full
description, the correct labels, and a `Closes #<n>` link so the originating
issue closes automatically on merge. `$ARGUMENTS` is optional: an issue number
(`18` or `#18`) to close, and/or a spec path (e.g. `doc/specs/schedule-api.md`)
to enrich the description. If empty, everything is inferred from the branch,
commits, and diff.

This is the counterpart to `/create-issue`: that command turns a spec into an
issue; this one turns the work on a branch into a PR that closes it.

## Ground rules

- **The diff is the source of truth.** Build the description from what actually
  changed on this branch, not from memory or from what was planned.
- The repo is `kekukhvy/Codemap`, base branch is **`main`** (per the git
  workflow: feature → PR → merge to `main`).
- Never open a PR from `main` itself — it is protected. If the current branch is
  `main`, stop and tell the user to create a feature branch first.
- Never invent a label. Only use labels that exist in the repo (fetched in
  Step 3). If unsure a label fits, leave it off rather than guess.
- Commit and push are outward-facing actions: only run them after showing the
  user what will happen and getting approval (see Step 6).

## Step 1 — Establish the branch state

```bash
git branch --show-current
git status --short
git log --oneline origin/main..HEAD      # commits unique to this branch
```

- If the branch is `main`, stop (see Ground rules).
- If `origin/main` is missing, fall back to `git log --oneline <base>..HEAD`
  using the resolved base from Step 2.
- Note whether there are uncommitted changes — they must be committed (Step 6)
  before the PR reflects them. Do not commit silently.

## Step 2 — Resolve the base branch

Default to `main`. Confirm it exists on the remote:

```bash
git ls-remote --heads origin main
```

If `main` does not exist, use the repo's default branch
(`gh repo view --json defaultBranchRef --jq '.defaultBranchRef.name'`) and tell
the user you did so. Honor an explicit base if the user passed one in
`$ARGUMENTS` (e.g. `base=main`).

## Step 3 — Refresh labels (don't trust a stale list)

```bash
gh label list --limit 100
```

Same taxonomy as `/create-issue` — pick from what actually exists:

- **Type** (exactly one): `bug`, `feature`, `chore`, `setup`, `devops`, `docs`
- **Module** (the one(s) the diff touches): `module:api`, `module:engine`,
  `module:adapters`, `module:admin`, `module:worker`, `module:sdk`
- **Priority** (exactly one): `priority:high`, `priority:medium`, `priority:low`

Classify from the **diff**, not guesses:

- Which Gradle module(s) do the changed paths live in? (`codemap-core/…` →
  `module:core`, `codemap-render/…` → `module:render`, etc.) Add more than one
  only if the change genuinely spans modules.
- Type: new capability → `feature`; fix → `bug`; docs-only → `docs`; build/CI →
  `devops`/`chore`. Exactly one.
- Priority: default `priority:medium` unless the work signals otherwise.
- If a linked issue (Step 4) or a spec's metadata block names labels, reuse
  them — but still validate against the live list.

## Step 4 — Find the issue to close

Determine the issue number to put in `Closes #<n>`:

1. If `$ARGUMENTS` contains a number (`18` or `#18`), use it.
2. Else parse it from the **branch name** — Codemap branches are typically
   `<issue-number>-<slug>` (e.g. `18-add-schedule-crud-api-…` → `18`). Take the
   leading number if present.
3. Else scan the branch's commit messages / any referenced spec's **Issue
   metadata** for an issue reference.

Verify the candidate is real and open before relying on it:

```bash
gh issue view <n> --repo kekukhvy/Codemap --json number,title,state,labels
```

Show the user the issue you intend to close (number + title) and get a yes
before wiring `Closes #<n>`. If no issue can be found, say so and open the PR
without a `Closes` line rather than guessing a number. Reuse the issue's labels
as a starting point for Step 3.

## Step 5 — Write the PR body

Build the description from the diff and commits. Keep the title short and
imperative; if the branch maps to one clear change, mirror the issue title.
Structure the body with these sections (omit one only if truly N/A):

```markdown
## Summary
<one or two sentences: what this PR does and why>

## Changes
- <notable change 1 — grouped by layer/module, from the diff>
- <notable change 2>

## Testing
<what was run and the result, e.g. "./gradlew build — 528 tests, 0 failures">
<call out migrations applying on a fresh DB / end-to-end happy path when relevant>

## Notes
<optional: follow-ups, deferred scope, links to doc/ sections>
<link the spec when there is one, e.g. "Spec: doc/specs/schedule-api.md">

Closes #<n>
```

The `Closes #<n>` line (only when Step 4 found and confirmed an issue) makes
GitHub close the issue when the PR merges into the default branch. Fold the
project's Definition of Done into the Testing section where it applies
(migrations apply cleanly, happy path works, edge cases covered).

## Step 6 — Confirm, push, create

Show the user the final **title, body, labels, base branch, and the issue to
close** in one preview. Create only after they approve.

1. If there are uncommitted changes or unpushed commits, commit (branch first if
   somehow on `main`) and push. Follow the commit-message convention
   and end commit messages with the required `Co-Authored-By:` trailer. Push the
   branch and set upstream:

   ```bash
   git push -u origin <current-branch>
   ```

2. Create the PR (ready, not draft), one `--label` flag per label:

   ```bash
   gh pr create \
     --repo kekukhvy/Codemap \
     --base main \
     --title "<title>" \
     --body "<body>" \
     --label "<type>" --label "module:<x>" --label "priority:<y>"
   ```

   End the PR body with the required Claude Code attribution line.

Report the created PR URL. If `gh pr create` fails (e.g. a renamed label, or the
branch isn't pushed), read the error, correct the offending flag or push the
branch, and retry — do not silently drop labels or the `Closes` link.
