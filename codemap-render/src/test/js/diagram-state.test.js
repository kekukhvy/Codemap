"use strict";

/**
 * Assertions on the diagram's box/link bookkeeping (spec 007 §6.1, §6.2): a
 * class has at most one box on the canvas ever, a second path to it links to
 * the existing box instead of drawing a duplicate, and collapsing one
 * expander path does not remove a box another expanded path still reaches
 * (reference counting).
 *
 * Run with: node src/test/js/diagram-state.test.js
 */

const assert = require("assert");
const { loadReportScript } = require("./report-test-harness");

function emptyFixture() {
  return { modules: [], entryPoints: [], classes: [], methods: [], edges: [], moduleDependencies: [], removedMethods: [] };
}

function run() {
  testEnsureBoxCreatesOnlyOnce();
  testCollapseRemovesOnlyThatExpanderPath();
  testBoxSurvivesWhileAnotherPathStillReachesIt();
  testCollapsingLastPathRemovesTheBox();
  testPrivateRowRevealAndUnreveal();

  console.log("diagram-state.test.js: all assertions passed");
}

function testEnsureBoxCreatesOnlyOnce() {
  const internal = loadReportScript(emptyFixture());
  const diagram = new internal.DiagramState();

  const first = diagram.ensureBox("com.example.Shared", "path-a");
  const second = diagram.ensureBox("com.example.Shared", "path-b");

  assert.strictEqual(first, second, "a second path to an already-drawn class must reuse the same box instance");
  assert.strictEqual(diagram.boxes.size, 1, "exactly one box must exist for the class");
}

function testCollapseRemovesOnlyThatExpanderPath() {
  const internal = loadReportScript(emptyFixture());
  const diagram = new internal.DiagramState();

  diagram.ensureBox("com.example.Shared", "path-a");
  diagram.collapse("path-a");

  assert.strictEqual(diagram.boxes.has("com.example.Shared"), false,
      "a box revealed by exactly one path must be removed when that path collapses");
}

function testBoxSurvivesWhileAnotherPathStillReachesIt() {
  const internal = loadReportScript(emptyFixture());
  const diagram = new internal.DiagramState();

  diagram.ensureBox("com.example.Shared", "path-a");
  diagram.ensureBox("com.example.Shared", "path-b");
  diagram.collapse("path-a");

  assert.strictEqual(diagram.boxes.has("com.example.Shared"), true,
      "the shared box must survive collapsing one of its two revealing paths");
}

function testCollapsingLastPathRemovesTheBox() {
  const internal = loadReportScript(emptyFixture());
  const diagram = new internal.DiagramState();

  diagram.ensureBox("com.example.Shared", "path-a");
  diagram.ensureBox("com.example.Shared", "path-b");
  diagram.collapse("path-a");
  diagram.collapse("path-b");

  assert.strictEqual(diagram.boxes.has("com.example.Shared"), false,
      "the box is removed only once every revealing path has collapsed");
}

function testPrivateRowRevealAndUnreveal() {
  const internal = loadReportScript(emptyFixture());
  const diagram = new internal.DiagramState();

  const box = diagram.ensureBox("com.example.Service", "entry-path");
  diagram.revealPrivateRow(box, "com.example.Service#validate()", "caller-path-1");
  assert.strictEqual(box.revealedPrivateMethodIds.has("com.example.Service#validate()"), true,
      "expanding a caller reveals the private row");

  diagram.revealPrivateRow(box, "com.example.Service#validate()", "caller-path-2");
  diagram.unrevealPrivateRow(box, "com.example.Service#validate()", "caller-path-1");
  assert.strictEqual(box.revealedPrivateMethodIds.has("com.example.Service#validate()"), true,
      "the private row must stay while another visible caller still reveals it");

  diagram.unrevealPrivateRow(box, "com.example.Service#validate()", "caller-path-2");
  assert.strictEqual(box.revealedPrivateMethodIds.has("com.example.Service#validate()"), false,
      "collapsing the last visible caller removes the private row again");
}

run();
