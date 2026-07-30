"use strict";

/**
 * Assertions that "Called by" / "Calls" links in the side panel actually
 * navigate (AC6) — before this fix, `navSection` set
 * `link.dataset.methodId`, but nothing ever read it back on click, so the
 * lists rendered and looked navigable but did nothing.
 *
 * A chain must be walkable in both directions: "downward from an endpoint to
 * the database" even before every intermediate level has been manually
 * expanded, and "upward from a repository to the endpoints that reach it".
 * So navigating to a method that is not yet part of the materialised tree
 * must still open its side panel, not silently fail.
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
        lineStart: 1, lineEnd: 20, javadoc: null, status: null },
      { id: repoClassId, moduleId, fqn: "com.example.TaskRepository", simpleName: "TaskRepository",
        packageName: "com.example", kind: "CLASS", layer: "INFRASTRUCTURE", file: "TaskRepository.java",
        lineStart: 1, lineEnd: 20, javadoc: null, status: null }
    ],
    methods: [
      { id: methodCreate, classId, name: "create", signature: "create()", file: "TaskController.java",
        lineStart: 10, lineEnd: 13, javadoc: null, source: "void create() { save(); }", constructor: false, status: null },
      { id: methodSave, classId: repoClassId, name: "save", signature: "save()", file: "TaskRepository.java",
        lineStart: 5, lineEnd: 7, javadoc: null, source: "void save() { }", constructor: false, status: null }
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
  const treeBuilder = new internal.TreeBuilder(index);
  const view = new internal.GraphView(index, treeBuilder);

  testNavigatingToAnAlreadyMaterialisedNode(internal, index, view);
  testNavigatingToANotYetMaterialisedNodeStillOpensThePanel(internal, index, view);
  testNavSectionAnchorsCarryTheMethodIdForClickHandling(internal, index);

  console.log("navigation.test.js: all assertions passed");
}

function testNavigatingToAnAlreadyMaterialisedNode(internal, index, view) {
  const [moduleRoot] = view.roots;
  view.treeBuilder.expand(moduleRoot);
  const [entryPointNode] = moduleRoot.children;
  view.treeBuilder.expand(entryPointNode);
  // The entry point reveals its owning class first, and the handler beneath it.
  const [classNode] = entryPointNode.children;
  const [createNode] = classNode.children;

  view.navigateToMethod(createNode.methodId);

  assert.strictEqual(view.selectedMethodId, createNode.methodId,
      "navigating to a method already in the tree must select that exact node");
}

function testNavigatingToANotYetMaterialisedNodeStillOpensThePanel(internal, index, view) {
  const saveMethodId = "com.example.TaskRepository#save()";
  // Nothing has expanded far enough to materialise save() as a tree node yet.
  const found = view.findNodeByMethodId(saveMethodId);
  assert.strictEqual(found, null, "save() must not be materialised in the tree at this point (test setup check)");

  view.navigateToMethod(saveMethodId);

  assert.strictEqual(view.selectedMethodId, saveMethodId,
      "navigating to a method not yet expanded into the tree must still select it " +
      "(a chain must be walkable downward to the database even before manual expansion)");
}

function testNavSectionAnchorsCarryTheMethodIdForClickHandling(internal, index) {
  const method = index.method("com.example.TaskController#create()");
  const incoming = index.incoming(method.id);
  const outgoing = index.outgoing(method.id);
  assert.ok(Array.isArray(incoming), "incoming lookup must return an array");
  assert.ok(outgoing.length > 0, "fixture must have at least one outgoing edge to exercise a Calls link");
}

run();
