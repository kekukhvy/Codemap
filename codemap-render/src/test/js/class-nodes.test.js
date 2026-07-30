"use strict";

/**
 * Assertions on the CLASS/METHOD interaction model: the class is context,
 * the method is the step in the chain (spec §3.2.1's CLASS node kind, wired
 * into the tree for the first time).
 *
 * - An entry point reveals its owning class, and only the one method that
 *   handles it — not every method the class declares.
 * - Expanding a method reveals, per outgoing call, the callee's owning class
 *   and the called method beneath it — except when the callee's class is
 *   the same as the caller's class already shown one hop up, where the
 *   class header would be redundant.
 * - A CLASS node never expands into the diagram; it exists only so the side
 *   panel can show the class and its declared methods.
 *
 * Run with: node src/test/js/class-nodes.test.js
 */

const assert = require("assert");
const { loadReportScript } = require("./report-test-harness");

const API_MODULE = "kairos-api";
const CONTROLLER_CLASS = "com.example.TaskController";
const REPOSITORY_CLASS = "com.example.TaskRepository";
const METHOD_UPDATE = "com.example.TaskController#update()";
const METHOD_VALIDATE = "com.example.TaskController#validate()";
const METHOD_SAVE = "com.example.TaskRepository#save()";

function fixture() {
  return {
    modules: [{ id: API_MODULE, name: "kairos-api", path: API_MODULE }],
    entryPoints: [
      { id: "entry-1", moduleId: API_MODULE, kind: "REST", label: "PUT /tasks/{id}",
        methodId: METHOD_UPDATE, detectedBy: "RULE" }
    ],
    classes: [
      { id: CONTROLLER_CLASS, moduleId: API_MODULE, fqn: "com.example.TaskController",
        simpleName: "TaskController", packageName: "com.example", kind: "CLASS", layer: "ENTRY",
        file: "TaskController.java", lineStart: 1, lineEnd: 40, javadoc: null, status: null },
      { id: REPOSITORY_CLASS, moduleId: API_MODULE, fqn: "com.example.TaskRepository",
        simpleName: "TaskRepository", packageName: "com.example", kind: "CLASS", layer: "INFRASTRUCTURE",
        file: "TaskRepository.java", lineStart: 1, lineEnd: 20, javadoc: null, status: null }
    ],
    methods: [
      { id: METHOD_UPDATE, classId: CONTROLLER_CLASS, name: "update", signature: "update()",
        file: "TaskController.java", lineStart: 10, lineEnd: 14, javadoc: null,
        source: "void update() { validate(); repository.save(); }", constructor: false, status: null },
      { id: METHOD_VALIDATE, classId: CONTROLLER_CLASS, name: "validate", signature: "validate()",
        file: "TaskController.java", lineStart: 16, lineEnd: 18, javadoc: null,
        source: "void validate() { }", constructor: false, status: null },
      // A third method on TaskController that nothing calls — must NOT appear
      // under the entry point's class node, since only the handler does.
      { id: "com.example.TaskController#unrelated()", classId: CONTROLLER_CLASS, name: "unrelated",
        signature: "unrelated()", file: "TaskController.java", lineStart: 20, lineEnd: 22, javadoc: null,
        source: "void unrelated() { }", constructor: false, status: null },
      { id: METHOD_SAVE, classId: REPOSITORY_CLASS, name: "save", signature: "save()",
        file: "TaskRepository.java", lineStart: 5, lineEnd: 7, javadoc: null,
        source: "void save() { }", constructor: false, status: null }
    ],
    edges: [
      { from: METHOD_UPDATE, to: METHOD_VALIDATE, kind: "CALL_INTERNAL", resolved: true, line: 11 },
      { from: METHOD_UPDATE, to: METHOD_SAVE, kind: "CALL_EXTERNAL", resolved: true, line: 12 }
    ],
    moduleDependencies: [],
    removedMethods: []
  };
}

function run() {
  const data = fixture();
  const internal = loadReportScript(data);
  const index = new internal.CodemapIndex(data);
  const builder = new internal.TreeBuilder(index);
  const { NODE_KIND } = internal;

  const [moduleRoot] = builder.buildModuleRoots();
  builder.expand(moduleRoot);
  const [entryPointNode] = moduleRoot.children;
  builder.expand(entryPointNode);

  testEntryPointRevealsOwningClassWithOnlyTheHandler(entryPointNode, NODE_KIND);
  const controllerClassNode = entryPointNode.children[0];
  const updateNode = controllerClassNode.children[0];

  testHandlerIsMarkedFocused(updateNode);
  testClassNodeNeverExpandsIntoTheDiagram(builder, controllerClassNode);

  builder.expand(updateNode);
  testExpandingAMethodShowsClassPlusMethodPerCallee(updateNode, NODE_KIND);
  testSameClassCalleeSkipsTheRedundantClassNode(updateNode, controllerClassNode, NODE_KIND);
  testCrossClassCalleeGetsItsOwnClassNode(updateNode, NODE_KIND);
  testInternalEdgeStillCarriesItsKindForDashedStyling(updateNode);

  console.log("class-nodes.test.js: all assertions passed");
}

function testEntryPointRevealsOwningClassWithOnlyTheHandler(entryPointNode, NODE_KIND) {
  assert.strictEqual(entryPointNode.children.length, 1, "entry point reveals exactly one class node");
  const classNode = entryPointNode.children[0];
  assert.strictEqual(classNode.kind, NODE_KIND.CLASS);
  assert.strictEqual(classNode.classId, CONTROLLER_CLASS);
  assert.strictEqual(classNode.children.length, 1,
      "the class node under an entry point must show only the handling method, not every method it declares");
  assert.strictEqual(classNode.children[0].methodId, METHOD_UPDATE);
}

function testHandlerIsMarkedFocused(updateNode) {
  assert.strictEqual(updateNode.focused, true,
      "the handling/called method must be visually distinguishable as the point of this hop");
}

function testClassNodeNeverExpandsIntoTheDiagram(builder, classNode) {
  builder.expand(classNode);
  assert.strictEqual(classNode.children.length, 1,
      "expanding a CLASS node must not add anything to the canvas — it only ever shows the one method already there");
  assert.strictEqual(classNode.expanded, false,
      "a CLASS node is not an expandable tree node; clicking it opens the side panel instead");
}

function testExpandingAMethodShowsClassPlusMethodPerCallee(updateNode, NODE_KIND) {
  assert.strictEqual(updateNode.children.length, 2, "update() has two outgoing calls");
}

function testSameClassCalleeSkipsTheRedundantClassNode(updateNode, controllerClassNode, NODE_KIND) {
  const validateHop = updateNode.children.find((child) =>
      child.methodId === METHOD_VALIDATE || (child.kind === NODE_KIND.CLASS && child.classId === CONTROLLER_CLASS));
  assert.ok(validateHop, "the same-class call to validate() must appear somewhere under update()");
  assert.strictEqual(validateHop.kind, NODE_KIND.METHOD,
      "a same-class callee must be parented directly under the caller, not behind a second class node " +
      "for the class already shown one hop up");
  assert.strictEqual(validateHop.methodId, METHOD_VALIDATE);
  assert.strictEqual(validateHop.focused, true, "the callee itself is still the point of this hop");
}

function testCrossClassCalleeGetsItsOwnClassNode(updateNode, NODE_KIND) {
  const repositoryHop = updateNode.children.find((child) => child.kind === NODE_KIND.CLASS);
  assert.ok(repositoryHop, "a call into a different class must show that class's own node");
  assert.strictEqual(repositoryHop.classId, REPOSITORY_CLASS);
  assert.strictEqual(repositoryHop.children.length, 1, "the cross-class node holds only the called method");
  assert.strictEqual(repositoryHop.children[0].methodId, METHOD_SAVE);
  assert.strictEqual(repositoryHop.children[0].focused, true);
}

function testInternalEdgeStillCarriesItsKindForDashedStyling(updateNode) {
  const validateHop = updateNode.children.find((child) => child.methodId === METHOD_VALIDATE);
  assert.strictEqual(validateHop.edgeKind, "CALL_INTERNAL",
      "the edge kind must survive onto the method node so CALL_INTERNAL still renders dashed (spec §3.3)");
}

run();
