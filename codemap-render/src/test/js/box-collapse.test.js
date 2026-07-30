"use strict";

/**
 * Collapsing a box to its header (spec 007 §2.2).
 *
 * A domain type with twenty accessors buries the structure around it once the
 * reader has seen it. Collapsing keeps the class on the canvas — its identity
 * and its relationships — but gives up its rows.
 *
 * Run with: node src/test/js/box-collapse.test.js
 */

const assert = require("assert");
const { loadReportScriptWithJoinableD3 } = require("./report-test-harness");

const CONTROLLER = "com.example.TaskController";
const SERVICE = "com.example.TaskService";

function fixture() {
  const cls = (id, simpleName, layer) => ({
    id, moduleId: "m", fqn: id, simpleName, packageName: "com.example", kind: "CLASS",
    layer, file: simpleName + ".java", lineStart: 1, lineEnd: 40, javadoc: null,
    status: "UNCHANGED", source: "class " + simpleName + " {}"
  });
  const method = (id, classId, name, signature, visibility) => ({
    id, classId, name, signature, file: "x.java", lineStart: 2, lineEnd: 4,
    javadoc: null, source: "", constructor: false, visibility: visibility || "PUBLIC", status: "UNCHANGED"
  });

  return {
    modules: [{ id: "m", name: "m", path: "." }],
    entryPoints: [{ id: "ep", moduleId: "m", kind: "REST", label: "PUT /t", methodId: CONTROLLER + "#update()", detectedBy: "RULE" }],
    classes: [cls(CONTROLLER, "TaskController", "ENTRY"), cls(SERVICE, "TaskService", "APPLICATION")],
    methods: [
      method(CONTROLLER + "#update()", CONTROLLER, "update", "update() : void"),
      method(SERVICE + "#save()", SERVICE, "save", "save() : void"),
      method(SERVICE + "#name()", SERVICE, "name", "name() : String"),
      method(SERVICE + "#id()", SERVICE, "id", "id() : long")
    ],
    edges: [{ from: CONTROLLER + "#update()", to: SERVICE + "#save()", kind: "CALL_EXTERNAL", resolved: true, line: 3 }],
    moduleDependencies: [], removedMethods: []
  };
}

function boxRows(view, classId) {
  const position = view.lastLayout.boxPositions.get(classId);
  const compartments = position.compartments;
  return [...compartments.constructors, ...compartments.publicMethods, ...compartments.revealedPrivateMethods];
}

function run() {
  testCollapsingDropsTheRowsButKeepsTheBox();
  testCollapsedBoxShrinksToItsHeader();
  testLinksSurviveByAttachingToTheHeader();
  testExpandingAgainRestoresTheRows();
  testReopeningTheSamePillKeepsCollapsedBoxes();
  testSwitchingEntryPointsForgetsCollapsedBoxes();

  console.log("box-collapse.test.js: all assertions passed");
}

function openedView() {
  const { view } = loadReportScriptWithJoinableD3(fixture());
  view.openEntryPointPill("ep");
  view.expandMethodRow(CONTROLLER + "#update()", CONTROLLER, view.expanderPathFor(CONTROLLER));
  view.render();
  return view;
}

function testCollapsingDropsTheRowsButKeepsTheBox() {
  const view = openedView();
  assert.ok(boxRows(view, SERVICE).length > 0, "precondition: the service box has rows");

  view.toggleBoxCollapsed(SERVICE);

  assert.ok(view.lastLayout.boxPositions.has(SERVICE), "the box itself must stay on the canvas");
  assert.strictEqual(boxRows(view, SERVICE).length, 0, "a collapsed box shows no member rows");
  assert.ok(boxRows(view, CONTROLLER).length > 0, "collapsing one box must not touch another");
}

function testCollapsedBoxShrinksToItsHeader() {
  const view = openedView();
  const before = view.lastLayout.boxPositions.get(SERVICE).rect.height;

  view.toggleBoxCollapsed(SERVICE);
  const after = view.lastLayout.boxPositions.get(SERVICE).rect.height;

  assert.ok(after < before, `a collapsed box must be shorter (${after} vs ${before})`);
}

/** The relationships are the reason the class is on the canvas at all. */
function testLinksSurviveByAttachingToTheHeader() {
  const view = openedView();
  const before = view.renderableLinks().map((link) => view.resolveEndpoints(link)).filter(Boolean).length;
  assert.ok(before > 0, "precondition: something links into the service");

  view.toggleBoxCollapsed(SERVICE);
  const after = view.renderableLinks().map((link) => view.resolveEndpoints(link)).filter(Boolean).length;

  assert.strictEqual(after, before, "collapsing a box must not silently drop the links that reach it");
}

function testExpandingAgainRestoresTheRows() {
  const view = openedView();
  const before = boxRows(view, SERVICE).length;

  view.toggleBoxCollapsed(SERVICE);
  view.toggleBoxCollapsed(SERVICE);

  assert.strictEqual(boxRows(view, SERVICE).length, before, "re-opening a box brings its rows back");
}

/** Re-clicking the pill you are already on must not throw your work away. */
function testReopeningTheSamePillKeepsCollapsedBoxes() {
  const view = openedView();
  view.toggleBoxCollapsed(SERVICE);

  view.openEntryPointPill("ep");

  assert.ok(view.collapsedClassIds.has(SERVICE),
      "clicking the pill already open must leave the canvas as the reader arranged it");
}

/** Moving to a different entry point does start clean. */
function testSwitchingEntryPointsForgetsCollapsedBoxes() {
  const data = fixture();
  data.entryPoints.push({
    id: "ep2", moduleId: "m", kind: "REST", label: "GET /t",
    methodId: SERVICE + "#name()", detectedBy: "RULE"
  });
  const { view } = loadReportScriptWithJoinableD3(data);
  view.openEntryPointPill("ep");
  view.expandMethodRow(CONTROLLER + "#update()", CONTROLLER, view.expanderPathFor(CONTROLLER));
  view.render();
  view.toggleBoxCollapsed(SERVICE);

  view.openEntryPointPill("ep2");

  assert.strictEqual(view.collapsedClassIds.size, 0,
      "a different entry point starts from a clean canvas, collapsed state included");
}

run();
