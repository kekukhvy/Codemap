"use strict";

/**
 * Assertions that cross-module calls are collapsed by default and jump to
 * the target module rather than expanding in place (AC8, spec §3.2.2).
 *
 * Before this fix, `TreeBuilder.callChildren` treated CROSS_MODULE edges
 * identically to CALL_INTERNAL/CALL_EXTERNAL: the callee was expandable like
 * any other node, so a reader "expanding" the module boundary silently
 * inlined a foreign module's private call chain into this one's tree —
 * exactly what the module-root design (spec §3.2.1) exists to keep separate.
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
        lineStart: 1, lineEnd: 20, javadoc: null, status: null },
      { id: sharedClassId, moduleId: commonModuleId, fqn: "com.example.Shared", simpleName: "Shared",
        packageName: "com.example", kind: "CLASS", layer: "SUPPORT", file: "Shared.java",
        lineStart: 1, lineEnd: 20, javadoc: null, status: null }
    ],
    methods: [
      { id: methodCreate, classId, name: "create", signature: "create()", file: "TaskController.java",
        lineStart: 10, lineEnd: 13, javadoc: null, source: "void create() { validate(); }", constructor: false, status: null },
      { id: methodValidate, classId: sharedClassId, name: "validate", signature: "validate()", file: "Shared.java",
        lineStart: 3, lineEnd: 5, javadoc: null, source: "void validate() { }", constructor: false, status: null }
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
  const builder = new internal.TreeBuilder(index);

  const [moduleRoot] = builder.buildModuleRoots();
  builder.expand(moduleRoot);
  const [entryPointNode] = moduleRoot.children;
  builder.expand(entryPointNode);
  // entry point -> [class: TaskController] -> create()
  const [controllerClassNode] = entryPointNode.children;
  const [createNode] = controllerClassNode.children;
  builder.expand(createNode);

  testCrossModuleCalleeIsCollapsedByDefault(createNode);
  testExpandingACollapsedCrossModuleNodeDoesNotMaterialiseChildren(builder, createNode);
  testGraphViewJumpsToTheTargetModuleRoot(internal, index, builder, createNode);

  console.log("cross-module.test.js: all assertions passed");
}

function testCrossModuleCalleeIsCollapsedByDefault(createNode) {
  assert.strictEqual(createNode.children.length, 1, "create() has exactly one outgoing call");
  const [crossModuleNode] = createNode.children;
  assert.strictEqual(crossModuleNode.collapsedCrossModule, true,
      "a CROSS_MODULE callee must render collapsed by default (spec §3.2.2)");
  assert.strictEqual(crossModuleNode.targetModuleId, "common",
      "a collapsed cross-module node must remember which module it points into, to jump there");
}

function testExpandingACollapsedCrossModuleNodeDoesNotMaterialiseChildren(builder, createNode) {
  const [crossModuleNode] = createNode.children;
  builder.expand(crossModuleNode);
  assert.strictEqual(crossModuleNode.children.length, 0,
      "expanding a collapsed cross-module node must not inline the target module's call chain in place");
}

function testGraphViewJumpsToTheTargetModuleRoot(internal, index, builder, createNode) {
  const view = new internal.GraphView(index, builder);
  const [crossModuleNode] = createNode.children;

  view.toggle(crossModuleNode);

  assert.strictEqual(view.selectedMethodId, crossModuleNode.methodId,
      "toggling a collapsed cross-module node must select/reveal its target method");
}

run();
