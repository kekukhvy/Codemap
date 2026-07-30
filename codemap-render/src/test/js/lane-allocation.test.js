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

  console.log("lane-allocation.test.js: all assertions passed");
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
