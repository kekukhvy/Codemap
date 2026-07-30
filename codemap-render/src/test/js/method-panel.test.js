"use strict";

/**
 * Assertions on the method-row side panel data (clicking a method row name,
 * spec 007 §4.2): the method source, plus the classes (deduplicated) that
 * call it — not a flat method list.
 *
 * Run with: node src/test/js/method-panel.test.js
 */

const assert = require("assert");
const { loadReportScript } = require("./report-test-harness");

const MODULE_ID = "kairos-api";
const REPOSITORY_CLASS = "com.example.TaskRepository";
const CONTROLLER_CLASS = "com.example.TaskController";
const SCHEDULER_CLASS = "com.example.TaskScheduler";
const METHOD_SAVE = "com.example.TaskRepository#save()";
const METHOD_CREATE = "com.example.TaskController#create()";
const METHOD_UPDATE = "com.example.TaskController#update()";
const METHOD_RUN = "com.example.TaskScheduler#run()";

function fixture() {
  return {
    modules: [{ id: MODULE_ID, name: "kairos-api", path: MODULE_ID }],
    entryPoints: [],
    classes: [
      { id: REPOSITORY_CLASS, moduleId: MODULE_ID, fqn: "com.example.TaskRepository", simpleName: "TaskRepository",
        packageName: "com.example", kind: "CLASS", layer: "INFRASTRUCTURE", file: "TaskRepository.java",
        lineStart: 1, lineEnd: 20, javadoc: null, status: null, source: "class TaskRepository { }" },
      { id: CONTROLLER_CLASS, moduleId: MODULE_ID, fqn: "com.example.TaskController", simpleName: "TaskController",
        packageName: "com.example", kind: "CLASS", layer: "ENTRY", file: "TaskController.java",
        lineStart: 1, lineEnd: 20, javadoc: null, status: null, source: "class TaskController { }" },
      { id: SCHEDULER_CLASS, moduleId: MODULE_ID, fqn: "com.example.TaskScheduler", simpleName: "TaskScheduler",
        packageName: "com.example", kind: "CLASS", layer: "APPLICATION", file: "TaskScheduler.java",
        lineStart: 1, lineEnd: 20, javadoc: null, status: null, source: "class TaskScheduler { }" }
    ],
    methods: [
      { id: METHOD_SAVE, classId: REPOSITORY_CLASS, name: "save", signature: "save()", file: "TaskRepository.java",
        lineStart: 5, lineEnd: 7, javadoc: null, source: "void save() { }",
        constructor: false, visibility: "PUBLIC", status: null },
      { id: METHOD_CREATE, classId: CONTROLLER_CLASS, name: "create", signature: "create()", file: "TaskController.java",
        lineStart: 10, lineEnd: 13, javadoc: null, source: "void create() { save(); }",
        constructor: false, visibility: "PUBLIC", status: null },
      { id: METHOD_UPDATE, classId: CONTROLLER_CLASS, name: "update", signature: "update()", file: "TaskController.java",
        lineStart: 15, lineEnd: 18, javadoc: null, source: "void update() { save(); }",
        constructor: false, visibility: "PUBLIC", status: null },
      { id: METHOD_RUN, classId: SCHEDULER_CLASS, name: "run", signature: "run()", file: "TaskScheduler.java",
        lineStart: 3, lineEnd: 5, javadoc: null, source: "void run() { save(); }",
        constructor: false, visibility: "PUBLIC", status: null }
    ],
    edges: [
      { from: METHOD_CREATE, to: METHOD_SAVE, kind: "CALL_EXTERNAL", resolved: true, line: 11 },
      { from: METHOD_UPDATE, to: METHOD_SAVE, kind: "CALL_EXTERNAL", resolved: true, line: 16 },
      { from: METHOD_RUN, to: METHOD_SAVE, kind: "CALL_EXTERNAL", resolved: true, line: 4 }
    ],
    moduleDependencies: [],
    removedMethods: []
  };
}

function run() {
  const data = fixture();
  const internal = loadReportScript(data);
  const index = new internal.CodemapIndex(data);

  const panelData = internal.buildMethodPanelData(index, METHOD_SAVE);

  assert.strictEqual(panelData.signature, "save()");
  assert.strictEqual(panelData.source, "void save() { }");
  assert.strictEqual(panelData.calledByClassIds.length, 2,
      "two callers from the same TaskController class must collapse into one class-granular entry");
  assert.ok(panelData.calledByClassIds.includes(CONTROLLER_CLASS));
  assert.ok(panelData.calledByClassIds.includes(SCHEDULER_CLASS));

  console.log("method-panel.test.js: all assertions passed");
}

run();
