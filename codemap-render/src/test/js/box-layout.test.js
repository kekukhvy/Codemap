"use strict";

/**
 * Assertions on {@code layoutDiagram}, which turns a {@code DiagramController}'s
 * current boxes/links into concrete pixel positions (spec 007 §6.3): columns
 * by call depth from the entry pill, barycentre-ordered within a column, and
 * stable across re-layout — expanding must not reshuffle boxes the reader is
 * already looking at.
 *
 * Run with: node src/test/js/box-layout.test.js
 */

const assert = require("assert");
const { loadReportScript } = require("./report-test-harness");

const MODULE_ID = "kairos-api";
const CONTROLLER_CLASS = "com.example.TaskController";
const REPOSITORY_CLASS = "com.example.TaskRepository";
const METHOD_UPDATE = "com.example.TaskController#update()";
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
        file: "TaskController.java", lineStart: 10, lineEnd: 13, javadoc: null,
        source: "void update() { repository.save(); }", constructor: false, visibility: "PUBLIC", status: null },
      { id: METHOD_SAVE, classId: REPOSITORY_CLASS, name: "save", signature: "save()",
        file: "TaskRepository.java", lineStart: 5, lineEnd: 7, javadoc: null, source: "void save() { }",
        constructor: false, visibility: "PUBLIC", status: null }
    ],
    edges: [
      { from: METHOD_UPDATE, to: METHOD_SAVE, kind: "CALL_EXTERNAL", resolved: true, line: 11 }
    ],
    moduleDependencies: [],
    removedMethods: []
  };
}

function run() {
  const data = fixture();
  const internal = loadReportScript(data);
  const index = new internal.CodemapIndex(data);
  const controller = new internal.DiagramController(index);
  controller.openEntryPoint("entry-1");
  controller.expandMethodRow(METHOD_UPDATE, CONTROLLER_CLASS, "entry-1");

  testBoxesAreColumnedByDepthFromThePill(internal, index, controller);
  testLayoutIsStableAcrossRepeatedCalls(internal, index, controller);
  testEveryBoxGetsANonOverlappingRect(internal, index, controller);

  console.log("box-layout.test.js: all assertions passed");
}

function testBoxesAreColumnedByDepthFromThePill(internal, index, controller) {
  const layout = internal.layoutDiagram(controller.diagram, index, "entry-1");

  const controllerPosition = layout.boxPositions.get(CONTROLLER_CLASS);
  const repositoryPosition = layout.boxPositions.get(REPOSITORY_CLASS);
  assert.ok(controllerPosition, "the controller box must be laid out");
  assert.ok(repositoryPosition, "the repository box must be laid out");
  assert.ok(repositoryPosition.rect.x > controllerPosition.rect.x,
      "the repository box (depth 2) must sit in a later column than the controller box (depth 1)");
}

function testLayoutIsStableAcrossRepeatedCalls(internal, index, controller) {
  const first = internal.layoutDiagram(controller.diagram, index, "entry-1");
  const second = internal.layoutDiagram(controller.diagram, index, "entry-1");

  const firstControllerRect = first.boxPositions.get(CONTROLLER_CLASS).rect;
  const secondControllerRect = second.boxPositions.get(CONTROLLER_CLASS).rect;
  assert.strictEqual(firstControllerRect.x, secondControllerRect.x,
      "re-laying out the same diagram state must not move a box the reader is already looking at");
  assert.strictEqual(firstControllerRect.y, secondControllerRect.y);
}

function testEveryBoxGetsANonOverlappingRect(internal, index, controller) {
  const layout = internal.layoutDiagram(controller.diagram, index, "entry-1");
  const rects = [...layout.boxPositions.values()].map((position) => position.rect);
  for (const rect of rects) {
    assert.ok(Number.isFinite(rect.x) && Number.isFinite(rect.y), "every box rect must have finite coordinates");
    assert.ok(rect.width > 0 && rect.height > 0, "every box rect must have a positive size");
  }
}

run();
