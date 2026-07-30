"use strict";

/**
 * Assertions that search, layer, and module filters actually change what is
 * drawn (AC13) — not just that state is stored.
 *
 * Before this fix, `matchesFilters` existed but nothing ever called it: the
 * render path only consulted `visibleRoots()` for focus-on-changes, so search
 * and layer/module selects updated state and re-rendered the exact same tree.
 *
 * Tree semantics chosen here (documented again in report.js): a node survives
 * filtering if it matches itself, OR if any descendant already materialised
 * in the (lazily expanded) tree matches — so a matching leaf stays reachable
 * through its ancestors rather than being orphaned from the roots.
 *
 * Run with: node src/test/js/filters.test.js
 */

const assert = require("assert");
const { loadReportScript } = require("./report-test-harness");

function fixtureWithTwoModulesAndLayers() {
  const moduleA = "kairos-api";
  const moduleB = "kairos-admin";
  const classId = "com.example.TaskController";
  const otherClassId = "com.example.TaskRepository";
  const methodCreate = "com.example.TaskController#create()";
  const methodSave = "com.example.TaskRepository#save()";

  return {
    modules: [
      { id: moduleA, name: "kairos-api", path: moduleA },
      { id: moduleB, name: "kairos-admin", path: moduleB }
    ],
    entryPoints: [
      { id: "entry-1", moduleId: moduleA, kind: "REST", label: "POST /tasks", methodId: methodCreate, detectedBy: "RULE" }
    ],
    classes: [
      { id: classId, moduleId: moduleA, fqn: "com.example.TaskController", simpleName: "TaskController",
        packageName: "com.example", kind: "CLASS", layer: "ENTRY", file: "TaskController.java",
        lineStart: 1, lineEnd: 20, javadoc: null, status: null },
      { id: otherClassId, moduleId: moduleA, fqn: "com.example.TaskRepository", simpleName: "TaskRepository",
        packageName: "com.example", kind: "CLASS", layer: "INFRASTRUCTURE", file: "TaskRepository.java",
        lineStart: 1, lineEnd: 20, javadoc: null, status: null }
    ],
    methods: [
      { id: methodCreate, classId, name: "create", signature: "create()", file: "TaskController.java",
        lineStart: 10, lineEnd: 13, javadoc: null, source: "void create() { save(); }", constructor: false, status: null },
      { id: methodSave, classId: otherClassId, name: "save", signature: "save()", file: "TaskRepository.java",
        lineStart: 5, lineEnd: 7, javadoc: null, source: "void save() { }", constructor: false, status: null }
    ],
    edges: [
      { from: methodCreate, to: methodSave, kind: "CALL_EXTERNAL", resolved: true, line: 11 }
    ],
    moduleDependencies: [],
    removedMethods: []
  };
}

function buildFullyExpandedTree(internal, index) {
  const builder = new internal.TreeBuilder(index);
  const [moduleRoot] = builder.buildModuleRoots();
  builder.expand(moduleRoot);
  const [entryPointNode] = moduleRoot.children;
  builder.expand(entryPointNode);
  // entry point -> [class: TaskController] -> create() -> [class: TaskRepository] -> save()
  const [controllerClassNode] = entryPointNode.children;
  const [createNode] = controllerClassNode.children;
  builder.expand(createNode);
  return { moduleRoot, entryPointNode, controllerClassNode, createNode };
}

function run() {
  const data = fixtureWithTwoModulesAndLayers();
  const internal = loadReportScript(data);
  const index = new internal.CodemapIndex(data);

  testSearchKeepsMatchingLeafAndItsAncestors(internal, index, data);
  testSearchWithNoMatchPrunesEverything(internal, index, data);
  testLayerFilterKeepsOnlyMatchingLayer(internal, index, data);
  testModuleFilterKeepsOnlyThatModuleRoot(internal, index, data);

  console.log("filters.test.js: all assertions passed");
}

function testSearchKeepsMatchingLeafAndItsAncestors(internal, index, data) {
  const { moduleRoot } = buildFullyExpandedTree(internal, index);
  // "save" only matches the callee three levels down (through the
  // TaskRepository class node); the search must not orphan it by pruning
  // its module/entry-point/class ancestors.
  const pruned = internal.pruneByFilters([moduleRoot], (node) => internal.nodeMatchesSearch(node, index, "save"));

  assert.strictEqual(pruned.length, 1, "the module root must survive because a descendant matches");
  const entryPointNode = pruned[0].children[0];
  assert.ok(entryPointNode, "the entry point must survive as an ancestor of the match");
  const controllerClassNode = entryPointNode.children[0];
  assert.ok(controllerClassNode, "the owning class node must survive as an ancestor of the match");
  const createNode = controllerClassNode.children[0];
  assert.ok(createNode, "create() must survive as an ancestor of the match, even though its own label does not match");
  const repositoryClassNode = createNode.children[0];
  assert.ok(repositoryClassNode, "the callee's class node must survive as an ancestor of the match");
  const saveNode = repositoryClassNode.children[0];
  assert.ok(saveNode, "save() itself must survive: it is the actual match");
}

function testSearchWithNoMatchPrunesEverything(internal, index, data) {
  const { moduleRoot } = buildFullyExpandedTree(internal, index);
  const pruned = internal.pruneByFilters([moduleRoot], (node) => internal.nodeMatchesSearch(node, index, "nonexistent"));

  assert.strictEqual(pruned.length, 0, "a search with no match anywhere in the tree must prune every root");
}

function testLayerFilterKeepsOnlyMatchingLayer(internal, index, data) {
  const { moduleRoot } = buildFullyExpandedTree(internal, index);
  // create() is ENTRY layer, save() is INFRASTRUCTURE.
  const pruned = internal.pruneByFilters([moduleRoot], (node) => internal.nodeMatchesLayer(node, index, "INFRASTRUCTURE"));

  const entryPointNode = pruned[0].children[0];
  const controllerClassNode = entryPointNode.children[0];
  const createNode = controllerClassNode.children[0];
  assert.ok(createNode, "create() survives as an ancestor of the INFRASTRUCTURE match");
  const repositoryClassNode = createNode.children[0];
  assert.ok(repositoryClassNode, "the callee's class node survives as an ancestor of the match");
  const saveNode = repositoryClassNode.children[0];
  assert.ok(saveNode, "save() is the INFRASTRUCTURE match and must survive");
}

function testModuleFilterKeepsOnlyThatModuleRoot(internal, index, data) {
  const builder = new internal.TreeBuilder(index);
  const roots = builder.buildModuleRoots();
  assert.strictEqual(roots.length, 2, "fixture has two module roots");

  const filtered = internal.filterModuleRoots(roots, "kairos-admin");
  assert.strictEqual(filtered.length, 1, "the module filter must keep only the selected module's root");
  assert.strictEqual(filtered[0].id, "kairos-admin");

  const unfiltered = internal.filterModuleRoots(roots, "");
  assert.strictEqual(unfiltered.length, 2, "an empty module filter must keep every root");
}

run();
