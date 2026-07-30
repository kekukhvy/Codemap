"use strict";

/**
 * Assertions on the pure CSS-class projection for a routed link (spec 007
 * §6.4): solid vs dashed by call style, plus the existing distinct
 * cross-module style layered on top (spec §7, kept from feature/6).
 *
 * Run with: node src/test/js/link-rendering-classes.test.js
 */

const assert = require("assert");
const { loadReportScript } = require("./report-test-harness");

function emptyFixture() {
  return { modules: [], entryPoints: [], classes: [], methods: [], edges: [], moduleDependencies: [], removedMethods: [] };
}

function run() {
  const internal = loadReportScript(emptyFixture());

  const solid = internal.linkCssClasses({ style: "solid", crossModule: false });
  assert.ok(solid.includes("class-link"));
  assert.ok(!solid.includes("class-link-dashed"));
  assert.ok(!solid.includes("class-link-cross-module"));

  const dashed = internal.linkCssClasses({ style: "dashed", crossModule: false });
  assert.ok(dashed.includes("class-link-dashed"), "a private call renders dashed");

  const crossModule = internal.linkCssClasses({ style: "solid", crossModule: true });
  assert.ok(crossModule.includes("class-link-cross-module"),
      "a cross-module call keeps its own distinct connector style layered on top of solid/dashed");

  console.log("link-rendering-classes.test.js: all assertions passed");
}

run();
