"use strict";

/**
 * Assertions on class-box change-status precedence (spec 007 §3): a box takes
 * the strongest status among itself and its visible rows, ADDED > CHANGED >
 * AFFECTED > UNCHANGED.
 *
 * Run with: node src/test/js/status-precedence.test.js
 */

const assert = require("assert");
const { loadReportScript } = require("./report-test-harness");

function emptyFixture() {
  return { modules: [], entryPoints: [], classes: [], methods: [], edges: [], moduleDependencies: [], removedMethods: [] };
}

function run() {
  const internal = loadReportScript(emptyFixture());
  const { strongestStatus, CHANGE_STATUS } = internal;

  assert.strictEqual(strongestStatus([CHANGE_STATUS.UNCHANGED, CHANGE_STATUS.AFFECTED]), CHANGE_STATUS.AFFECTED);
  assert.strictEqual(strongestStatus([CHANGE_STATUS.AFFECTED, CHANGE_STATUS.CHANGED]), CHANGE_STATUS.CHANGED);
  assert.strictEqual(strongestStatus([CHANGE_STATUS.CHANGED, CHANGE_STATUS.ADDED]), CHANGE_STATUS.ADDED);
  assert.strictEqual(strongestStatus([CHANGE_STATUS.ADDED, CHANGE_STATUS.AFFECTED, CHANGE_STATUS.CHANGED]),
      CHANGE_STATUS.ADDED, "ADDED wins over every other status present");
  assert.strictEqual(strongestStatus([null, undefined, CHANGE_STATUS.UNCHANGED]), CHANGE_STATUS.UNCHANGED,
      "null/undefined statuses do not outrank UNCHANGED");
  assert.strictEqual(strongestStatus([]), CHANGE_STATUS.UNCHANGED, "no statuses at all defaults to UNCHANGED");

  console.log("status-precedence.test.js: all assertions passed");
}

run();
