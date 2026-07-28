---
description: Create a well-formed GitHub issue for Codemap from a specification file, with the right labels and milestone
---

Turn a specification into a GitHub issue in `kekukhvy/Codemap`, with all metadata
(labels, milestone) set. `$ARGUMENTS` is expected to be a path to a spec file
produced by `/specification` (e.g. `doc/specs/schedule-api.md`). Use the `gh` CLI
to create the issue.

The discussion already happened in `/specification` — **do not re-open it**. This
command reads the finished spec, classifies it, and creates the issue.

## Ground rules

- **The spec is the input.** Read it and build the issue from it. Only ask the
  user something if the spec is missing or genuinely ambiguous for
  classification — don't re-litigate the design.
- Never invent a label or milestone. Only use ones that actually exist in the
  repo (fetched in Step 2). If unsure a label fits, leave it off rather than guess.
- The repo is `kekukhvy/Codemap`. Do not create issues anywhere else.

## Step 1 — Read the specification

Resolve `$ARGUMENTS`:

- If it's a path to an existing file (typically under `doc/specs/`), read it.
- If it's empty, look in `doc/specs/` and ask the user which spec to use (list
  the candidates). If there are none, tell the user to run `/specification` first.
- If it's free-form text rather than a path, treat it as an inline spec, but note
  to the user that running `/specification` first produces a better issue.

The spec's **Issue metadata (suggested)** block, if present, is a hand-off hint —
use it as a starting point but still validate it in Steps 2–3 against the live
repo. Everything else in the spec feeds the issue title and body.

## Step 2 — Refresh the metadata (don't trust a stale list)

Labels and milestones change over time. Fetch the current sets first:

```bash
gh label list --limit 100
gh api repos/kekukhvy/Codemap/milestones --jq '.[] | "\(.number)\t\(.title)\t(\(.state))"'
```

For reference, the label taxonomy at time of writing is:

- **Type** (pick exactly one): `bug`, `feature`, `chore`, `setup`, `devops`, `docs`
- **Module** (pick the one(s) the change touches): `module:api`, `module:engine`,
  `module:adapters`, `module:admin`, `module:worker`, `module:sdk`
- **Priority** (pick exactly one): `priority:high`, `priority:medium`, `priority:low`

Milestones map to roadmap areas: `Scheduling Core`, `Delivery Adapters`,
`Admin UI`, `SDK`, `Observability`, `Multi-tenancy`.

## Step 3 — Classify the issue

Using the spec (its metadata block and content) plus repo context, decide:

1. **Type** — is it a bug, a new feature, a chore, docs, devops, or setup? Exactly one.
2. **Module(s)** — which Gradle module(s) does it touch? Match the code area:
   - model / parser / call graph / detection / diff → `module:core`
   - `index.json` → `report.html` rendering → `module:render`
   - CLI arguments, config loading → `module:cli`
   - optional AI classifier → `module:ai`
   - Add more than one only if the work genuinely spans modules.
   Optionally add an `area:` label (`area:parsing`, `area:callgraph`,
   `area:entrypoints`, `area:git`, `area:ui`, `area:performance`).
3. **Priority** — default to `priority:medium` unless the description signals
   otherwise (a broken production path / data-loss risk → `priority:high`;
   nice-to-have / cosmetic → `priority:low`).
4. **Milestone** — map the work to the roadmap area (e.g. an admin-UI feature →
   `Admin UI`; a new Kafka adapter behavior → `Delivery Adapters`). Pick the
   single best-fit open milestone. If nothing fits, leave the milestone unset
   and say so.

If a classification is still ambiguous from the spec, ask one focused question
rather than guessing.

## Step 4 — Write the issue body

Turn the spec into the issue body. Keep the title short and imperative (e.g.
"Add webhook delivery adapter"). Map the spec's sections onto the issue body —
Problem/Design → Summary + Context, the spec's Acceptance criteria carry over
directly. Structure the body with these sections (omit a section only if truly
N/A):

```markdown
## Summary
<one or two sentences on what and why>

## Context
<relevant background: which module, what exists today, why this is needed>

## Acceptance criteria
- [ ] <verifiable outcome 1>
- [ ] <verifiable outcome 2>

## Notes
<optional: constraints, links to doc/ sections, related issues>
<when built from a spec file, link it, e.g. "Spec: doc/specs/schedule-api.md">
```

Respect the project's Definition of Done where relevant (migrations apply on a
fresh DB, happy path works end-to-end, tests cover edge cases) — fold it into
the acceptance criteria when the work warrants it.

## Step 5 — Confirm, then create

Show the user the final title, body, labels, and milestone. Create the issue only
after they approve. Pass each label with its own `--label` flag and the milestone
by title:

```bash
gh issue create \
  --repo kekukhvy/Codemap \
  --title "<title>" \
  --body "<body>" \
  --label "<type>" --label "module:<x>" --label "priority:<y>" \
  --milestone "<Milestone Title>"
```

Report back the created issue URL. If `gh issue create` fails (e.g. a label was
renamed), read the error, correct the offending flag against the live lists from
Step 2, and retry — do not silently drop metadata.
