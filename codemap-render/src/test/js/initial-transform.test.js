"use strict";

/**
 * Assertions on {@code DiagramView}'s initial viewport placement (spec §7,
 * kept from feature/6): the viewport must receive a non-identity initial
 * transform, and the zoom behaviour's internal state must agree with it, or
 * the first pan/zoom gesture would jump.
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

  new internal.DiagramView(index);

  assert.ok(viewportTransforms.length > 0,
      "the viewport must receive an initial transform rather than staying at the implicit identity (null)");
  const initialViewportTransform = viewportTransforms[0];
  assert.ok(initialViewportTransform.x > 0 || initialViewportTransform.y > 0,
      "the initial transform must move the diagram away from the SVG's top-left corner, " +
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
