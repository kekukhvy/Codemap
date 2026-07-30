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
