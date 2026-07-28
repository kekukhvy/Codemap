# SDLC sync-agent run markers

One file per slice — `<branch-slug>.md` — recording which sync subagents ran (or
were deliberately skipped) during that slice. `/implement` step 4 appends to it as
it runs the agents; `/sdlc-check` (the pre-PR gate, `/ship` Stage 3.5) reads it and
cross-checks each line against the actual diff before allowing a PR.

## Line format

```
- <agent-name> | <ISO-date> | ran: <one-line what it did> | files: <n>
- <agent-name> | <ISO-date> | skipped: <why this diff doesn't need it>
```

The five sync agents tracked here: `spec-keeper`, `user-docs-writer`,
`test-author`, `javadoc-writer`, `logging-instrumenter`.

A `skipped:` line is only honest when the diff genuinely doesn't trigger the agent
(see the trigger table in `.claude/agents/sdlc-gate.md`). The gate re-derives the
requirement from the diff, so a false skip is caught and blocks the PR.
