"use strict";

/**
 * Assertions on the click interaction model:
 *
 * - Clicking a METHOD node does both at once: opens the side panel (with its
 *   source) AND expands/collapses it to show its callees. Before this fix,
 *   a single click only toggled expansion; the side panel needed a double
 *   click, which read as broken to a reader who single-clicked and saw no
 *   code (AC per user feedback).
 * - Clicking a CLASS node opens the side panel only — it never adds
 *   anything to the canvas (the class is context, not a step in the chain).
 *
 * Run with: node src/test/js/click-behaviour.test.js
 */

const assert = require("assert");
const { loadReportScript } = require("./report-test-harness");

const MODULE_ID = "kairos-api";
const CLASS_ID = "com.example.TaskController";
const METHOD_ID = "com.example.TaskController#create()";

function fixture() {
  return {
    modules: [{ id: MODULE_ID, name: "kairos-api", path: MODULE_ID }],
    entryPoints: [
      { id: "entry-1", moduleId: MODULE_ID, kind: "REST", label: "POST /tasks", methodId: METHOD_ID, detectedBy: "RULE" }
    ],
    classes: [
      { id: CLASS_ID, moduleId: MODULE_ID, fqn: "com.example.TaskController", simpleName: "TaskController",
        packageName: "com.example", kind: "CLASS", layer: "ENTRY", file: "TaskController.java",
        lineStart: 1, lineEnd: 20, javadoc: null, status: null }
    ],
    methods: [
      { id: METHOD_ID, classId: CLASS_ID, name: "create", signature: "create()", file: "TaskController.java",
        lineStart: 10, lineEnd: 13, javadoc: null, source: "void create() { }", constructor: false, status: null }
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
  const treeBuilder = new internal.TreeBuilder(index);
  const view = new internal.GraphView(index, treeBuilder);

  const [moduleRoot] = view.roots;
  view.treeBuilder.expand(moduleRoot);
  const [entryPointNode] = moduleRoot.children;
  view.treeBuilder.expand(entryPointNode);
  const [classNode] = entryPointNode.children;
  const [methodNode] = classNode.children;

  testClickingAMethodSelectsAndToggles(view, methodNode);
  testClickingAClassOnlySelectsNeverExpands(view, classNode);

  console.log("click-behaviour.test.js: all assertions passed");
}

function testClickingAMethodSelectsAndToggles(view, methodNode) {
  assert.strictEqual(methodNode.expanded, false, "method starts collapsed (test setup check)");

  view.handleNodeClick(methodNode);

  assert.strictEqual(view.selectedMethodId, methodNode.methodId,
      "a single click on a method must open its side panel");
  assert.strictEqual(methodNode.expanded, true,
      "the same single click must also expand the method to show its callees");

  view.handleNodeClick(methodNode);
  assert.strictEqual(methodNode.expanded, false, "clicking an expanded method again must collapse it");
}

function testClickingAClassOnlySelectsNeverExpands(view, classNode) {
  const childCountBefore = classNode.children.length;

  view.handleNodeClick(classNode);

  assert.strictEqual(view.selectedMethodId, null,
      "selecting a class node must not carry over a stale selected method id");
  assert.strictEqual(classNode.children.length, childCountBefore,
      "clicking a class node must never add anything to the canvas");
  assert.strictEqual(classNode.expanded, false, "a class node never becomes 'expanded'");
}

run();
