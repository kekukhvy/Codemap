"use strict";

/**
 * Direct assertions on report.js's tree layout function.
 *
 * A Chrome inspection of the real Kairos report caught what no Java test
 * could: all 10 module roots rendered overlapping and clipped in the
 * top-left corner, because (a) sibling spacing was sized for a bare circle
 * rather than a circle plus its label, and (b) the viewport had no initial
 * transform. This test asserts the two properties that would have caught
 * the regression: minimum readable spacing between same-depth siblings, and
 * every coordinate the layout produces falling within sane bounds.
 *
 * Run with: node src/test/js/layout.test.js
 */

const assert = require("assert");
const { loadReportScript, groupBy } = require("./report-test-harness");

/** Ten module roots with names as long as Kairos's real ones. */
function fixtureWithManyLongModuleNames() {
  const moduleNames = [
    "kairos-api", "kairos-admin", "kairos-engine", "kairos-worker", "kairos-sdk",
    "kairos-scheduler", "kairos-notifications", "kairos-reporting", "kairos-gateway", "common"
  ];
  return {
    modules: moduleNames.map((name) => ({ id: name, name, path: name })),
    entryPoints: [],
    classes: [],
    methods: [],
    edges: [],
    moduleDependencies: [],
    removedMethods: []
  };
}

function run() {
  const data = fixtureWithManyLongModuleNames();
  const internal = loadReportScript(data);
  const index = new internal.CodemapIndex(data);
  const builder = new internal.TreeBuilder(index);

  const roots = builder.buildModuleRoots();
  const layoutNodes = [];
  const layoutLinks = [];
  internal.layoutTree(roots, layoutNodes, layoutLinks);

  assertMinimumSiblingSpacing(layoutNodes, internal);
  assertCoordinatesInSaneBounds(layoutNodes);
  assertDepthGrowsAlongOneAxisOnly(layoutNodes);

  console.log("layout.test.js: all assertions passed");
}

function assertMinimumSiblingSpacing(layoutNodes, internal) {
  const byDepth = groupBy(layoutNodes, (n) => n.depth);
  for (const [depth, nodesAtDepth] of byDepth) {
    const sorted = [...nodesAtDepth].sort((a, b) => a.crossAxis - b.crossAxis);
    for (let i = 1; i < sorted.length; i++) {
      const gap = sorted[i].crossAxis - sorted[i - 1].crossAxis;
      assert.ok(gap >= internal.MIN_SIBLING_SPACING,
          `siblings at depth ${depth} must be at least ${internal.MIN_SIBLING_SPACING}px apart ` +
          `(labels collide otherwise), got ${gap}px between "${sorted[i - 1].label}" and "${sorted[i].label}"`);
    }
  }
}

function assertCoordinatesInSaneBounds(layoutNodes) {
  for (const node of layoutNodes) {
    assert.ok(Number.isFinite(node.x), `node "${node.label}" must have a finite x`);
    assert.ok(Number.isFinite(node.y), `node "${node.label}" must have a finite y`);
    assert.ok(node.x >= 0, `node "${node.label}" must not be laid out at a negative x, got ${node.x}`);
    assert.ok(node.y >= 0, `node "${node.label}" must not be laid out at a negative y, got ${node.y}`);
  }
}

/** Every node at the same depth must share the same along-axis coordinate. */
function assertDepthGrowsAlongOneAxisOnly(layoutNodes) {
  const byDepth = groupBy(layoutNodes, (n) => n.depth);
  for (const [depth, nodesAtDepth] of byDepth) {
    const alongAxisValues = new Set(nodesAtDepth.map((n) => n.alongAxis));
    assert.strictEqual(alongAxisValues.size, 1,
        `all nodes at depth ${depth} must align on the depth axis, got ${[...alongAxisValues]}`);
  }
}

run();
