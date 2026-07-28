---
description: Discuss a raw idea with the user and shape it into a specification file under doc/specs/
---

Treat `$ARGUMENTS` as a raw **idea**, not a finished spec. This command's job is
to **discuss the idea with the user**, shape it together, and write the result
as a specification file under `doc/specs/`. It does **not** create a GitHub issue
— that is `/create-issue`'s job, and it reads the file this command produces.

If `$ARGUMENTS` is empty, ask the user what idea they want to work through.

## Ground rules

- **Discuss before you write.** The conversation (Step 1) is the main event. Only
  write the file once the user is satisfied the idea is fully shaped.
- Ground every opinion in the real codebase and `doc/` — read the relevant area
  before asking, so questions are concrete, not generic.
- One spec = one concern. If the idea is really two things, say so and suggest
  splitting into two specs (SRP applies to specs too).
- Do not run `gh` or create any issue here.

## Step 1 — Discuss the idea (the main event)

Engage as a collaborator, not a form-filler. Work through, as needed:

- **What problem does this solve, and for whom?** Restate the idea in your own
  words so the user can correct your understanding.
- **Scope** — what's in, what's explicitly out. Push back on scope creep.
- **How it fits Codemap** — which module(s), which pipeline stage; does it respect
  the module boundaries and the four settled decisions in `.claude/CLAUDE.md`?
  Re-read the relevant `doc/specification.md` sections. Flag conflicts
  with settled decisions rather than quietly designing around them.
- **Open questions / trade-offs** — surface the real decisions (API shape, schema
  impact, edge cases, error codes) and give a recommendation, not just a menu.
- **Acceptance criteria** — converge on what "done" verifiably looks like,
  folding in the project's Definition of Done where relevant (migrations apply on
  a fresh DB, happy path end-to-end, tests cover edge cases).

Ask focused questions (a few at a time), incorporate answers, keep refining.
Iterate until the idea is concrete and the user signals they're satisfied. Do
**not** write the file while questions are still open.

## Step 2 — Write the specification file

Pick a short kebab-case slug for the idea (e.g. `schedule-api`) and write
`doc/specs/<slug>.md` (create the `doc/specs/` directory if it doesn't exist).
Use this structure:

```markdown
# <Title>

## Problem
<what and why, for whom>

## Scope
**In:** <bullets>
**Out:** <explicitly excluded bullets>

## Design
<how it fits Codemap: module(s), pipeline stage, CLI/config surface, index impact,
error codes, key decisions made during discussion and the rationale>

## Acceptance criteria
- [ ] <verifiable outcome>
- [ ] <verifiable outcome>

## Notes
<links to doc/ sections, related specs/issues, open items deliberately deferred>

## Issue metadata (suggested)
- **Type:** <bug|feature|chore|docs|devops|setup>
- **Module(s):** <module:x ...>
- **Priority:** <priority:high|medium|low>
- **Milestone:** <roadmap area, or "unset">
```

The **Issue metadata** block is a hand-off hint for `/create-issue` — fill it in
from the discussion, but `/create-issue` re-validates it against the live repo
labels/milestones before using it.

## Step 3 — Report

Tell the user the file path you wrote and give a one-line summary. Remind them
they can now run `/create-issue doc/specs/<slug>.md` to turn it into a GitHub
issue.
