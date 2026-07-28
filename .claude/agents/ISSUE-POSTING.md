# Sync-agent issue posting (shared contract)

Every post-TDD sync agent records the result of its own work as a comment on the
slice's GitHub issue, so a human can confirm at a glance that the agent actually
ran and what it did. This is **automated per-stage feedback** — post it WITHOUT
asking for confirmation (this is the explicit exception to `.claude/reviews/POSTING.md`,
which requires confirmation only for human-authored summary comments).

## When
At the very end of your run, after your work is done and (if you edit code) the
affected module compiles. One comment per agent per slice.

## How
Resolve the issue number: the branch is `<issue#>-<slug>`, so `git branch --show-current`
→ take the leading number; or it's passed in the prompt. Then:

```bash
gh issue comment <n> --body "<body>"
```

If no issue number can be resolved, skip posting and say so in your summary — do
not guess a number.

## Format (hard limit: ≤ 15 lines / ≤ 1200 characters — keep it scannable)

```
### 🤖 <agent-name>
<one line: what you did on this slice>
- <key point 1>
- <key point 2 — up to ~4 bullets, only the ones that matter>
<Files: N · Build: ✅ | Tests: X/Y · or the agent's own one-line verdict>
```

Rules:
- **No wall of text.** Bullets, not paragraphs. If you're over the limit, cut
  detail — the full detail lives in your normal end-of-run summary to the caller,
  not in the issue comment.
- State the outcome plainly: what changed, or "no changes needed — <why>".
- Use the identifiers/paths that matter (file count, key file, test counts, verdict).
- Don't repeat the whole diff or paste code. One comment = proof you ran + the gist.

## Idempotence
If a comment from you for this slice already exists (re-run), post a short
"re-ran — <what changed since>" rather than a duplicate full report.
