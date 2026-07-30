"use strict";

/**
 * Assertions that side-panel navigation actually opens the target (spec 007
 * §4.2 links to classes/methods): selecting a class or method updates the
 * view's selection state, whether or not that class is currently drawn on
 * the canvas — a "calls"/"called by" link must always be able to open the
 * side panel, even for a collaborator the reader has not expanded yet.
 *
 * Run with: node src/test/js/navigation.test.js
 */

const assert = require("assert");
const { loadReportScript } = require("./report-test-harness");

function fixtureWithChain() {
  const moduleId = "kairos-api";
  const classId = "com.example.TaskController";
  const repoClassId = "com.example.TaskRepository";
  const methodCreate = "com.example.TaskController#create()";
  const methodSave = "com.example.TaskRepository#save()";

  return {
    modules: [{ id: moduleId, name: "kairos-api", path: moduleId }],
    entryPoints: [
      { id: "entry-1", moduleId, kind: "REST", label: "POST /tasks", methodId: methodCreate, detectedBy: "RULE" }
    ],
    classes: [
      { id: classId, moduleId, fqn: "com.example.TaskController", simpleName: "TaskController",
        packageName: "com.example", kind: "CLASS", layer: "ENTRY", file: "TaskController.java",
        lineStart: 1, lineEnd: 20, javadoc: null, status: null, source: "class TaskController { }" },
      { id: repoClassId, moduleId, fqn: "com.example.TaskRepository", simpleName: "TaskRepository",
        packageName: "com.example", kind: "CLASS", layer: "INFRASTRUCTURE", file: "TaskRepository.java",
        lineStart: 1, lineEnd: 20, javadoc: null, status: null, source: "class TaskRepository { }" }
    ],
    methods: [
      { id: methodCreate, classId, name: "create", signature: "create()", file: "TaskController.java",
        lineStart: 10, lineEnd: 13, javadoc: null, source: "void create() { save(); }",
        constructor: false, visibility: "PUBLIC", status: null },
      { id: methodSave, classId: repoClassId, name: "save", signature: "save()", file: "TaskRepository.java",
        lineStart: 5, lineEnd: 7, javadoc: null, source: "void save() { }",
        constructor: false, visibility: "PUBLIC", status: null }
    ],
    edges: [
      { from: methodCreate, to: methodSave, kind: "CALL_EXTERNAL", resolved: true, line: 11 }
    ],
    moduleDependencies: [],
    removedMethods: []
  };
}

function run() {
  const data = fixtureWithChain();
  const internal = loadReportScript(data);
  const index = new internal.CodemapIndex(data);
  const view = new internal.DiagramView(index);

  testNavigatingToAMethodNotYetDrawnStillSelectsIt(internal, view);
  testNavigatingToAClassNotYetDrawnStillSelectsIt(internal, view);
  testSelectingAMethodOpensItsPanelData(internal, index, view);

  console.log("navigation.test.js: all assertions passed");
}

function testNavigatingToAMethodNotYetDrawnStillSelectsIt(internal, view) {
  const saveMethodId = "com.example.TaskRepository#save()";

  view.navigateToMethod(saveMethodId);

  assert.strictEqual(view.selection.kind, "METHOD");
  assert.strictEqual(view.selection.methodId, saveMethodId,
      "navigating to a method not yet expanded into the diagram must still select it");
}

function testNavigatingToAClassNotYetDrawnStillSelectsIt(internal, view) {
  const repoClassId = "com.example.TaskRepository";

  view.navigateToClass(repoClassId);

  assert.strictEqual(view.selection.kind, "CLASS");
  assert.strictEqual(view.selection.classId, repoClassId);
}

function testSelectingAMethodOpensItsPanelData(internal, index, view) {
  const methodId = "com.example.TaskController#create()";

  view.navigateToMethod(methodId);

  const panelData = internal.buildMethodPanelData(index, view.selection.methodId);
  assert.strictEqual(panelData.signature, "create()");
}

run();
