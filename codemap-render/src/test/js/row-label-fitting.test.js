"use strict";

/**
 * Row labels must fit inside their box (spec 007 §2.2).
 *
 * Real signatures run long — a handler constructor taking six use-cases is 164
 * characters — and a fixed-width box let them spill across the canvas and over
 * the `(+)` expander. Boxes size to their content up to a cap, then labels
 * shorten: the parameter list collapses first, the method name always survives.
 *
 * Run with: node src/test/js/row-label-fitting.test.js
 */

const assert = require("assert");
const { loadReportScript } = require("./report-test-harness");

const EMPTY = { modules: [], entryPoints: [], classes: [], methods: [], edges: [], moduleDependencies: [], removedMethods: [] };

const ROW_TEXT_X = 12;
const ROW_EXPANDER_RESERVE = 30;
const CHAR_WIDTH = 6.2;

function method(name, signature, visibility) {
  return {
    id: "C#" + signature, classId: "C", name, signature,
    file: "C.java", lineStart: 1, lineEnd: 2, javadoc: null, source: "",
    constructor: false, visibility: visibility || "PUBLIC", status: "UNCHANGED"
  };
}

function budgetFor(width) {
  return Math.max(8, Math.floor((width - ROW_TEXT_X - ROW_EXPANDER_RESERVE) / CHAR_WIDTH));
}

function run() {
  const internal = loadReportScript(EMPTY);

  testShortLabelIsUntouched(internal);
  testLongParameterListCollapsesFirst(internal);
  testMethodNameAlwaysSurvives(internal);
  testReturnTypeCountsAgainstTheBudget(internal);
  testBoxGrowsToFitButIsCapped(internal);
  testEveryShortenedLabelFitsItsBudget(internal);

  console.log("row-label-fitting.test.js: all assertions passed");
}

function testShortLabelIsUntouched(internal) {
  const label = internal.memberRowLabel(method("save", "save(User) : void"), internal.BOX_WIDTH);

  assert.strictEqual(label, "+ save(User) : void", "a label that already fits must not be altered");
}

function testLongParameterListCollapsesFirst(internal) {
  const signature = "TaskHandler(ObjectMapper, CreateTaskUseCase, UpdateTaskUseCase, SoftDeleteTaskUseCase, ListTasksUseCase)";
  const label = internal.memberRowLabel(method("TaskHandler", signature), internal.BOX_WIDTH);

  assert.ok(label.includes("TaskHandler("), "the method name and open paren must survive");
  assert.ok(label.includes("…"), "the parameter list must be visibly elided");
  assert.ok(label.endsWith(")"), "the closing paren must survive so the row still reads as a call");
  assert.ok(label.length <= budgetFor(internal.BOX_WIDTH), "the shortened label must fit the budget");
}

function testMethodNameAlwaysSurvives(internal) {
  const signature = "aRatherLongMethodName(AlphaType, BetaType, GammaType) : SomeVeryLongReturnType<With, Generics>";
  const label = internal.memberRowLabel(method("aRatherLongMethodName", signature), internal.BOX_WIDTH);

  assert.ok(label.startsWith("+ aRatherLongMethodName"),
      "the reader must always be able to tell which method the row is: " + label);
}

/** The return type sits after ")" and must be counted, or the row still overflows. */
function testReturnTypeCountsAgainstTheBudget(internal) {
  const signature = "scheduleRequestFactory() : Function<UUID, CreateScheduleRequest, AndMoreGenerics>";
  const width = internal.BOX_MAX_WIDTH;

  const label = internal.memberRowLabel(method("scheduleRequestFactory", signature), width);

  assert.ok(label.length <= budgetFor(width),
      "a long return type on an empty parameter list must still be brought inside the budget: " + label);
}

function testBoxGrowsToFitButIsCapped(internal) {
  const narrow = { constructors: [], publicMethods: [method("go", "go() : void")], revealedPrivateMethods: [] };
  const wide = {
    constructors: [method("H", "H(ObjectMapper, CreateTaskUseCase, UpdateTaskUseCase, SoftDeleteTaskUseCase, ListTasksUseCase)")],
    publicMethods: [], revealedPrivateMethods: []
  };

  assert.strictEqual(internal.boxWidthFor(narrow, "C"), internal.BOX_WIDTH,
      "a box whose rows are short must stay at the default width");
  assert.strictEqual(internal.boxWidthFor(wide, "TaskHandler"), internal.BOX_MAX_WIDTH,
      "a box with a very long row must widen, but no further than the cap");
}

/** The property that actually matters: nothing ever exceeds its own box. */
function testEveryShortenedLabelFitsItsBudget(internal) {
  const signatures = [
    "of(Integer, Integer) : Pagination",
    "Pagination(int, int)",
    "DestinationHandler(CreateDestinationUseCase, UpdateDestinationUseCase, DeleteDestinationUseCase, ListDestinationsUseCase, GetDestinationByIdUseCase)",
    "execute(Pagination) : List<DestinationResponse>",
    "x()",
    "destinationType(DestinationConfigSchema, SomethingElse) : DestinationType"
  ];

  for (const width of [internal.BOX_WIDTH, 300, internal.BOX_MAX_WIDTH]) {
    for (const signature of signatures) {
      const label = internal.memberRowLabel(method("m", signature), width);
      assert.ok(label.length <= budgetFor(width),
          `"${label}" (${label.length}) must fit budget ${budgetFor(width)} at width ${width}`);
    }
  }
}

run();
