"use strict";

/**
 * Assertions on {@code DiagramController}, the box/link build-out driving the
 * UML diagram's interaction model (spec 007 §4): clicking an entry point,
 * the `(+)` expander on a class header, and the `(+)` expander on a method
 * row.
 *
 * Run with: node src/test/js/diagram-controller.test.js
 */

const assert = require("assert");
const { loadReportScript } = require("./report-test-harness");

const API_MODULE = "kairos-api";
const CONTROLLER_CLASS = "com.example.TaskController";
const REPOSITORY_CLASS = "com.example.TaskRepository";
const METHOD_UPDATE = "com.example.TaskController#update()";
const METHOD_VALIDATE = "com.example.TaskController#validate()"; // private, same class
const METHOD_LOG = "com.example.TaskController#log()"; // public, same class
const METHOD_SAVE = "com.example.TaskRepository#save()"; // public, other class
const METHOD_FIND = "com.example.TaskRepository#find()"; // public, other class
const METHOD_UNRESOLVED = "com.example.ThirdParty#doSomething()";

function fixture() {
  return {
    modules: [{ id: API_MODULE, name: "kairos-api", path: API_MODULE }],
    entryPoints: [
      { id: "entry-1", moduleId: API_MODULE, kind: "REST", label: "PUT /tasks/{id}",
        methodId: METHOD_UPDATE, detectedBy: "RULE" }
    ],
    classes: [
      { id: CONTROLLER_CLASS, moduleId: API_MODULE, fqn: "com.example.TaskController", simpleName: "TaskController",
        packageName: "com.example", kind: "CLASS", layer: "ENTRY", file: "TaskController.java",
        lineStart: 1, lineEnd: 40, javadoc: null, status: null, source: "class TaskController { }" },
      { id: REPOSITORY_CLASS, moduleId: API_MODULE, fqn: "com.example.TaskRepository", simpleName: "TaskRepository",
        packageName: "com.example", kind: "CLASS", layer: "INFRASTRUCTURE", file: "TaskRepository.java",
        lineStart: 1, lineEnd: 20, javadoc: null, status: null, source: "class TaskRepository { }" }
    ],
    methods: [
      { id: METHOD_UPDATE, classId: CONTROLLER_CLASS, name: "update", signature: "update()",
        file: "TaskController.java", lineStart: 10, lineEnd: 16, javadoc: null,
        source: "void update() { validate(); log(); repository.save(); repository.find(); }",
        constructor: false, visibility: "PUBLIC", status: null },
      { id: METHOD_VALIDATE, classId: CONTROLLER_CLASS, name: "validate", signature: "validate()",
        file: "TaskController.java", lineStart: 18, lineEnd: 20, javadoc: null, source: "void validate() { }",
        constructor: false, visibility: "PRIVATE", status: null },
      { id: METHOD_LOG, classId: CONTROLLER_CLASS, name: "log", signature: "log()",
        file: "TaskController.java", lineStart: 22, lineEnd: 24, javadoc: null, source: "void log() { }",
        constructor: false, visibility: "PUBLIC", status: null },
      { id: METHOD_SAVE, classId: REPOSITORY_CLASS, name: "save", signature: "save()",
        file: "TaskRepository.java", lineStart: 5, lineEnd: 7, javadoc: null, source: "void save() { }",
        constructor: false, visibility: "PUBLIC", status: null },
      { id: METHOD_FIND, classId: REPOSITORY_CLASS, name: "find", signature: "find()",
        file: "TaskRepository.java", lineStart: 9, lineEnd: 11, javadoc: null, source: "void find() { }",
        constructor: false, visibility: "PUBLIC", status: null }
    ],
    edges: [
      { from: METHOD_UPDATE, to: METHOD_VALIDATE, kind: "CALL_INTERNAL", resolved: true, line: 11 },
      { from: METHOD_UPDATE, to: METHOD_LOG, kind: "CALL_INTERNAL", resolved: true, line: 12 },
      { from: METHOD_UPDATE, to: METHOD_SAVE, kind: "CALL_EXTERNAL", resolved: true, line: 13 },
      { from: METHOD_UPDATE, to: METHOD_FIND, kind: "CALL_EXTERNAL", resolved: true, line: 14 },
      { from: METHOD_UPDATE, to: METHOD_UNRESOLVED, kind: "CALL_EXTERNAL", resolved: false, line: 15 }
    ],
    moduleDependencies: [],
    removedMethods: []
  };
}

function run() {
  const data = fixture();
  const internal = loadReportScript(data);
  const index = new internal.CodemapIndex(data);

  testOpeningAnEntryPointDrawsTheDeclaringClassWithHandlerUnderlined(internal, index);
  testExpandingAMethodRowDrawsOneLinkPerCall(internal, index);
  testPrivateSameClassCallRevealsTheRowAndIsDashed(internal, index);
  testPublicOtherClassCallIsSolidAndUnderlinesTheTargetRow(internal, index);
  testUnresolvedCallNeverBecomesABox(internal, index);
  testCollapsingAMethodRowRemovesOnlyWhatItRevealed(internal, index);
  testClassHeaderExpanderRevealsEveryDistinctCollaborator(internal, index);

  console.log("diagram-controller.test.js: all assertions passed");
}

function testOpeningAnEntryPointDrawsTheDeclaringClassWithHandlerUnderlined(internal, index) {
  const controller = new internal.DiagramController(index);

  const result = controller.openEntryPoint("entry-1");

  assert.strictEqual(result.box.classId, CONTROLLER_CLASS, "the entry point's declaring class box is drawn");
  assert.strictEqual(result.underlinedMethodId, METHOD_UPDATE, "the entry-point method row is underlined");
  assert.ok(result.link, "a link from the pill to the handler row is drawn");
}

function testExpandingAMethodRowDrawsOneLinkPerCall(internal, index) {
  const controller = new internal.DiagramController(index);
  controller.openEntryPoint("entry-1");

  const result = controller.expandMethodRow(METHOD_UPDATE, CONTROLLER_CLASS, "entry-1");

  // 4 resolved calls: validate() (private/same-class), log() (public/same-class),
  // save() and find() (public/other-class) — the unresolved 5th call draws nothing.
  assert.strictEqual(result.links.length, 4, "one link per resolved call, including two to the same repository class");
}

function testPrivateSameClassCallRevealsTheRowAndIsDashed(internal, index) {
  const controller = new internal.DiagramController(index);
  controller.openEntryPoint("entry-1");
  const result = controller.expandMethodRow(METHOD_UPDATE, CONTROLLER_CLASS, "entry-1");

  const privateLink = result.links.find((link) => link.targetMethodId === METHOD_VALIDATE);
  assert.ok(privateLink, "a link to the private same-class method must be drawn");
  assert.strictEqual(privateLink.style, "dashed", "a private same-class call is a dashed link (spec 007 §4.3.3)");

  const controllerBox = controller.diagram.boxFor(CONTROLLER_CLASS);
  assert.strictEqual(controllerBox.revealedPrivateMethodIds.has(METHOD_VALIDATE), true,
      "the private row must appear in the same box, itself expandable");
}

function testPublicOtherClassCallIsSolidAndUnderlinesTheTargetRow(internal, index) {
  const controller = new internal.DiagramController(index);
  controller.openEntryPoint("entry-1");
  const result = controller.expandMethodRow(METHOD_UPDATE, CONTROLLER_CLASS, "entry-1");

  const saveLink = result.links.find((link) => link.targetMethodId === METHOD_SAVE);
  const findLink = result.links.find((link) => link.targetMethodId === METHOD_FIND);
  assert.ok(saveLink && findLink, "a method calling two methods of one class draws two links (spec 007 §4.3)");
  assert.strictEqual(saveLink.style, "solid", "a public call to another class is a solid link");
  assert.strictEqual(saveLink.underlineTarget, true, "the target row is underlined");

  const repositoryBox = controller.diagram.boxFor(REPOSITORY_CLASS);
  assert.ok(repositoryBox, "the repository box must be drawn for the public cross-class call");
}

function testUnresolvedCallNeverBecomesABox(internal, index) {
  const controller = new internal.DiagramController(index);
  controller.openEntryPoint("entry-1");
  const result = controller.expandMethodRow(METHOD_UPDATE, CONTROLLER_CLASS, "entry-1");

  const unresolvedLink = result.links.find((link) => link.targetMethodId === METHOD_UNRESOLVED);
  assert.strictEqual(unresolvedLink, undefined,
      "a call into an unresolved/third-party target must never become a box or a link (spec 007 §4.4)");
  assert.strictEqual(controller.diagram.boxes.size, 2,
      "only the controller and repository boxes exist; the unresolved target drew nothing");
}

function testCollapsingAMethodRowRemovesOnlyWhatItRevealed(internal, index) {
  const controller = new internal.DiagramController(index);
  controller.openEntryPoint("entry-1");
  controller.expandMethodRow(METHOD_UPDATE, CONTROLLER_CLASS, "entry-1");

  assert.ok(controller.diagram.boxFor(REPOSITORY_CLASS), "repository box exists before collapse");

  controller.collapseMethodRow(METHOD_UPDATE, "entry-1");

  assert.strictEqual(controller.diagram.boxFor(REPOSITORY_CLASS), undefined,
      "collapsing the only expander that revealed the repository box removes it");
  assert.ok(controller.diagram.boxFor(CONTROLLER_CLASS),
      "the controller box itself stays — it was revealed by the entry point, not by this expander");
  const controllerBox = controller.diagram.boxFor(CONTROLLER_CLASS);
  assert.strictEqual(controllerBox.revealedPrivateMethodIds.has(METHOD_VALIDATE), false,
      "collapsing the expander also un-reveals the private row it revealed");
}

function testClassHeaderExpanderRevealsEveryDistinctCollaborator(internal, index) {
  const controller = new internal.DiagramController(index);
  controller.openEntryPoint("entry-1");

  const result = controller.expandClassHeader(CONTROLLER_CLASS);

  const revealedClassIds = result.boxes.map((box) => box.classId);
  assert.ok(revealedClassIds.includes(REPOSITORY_CLASS),
      "the class header expander reveals every distinct collaborator class of any method of this class");
  assert.strictEqual(new Set(revealedClassIds).size, revealedClassIds.length,
      "each distinct collaborator class must be revealed only once, even though update() calls it twice");
}

run();
