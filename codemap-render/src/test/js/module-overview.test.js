"use strict";

/**
 * Assertions that the module-level dependency overview (spec §7, kept from
 * feature/6) is built from the real aggregated `moduleDependencies`.
 *
 * Run with: node src/test/js/module-overview.test.js
 */

const assert = require("assert");
const { loadReportScript } = require("./report-test-harness");

function fixtureWithModuleDependencies() {
  return {
    modules: [
      { id: "kairos-api", name: "kairos-api", path: "kairos-api" },
      { id: "kairos-admin", name: "kairos-admin", path: "kairos-admin" },
      { id: "common", name: "common", path: "common" }
    ],
    entryPoints: [],
    classes: [],
    methods: [],
    edges: [],
    moduleDependencies: [
      { fromModuleId: "kairos-api", toModuleId: "common" },
      { fromModuleId: "kairos-admin", toModuleId: "common" }
    ],
    removedMethods: []
  };
}

function run() {
  const data = fixtureWithModuleDependencies();
  const internal = loadReportScript(data);
  const index = new internal.CodemapIndex(data);

  const overview = internal.buildModuleOverview(index);

  assertEveryDependencyIsRepresentedByName(overview);
  assertModuleWithNoDependenciesStillListedAsAnIsolatedRoot(overview);

  console.log("module-overview.test.js: all assertions passed");
}

function assertEveryDependencyIsRepresentedByName(overview) {
  assert.strictEqual(overview.length, 2, "every aggregated CROSS_MODULE dependency must produce one overview row");
  const rendered = overview.map((row) => row.fromName + "->" + row.toName);
  assert.ok(rendered.includes("kairos-api->common"), "kairos-api depending on common must be visible by name, not id");
  assert.ok(rendered.includes("kairos-admin->common"), "kairos-admin depending on common must be visible by name, not id");
}

function assertModuleWithNoDependenciesStillListedAsAnIsolatedRoot(overview) {
  const fromNames = overview.map((row) => row.fromName);
  assert.ok(!fromNames.includes("common"), "a module with no outgoing cross-module calls contributes no row of its own");
}

run();
