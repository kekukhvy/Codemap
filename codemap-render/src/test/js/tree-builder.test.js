"use strict";

/**
 * A small, dependency-free smoke test for report.js's tree-building logic
 * (spec §3.1: lazy expansion, the revisit rule, and cycle termination).
 *
 * D3 itself is not exercised here — the Java side already asserts D3 is
 * inlined and the page assembles — this only proves the graph-to-tree logic
 * degrades a real recursive cycle into a single collapsed revisit node
 * instead of hanging, even with a CLASS node inserted between each method
 * hop (the class is context; the method is the step in the chain — the
 * revisit rule keys on the method, never the class).
 *
 * Both methods here are declared by the same class, so per the "no redundant
 * class header" rule a same-class callee is parented directly under the
 * calling method rather than under a second CLASS node for the class already
 * shown one hop up.
 *
 * Run with: node src/test/js/tree-builder.test.js
 */

const assert = require("assert");
const { loadReportScript } = require("./report-test-harness");

function fixtureWithCycle() {
  const moduleId = "kairos-api";
  const classId = "com.example.Service";
  const methodA = "com.example.Service#a()";
  const methodB = "com.example.Service#b()";

  return {
    modules: [{ id: moduleId, name: "kairos-api", path: "kairos-api" }],
    entryPoints: [{
      id: "entry-1", moduleId, kind: "REST", label: "POST /a", methodId: methodA, detectedBy: "RULE"
    }],
    classes: [{
      id: classId, moduleId, fqn: "com.example.Service", simpleName: "Service", packageName: "com.example",
      kind: "CLASS", layer: "APPLICATION", file: "Service.java", lineStart: 1, lineEnd: 10, javadoc: null, status: null
    }],
    methods: [
      { id: methodA, classId, name: "a", signature: "a()", file: "Service.java", lineStart: 1, lineEnd: 3,
        javadoc: null, source: "void a() { b(); }", constructor: false, status: null },
      { id: methodB, classId, name: "b", signature: "b()", file: "Service.java", lineStart: 4, lineEnd: 6,
        javadoc: null, source: "void b() { a(); }", constructor: false, status: null }
    ],
    edges: [
      { from: methodA, to: methodB, kind: "CALL_INTERNAL", resolved: true, line: 2 },
      { from: methodB, to: methodA, kind: "CALL_INTERNAL", resolved: true, line: 5 }
    ],
    moduleDependencies: [],
    removedMethods: []
  };
}

function run() {
  const data = fixtureWithCycle();
  const internal = loadReportScript(data);
  const index = new internal.CodemapIndex(data);
  const builder = new internal.TreeBuilder(index);
  const { NODE_KIND } = internal;

  const moduleRoots = builder.buildModuleRoots();
  assert.strictEqual(moduleRoots.length, 1, "one module root");

  const moduleNode = moduleRoots[0];
  builder.expand(moduleNode);
  assert.strictEqual(moduleNode.children.length, 1, "module has one entry point");

  const entryPointNode = moduleNode.children[0];
  builder.expand(entryPointNode);
  assert.strictEqual(entryPointNode.children.length, 1, "entry point expands to its owning class");
  const serviceClassNode = entryPointNode.children[0];
  assert.strictEqual(serviceClassNode.kind, NODE_KIND.CLASS, "entry point reveals the CLASS node first");
  assert.strictEqual(serviceClassNode.children.length, 1, "the class node under an entry point holds only the handler");

  const methodANode = serviceClassNode.children[0];
  assert.strictEqual(methodANode.kind, NODE_KIND.METHOD);
  assert.strictEqual(methodANode.methodId, "com.example.Service#a()");

  builder.expand(methodANode);
  assert.strictEqual(methodANode.children.length, 1, "a() calls b(), on the same class");
  const methodBNode = methodANode.children[0];
  assert.strictEqual(methodBNode.kind, NODE_KIND.METHOD,
      "a same-class callee is parented directly under the caller, with no redundant class node");
  assert.strictEqual(methodBNode.methodId, "com.example.Service#b()");

  builder.expand(methodBNode);
  assert.strictEqual(methodBNode.children.length, 1, "b() calls a() again");
  const revisitedANode = methodBNode.children[0];
  assert.strictEqual(revisitedANode.kind, NODE_KIND.METHOD);
  assert.strictEqual(revisitedANode.collapsedRevisit, true,
      "revisiting a() higher in the branch must render collapsed (spec §3.1), even through the CLASS level");
  assert.strictEqual(revisitedANode.revisitTargetKey, methodANode.nodeKey,
      "the revisit badge must link back to the original occurrence");

  // Expanding the revisited node must not recurse further — this is what
  // keeps a cycle from hanging the UI.
  builder.expand(revisitedANode);
  assert.strictEqual(revisitedANode.children.length, 0,
      "a collapsed revisit node must not materialise further children");

  console.log("tree-builder.test.js: all assertions passed");
}

run();
