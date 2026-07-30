"use strict";

/**
 * Assertions on the class side panel's data (clicking a class name, spec 007
 * §4.2): `calls:` / `called by:` are class-granular — deduplicated classes,
 * not a flat method list — followed by the full verbatim class source
 * (§5.2), and the method list for the box's own compartments.
 *
 * Run with: node src/test/js/class-panel.test.js
 */

const assert = require("assert");
const { loadReportScript } = require("./report-test-harness");

const MODULE_ID = "kairos-api";
const CLASS_ID = "com.example.TaskController";
const REPOSITORY_CLASS = "com.example.TaskRepository";
const VALIDATOR_CLASS = "com.example.TaskValidator";
const METHOD_CREATE = "com.example.TaskController#create()";
const METHOD_DELETE = "com.example.TaskController#delete()";
const METHOD_SAVE = "com.example.TaskRepository#save()";
const METHOD_FIND = "com.example.TaskRepository#find()";
const METHOD_VALIDATE = "com.example.TaskValidator#validate()";
const CLASS_SOURCE = "public class TaskController {\n  // real body\n}";

function fixture() {
  return {
    modules: [{ id: MODULE_ID, name: "kairos-api", path: MODULE_ID }],
    entryPoints: [],
    classes: [
      { id: CLASS_ID, moduleId: MODULE_ID, fqn: "com.example.TaskController", simpleName: "TaskController",
        packageName: "com.example", kind: "CLASS", layer: "ENTRY", file: "TaskController.java",
        lineStart: 1, lineEnd: 40, javadoc: "Handles task requests.", status: null, source: CLASS_SOURCE },
      { id: REPOSITORY_CLASS, moduleId: MODULE_ID, fqn: "com.example.TaskRepository", simpleName: "TaskRepository",
        packageName: "com.example", kind: "CLASS", layer: "INFRASTRUCTURE", file: "TaskRepository.java",
        lineStart: 1, lineEnd: 20, javadoc: null, status: null, source: "class TaskRepository { }" },
      { id: VALIDATOR_CLASS, moduleId: MODULE_ID, fqn: "com.example.TaskValidator", simpleName: "TaskValidator",
        packageName: "com.example", kind: "CLASS", layer: "APPLICATION", file: "TaskValidator.java",
        lineStart: 1, lineEnd: 10, javadoc: null, status: null, source: "class TaskValidator { }" }
    ],
    methods: [
      { id: METHOD_CREATE, classId: CLASS_ID, name: "create", signature: "create()", file: "TaskController.java",
        lineStart: 10, lineEnd: 13, javadoc: null, source: "void create() { save(); find(); }",
        constructor: false, visibility: "PUBLIC", status: null },
      { id: METHOD_DELETE, classId: CLASS_ID, name: "delete", signature: "delete()", file: "TaskController.java",
        lineStart: 15, lineEnd: 18, javadoc: null, source: "void delete() { }",
        constructor: false, visibility: "PUBLIC", status: null },
      { id: METHOD_SAVE, classId: REPOSITORY_CLASS, name: "save", signature: "save()", file: "TaskRepository.java",
        lineStart: 5, lineEnd: 7, javadoc: null, source: "void save() { }",
        constructor: false, visibility: "PUBLIC", status: null },
      { id: METHOD_FIND, classId: REPOSITORY_CLASS, name: "find", signature: "find()", file: "TaskRepository.java",
        lineStart: 9, lineEnd: 11, javadoc: null, source: "void find() { }",
        constructor: false, visibility: "PUBLIC", status: null },
      { id: METHOD_VALIDATE, classId: VALIDATOR_CLASS, name: "validate", signature: "validate()", file: "TaskValidator.java",
        lineStart: 3, lineEnd: 5, javadoc: null, source: "void validate() { create(); }",
        constructor: false, visibility: "PUBLIC", status: null }
    ],
    edges: [
      { from: METHOD_CREATE, to: METHOD_SAVE, kind: "CALL_EXTERNAL", resolved: true, line: 11 },
      { from: METHOD_CREATE, to: METHOD_FIND, kind: "CALL_EXTERNAL", resolved: true, line: 12 },
      { from: METHOD_VALIDATE, to: METHOD_CREATE, kind: "CALL_EXTERNAL", resolved: true, line: 4 }
    ],
    moduleDependencies: [],
    removedMethods: []
  };
}

function run() {
  const data = fixture();
  const internal = loadReportScript(data);
  const index = new internal.CodemapIndex(data);

  const panelData = internal.buildClassPanelData(index, CLASS_ID);

  assert.strictEqual(panelData.simpleName, "TaskController");
  assert.strictEqual(panelData.file, "TaskController.java");
  assert.strictEqual(panelData.layer, "ENTRY");
  assert.strictEqual(panelData.moduleName, "kairos-api");
  assert.strictEqual(panelData.javadoc, "Handles task requests.");
  assert.strictEqual(panelData.source, CLASS_SOURCE, "the full verbatim class source must be included (spec 007 §5.2)");

  assert.strictEqual(panelData.callsClassIds.length, 1,
      "two calls into TaskRepository must collapse into one class-granular entry");
  assert.ok(panelData.callsClassIds.includes(REPOSITORY_CLASS));

  assert.strictEqual(panelData.calledByClassIds.length, 1);
  assert.ok(panelData.calledByClassIds.includes(VALIDATOR_CLASS));

  assert.strictEqual(panelData.methods.length, 2, "every method the class declares must be listed");

  console.log("class-panel.test.js: all assertions passed");
}

run();
