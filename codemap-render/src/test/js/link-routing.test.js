"use strict";

/**
 * Assertions on orthogonal link routing (spec 007 §6.4, AC11): no routed
 * segment may pass through a box rectangle, and no two links may share a
 * segment — each gets its own lane offset.
 *
 * Run with: node src/test/js/link-routing.test.js
 */

const assert = require("assert");
const { loadReportScript } = require("./report-test-harness");

function emptyFixture() {
  return { modules: [], entryPoints: [], classes: [], methods: [], edges: [], moduleDependencies: [], removedMethods: [] };
}

function rect(x, y, width, height) {
  return { x, y, width, height };
}

/** True when a horizontal or vertical segment overlaps a rectangle's interior. */
function segmentIntersectsRect(segment, box) {
  const [a, b] = segment;
  const minX = Math.min(a.x, b.x);
  const maxX = Math.max(a.x, b.x);
  const minY = Math.min(a.y, b.y);
  const maxY = Math.max(a.y, b.y);
  const overlapsX = maxX > box.x && minX < box.x + box.width;
  const overlapsY = maxY > box.y && minY < box.y + box.height;
  return overlapsX && overlapsY;
}

function segmentsOf(polyline) {
  const segments = [];
  for (let i = 1; i < polyline.length; i++) {
    segments.push([polyline[i - 1], polyline[i]]);
  }
  return segments;
}

function segmentKey(segment) {
  const [a, b] = segment;
  return [a.x, a.y, b.x, b.y].join(",");
}

function run() {
  testNoSegmentCrossesAnObstacleBox();
  testNoTwoLinksShareASegment();
  testSelfLinkRoutesOutAndBackWithALoop();
  testEveryPolylineSegmentIsAxisAligned();
  testSelfLinkLoopClearsANeighbourBoxInTheSameColumn();
  testSelfLinkLoopClearsTheNextColumnEvenAtAHighLane();
  testClampedSelfLinkLoopsStayDistinctPerLane();

  console.log("link-routing.test.js: all assertions passed");
}

function testNoSegmentCrossesAnObstacleBox() {
  const internal = loadReportScript(emptyFixture());

  // Source box on the left, obstacle box directly between source and target,
  // target box on the right — a naive straight line would cut through it.
  const source = { id: "Source", rect: rect(0, 0, 100, 40), rowY: 20 };
  const obstacle = { id: "Obstacle", rect: rect(150, 0, 100, 200), rowY: 100 };
  const target = { id: "Target", rect: rect(300, 300, 100, 40), rowY: 320 };

  const link = { from: source, to: target, lane: 0 };
  const polyline = internal.routeOrthogonalLink(link, [obstacle]);

  for (const segment of segmentsOf(polyline)) {
    assert.strictEqual(segmentIntersectsRect(segment, obstacle.rect), false,
        "no routed segment may pass through an obstacle box's rectangle");
  }
}

function testNoTwoLinksShareASegment() {
  const internal = loadReportScript(emptyFixture());

  const source = { id: "Source", rect: rect(0, 0, 100, 80), rowY: 20 };
  const targetA = { id: "TargetA", rect: rect(300, 0, 100, 40), rowY: 20 };
  const targetB = { id: "TargetB", rect: rect(300, 100, 100, 40), rowY: 120 };

  const linkA = { from: source, to: targetA, lane: 0 };
  const linkB = { from: source, to: targetB, lane: 1 };

  const polylineA = internal.routeOrthogonalLink(linkA, []);
  const polylineB = internal.routeOrthogonalLink(linkB, []);

  const keysA = new Set(segmentsOf(polylineA).map(segmentKey));
  const keysB = segmentsOf(polylineB).map(segmentKey);
  for (const key of keysB) {
    assert.strictEqual(keysA.has(key), false, "two links assigned different lanes must not share a segment");
  }
}

function testSelfLinkRoutesOutAndBackWithALoop() {
  const internal = loadReportScript(emptyFixture());

  const box = { id: "Self", rect: rect(0, 0, 160, 100), rowY: 40 };
  const link = { from: box, to: box, lane: 0, selfLink: true };

  const polyline = internal.routeOrthogonalLink(link, []);

  assert.ok(polyline.length >= 4, "a self-link loop needs at least 4 points to go out and back");
  const startsAndEndsOnTheBoxEdge = polyline[0].x >= box.rect.x + box.rect.width
      && polyline[polyline.length - 1].x >= box.rect.x + box.rect.width;
  assert.ok(startsAndEndsOnTheBoxEdge, "a self-link departs and returns on the same side of its own box");
}

function testSelfLinkLoopClearsANeighbourBoxInTheSameColumn() {
  const internal = loadReportScript(emptyFixture());

  // A self-linking box with a neighbour directly below it, in the same
  // column, close enough that the self-link's default loop width would cut
  // through the neighbour's rectangle (this reproduces a real diagram found
  // running against Kairos: a Vaadin view's constructor calling its own
  // builder methods, stacked directly above the component box it also links
  // to at the same column x).
  const box = { id: "Self", rect: rect(0, 0, 160, 40), rowY: 20 };
  const neighbour = { id: "Neighbour", rect: rect(0, 60, 160, 200), rowY: 100 };
  const link = { from: box, to: box, lane: 0, selfLink: true };

  const polyline = internal.routeOrthogonalLink(link, [neighbour]);

  for (const segment of segmentsOf(polyline)) {
    assert.strictEqual(segmentIntersectsRect(segment, neighbour.rect), false,
        "a self-link's loop must be routed clear of a neighbour box in the same column (spec 007 §6.4.3)");
  }
}

function testSelfLinkLoopClearsTheNextColumnEvenAtAHighLane() {
  const internal = loadReportScript(emptyFixture());

  // Reproduces a diagram found running against Kairos: a class with many
  // self-links (a constructor calling several of its own builder methods)
  // pushes the self-link loop lane high enough that a fixed per-lane offset
  // reaches into the next column's boxes — the loop must stay clear of
  // whatever obstacles it is given, however high its lane index is.
  const boxRect = rect(290, 40, 220, 284);
  const sourceRow = { rect: boxRect, rowY: 80 };
  const targetRow = { rect: boxRect, rowY: 152 };
  const nextColumnBox = { id: "NextColumn", rect: rect(600, 40, 220, 178), rowY: 100 };
  const link = { from: sourceRow, to: targetRow, lane: 7, selfLink: true };

  const polyline = internal.routeOrthogonalLink(link, [nextColumnBox]);

  for (const segment of segmentsOf(polyline)) {
    assert.strictEqual(segmentIntersectsRect(segment, nextColumnBox.rect), false,
        "a self-link loop must never reach into a following column's box, regardless of its lane index (spec 007 §6.4.3)");
  }
}

function testClampedSelfLinkLoopsStayDistinctPerLane() {
  const internal = loadReportScript(emptyFixture());

  // Reproduces the exact geometry found running against Kairos: five
  // self-links sharing one source row (a constructor calling five of its own
  // builder methods), all clamped by the same following-column obstacle.
  // Clamping to a single flat ceiling collapsed every one of their outbound
  // segments onto the same `(exitX, sourceY) -> (ceiling, sourceY)` run,
  // which violates "no two links share a segment" (AC11) just as surely as
  // crossing a box does.
  const boxRect = rect(290, 40, 220, 284);
  const sourceRow = { rect: boxRect, rowY: 80 };
  const obstacle = { id: "NextColumn", rect: rect(600, 40, 220, 178), rowY: 100 };
  const lanes = [5, 6, 7, 8, 9];

  const loopXs = lanes.map((lane) => {
    const targetRow = { rect: boxRect, rowY: 100 + lane * 20 };
    const link = { from: sourceRow, to: targetRow, lane, selfLink: true };
    return internal.routeOrthogonalLink(link, [obstacle])[1].x; // the loop's vertical-run x
  });

  const distinctLoopXs = new Set(loopXs);
  assert.strictEqual(distinctLoopXs.size, lanes.length,
      "every self-link lane must produce a distinct loop x even once obstacle-clamped (spec 007 §6.4.4, AC11)");
}

function testEveryPolylineSegmentIsAxisAligned() {
  const internal = loadReportScript(emptyFixture());

  const source = { id: "Source", rect: rect(0, 0, 100, 40), rowY: 20 };
  const target = { id: "Target", rect: rect(300, 150, 100, 40), rowY: 170 };
  const link = { from: source, to: target, lane: 0 };

  const polyline = internal.routeOrthogonalLink(link, []);

  for (const [a, b] of segmentsOf(polyline)) {
    const horizontal = a.y === b.y;
    const vertical = a.x === b.x;
    assert.ok(horizontal || vertical, "every segment must be purely horizontal or vertical (orthogonal routing)");
  }
}

run();
