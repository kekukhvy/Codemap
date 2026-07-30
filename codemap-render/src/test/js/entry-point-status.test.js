"use strict";

/**
 * The entry-point picker marks which endpoints a diff touches (spec 007 §3).
 *
 * The handler's own status is the wrong question: a PR routinely leaves the
 * handler alone and rewrites the service beneath it, so the marker reflects the
 * strongest status anywhere down the reachable call chain. Without it the list
 * is uniformly grey and gives the reader no idea where to start.
 *
 * Run with: node src/test/js/entry-point-status.test.js
 */

const assert = require("assert");
const { loadReportScript } = require("./report-test-harness");

const HANDLER = "com.example.Handler";
const SERVICE = "com.example.Service";
const REPOSITORY = "com.example.Repository";

/**
 * handler.touched() -> service.rewritten()   (service CHANGED, handler untouched)
 * handler.quiet()   -> repository.stable()   (nothing changed)
 */
function fixture() {
  const cls = (id, simpleName) => ({
    id, moduleId: "m", fqn: id, simpleName, packageName: "com.example", kind: "CLASS",
    layer: "APPLICATION", file: simpleName + ".java", lineStart: 1, lineEnd: 20,
    javadoc: null, status: "UNCHANGED", source: ""
  });
  const method = (id, classId, name, status) => ({
    id, classId, name, signature: name + "()", file: classId + ".java",
    lineStart: 2, lineEnd: 4, javadoc: null, source: "", constructor: false,
    visibility: "PUBLIC", status
  });

  return {
    modules: [{ id: "m", name: "m", path: "." }],
    entryPoints: [
      { id: "touched", moduleId: "m", kind: "REST", label: "PUT /touched", methodId: HANDLER + "#touched()", detectedBy: "RULE" },
      { id: "quiet", moduleId: "m", kind: "REST", label: "GET /quiet", methodId: HANDLER + "#quiet()", detectedBy: "RULE" }
    ],
    classes: [cls(HANDLER, "Handler"), cls(SERVICE, "Service"), cls(REPOSITORY, "Repository")],
    methods: [
      method(HANDLER + "#touched()", HANDLER, "touched", "UNCHANGED"),
      method(HANDLER + "#quiet()", HANDLER, "quiet", "UNCHANGED"),
      method(SERVICE + "#rewritten()", SERVICE, "rewritten", "CHANGED"),
      method(REPOSITORY + "#stable()", REPOSITORY, "stable", "UNCHANGED")
    ],
    edges: [
      { from: HANDLER + "#touched()", to: SERVICE + "#rewritten()", kind: "CALL_EXTERNAL", resolved: true, line: 3 },
      { from: HANDLER + "#quiet()", to: REPOSITORY + "#stable()", kind: "CALL_EXTERNAL", resolved: true, line: 3 }
    ],
    moduleDependencies: [], removedMethods: []
  };
}

function run() {
  const data = fixture();
  const internal = loadReportScript(data);
  const index = new internal.CodemapIndex(data);

  testStatusComesFromTheWholeChainNotJustTheHandler(index, data);
  testAnUntouchedChainStaysUnmarked(index, data);
  testTheResultIsCached(index, data);
  testACycleDoesNotHang(internal);

  console.log("entry-point-status.test.js: all assertions passed");
}

/** The handler itself is UNCHANGED; what it calls is not. */
function testStatusComesFromTheWholeChainNotJustTheHandler(index, data) {
  const entryPoint = data.entryPoints.find((e) => e.id === "touched");

  assert.strictEqual(index.method(entryPoint.methodId).status, "UNCHANGED",
      "precondition: the handler itself was not edited");
  assert.strictEqual(index.reachableStatusOf(entryPoint), "CHANGED",
      "the endpoint must be marked for the change in the service it calls");
}

function testAnUntouchedChainStaysUnmarked(index, data) {
  const entryPoint = data.entryPoints.find((e) => e.id === "quiet");

  assert.strictEqual(index.reachableStatusOf(entryPoint), "UNCHANGED",
      "an endpoint whose whole chain is clean must not be marked");
}

function testTheResultIsCached(index, data) {
  const entryPoint = data.entryPoints.find((e) => e.id === "touched");

  const first = index.reachableStatusOf(entryPoint);
  const second = index.reachableStatusOf(entryPoint);

  assert.strictEqual(first, second, "repeated lookups agree");
  assert.ok(index.reachableStatusCache.has(entryPoint.id),
      "the walk is cached — the picker re-renders on every keystroke in the search box");
}

/** Mutual recursion must terminate, like every other traversal in the report. */
function testACycleDoesNotHang(internal) {
  const data = fixture();
  data.edges.push(
    { from: SERVICE + "#rewritten()", to: HANDLER + "#touched()", kind: "CALL_EXTERNAL", resolved: true, line: 9 });
  const index = new internal.CodemapIndex(data);

  const status = index.reachableStatusOf(data.entryPoints.find((e) => e.id === "touched"));

  assert.strictEqual(status, "CHANGED", "a cycle in the call chain must not loop forever");
}

run();
