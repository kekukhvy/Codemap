"use strict";

/**
 * Assertions on {@code DiagramView}'s click handlers (spec 007 §4): opening
 * an entry-point pill, the class-header and method-row `(+)`/`(−)`
 * expanders, and that clicking a name only opens the side panel without
 * touching the canvas.
 *
 * Run with: node src/test/js/diagram-view-interaction.test.js
 */

const assert = require("assert");
const { loadReportScript } = require("./report-test-harness");

const MODULE_ID = "kairos-api";
const CONTROLLER_CLASS = "com.example.TaskController";
const REPOSITORY_CLASS = "com.example.TaskRepository";
const METHOD_UPDATE = "com.example.TaskController#update()";
const METHOD_VALIDATE = "com.example.TaskController#validate()";
const METHOD_SAVE = "com.example.TaskRepository#save()";

function fixture() {
  return {
    modules: [{ id: MODULE_ID, name: "kairos-api", path: MODULE_ID }],
    entryPoints: [
      { id: "entry-1", moduleId: MODULE_ID, kind: "REST", label: "PUT /tasks/{id}", methodId: METHOD_UPDATE, detectedBy: "RULE" }
    ],
    classes: [
      { id: CONTROLLER_CLASS, moduleId: MODULE_ID, fqn: "com.example.TaskController", simpleName: "TaskController",
        packageName: "com.example", kind: "CLASS", layer: "ENTRY", file: "TaskController.java",
        lineStart: 1, lineEnd: 40, javadoc: null, status: null, source: "class TaskController { }" },
      { id: REPOSITORY_CLASS, moduleId: MODULE_ID, fqn: "com.example.TaskRepository", simpleName: "TaskRepository",
        packageName: "com.example", kind: "CLASS", layer: "INFRASTRUCTURE", file: "TaskRepository.java",
        lineStart: 1, lineEnd: 20, javadoc: null, status: null, source: "class TaskRepository { }" }
    ],
    methods: [
      { id: METHOD_UPDATE, classId: CONTROLLER_CLASS, name: "update", signature: "update()",
        file: "TaskController.java", lineStart: 10, lineEnd: 16, javadoc: null,
        source: "void update() { validate(); repository.save(); }", constructor: false, visibility: "PUBLIC", status: null },
      { id: METHOD_VALIDATE, classId: CONTROLLER_CLASS, name: "validate", signature: "validate()",
        file: "TaskController.java", lineStart: 18, lineEnd: 20, javadoc: null, source: "void validate() { }",
        constructor: false, visibility: "PRIVATE", status: null },
      { id: METHOD_SAVE, classId: REPOSITORY_CLASS, name: "save", signature: "save()",
        file: "TaskRepository.java", lineStart: 5, lineEnd: 7, javadoc: null, source: "void save() { }",
        constructor: false, visibility: "PUBLIC", status: null }
    ],
    edges: [
      { from: METHOD_UPDATE, to: METHOD_VALIDATE, kind: "CALL_INTERNAL", resolved: true, line: 11 },
      { from: METHOD_UPDATE, to: METHOD_SAVE, kind: "CALL_EXTERNAL", resolved: true, line: 12 }
    ],
    moduleDependencies: [],
    removedMethods: []
  };
}

function run() {
  const data = fixture();
  const internal = loadReportScript(data);
  const index = new internal.CodemapIndex(data);

  testOpeningTheEntryPointPillDrawsOneBoxWithTheHandlerUnderlined(internal, index);
  testExpandingThenCollapsingAMethodRowRemovesOnlyWhatItRevealed(internal, index);
  testSelectingAClassNeverTouchesTheCanvas(internal, index);

  console.log("diagram-view-interaction.test.js: all assertions passed");
}

function testOpeningTheEntryPointPillDrawsOneBoxWithTheHandlerUnderlined(internal, index) {
  const view = new internal.DiagramView(index);

  view.openEntryPointPill("entry-1");

  assert.strictEqual(view.controller.diagram.boxes.size, 1, "opening the pill draws exactly one class box");
  assert.strictEqual(view.underlinedMethodIds.has(METHOD_UPDATE), true,
      "the entry-point method row must be underlined (spec 007 §4.1)");
}

function testExpandingThenCollapsingAMethodRowRemovesOnlyWhatItRevealed(internal, index) {
  const view = new internal.DiagramView(index);
  view.openEntryPointPill("entry-1");

  view.expandMethodRow(METHOD_UPDATE, CONTROLLER_CLASS, "entry-1");
  assert.strictEqual(view.controller.diagram.boxes.size, 2, "expanding draws the repository box too");
  assert.strictEqual(
      view.controller.diagram.boxFor(CONTROLLER_CLASS).revealedPrivateMethodIds.has(METHOD_VALIDATE), true);

  view.collapseMethodRow(METHOD_UPDATE, "entry-1");
  assert.strictEqual(view.controller.diagram.boxes.size, 1,
      "collapsing the method row removes the repository box it alone revealed");
  assert.strictEqual(
      view.controller.diagram.boxFor(CONTROLLER_CLASS).revealedPrivateMethodIds.has(METHOD_VALIDATE), false,
      "collapsing also un-reveals the private row it alone revealed");
}

function testSelectingAClassNeverTouchesTheCanvas(internal, index) {
  const view = new internal.DiagramView(index);
  view.openEntryPointPill("entry-1");
  const boxCountBefore = view.controller.diagram.boxes.size;

  view.navigateToClass(CONTROLLER_CLASS);

  assert.strictEqual(view.controller.diagram.boxes.size, boxCountBefore,
      "selecting a class name for the side panel must never add or remove a box");
  assert.strictEqual(view.selection.classId, CONTROLLER_CLASS);
}

run();
