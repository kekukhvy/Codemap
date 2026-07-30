"use strict";

/**
 * Assertions on lane allocation between two columns (spec 007 §6.4.1/§6.4.4):
 * every link crossing the same inter-column gap gets its own lane offset, so
 * two parallel relationships never collapse onto the same corridor.
 *
 * Run with: node src/test/js/lane-allocation.test.js
 */

const assert = require("assert");
const { loadReportScript } = require("./report-test-harness");

function emptyFixture() {
  return { modules: [], entryPoints: [], classes: [], methods: [], edges: [], moduleDependencies: [], removedMethods: [] };
}

function run() {
  testEveryLinkGetsADistinctLane();
  testLaneAssignmentIsDeterministic();
  testLanesAreScopedPerGapNotGlobally();

  console.log("lane-allocation.test.js: all assertions passed");
}

function testLanesAreScopedPerGapNotGlobally() {
  const internal = loadReportScript(emptyFixture());

  // Two links share the A->X gap; one unrelated link crosses a different B->Y
  // gap. The renderer groups by gap and allocates lanes within each group
  // (spec 007 §6.4.1) — this is the function the live rendering path uses,
  // not a parallel hand-rolled counter (DRY).
  const links = [
    { id: "link-1", source: "A", target: "X" },
    { id: "link-2", source: "A", target: "X" },
    { id: "link-3", source: "B", target: "Y" }
  ];

  const lanes = internal.assignLanesByGap(links, (link) => link.source + ">" + link.target);

  assert.notStrictEqual(lanes.get("link-1"), lanes.get("link-2"),
      "two links in the same gap must receive distinct lanes");
  assert.strictEqual(lanes.get("link-3"), 0, "a lone link in its own gap starts at lane 0");
}

function testEveryLinkGetsADistinctLane() {
  const internal = loadReportScript(emptyFixture());

  const links = [
    { id: "link-1", source: "A", target: "X" },
    { id: "link-2", source: "A", target: "Y" },
    { id: "link-3", source: "B", target: "X" }
  ];

  const lanes = internal.allocateLanes(links);

  const laneValues = links.map((link) => lanes.get(link.id));
  const distinctLanes = new Set(laneValues);
  assert.strictEqual(distinctLanes.size, links.length, "every link in the same gap must receive its own lane");
}

function testLaneAssignmentIsDeterministic() {
  const internal = loadReportScript(emptyFixture());
  const links = [
    { id: "link-1", source: "A", target: "X" },
    { id: "link-2", source: "A", target: "Y" }
  ];

  const first = internal.allocateLanes(links);
  const second = internal.allocateLanes(links);

  assert.strictEqual(first.get("link-1"), second.get("link-1"), "lane allocation must be stable across calls");
  assert.strictEqual(first.get("link-2"), second.get("link-2"), "lane allocation must be stable across calls");
}

run();
