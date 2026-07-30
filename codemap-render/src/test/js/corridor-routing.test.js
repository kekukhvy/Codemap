"use strict";

/**
 * AC11 for the corridor router: no routed segment crosses a box, and links
 * routed one after another keep off each other's tracks (spec 007 §6.4).
 *
 * These are properties, checked over many generated layouts, rather than
 * assertions about one shape. An earlier stress test only generated links
 * running left-to-right and left the endpoint boxes out of the obstacle set —
 * so it exercised the one case that already worked and reported a clean bill
 * while 108 links on a real project swept through their own rectangles.
 *
 * Run with: node src/test/js/corridor-routing.test.js
 */

const assert = require("assert");
const { loadReportScript } = require("./report-test-harness");

const EMPTY = { modules: [], entryPoints: [], classes: [], methods: [], edges: [], moduleDependencies: [], removedMethods: [] };

let seed = 20260731;
function rnd(bound) {
  seed = (seed * 1103515245 + 12345) & 0x7fffffff;
  return seed % bound;
}

function round(value) {
  return Math.round(value * 10) / 10;
}

function crossesRect(a, b, rect) {
  const pad = 0.5;
  return Math.min(a.x, b.x) < rect.x + rect.width - pad && Math.max(a.x, b.x) > rect.x + pad
      && Math.min(a.y, b.y) < rect.y + rect.height - pad && Math.max(a.y, b.y) > rect.y + pad;
}

/** Generates a layout of variable-width boxes in a few columns. */
function generateLayout() {
  const boxes = [];
  let x = 200;
  const columns = 2 + rnd(3);
  for (let column = 0; column < columns; column++) {
    const width = 220 + rnd(200);
    const count = 2 + rnd(3);
    for (let row = 0; row < count; row++) {
      boxes.push({
        id: "c" + column + "b" + row,
        rect: { x, y: 40 + row * 190 + rnd(40), width, height: 70 + rnd(110) }
      });
    }
    x += width + 90;
  }
  return boxes;
}

/** Links in every direction: forward, within one column, and backward. */
function generateLinks(boxes) {
  const links = [];
  for (let i = 0; i < 10; i++) {
    const from = boxes[rnd(boxes.length)];
    const to = boxes[rnd(boxes.length)];
    if (from === to) {
      continue;
    }
    links.push({
      id: "l" + i,
      lane: links.length,
      selfLink: false,
      from: { rect: from.rect, rowY: from.rect.y + 25 + rnd(Math.max(1, from.rect.height - 40)), box: from },
      to: { rect: to.rect, rowY: to.rect.y + 25 + rnd(Math.max(1, to.rect.height - 40)), box: to }
    });
  }
  return links;
}

function run() {
  const internal = loadReportScript(EMPTY);

  testNoSegmentEverCrossesABox(internal);
  testParallelLinksTakeDifferentTracks(internal);
  testEveryRouteIsStrictlyOrthogonal(internal);
  testRoutesAroundABoxDirectlyInTheWay(internal);
  testAPathNeverSweepsThroughItsOwnEndpointBox(internal);

  console.log("corridor-routing.test.js: all assertions passed");
}

/** The invariant: including the link's own endpoint boxes, which it must not cut through. */
function testNoSegmentEverCrossesABox(internal) {
  let routed = 0;

  for (let trial = 0; trial < 120; trial++) {
    const boxes = generateLayout();
    const links = generateLinks(boxes);
    const reserved = new Set();

    for (const link of links) {
      const obstacles = boxes
          .filter((box) => box !== link.from.box && box !== link.to.box)
          .map((box) => ({ rect: box.rect }));
      const points = internal.routeOrthogonalLink(link, obstacles, reserved);
      if (!points || points.length < 2) {
        continue;
      }
      routed++;
      internal.reserveTraversedSegments(points, reserved);

      for (let i = 0; i + 1 < points.length; i++) {
        for (const box of boxes) {
          const isOwnEndpoint = box.rect === link.from.rect || box.rect === link.to.rect;
          if (isOwnEndpoint) {
            continue;
          }
          assert.ok(!crossesRect(points[i], points[i + 1], box.rect),
              `trial ${trial} ${link.id}: segment (${round(points[i].x)},${round(points[i].y)})`
              + `-(${round(points[i + 1].x)},${round(points[i + 1].y)}) crosses ${box.id}`);
        }
      }
    }
  }

  assert.ok(routed > 400, "the sweep must actually route a meaningful number of links, got " + routed);
}

/** Reserving one route must push the next one onto different ground. */
function testParallelLinksTakeDifferentTracks(internal) {
  const link = (lane) => ({
    lane, selfLink: false,
    from: { rect: { x: 0, y: 0, width: 200, height: 100 }, rowY: 30 },
    to: { rect: { x: 400, y: 200, width: 200, height: 100 }, rowY: 230 }
  });

  const reserved = new Set();
  const shapes = new Set();
  for (const lane of [0, 1, 2]) {
    const points = internal.routeOrthogonalLink(link(lane), [], reserved);
    shapes.add(JSON.stringify(points));
    internal.reserveTraversedSegments(points, reserved);
  }

  assert.strictEqual(shapes.size, 3,
      "three links between the same two rows must take three distinct routes, not stack on one");
}

function testEveryRouteIsStrictlyOrthogonal(internal) {
  for (let trial = 0; trial < 40; trial++) {
    const boxes = generateLayout();
    const reserved = new Set();
    for (const link of generateLinks(boxes)) {
      const obstacles = boxes
          .filter((box) => box !== link.from.box && box !== link.to.box)
          .map((box) => ({ rect: box.rect }));
      const points = internal.routeOrthogonalLink(link, obstacles, reserved);
      for (let i = 0; i + 1 < points.length; i++) {
        const sameX = round(points[i].x) === round(points[i + 1].x);
        const sameY = round(points[i].y) === round(points[i + 1].y);
        assert.ok(sameX || sameY, "every segment must be axis-aligned");
      }
    }
  }
}

/** The case a fixed template could never handle: something square in the path. */
function testRoutesAroundABoxDirectlyInTheWay(internal) {
  const link = {
    lane: 0, selfLink: false,
    from: { rect: { x: 0, y: 0, width: 200, height: 60 }, rowY: 30 },
    to: { rect: { x: 600, y: 0, width: 200, height: 60 }, rowY: 30 }
  };
  const blocker = { rect: { x: 300, y: -50, width: 200, height: 200 } };

  const points = internal.routeOrthogonalLink(link, [blocker], new Set());

  for (let i = 0; i + 1 < points.length; i++) {
    assert.ok(!crossesRect(points[i], points[i + 1], blocker.rect),
        "the route must go around a box sitting squarely between the two rows");
  }
}

/**
 * The endpoint boxes were modelled as walls with a slot at the attachment row,
 * so a path could enter one side and leave the other — sweeping the whole box
 * on the way. A link starts on the source's edge and ends on the target's, so
 * it never needs to be inside either.
 */
function testAPathNeverSweepsThroughItsOwnEndpointBox(internal) {
  // Target to the LEFT of the source: the case that produced the sweep.
  const link = {
    lane: 0, selfLink: false,
    from: { rect: { x: 600, y: 40, width: 284, height: 98 }, rowY: 106 },
    to: { rect: { x: 200, y: 300, width: 240, height: 80 }, rowY: 340 }
  };

  const points = internal.routeOrthogonalLink(link, [], new Set());

  for (let i = 0; i + 1 < points.length; i++) {
    for (const [name, rect] of [["source", link.from.rect], ["target", link.to.rect]]) {
      assert.ok(!crossesRect(points[i], points[i + 1], rect),
          `segment (${points[i].x},${points[i].y})-(${points[i + 1].x},${points[i + 1].y})`
          + ` runs through its own ${name} box`);
    }
  }
}

run();
