"use strict";

/**
 * Assertions on the class side panel's data (clicking a CLASS node, spec
 * §3.2.1): it must show the class name, file, layer/module, and the list of
 * methods the class declares — but adds nothing to the canvas (covered by
 * click-behaviour.test.js). Full file source is deliberately NOT embedded a
 * second time here: `MethodView.source` already carries every method body,
 * and duplicating the surrounding file text would roughly double the
 * embedded source payload for no new information (measured: class-declared
 * line spans run ~1.7x method-declared spans in Kairos).
 *
 * Run with: node src/test/js/class-panel.test.js
 */

const assert = require("assert");
const { loadReportScript } = require("./report-test-harness");

const MODULE_ID = "kairos-api";
const CLASS_ID = "com.example.TaskController";
const METHOD_CREATE = "com.example.TaskController#create()";
const METHOD_DELETE = "com.example.TaskController#delete()";

function fixture() {
  return {
    modules: [{ id: MODULE_ID, name: "kairos-api", path: MODULE_ID }],
    entryPoints: [],
    classes: [
      { id: CLASS_ID, moduleId: MODULE_ID, fqn: "com.example.TaskController", simpleName: "TaskController",
        packageName: "com.example", kind: "CLASS", layer: "ENTRY", file: "TaskController.java",
        lineStart: 1, lineEnd: 40, javadoc: "Handles task requests.", status: null }
    ],
    methods: [
      { id: METHOD_CREATE, classId: CLASS_ID, name: "create", signature: "create()", file: "TaskController.java",
        lineStart: 10, lineEnd: 13, javadoc: null, source: "void create() { }", constructor: false, status: null },
      { id: METHOD_DELETE, classId: CLASS_ID, name: "delete", signature: "delete()", file: "TaskController.java",
        lineStart: 15, lineEnd: 18, javadoc: null, source: "void delete() { }", constructor: false, status: null }
    ],
    edges: [],
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
  assert.strictEqual(panelData.methods.length, 2, "every method the class declares must be listed");
  const methodIds = panelData.methods.map((m) => m.id);
  assert.ok(methodIds.includes(METHOD_CREATE));
  assert.ok(methodIds.includes(METHOD_DELETE));
  assert.ok(!("source" in panelData) || panelData.source === undefined,
      "the class panel must not embed a second copy of the file's full source");

  console.log("class-panel.test.js: all assertions passed");
}

run();
