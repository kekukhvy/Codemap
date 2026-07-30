"use strict";

/**
 * Assertions on the pure CSS-class projection for boxes and member rows
 * (spec 007 §3): a box's own class carries the strongest status among itself
 * and its visible rows, and every row carries its own status and underline
 * state independently.
 *
 * Run with: node src/test/js/box-rendering-classes.test.js
 */

const assert = require("assert");
const { loadReportScript } = require("./report-test-harness");

function emptyFixture() {
  return { modules: [], entryPoints: [], classes: [], methods: [], edges: [], moduleDependencies: [], removedMethods: [] };
}

function run() {
  const internal = loadReportScript(emptyFixture());
  const { CHANGE_STATUS } = internal;

  testBoxClassCarriesStrongestStatus(internal, CHANGE_STATUS);
  testUnchangedBoxHasNoStatusClass(internal, CHANGE_STATUS);
  testRowClassCarriesItsOwnStatusAndUnderline(internal, CHANGE_STATUS);

  console.log("box-rendering-classes.test.js: all assertions passed");
}

function testBoxClassCarriesStrongestStatus(internal, CHANGE_STATUS) {
  const classes = internal.boxCssClasses(CHANGE_STATUS.UNCHANGED, [CHANGE_STATUS.AFFECTED, CHANGE_STATUS.ADDED]);
  assert.ok(classes.includes("class-box"));
  assert.ok(classes.includes("status-added"), "the box takes the strongest status among itself and its visible rows");
  assert.ok(!classes.includes("status-affected"), "only the strongest status class is applied to the box");
}

function testUnchangedBoxHasNoStatusClass(internal, CHANGE_STATUS) {
  const classes = internal.boxCssClasses(CHANGE_STATUS.UNCHANGED, []);
  assert.ok(!classes.some((c) => c.startsWith("status-")), "an unchanged box carries no status class at all");
}

function testRowClassCarriesItsOwnStatusAndUnderline(internal, CHANGE_STATUS) {
  const underlined = internal.rowCssClasses(CHANGE_STATUS.CHANGED, true);
  assert.ok(underlined.includes("member-row"));
  assert.ok(underlined.includes("status-changed"));
  assert.ok(underlined.includes("underlined"));

  const plain = internal.rowCssClasses(CHANGE_STATUS.UNCHANGED, false);
  assert.ok(!plain.includes("underlined"));
  assert.ok(!plain.some((c) => c.startsWith("status-")));
}

run();
