"use strict";

/**
 * Assertions that search, layer, and module filters narrow the entry-point
 * pill list (spec §7 keeps these from feature/6). In the box-diagram model,
 * filtering operates over the module/entry-point picker that seeds the
 * canvas — the diagram itself only ever contains what the reader expanded —
 * so what's filtered here is "which entry points can I open", by class/method
 * name, by layer, and by owning module.
 *
 * Run with: node src/test/js/filters.test.js
 */

const assert = require("assert");
const { loadReportScript } = require("./report-test-harness");

const MODULE_A = "kairos-api";
const MODULE_B = "kairos-admin";
const CONTROLLER_CLASS = "com.example.TaskController";
const REPOSITORY_CLASS = "com.example.TaskRepository";
const METHOD_CREATE = "com.example.TaskController#create()";

function fixture() {
  return {
    modules: [
      { id: MODULE_A, name: "kairos-api", path: MODULE_A },
      { id: MODULE_B, name: "kairos-admin", path: MODULE_B }
    ],
    entryPoints: [
      { id: "entry-1", moduleId: MODULE_A, kind: "REST", label: "POST /tasks", methodId: METHOD_CREATE, detectedBy: "RULE" },
      { id: "entry-2", moduleId: MODULE_B, kind: "MAIN", label: "main()", methodId: METHOD_CREATE, detectedBy: "RULE" }
    ],
    classes: [
      { id: CONTROLLER_CLASS, moduleId: MODULE_A, fqn: "com.example.TaskController", simpleName: "TaskController",
        packageName: "com.example", kind: "CLASS", layer: "ENTRY", file: "TaskController.java",
        lineStart: 1, lineEnd: 20, javadoc: null, status: null, source: "class TaskController { }" },
      { id: REPOSITORY_CLASS, moduleId: MODULE_A, fqn: "com.example.TaskRepository", simpleName: "TaskRepository",
        packageName: "com.example", kind: "CLASS", layer: "INFRASTRUCTURE", file: "TaskRepository.java",
        lineStart: 1, lineEnd: 20, javadoc: null, status: null, source: "class TaskRepository { }" }
    ],
    methods: [
      { id: METHOD_CREATE, classId: CONTROLLER_CLASS, name: "create", signature: "create()", file: "TaskController.java",
        lineStart: 10, lineEnd: 13, javadoc: null, source: "void create() { }",
        constructor: false, visibility: "PUBLIC", status: null }
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

  testSearchMatchesEntryPointLabelOrOwningClassName(internal, index);
  testLayerFilterMatchesTheDeclaringClassLayer(internal, index);
  testModuleFilterKeepsOnlyThatModulesEntryPoints(internal, index);

  console.log("filters.test.js: all assertions passed");
}

function testSearchMatchesEntryPointLabelOrOwningClassName(internal, index) {
  const allEntryPoints = index.data.entryPoints;

  const byLabel = internal.filterEntryPoints(allEntryPoints, index, { searchTerm: "post" });
  assert.strictEqual(byLabel.length, 1);
  assert.strictEqual(byLabel[0].id, "entry-1");

  const byClassName = internal.filterEntryPoints(allEntryPoints, index, { searchTerm: "taskcontroller" });
  assert.strictEqual(byClassName.length, 2, "both entry points share the same handling class");

  const noMatch = internal.filterEntryPoints(allEntryPoints, index, { searchTerm: "nonexistent" });
  assert.strictEqual(noMatch.length, 0);
}

function testLayerFilterMatchesTheDeclaringClassLayer(internal, index) {
  const allEntryPoints = index.data.entryPoints;

  const filtered = internal.filterEntryPoints(allEntryPoints, index, { layer: "ENTRY" });
  assert.strictEqual(filtered.length, 2, "both entry points are handled by an ENTRY-layer class");

  const none = internal.filterEntryPoints(allEntryPoints, index, { layer: "INFRASTRUCTURE" });
  assert.strictEqual(none.length, 0);
}

function testModuleFilterKeepsOnlyThatModulesEntryPoints(internal, index) {
  const allEntryPoints = index.data.entryPoints;

  const filtered = internal.filterEntryPoints(allEntryPoints, index, { moduleId: MODULE_B });
  assert.strictEqual(filtered.length, 1);
  assert.strictEqual(filtered[0].id, "entry-2");

  const unfiltered = internal.filterEntryPoints(allEntryPoints, index, {});
  assert.strictEqual(unfiltered.length, 2, "no module filter keeps every entry point");
}

run();
