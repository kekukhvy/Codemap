"use strict";

/**
 * End-to-end assertions on {@code DiagramView.render()} against a real (if
 * minimal) joinable D3 stub (spec 007 §2, §4, §6): unlike the other
 * `DiagramView` tests, which exercise the model through a no-op D3 stub, this
 * proves the SVG actually built — box compartments, the `«stereotype»` and
 * status glyph in the header, underlined target rows, and every routed link —
 * matches what the pure geometry/model functions promise in isolation.
 *
 * Run with: node src/test/js/diagram-render-integration.test.js
 */

const assert = require("assert");
const { loadReportScriptWithJoinableD3 } = require("./report-test-harness");

const MODULE_ID = "kairos-api";
const CONTROLLER_CLASS = "com.example.TaskController";
const REPOSITORY_CLASS = "com.example.TaskRepository";
const METHOD_UPDATE = "com.example.TaskController#update()";
const METHOD_VALIDATE = "com.example.TaskController#validate()";
const METHOD_SAVE = "com.example.TaskRepository#save()";
const METHOD_FIND = "com.example.TaskRepository#find()";
const METHOD_ARCHIVE = "com.example.TaskController#archive()";

function fixture() {
  return {
    modules: [{ id: MODULE_ID, name: "kairos-api", path: MODULE_ID }],
    entryPoints: [
      { id: "entry-1", moduleId: MODULE_ID, kind: "REST", label: "PUT /tasks/{id}", methodId: METHOD_UPDATE, detectedBy: "RULE" }
    ],
    classes: [
      { id: CONTROLLER_CLASS, moduleId: MODULE_ID, fqn: CONTROLLER_CLASS, simpleName: "TaskController",
        packageName: "com.example", kind: "CLASS", layer: "ENTRY", file: "TaskController.java",
        lineStart: 1, lineEnd: 40, javadoc: null, status: null, source: "class TaskController { }" },
      { id: REPOSITORY_CLASS, moduleId: MODULE_ID, fqn: REPOSITORY_CLASS, simpleName: "TaskRepository",
        packageName: "com.example", kind: "CLASS", layer: "INFRASTRUCTURE", file: "TaskRepository.java",
        lineStart: 1, lineEnd: 20, javadoc: null, status: null, source: "class TaskRepository { }" }
    ],
    methods: [
      { id: METHOD_UPDATE, classId: CONTROLLER_CLASS, name: "update", signature: "update()", file: "TaskController.java",
        lineStart: 10, lineEnd: 16, javadoc: null,
        source: "void update() { validate(); repository.save(); repository.find(); }",
        constructor: false, visibility: "PUBLIC", status: null },
      { id: METHOD_VALIDATE, classId: CONTROLLER_CLASS, name: "validate", signature: "validate()", file: "TaskController.java",
        lineStart: 18, lineEnd: 20, javadoc: null, source: "void validate() { }",
        constructor: false, visibility: "PRIVATE", status: null },
      { id: METHOD_SAVE, classId: REPOSITORY_CLASS, name: "save", signature: "save()", file: "TaskRepository.java",
        lineStart: 5, lineEnd: 7, javadoc: null, source: "void save() { }",
        constructor: false, visibility: "PUBLIC", status: null },
      { id: METHOD_FIND, classId: REPOSITORY_CLASS, name: "find", signature: "find()", file: "TaskRepository.java",
        lineStart: 9, lineEnd: 11, javadoc: null, source: "void find() { }",
        constructor: false, visibility: "PUBLIC", status: null }
    ],
    edges: [
      { from: METHOD_UPDATE, to: METHOD_VALIDATE, kind: "CALL_INTERNAL", resolved: true, line: 11 },
      { from: METHOD_UPDATE, to: METHOD_SAVE, kind: "CALL_EXTERNAL", resolved: true, line: 12 },
      { from: METHOD_UPDATE, to: METHOD_FIND, kind: "CALL_EXTERNAL", resolved: true, line: 13 }
    ],
    moduleDependencies: [],
    removedMethods: []
  };
}

function classBoxes(view) {
  return view.viewport.nodes[0].children.filter(
      (node) => node.tag === "g" && (node.getAttribute("class") || "").includes("class-box"));
}

function links(view) {
  return view.viewport.nodes[0].children.filter((node) => node.tag === "path");
}

function segmentsOf(pathD) {
  const points = pathD.split(" ").map((token) => {
    const [x, y] = token.slice(1).split(",").map(Number);
    return { x, y };
  });
  const segments = [];
  for (let i = 1; i < points.length; i++) {
    segments.push([points[i - 1], points[i]]);
  }
  return segments;
}

function run() {
  const data = fixture();
  const { view } = loadReportScriptWithJoinableD3(data);

  view.openEntryPointPill("entry-1");
  testOpeningTheEntryPointRendersTheClassBoxWithTheHandlerRowUnderlined(view);

  view.toggleMethodRow(METHOD_UPDATE, CONTROLLER_CLASS, "entry-1");
  testExpandingRendersEveryCompartmentWithMarkersAndStereotype(view);
  testExpandingRendersOneLinkPerCallWithNoSharedSegment(view);

  testAddedClassGetsGreenHeaderFillAndStatusGlyph();
  testChangedClassGetsGreenBorderWithOnlyItsChangedRowGreenAndNoHeaderFill();
  testAffectedClassGetsDashedAmberBorderAndTriangleGlyph();
  testLinksToDifferentClassesInTheSameColumnNeverShareASegment();
  testHoveringALinkHighlightsItAndBothEndpointRows();

  console.log("diagram-render-integration.test.js: all assertions passed");
}

/**
 * Reproduces a real diagram found running against Kairos: a constructor
 * calling several methods, some on one collaborator class and some on
 * another, where both collaborators land in the same column. Lane allocation
 * keyed by class pair (rather than by the visual gap the links actually
 * cross) gave two links to *different* target classes the same lane index,
 * so their outbound segments — which both start at the constructor's row —
 * were bit-for-bit identical (AC11).
 */
function testLinksToDifferentClassesInTheSameColumnNeverShareASegment() {
  const STYLE_CLASS = "com.example.StyleConfig";
  const GRID_CLASS = "com.example.TaskGrid";
  const METHOD_STYLE_GAP = "com.example.StyleConfig#gap()";
  const METHOD_GRID_SET_ROWS = "com.example.TaskGrid#setRows()";

  const data = fixture();
  data.classes.push(
      { id: STYLE_CLASS, moduleId: MODULE_ID, fqn: STYLE_CLASS, simpleName: "StyleConfig", packageName: "com.example",
        kind: "CLASS", layer: "SUPPORT", file: "StyleConfig.java", lineStart: 1, lineEnd: 10, javadoc: null, status: null,
        source: "class StyleConfig { }" },
      { id: GRID_CLASS, moduleId: MODULE_ID, fqn: GRID_CLASS, simpleName: "TaskGrid", packageName: "com.example",
        kind: "CLASS", layer: "SUPPORT", file: "TaskGrid.java", lineStart: 1, lineEnd: 10, javadoc: null, status: null,
        source: "class TaskGrid { }" });
  data.methods.push(
      { id: METHOD_STYLE_GAP, classId: STYLE_CLASS, name: "gap", signature: "gap()", file: "StyleConfig.java",
        lineStart: 2, lineEnd: 4, javadoc: null, source: "void gap() { }", constructor: false, visibility: "PUBLIC", status: null },
      { id: METHOD_GRID_SET_ROWS, classId: GRID_CLASS, name: "setRows", signature: "setRows()", file: "TaskGrid.java",
        lineStart: 2, lineEnd: 4, javadoc: null, source: "void setRows() { }", constructor: false, visibility: "PUBLIC", status: null });
  data.edges.push(
      { from: METHOD_UPDATE, to: METHOD_STYLE_GAP, kind: "CALL_EXTERNAL", resolved: true, line: 14 },
      { from: METHOD_UPDATE, to: METHOD_GRID_SET_ROWS, kind: "CALL_EXTERNAL", resolved: true, line: 15 });

  const { view } = loadReportScriptWithJoinableD3(data);
  view.openEntryPointPill("entry-1");
  view.toggleMethodRow(METHOD_UPDATE, CONTROLLER_CLASS, "entry-1");

  const drawnLinks = links(view);
  assert.strictEqual(drawnLinks.length, 5, "one link per resolved call from the constructor");

  const seenSegmentKeys = new Set();
  for (const link of drawnLinks) {
    for (const [a, b] of segmentsOf(link.getAttribute("d"))) {
      const key = a.x + "," + a.y + "->" + b.x + "," + b.y;
      assert.strictEqual(seenSegmentKeys.has(key), false,
          "two links to different classes sharing the same visual column gap must not share a segment (spec 007 §6.4.1, AC11)");
      seenSegmentKeys.add(key);
    }
  }
}

/**
 * Spec 007 §6.4.6: "Hovering a link highlights it and both endpoints so a
 * long route can be followed by eye" — not just the path itself.
 */
function testHoveringALinkHighlightsItAndBothEndpointRows() {
  const data = fixture();
  const { view } = loadReportScriptWithJoinableD3(data);
  view.openEntryPointPill("entry-1");
  view.toggleMethodRow(METHOD_UPDATE, CONTROLLER_CLASS, "entry-1");

  const dashedLink = links(view).find((link) => (link.getAttribute("class") || "").includes("class-link-dashed"));
  const [mouseenter, mouseleave] = [dashedLink.handlers.get("mouseenter"), dashedLink.handlers.get("mouseleave")];

  mouseenter(null, dashedLink.datum);

  assert.ok((dashedLink.getAttribute("class") || "").includes("hovered"), "the hovered link itself is highlighted");
  const memberRows = classBoxes(view).flatMap((box) =>
      box.children.filter((node) => (node.getAttribute("class") || "").includes("member-row")));
  const highlightedRows = memberRows.filter((row) => (row.getAttribute("class") || "").includes("hovered"));
  assert.strictEqual(highlightedRows.length, 2, "both the source row and the target row are highlighted");

  mouseleave(null, dashedLink.datum);

  assert.strictEqual((dashedLink.getAttribute("class") || "").includes("hovered"), false,
      "leaving the link removes the highlight");
  const stillHighlighted = memberRows.filter((row) => (row.getAttribute("class") || "").includes("hovered"));
  assert.strictEqual(stillHighlighted.length, 0, "leaving the link un-highlights both endpoint rows too");
}

function testAddedClassGetsGreenHeaderFillAndStatusGlyph() {
  const data = fixture();
  data.classes[0] = { ...data.classes[0], status: "ADDED" };
  data.methods[0] = { ...data.methods[0], status: "ADDED" };
  const { view } = loadReportScriptWithJoinableD3(data);

  view.openEntryPointPill("entry-1");

  const box = classBoxes(view)[0];
  assert.ok((box.getAttribute("class") || "").includes("status-added"),
      "an ADDED class box carries the status-added CSS class (spec 007 §3)");

  const headerFill = box.children.find((node) => node.getAttribute("class") === "box-header-fill");
  assert.ok(headerFill, "an ADDED box gets a green header fill, in addition to its solid green border (spec 007 §3)");

  const glyph = box.children.find((node) => (node.getAttribute("class") || "").startsWith("status-glyph "));
  assert.ok(glyph, "the header carries a status glyph, so status is never colour-only (spec 007 §3)");
  assert.ok((glyph.getAttribute("class") || "").includes("status-glyph-added"));

  const updateRow = box.children.find((node) => (node.getAttribute("class") || "").includes("member-row"));
  assert.ok((updateRow.getAttribute("class") || "").includes("status-added"), "the changed row itself is also marked");
}

/**
 * Spec 007 §3/§4.3: a `CHANGED` class draws a green border but, unlike
 * `ADDED`, gets no green header fill — and only the rows that themselves
 * changed are coloured, not every row the box happens to show.
 */
function testChangedClassGetsGreenBorderWithOnlyItsChangedRowGreenAndNoHeaderFill() {
  const data = fixture();
  data.classes[0] = { ...data.classes[0], status: "CHANGED" };
  data.methods[0] = { ...data.methods[0], status: "CHANGED" };
  data.methods.push({
    id: METHOD_ARCHIVE, classId: CONTROLLER_CLASS, name: "archive", signature: "archive()",
    file: "TaskController.java", lineStart: 22, lineEnd: 24, javadoc: null, source: "void archive() { }",
    constructor: false, visibility: "PUBLIC", status: "UNCHANGED"
  });
  const { view } = loadReportScriptWithJoinableD3(data);

  view.openEntryPointPill("entry-1");

  const box = classBoxes(view)[0];
  assert.ok((box.getAttribute("class") || "").includes("status-changed"),
      "a CHANGED class box carries the status-changed CSS class (spec 007 §3)");

  const headerFill = box.children.find((node) => node.getAttribute("class") === "box-header-fill");
  assert.ok(!headerFill, "a CHANGED box gets no green header fill — that is ADDED-only (spec 007 §3)");

  const memberRows = box.children.filter((node) => (node.getAttribute("class") || "").includes("member-row"));
  const changedRow = memberRows.find((node) => node.text().includes("update()"));
  const unchangedRow = memberRows.find((node) => node.text().includes("archive()"));
  assert.ok((changedRow.getAttribute("class") || "").includes("status-changed"),
      "the changed method's own row is coloured");
  assert.ok(!(unchangedRow.getAttribute("class") || "").includes("status-changed"),
      "the unchanged sibling row inside a CHANGED class stays plain (spec 007 §3)");
}

/**
 * Spec 007 §3: an `AFFECTED` class draws a dashed amber border, and its
 * status glyph is the triangle (`▲`), visibly distinct from the filled
 * circle (`●`) ADDED/CHANGED share — status is never colour-only.
 */
function testAffectedClassGetsDashedAmberBorderAndTriangleGlyph() {
  const data = fixture();
  data.classes[0] = { ...data.classes[0], status: "AFFECTED" };
  const { view } = loadReportScriptWithJoinableD3(data);

  view.openEntryPointPill("entry-1");

  const box = classBoxes(view)[0];
  assert.ok((box.getAttribute("class") || "").includes("status-affected"),
      "an AFFECTED class box carries the status-affected CSS class, which the stylesheet dashes (spec 007 §3)");

  const glyph = box.children.find((node) => (node.getAttribute("class") || "").startsWith("status-glyph "));
  assert.ok(glyph, "the header carries a status glyph, so status is never colour-only (spec 007 §3)");
  assert.ok((glyph.getAttribute("class") || "").includes("status-glyph-affected"),
      "AFFECTED uses its own glyph CSS class, distinct from status-glyph-added/status-glyph-changed");
}

function testOpeningTheEntryPointRendersTheClassBoxWithTheHandlerRowUnderlined(view) {
  const boxes = classBoxes(view);
  assert.strictEqual(boxes.length, 1, "opening the pill renders exactly one class box");

  const box = boxes[0];
  const header = box.children.find((node) => node.getAttribute("class") === "box-header");
  assert.strictEqual(header.text(), "TaskController");

  const stereotype = box.children.find((node) => node.getAttribute("class") === "box-stereotype");
  assert.strictEqual(stereotype.text(), "«entry»", "the header carries the layer as a UML stereotype (spec 007 §2.2)");

  const updateRow = box.children.find((node) => (node.getAttribute("class") || "").includes("member-row"));
  assert.ok(updateRow.text().startsWith("+ "), "a PUBLIC row is marked with the UML `+` visibility marker");
  assert.ok((updateRow.getAttribute("class") || "").includes("underlined"),
      "the entry-point method row is underlined (spec 007 §4.1)");
}

function testExpandingRendersEveryCompartmentWithMarkersAndStereotype(view) {
  const controllerBox = classBoxes(view).find((box) =>
      box.children.some((node) => node.getAttribute("class") === "box-header" && node.text() === "TaskController"));
  const rows = controllerBox.children.filter((node) => (node.getAttribute("class") || "").includes("member-row"));
  const rowLabels = rows.map((node) => node.text());

  assert.ok(rowLabels.includes("+ update()"), "the public compartment lists the handler");
  assert.ok(rowLabels.includes("- validate()"),
      "the private row appears once a visible caller expands into it (spec 007 §2.3)");

  const separator = controllerBox.children.find((node) =>
      (node.getAttribute("class") || "").includes("compartment-rule-private"));
  assert.ok(separator, "a dashed rule separates the revealed private compartment (spec 007 §2.2)");

  const repositoryBox = classBoxes(view).find((box) =>
      box.children.some((node) => node.getAttribute("class") === "box-header" && node.text() === "TaskRepository"));
  const repositoryStereotype = repositoryBox.children.find((node) => node.getAttribute("class") === "box-stereotype");
  assert.strictEqual(repositoryStereotype.text(), "«infrastructure»");
  const repositoryRows = repositoryBox.children.filter((node) => (node.getAttribute("class") || "").includes("member-row"));
  assert.ok(repositoryRows.every((node) => (node.getAttribute("class") || "").includes("underlined")),
      "both save() and find() are underlined as expansion targets (spec 007 §4.3)");
}

function testExpandingRendersOneLinkPerCallWithNoSharedSegment(view) {
  const drawnLinks = links(view);
  assert.strictEqual(drawnLinks.length, 3,
      "one link per resolved call: the private validate() call plus the two repository calls (spec 007 §4.3)");

  const dashed = drawnLinks.filter((link) => (link.getAttribute("class") || "").includes("class-link-dashed"));
  assert.strictEqual(dashed.length, 1, "only the private same-class call is dashed");

  const seenSegmentKeys = new Set();
  for (const link of drawnLinks) {
    for (const [a, b] of segmentsOf(link.getAttribute("d"))) {
      assert.ok(a.x === b.x || a.y === b.y, "every routed segment is purely horizontal or vertical");
      const key = a.x + "," + a.y + "->" + b.x + "," + b.y;
      assert.strictEqual(seenSegmentKeys.has(key), false, "no two links may share a routed segment (spec 007 §6.4, AC11)");
      seenSegmentKeys.add(key);
    }
  }
}

run();
