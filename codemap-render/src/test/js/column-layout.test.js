"use strict";

/**
 * Assertions on layered-column placement (spec 007 §6.3): boxes are columned
 * by call depth from the entry point, a class reached at two depths sits in
 * the shallowest, and a barycentre pass orders each column deterministically
 * to reduce link crossings.
 *
 * Run with: node src/test/js/column-layout.test.js
 */

const assert = require("assert");
const { loadReportScript } = require("./report-test-harness");

function emptyFixture() {
  return { modules: [], entryPoints: [], classes: [], methods: [], edges: [], moduleDependencies: [], removedMethods: [] };
}

function run() {
  testShallowestColumnWins();
  testBarycentreOrderingIsDeterministic();
  testBarycentreOrderingMinimisesCrossing();

  console.log("column-layout.test.js: all assertions passed");
}

function testShallowestColumnWins() {
  const internal = loadReportScript(emptyFixture());

  // pill(0) -> A(1) -> B(2) -> C(3), and also A(1) -> C(2) directly.
  const boxes = ["pill", "A", "B", "C"];
  const links = [
    { source: "pill", target: "A" },
    { source: "A", target: "B" },
    { source: "B", target: "C" },
    { source: "A", target: "C" }
  ];

  const columns = internal.assignColumns(boxes, links, "pill");

  assert.strictEqual(columns.get("pill"), 0);
  assert.strictEqual(columns.get("A"), 1);
  assert.strictEqual(columns.get("B"), 2);
  assert.strictEqual(columns.get("C"), 2, "C is reached at depth 3 via B and depth 2 via A; the shallowest wins");
}

function testBarycentreOrderingIsDeterministic() {
  const internal = loadReportScript(emptyFixture());

  const columns = new Map([["root", 0], ["A", 1], ["B", 1], ["C", 1]]);
  const links = [
    { source: "root", target: "A" },
    { source: "root", target: "B" },
    { source: "root", target: "C" }
  ];
  const positions = { root: 0 };

  const first = internal.orderColumnByBarycentre(["C", "A", "B"], links, positions);
  const second = internal.orderColumnByBarycentre(["C", "A", "B"], links, positions);

  assert.deepStrictEqual([...first], [...second], "the same input must always produce the same order");
}

function testBarycentreOrderingMinimisesCrossing() {
  const internal = loadReportScript(emptyFixture());

  // Parents "P1" at y=0 connects to "X"; "P2" at y=1 connects to "Y". Column
  // order should place X above Y to avoid the links crossing.
  const links = [
    { source: "P1", target: "X" },
    { source: "P2", target: "Y" }
  ];
  const parentPositions = { P1: 0, P2: 1 };

  const ordered = internal.orderColumnByBarycentre(["Y", "X"], links, parentPositions);

  assert.deepStrictEqual([...ordered], ["X", "Y"], "barycentre ordering should place X (parent at 0) before Y (parent at 1)");
}

run();
