"use strict";

/**
 * Assertions that a cross-module call still draws a box and a link like any
 * other public cross-class call (spec 007 §4.3), while keeping its own
 * distinct connector style (spec §7, kept from feature/6) so a reader can
 * still tell a module boundary was crossed.
 *
 * Run with: node src/test/js/cross-module.test.js
 */

const assert = require("assert");
const { loadReportScript } = require("./report-test-harness");

function fixtureWithCrossModuleCall() {
  const apiModuleId = "kairos-api";
  const commonModuleId = "common";
  const classId = "com.example.TaskController";
  const sharedClassId = "com.example.Shared";
  const methodCreate = "com.example.TaskController#create()";
  const methodValidate = "com.example.Shared#validate()";

  return {
    modules: [
      { id: apiModuleId, name: "kairos-api", path: apiModuleId },
      { id: commonModuleId, name: "common", path: commonModuleId }
    ],
    entryPoints: [
      { id: "entry-1", moduleId: apiModuleId, kind: "REST", label: "POST /tasks", methodId: methodCreate, detectedBy: "RULE" }
    ],
    classes: [
      { id: classId, moduleId: apiModuleId, fqn: "com.example.TaskController", simpleName: "TaskController",
        packageName: "com.example", kind: "CLASS", layer: "ENTRY", file: "TaskController.java",
        lineStart: 1, lineEnd: 20, javadoc: null, status: null, source: "class TaskController { }" },
      { id: sharedClassId, moduleId: commonModuleId, fqn: "com.example.Shared", simpleName: "Shared",
        packageName: "com.example", kind: "CLASS", layer: "SUPPORT", file: "Shared.java",
        lineStart: 1, lineEnd: 20, javadoc: null, status: null, source: "class Shared { }" }
    ],
    methods: [
      { id: methodCreate, classId, name: "create", signature: "create()", file: "TaskController.java",
        lineStart: 10, lineEnd: 13, javadoc: null, source: "void create() { validate(); }",
        constructor: false, visibility: "PUBLIC", status: null },
      { id: methodValidate, classId: sharedClassId, name: "validate", signature: "validate()", file: "Shared.java",
        lineStart: 3, lineEnd: 5, javadoc: null, source: "void validate() { }",
        constructor: false, visibility: "PUBLIC", status: null }
    ],
    edges: [
      { from: methodCreate, to: methodValidate, kind: "CROSS_MODULE", resolved: true, line: 11,
        fromModuleId: apiModuleId, toModuleId: commonModuleId }
    ],
    moduleDependencies: [{ fromModuleId: apiModuleId, toModuleId: commonModuleId }],
    removedMethods: []
  };
}

function run() {
  const data = fixtureWithCrossModuleCall();
  const internal = loadReportScript(data);
  const index = new internal.CodemapIndex(data);
  const controller = new internal.DiagramController(index);

  controller.openEntryPoint("entry-1");
  const result = controller.expandMethodRow(
      "com.example.TaskController#create()", "com.example.TaskController", "entry-1");

  assert.strictEqual(result.links.length, 1, "the cross-module call still draws exactly one link");
  const [link] = result.links;
  assert.strictEqual(link.targetClassId, "com.example.Shared", "the target class box is drawn, not collapsed");
  assert.strictEqual(link.style, "solid", "a public cross-module call is a solid link, same rule as any public cross-class call");
  assert.strictEqual(link.crossModule, true,
      "the link must still carry the cross-module flag so the existing distinct connector style applies (spec §7)");

  assert.ok(controller.diagram.boxFor("com.example.Shared"), "the target class box exists on the canvas");

  console.log("cross-module.test.js: all assertions passed");
}

run();
