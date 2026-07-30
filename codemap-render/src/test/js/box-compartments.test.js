"use strict";

/**
 * Assertions on class-box compartment composition (spec 007 §2.2): a box
 * lists constructors, then public methods (PUBLIC/PROTECTED), then revealed
 * private/package rows below a dashed separator — a compartment with no rows
 * is omitted entirely, and every row carries its UML visibility marker.
 *
 * Run with: node src/test/js/box-compartments.test.js
 */

const assert = require("assert");
const { loadReportScript } = require("./report-test-harness");

const MODULE_ID = "kairos-api";
const CLASS_ID = "com.example.UserService";

function method(id, name, visibility, constructor) {
  return {
    id, classId: CLASS_ID, name, signature: name + "()", file: "UserService.java",
    lineStart: 1, lineEnd: 3, javadoc: null, source: "void " + name + "() { }",
    constructor: !!constructor, visibility, status: null
  };
}

function fixture(methods) {
  return {
    modules: [{ id: MODULE_ID, name: "kairos-api", path: MODULE_ID }],
    entryPoints: [],
    classes: [
      { id: CLASS_ID, moduleId: MODULE_ID, fqn: "com.example.UserService", simpleName: "UserService",
        packageName: "com.example", kind: "CLASS", layer: "APPLICATION", file: "UserService.java",
        lineStart: 1, lineEnd: 40, javadoc: null, status: null, source: "class UserService { }" }
    ],
    methods,
    edges: [],
    moduleDependencies: [],
    removedMethods: []
  };
}

function run() {
  testConstructorsPublicAndPrivateCompartmentsInOrder();
  testEmptyCompartmentIsOmitted();
  testVisibilityMarkers();
  testPrivateRowsAbsentUntilRevealed();

  console.log("box-compartments.test.js: all assertions passed");
}

function testConstructorsPublicAndPrivateCompartmentsInOrder() {
  const data = fixture([
    method(CLASS_ID + "#UserService()", "UserService", "PUBLIC", true),
    method(CLASS_ID + "#update()", "update", "PUBLIC"),
    method(CLASS_ID + "#validate()", "validate", "PRIVATE")
  ]);
  const internal = loadReportScript(data);
  const index = new internal.CodemapIndex(data);

  const compartments = internal.buildCompartments(index, CLASS_ID, new Set([CLASS_ID + "#validate()"]));

  assert.strictEqual(compartments.constructors.length, 1, "one constructor");
  assert.strictEqual(compartments.constructors[0].name, "UserService");
  assert.strictEqual(compartments.publicMethods.length, 1, "one public method");
  assert.strictEqual(compartments.publicMethods[0].name, "update");
  assert.strictEqual(compartments.revealedPrivateMethods.length, 1, "one revealed private method");
  assert.strictEqual(compartments.revealedPrivateMethods[0].name, "validate");
}

function testEmptyCompartmentIsOmitted() {
  const data = fixture([method(CLASS_ID + "#update()", "update", "PUBLIC")]);
  const internal = loadReportScript(data);
  const index = new internal.CodemapIndex(data);

  const compartments = internal.buildCompartments(index, CLASS_ID, new Set());

  assert.strictEqual(compartments.constructors.length, 0, "no constructors declared");
  assert.strictEqual(compartments.revealedPrivateMethods.length, 0, "no private rows revealed");
  assert.strictEqual(compartments.publicMethods.length, 1);
}

function testVisibilityMarkers() {
  const data = fixture([
    method(CLASS_ID + "#pub()", "pub", "PUBLIC"),
    method(CLASS_ID + "#prot()", "prot", "PROTECTED"),
    method(CLASS_ID + "#pkg()", "pkg", "PACKAGE"),
    method(CLASS_ID + "#priv()", "priv", "PRIVATE")
  ]);
  const internal = loadReportScript(data);
  const index = new internal.CodemapIndex(data);
  const revealed = new Set([CLASS_ID + "#pkg()", CLASS_ID + "#priv()"]);

  const compartments = internal.buildCompartments(index, CLASS_ID, revealed);

  assert.strictEqual(internal.visibilityMarker("PUBLIC"), "+");
  assert.strictEqual(internal.visibilityMarker("PROTECTED"), "#");
  assert.strictEqual(internal.visibilityMarker("PACKAGE"), "~");
  assert.strictEqual(internal.visibilityMarker("PRIVATE"), "-");

  const publicNames = compartments.publicMethods.map((m) => m.name);
  assert.ok(publicNames.includes("pub") && publicNames.includes("prot"),
      "PUBLIC and PROTECTED both sit in the public compartment");
  const revealedNames = compartments.revealedPrivateMethods.map((m) => m.name);
  assert.ok(revealedNames.includes("pkg") && revealedNames.includes("priv"),
      "PACKAGE and PRIVATE are reveal-only rows");
}

function testPrivateRowsAbsentUntilRevealed() {
  const data = fixture([
    method(CLASS_ID + "#update()", "update", "PUBLIC"),
    method(CLASS_ID + "#validate()", "validate", "PRIVATE")
  ]);
  const internal = loadReportScript(data);
  const index = new internal.CodemapIndex(data);

  const beforeReveal = internal.buildCompartments(index, CLASS_ID, new Set());
  assert.strictEqual(beforeReveal.revealedPrivateMethods.length, 0,
      "a private method never shows on first draw (spec 007 §2.3)");

  const afterReveal = internal.buildCompartments(index, CLASS_ID, new Set([CLASS_ID + "#validate()"]));
  assert.strictEqual(afterReveal.revealedPrivateMethods.length, 1,
      "the private row appears once its expander path reveals it");
}

run();
