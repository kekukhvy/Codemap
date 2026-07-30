# Review cycle — PR #16 (UML class diagram)

Scope: `git diff 23740df..HEAD` — 25 commits, 78 files, +12020/-130.
Sources: correctness (bug hunt), security, architecture, plus a browser pass
driving the real report in Chrome.

Every finding below was reproduced before being fixed, and every fix has a test
that fails against the previous behaviour.

## Fixed

| # | Severity | Finding | Fix |
|---|---|---|---|
| 1 | **HIGH** | `ClassSourceReader` read files reached through a **symlink**. `normalize()` is lexical and does not follow links, so a repository containing `Config.java` as a link to a private file had that file embedded in the report. No index tampering needed. | `toRealPath()` before the containment check. Tests for symlinked file and symlinked directory. |
| 2 | **HIGH** | `edgeViaReceiverType` attached edges to the **wrong method**, stamped `resolved: true`. `getDeclaredMethods()` excludes inherited methods, so a class inheriting `go(String)` and declaring `go(int,int)` looked unambiguous and a 1-arg call was recorded against the 2-arg method. | Considers inherited methods, matches arity, refuses a match declared in a supertype. Removed 59 fabricated edges on Kairos (1831 → 1772). |
| 3 | **HIGH** | Collapsing a row **stranded its nested rows** in the view's ledger. Re-opening the outer row resurrected an expansion nobody requested; the stranded row's next click ran collapse instead of expand. | `forgetRowsBeneath` retires the ledger in step with the diagram's paths. |
| 4 | **HIGH** | `expanderPathFor` returned an arbitrary `Set` member, so collapsing an unrelated sibling could retire the path another expansion was scoped under, leaving a row marked expanded with no links. | Shortest path wins (closest to the entry point, survives the most collapses), lexical tie-break. |
| 5 | MEDIUM | Dragging several boxes onto one spot left them **permanently stacked** — 136 overlapping pairs — because dragged boxes were exempt from separation. | Only the first dragged box is immovable; attempt cap 12 → 40. Pathological case now 0 overlaps. |
| 6 | LOW | `gh`'s branch name reached `git merge-base` without `--`. Not exploitable (merge-base rejects the dangerous options, GitHub will not mint such a name) but the value is attacker-influenced. | Added the `--` separator. |
| 7 | must-fix (arch) | `PullRequestBase` sat in `codemap-core` with no caller there; `GitCommandRunner.runTool` generalised a git-named class to arbitrary binaries for one caller. | Moved to `codemap-cli`; `GitCommandRunner` is git-only again. |
| 8 | suggestion | Dead field `hasFittedView`. | Removed. |

## Open — recorded, not fixed

**MEDIUM — links overlap each other.** AC11's first half holds: measured over
the rendered DOM across all 25 entry points, with boxes collapsed and dragged,
**0 segments cross a box**. The second half does not: collinear overlap between
links totals ~403,000px, with 64 overlaps longer than 40px on a single entry
point and the longest at 188px. Confirmed visually — links between the handler
column and the next run as a dense bundle that is hard to follow.

Self-links were given the reservation set they had been routed without, which
was a genuine omission but moved the number by under a percent, so they were
not the cause. The real source is the corridor grid: links converge on the same
candidate lines between columns, and the shared-segment penalty only fires on
whole grid edges, not on partial collinear runs. Fixing it properly means
penalising *partial* overlap during the search, which is a routing change worth
doing on its own rather than at the end of a review cycle.

The existing AC11 test hashes bit-identical endpoint pairs into a `Set`, so a
70px overlap between segments with different endpoints never registers, and its
fixture is 3 boxes. **That test overstates what it proves** and should be
rewritten against interval overlap.

**MEDIUM — render cost grows with canvas size.** At 47 boxes / ~100 links a
single click takes 200-260ms (five measurements: 259, 211, 210, 192, 205). At
the sizes a reader reaches by hand — 10-16 boxes — it is 1-6ms. Every link is
re-routed from scratch on every render; caching routes whose endpoints did not
move would fix it.

## Clean — investigated, no defect

- **`tokenizeJava`**: lossless round-trip on all 1009 real sources plus edge
  cases; no catastrophic backtracking (9 pathological inputs to 500k chars, all
  ≤31ms); token `kind` provably closed under 200k fuzz cases, so `"tok-" + kind`
  cannot be influenced by source text.
- **XSS surface**: no dangerous sink in `report.js` — the four `innerHTML` uses
  all assign `""`, there is no d3 `.html()`. Escaping is character-level and
  correctly wired; the 1.8MB report contains exactly 3 `</script` occurrences,
  all genuine closers. CSP intact, no external references.
- **`PullRequestBase`**: no injection possible — PR number is an `int`,
  `ProcessBuilder` takes a list with no shell. Streams drained before `waitFor`,
  so no pipe deadlock.
- Box uniqueness (AC9), refcounted collapse (AC10), entry-point-switch cleanup,
  reachable-status caching: all hold on real data.

## A note on measurement

Three defects earlier in this session shipped because the *measurement* was
wrong, not because the code was unread: a stress test that generated only
forward links, a crossing check that re-routed instead of reading the DOM, and
a Node harness that reported a control as clickable when a real browser could
not click it. This cycle was therefore run with instructions to prefer running
over reading — which is how findings 2, 3 and 4 were caught, all of which pass
the existing test suite.

One test written during this cycle was deleted rather than kept: its fixture
gave the box a single path, so it would have passed with or without the fix.
A test that cannot fail is worse than no test.
