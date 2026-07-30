"use strict";

/**
 * Assertions on GraphView's initial viewport placement.
 *
 * The bug a Chrome inspection caught: the viewport `<g>` started with
 * `transform: null`, so the whole tree sat exactly on the SVG's top-left
 * corner and was half clipped, and the zoom behaviour's own transform state
 * disagreed with what was on screen — the first pan/zoom gesture would have
 * jumped. This asserts both: the viewport receives a non-identity initial
 * transform, and the zoom behaviour is told the same value.
 *
 * Run with: node src/test/js/initial-transform.test.js
 */

const assert = require("assert");
const { loadReportScript } = require("./report-test-harness");

function emptyProjectFixture() {
  return {
    modules: [{ id: "kairos-api", name: "kairos-api", path: "kairos-api" }],
    entryPoints: [],
    classes: [],
    methods: [],
    edges: [],
    moduleDependencies: [],
    removedMethods: []
  };
}

function run() {
  const data = emptyProjectFixture();
  const viewportTransforms = [];
  const zoomBehaviorTransforms = [];
  const internal = loadReportScript(data, { viewportTransforms, zoomBehaviorTransforms });
  const index = new internal.CodemapIndex(data);
  const treeBuilder = new internal.TreeBuilder(index);

  new internal.GraphView(index, treeBuilder);

  assert.ok(viewportTransforms.length > 0,
      "the viewport must receive an initial transform rather than staying at the implicit identity (null)");
  const initialViewportTransform = viewportTransforms[0];
  assert.ok(initialViewportTransform.x > 0 || initialViewportTransform.y > 0,
      "the initial transform must move the tree away from the SVG's top-left corner, " +
      `got x=${initialViewportTransform.x}, y=${initialViewportTransform.y}`);

  assert.ok(zoomBehaviorTransforms.length > 0,
      "the zoom behaviour's internal state must be seeded too, or the first user gesture will jump");
  const initialZoomBehaviorTransform = zoomBehaviorTransforms[0];
  assert.strictEqual(initialZoomBehaviorTransform.x, initialViewportTransform.x,
      "the zoom behaviour's transform must agree with what was actually applied to the viewport");
  assert.strictEqual(initialZoomBehaviorTransform.y, initialViewportTransform.y,
      "the zoom behaviour's transform must agree with what was actually applied to the viewport");

  console.log("initial-transform.test.js: all assertions passed");
}

run();
