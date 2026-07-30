"use strict";

/**
 * The expand/collapse lifecycle (spec 007 §4.3, §6.2).
 *
 * These cover the cases a reader hits by clicking around rather than by
 * expanding once: collapsing a row that had things expanded *beneath* it,
 * re-expanding after a collapse, clicking the same expander twice, and
 * switching to a different entry point. Each one previously left the canvas
 * holding boxes no further click could remove.
 *
 * Run with: node src/test/js/expansion-lifecycle.test.js
 */

const assert = require("assert");
const { loadReportScriptWithJoinableD3 } = require("./report-test-harness");

/**
 * Alpha.entry()  -> Beta.run() -> Gamma.deep()
 * Alpha.second() -> Beta.run()          (a second route to the same box)
 * plus Alpha.entry() -> Alpha.helper() (private, reveal-only)
 */
function fixture() {
  const cls = (id, layer) => ({
    id, moduleId: "m", fqn: id, simpleName: id, packageName: "p", kind: "CLASS",
    layer, file: id + ".java", lineStart: 1, lineEnd: 9, javadoc: null,
    status: "UNCHANGED", source: "class " + id + " {}"
  });
  const method = (id, classId, name, visibility, extra) => Object.assign({
    id, classId, name, signature: name + "()", file: classId + ".java",
    lineStart: 2, lineEnd: 4, javadoc: null, source: "void " + name + "(){}",
    constructor: false, visibility, status: "UNCHANGED"
  }, extra || {});

  return {
    modules: [{ id: "m", name: "m", path: "." }],
    entryPoints: [
      { id: "ep1", moduleId: "m", kind: "REST", label: "PUT /a", methodId: "Alpha#entry()", detectedBy: "RULE" },
      { id: "ep2", moduleId: "m", kind: "REST", label: "PUT /d", methodId: "Delta#other()", detectedBy: "RULE" }
    ],
    classes: [cls("Alpha", "CONTROLLER"), cls("Beta", "SERVICE"), cls("Gamma", "INFRASTRUCTURE"), cls("Delta", "CONTROLLER")],
    methods: [
      method("Alpha#entry()", "Alpha", "entry", "PUBLIC"),
      method("Alpha#second()", "Alpha", "second", "PUBLIC"),
      method("Alpha#helper()", "Alpha", "helper", "PRIVATE"),
      method("Beta#run()", "Beta", "run", "PUBLIC"),
      method("Gamma#deep()", "Gamma", "deep", "PUBLIC"),
      method("Delta#other()", "Delta", "other", "PUBLIC")
    ],
    edges: [
      { from: "Alpha#entry()", to: "Beta#run()", kind: "CALL_EXTERNAL", resolved: true, line: 3 },
      { from: "Alpha#entry()", to: "Alpha#helper()", kind: "CALL_INTERNAL", resolved: true, line: 4 },
      { from: "Beta#run()", to: "Gamma#deep()", kind: "CALL_EXTERNAL", resolved: true, line: 3 },
      { from: "Alpha#second()", to: "Beta#run()", kind: "CALL_EXTERNAL", resolved: true, line: 6 }
    ],
    moduleDependencies: [], removedMethods: []
  };
}

function newView() {
  return loadReportScriptWithJoinableD3(fixture());
}

function run() {
  testNestedCollapseRetiresTheWholeSubtree();
  testReExpandThenCollapseStillRemovesTheBox();
  testSwitchingEntryPointsClearsTheCanvas();
  testPrivateRowUnrevealsWhenItsCallerCollapses();
  testUnderlinesAreDroppedOnCollapse();
  testNestedRowsAreForgottenWhenTheirParentCollapses();
  testExpanderPathIsDeterministicAndDurable();

  console.log("expansion-lifecycle.test.js: all assertions passed");
}

/** Collapsing a row must retire whatever was expanded *through* it. */
function testNestedCollapseRetiresTheWholeSubtree() {
  const { view } = newView();

  view.openEntryPointPill("ep1");
  const alphaPath = view.expanderPathFor("Alpha");
  view.expandMethodRow("Alpha#entry()", "Alpha", alphaPath);
  const betaPath = view.expanderPathFor("Beta");
  view.expandMethodRow("Beta#run()", "Beta", betaPath);

  assert.ok(view.controller.diagram.boxes.has("Gamma"), "Gamma should be drawn after the nested expand");

  view.collapseMethodRow("Alpha#entry()");

  assert.strictEqual(view.controller.diagram.boxes.has("Beta"), false,
      "Beta was revealed by the collapsed row and must go");
  assert.strictEqual(view.controller.diagram.boxes.has("Gamma"), false,
      "Gamma was revealed *through* the collapsed row and must go too — a hierarchical path must unwind");
}

/** Expand -> collapse -> expand -> collapse must still empty the canvas. */
function testReExpandThenCollapseStillRemovesTheBox() {
  const { view } = newView();

  view.openEntryPointPill("ep1");
  const path = view.expanderPathFor("Alpha");
  view.expandMethodRow("Alpha#entry()", "Alpha", path);
  view.collapseMethodRow("Alpha#entry()");
  view.expandMethodRow("Alpha#entry()", "Alpha", view.expanderPathFor("Alpha"));
  view.collapseMethodRow("Alpha#entry()");

  assert.strictEqual(view.controller.diagram.boxes.has("Beta"), false,
      "re-expanding then collapsing must not leave an unremovable box");
  assert.strictEqual(view.expandedMethodRows.has("Alpha#entry()"), false,
      "the row must not still be marked expanded");
}

/** Opening another entry point starts from a clean canvas. */
function testSwitchingEntryPointsClearsTheCanvas() {
  const { view } = newView();

  view.openEntryPointPill("ep1");
  view.expandMethodRow("Alpha#entry()", "Alpha", view.expanderPathFor("Alpha"));
  assert.ok(view.controller.diagram.boxes.size >= 2, "precondition: several boxes are open");

  view.openEntryPointPill("ep2");

  assert.deepStrictEqual([...view.controller.diagram.boxes.keys()], ["Delta"],
      "only the new entry point's class may remain — stale boxes get no column and would vanish silently");
  assert.strictEqual(view.expandedMethodRows.size, 0, "expansions from the previous pill must be forgotten");
}

/** §2.3: the private row disappears once its last visible caller collapses. */
function testPrivateRowUnrevealsWhenItsCallerCollapses() {
  const { view } = newView();

  view.openEntryPointPill("ep1");
  view.expandMethodRow("Alpha#entry()", "Alpha", view.expanderPathFor("Alpha"));

  const alpha = view.controller.diagram.boxFor("Alpha");
  assert.ok(alpha.revealedPrivateMethodIds.has("Alpha#helper()"),
      "precondition: the private row is revealed by its caller");

  view.collapseMethodRow("Alpha#entry()");

  const stillOpen = view.controller.diagram.boxFor("Alpha");
  assert.ok(stillOpen, "Alpha itself stays — the entry point still reaches it");
  assert.strictEqual(stillOpen.revealedPrivateMethodIds.has("Alpha#helper()"), false,
      "the private row must un-reveal once its only caller is collapsed");
}

/** A row must not stay underlined once the link that targeted it is gone. */
function testUnderlinesAreDroppedOnCollapse() {
  const { view } = newView();

  view.openEntryPointPill("ep1");
  view.expandMethodRow("Alpha#entry()", "Alpha", view.expanderPathFor("Alpha"));
  assert.ok(view.underlinedMethodIds.has("Beta#run()"), "precondition: the link target is underlined");

  view.collapseMethodRow("Alpha#entry()");

  assert.strictEqual(view.underlinedMethodIds.has("Beta#run()"), false,
      "no link points at it any more, so the underline must go");
  assert.ok(view.underlinedMethodIds.has("Alpha#entry()"),
      "the entry method's own underline is owned by the pill and must survive");
}

/** The diagram retires nested paths; the view's ledger must agree. */
function testNestedRowsAreForgottenWhenTheirParentCollapses() {
  const { view } = newView();
  view.openEntryPointPill("ep1");
  view.expandMethodRow("Alpha#entry()", "Alpha", view.expanderPathFor("Alpha"));
  view.render();
  view.expandMethodRow("Beta#run()", "Beta", view.expanderPathFor("Beta"));
  assert.ok(view.expandedMethodRows.has("Beta#run()"), "precondition: the nested row is expanded");

  view.collapseMethodRow("Alpha#entry()");

  assert.strictEqual(view.expandedMethodRows.has("Beta#run()"), false,
      "a row expanded through the collapsed one must not stay marked expanded");
}

/**
 * A box reached by several routes must hand out a stable expander path.
 * Taking whichever set member happened to be inserted first meant an expansion
 * could be scoped under a path that an unrelated collapse then retired,
 * leaving the row marked expanded with none of its links.
 */
function testExpanderPathIsDeterministicAndDurable() {
  const { view } = newView();
  view.openEntryPointPill("ep1");
  const box = view.controller.diagram.boxFor("Alpha");

  // Reached three ways, inserted longest-first.
  box.revealingPaths.clear();
  box.revealingPaths.add("ep1>a>b>c");
  box.revealingPaths.add("ep1>z");
  box.revealingPaths.add("ep1>a");

  const first = view.expanderPathFor("Alpha");
  const again = view.expanderPathFor("Alpha");

  assert.strictEqual(first, again, "the same state must always yield the same path");
  assert.strictEqual(first, "ep1>a",
      "the shortest path wins: it sits closest to the entry point and survives the most collapses");
}

run();
