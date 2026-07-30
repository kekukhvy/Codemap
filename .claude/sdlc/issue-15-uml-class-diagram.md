# SDLC run record — issue #15 (UML-style class diagram)

Branch `feature/15-uml-class-diagram`, slice `23740df..HEAD`.

| Agent | Required by diff? | Ran | What it did |
|---|---|---|---|
| **tdd-implementer** | yes | ✅ ×2 | Part 1: `Visibility` in core + parser, `ClassSourceReader`, `ClassView.source`. Part 2: the class-box diagram, `DiagramState`/`DiagramController`, column layout, lane routing. |
| **spec-keeper** | yes — index format + report design changed | ✅ | Rewrote §3.1 (tree → diagram), §6.4 (`visibility` field), §6.6, §7, §9. Its incorrect `schemaVersion` 2→3 bump and a column-0 off-by-one were corrected by hand (`068a418`). |
| **user-docs-writer** | yes — user-visible output replaced | ✅ | Rewrote README "The diagram", class boxes, expanders, routing, uniqueness, side panel, change highlighting. Nine invented details corrected by hand (`068a418`). |
| **test-author** | yes | ✅ | Closed the two acceptance-evidence gaps: `MethodVisibilitiesTest` degrade branch, plus CHANGED/AFFECTED full-render cases. |
| **javadoc-writer** | no | ➖ | New public types (`Visibility`, `MethodVisibilities`, `ClassSourceReader`) were authored with Javadoc; verified present. Nothing left undocumented. |
| **logging-instrumenter** | marginal | ➖ (done by hand) | `MethodVisibilities` already logged its degrade path. `ClassSourceReader` silently swallowed failures against project convention (`SourceFileScanner:65`); a WARN was added by hand. |
| **architecture-reviewer** | yes | ✅ | 0 must-fix, 2 suggestions — both applied (named `ROW_LABEL_BASELINE_OFFSET`, moved `expandedMethodRows` into the constructor, dropping four defensive guards). |
| **security review** | yes — new untrusted payload + new file read | ✅ | Found HIGH path traversal in `ClassSourceReader` (verified reading `/etc/hosts`) and a MEDIUM degrade-invariant break. Both fixed (`92da6df`) with tests. |
| **acceptance-verifier** | yes | ✅ | 14/14 criteria green; two precision gaps recorded, both since closed (`cb454bd`). |

## Not done

- **Genuine browser click-through.** The Chrome extension was not connected in this
  session, so `file://` interaction was not driven by a real browser. Interaction is
  covered by Node-harness tests that invoke the real event handlers and build real SVG
  structure, plus a headless load with no console errors — but that is not a click
  session. Worth one manual pass before merge.
