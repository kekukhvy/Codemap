# Codemap AI SDLC — commands & agents

This directory automates the Codemap delivery lifecycle: from a raw idea to a
reviewed pull request. **Commands** (`commands/*.md`, invoked as `/name`) are the
orchestration layer you drive; **agents** (`agents/*.md`, invoked via the Task
tool) are the specialists commands delegate to. Every stage honors
[GUIDELINES.md](./GUIDELINES.md) and [CLAUDE.md](./CLAUDE.md).

## The pipeline

```
idea ─▶ /specification ─▶ doc/specs/*.md
spec ─▶ /create-issue   ─▶ GitHub issue
issue▶ /implement       ─▶ TDD code + sync (spec/docs/log/javadoc/tests) on a branch
      ▶ /review-cycle    ─▶ review-all ▶ validate-findings ▶ fix-findings
      ▶ /verify-coverage ─▶ acceptance evidence (tests run, PASS/FAIL matrix)  ← separate stage
      ▶ /sdlc-check      ─▶ pre-PR gate: every needed sync agent ran (else run + block)
      ▶ /create-pr       ─▶ pull request (Closes #issue)
PR   ─▶ /review          ─▶ review the pull request

           ╰──────────  /ship  runs implement ▶ … ▶ review as one pass ──────────╯
```

Each stage is independently runnable — `/ship` just chains them with human gates.

## Commands

| Command | Stage | What it does |
|---|---|---|
| `/specification` | Ideate | Shapes a raw idea into a spec file under `doc/specs/`. |
| `/create-issue` | Plan | Turns a spec into a well-formed GitHub issue (labels, milestone). |
| `/implement <issue>` | **Build** | TDD implementation (delegates **tdd-implementer**) on a feature branch, then syncs spec/docs/logging/Javadoc/tests. |
| `/sync` | Build | For code the **user** wrote by hand: shows a plan, then writes tests/docs/spec/logging/Javadoc. Not needed after `/implement` (it already synced). |
| `/sync-review <scope>` | **Build→Review** | For hand-written code: plan → `/sync` → `/verify-coverage` → `/review-all` → `/validate-findings`. **Stops before fixes** (you fix, or run `/fix-findings`). |
| `/review-all [scope]` | Review | Runs `/code-review`, `/security-review`, **architecture-reviewer** over one scope → merged findings doc in `reviews/`. |
| `/validate-findings [doc]` | Review | **finding-validator** marks each finding VALID / INVALID / NEEDS-HUMAN. |
| `/fix-findings [doc]` | Review | Applies **VALID** findings only (`--all` for everything); records outcomes. |
| `/review-cycle [scope]` | Review | Orchestrates review-all ▶ validate ▶ fix. (Verify is a separate stage, not inside — avoids running it twice.) |
| `/verify-coverage [spec\|issue]` | **Verify** | **acceptance-verifier** maps each acceptance criterion to a test, runs it, writes an evidence report. |
| `/sdlc-check [issue]` | **Gate** | **sdlc-gate** verifies every sync agent the diff needed (spec/docs/test/javadoc/logging) actually ran; runs the missing ones and blocks until the pipeline is whole. Runs pre-PR (`/ship` Stage 3.5). |
| `/create-pr <issue>` | Ship | Opens a PR to `main` with a diff-based description and `Closes #<issue>`. |
| `/review <PR#>` | Ship | Reviews an existing GitHub pull request. |
| `/ship <issue>` | **All** | Full pipeline: implement ▶ review-cycle ▶ verify ▶ create-pr ▶ review, with gates. |

### Easily confused — read this once

- **`/review`** = review an existing **GitHub PR**. **`/code-review`** (built-in)
  = review your **local diff**. **`/review-cycle`** = the whole local
  review+fix+verify pipeline. **`/review-all`** = just the gather step inside it.
- **`/implement`** builds *and* syncs; **`/sync`** only syncs (for hand-written
  code). Don't run `/sync` right after `/implement` — it already synced.
- **`/sync`** vs **`/sync-review`**: both are for code you wrote by hand. `/sync`
  = just write the missing artifacts. `/sync-review` = that **plus** verify +
  full review + validation, stopping before fixes. Use `/sync-review` when you
  want your manual change taken all the way to a reviewed, validated state.

## Agents

**Build**
- **tdd-implementer** — writes production code *and* tests test-first
  (red→green→refactor). Owns both, unlike test-author.

**Sync** (keep artifacts aligned with code; run once per slice by `/implement`
step 4, or by `/sync` for hand-written code — never per file edit)
- **spec-keeper** — updates `doc/specification.md` on model/index/detection/architecture changes.
- **user-docs-writer** — updates `README.md` on CLI flag / config / output changes.
- **test-author** — adds unit + fixture tests for **existing** code.
- **javadoc-writer** — Javadoc for public/protected types & members lacking it.
- **logging-instrumenter** — SLF4J logging at right levels.

**Review & verify** (read-only on product code)
- **architecture-reviewer** — Clean Architecture / Clean Code findings.
- **finding-validator** — judges findings VALID/INVALID/NEEDS-HUMAN.
- **acceptance-verifier** — proves acceptance criteria are covered + passing,
  with real test output as evidence.
- **sdlc-gate** — pre-PR integrity gate: verifies every sync agent the diff
  needed actually ran (markers + diff cross-check); PASS/BLOCK verdict.

## Artifacts (audit trail)

Review and verification runs write to `reviews/`:
- `reviews/<date>-<branch>.md` — merged + validated findings (review-cycle).
- `reviews/<date>-<branch>-acceptance.md` — acceptance evidence (verify-coverage).

Sync-agent run markers live in `sdlc/`:
- `sdlc/<branch-slug>.md` — one line per sync-agent run (or deliberate skip),
  appended by `/implement` step 4 and `/sdlc-check`; the pre-PR gate reads it.

## Conventions for new commands/agents

- One responsibility per agent (SRP); say explicitly what it does **not** do and
  which sibling owns that.
- Commands orchestrate and gate; agents do the specialized work. A command
  chains other commands/agents rather than re-implementing them.
- Read-only reviewers never edit code — they report; a separate fixer applies.
- Outward-facing actions (commit, push, PR) always ask before acting.
- Keep the pipeline table above in sync when you add a stage.
