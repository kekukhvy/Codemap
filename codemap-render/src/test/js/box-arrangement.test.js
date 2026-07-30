"use strict";

/**
 * Arranging the canvas: folding a box down to one link, keeping the header
 * controls clickable, and dragging a box where you want it (spec 007 §2.2, §6.3).
 *
 * Run with: node src/test/js/box-arrangement.test.js
 */

const assert = require("assert");
const { loadReportScript, loadReportScriptWithJoinableD3 } = require("./report-test-harness");

const EMPTY = { modules: [], entryPoints: [], classes: [], methods: [], edges: [], moduleDependencies: [], removedMethods: [] };

const CONTROLLER = "com.example.TaskController";
const DOMAIN = "com.example.Task";

/** A controller whose one method makes three separate calls into one domain class. */
function fixture() {
  const cls = (id, simpleName, layer) => ({
    id, moduleId: "m", fqn: id, simpleName, packageName: "com.example", kind: "CLASS",
    layer, file: simpleName + ".java", lineStart: 1, lineEnd: 40, javadoc: null,
    status: "UNCHANGED", source: ""
  });
  const method = (id, classId, name, signature) => ({
    id, classId, name, signature, file: "x.java", lineStart: 2, lineEnd: 4,
    javadoc: null, source: "", constructor: false, visibility: "PUBLIC", status: "UNCHANGED"
  });

  return {
    modules: [{ id: "m", name: "m", path: "." }],
    entryPoints: [{ id: "ep", moduleId: "m", kind: "REST", label: "PUT /t", methodId: CONTROLLER + "#update()", detectedBy: "RULE" }],
    classes: [cls(CONTROLLER, "TaskController", "ENTRY"), cls(DOMAIN, "Task", "DOMAIN")],
    methods: [
      method(CONTROLLER + "#update()", CONTROLLER, "update", "update() : void"),
      method(DOMAIN + "#id()", DOMAIN, "id", "id() : TaskId"),
      method(DOMAIN + "#name()", DOMAIN, "name", "name() : String"),
      method(DOMAIN + "#payload()", DOMAIN, "payload", "payload() : String")
    ],
    edges: [
      { from: CONTROLLER + "#update()", to: DOMAIN + "#id()", kind: "CALL_EXTERNAL", resolved: true, line: 3 },
      { from: CONTROLLER + "#update()", to: DOMAIN + "#name()", kind: "CALL_EXTERNAL", resolved: true, line: 4 },
      { from: CONTROLLER + "#update()", to: DOMAIN + "#payload()", kind: "CALL_EXTERNAL", resolved: true, line: 5 }
    ],
    moduleDependencies: [], removedMethods: []
  };
}

function openedView() {
  const { view } = loadReportScriptWithJoinableD3(fixture());
  view.openEntryPointPill("ep");
  view.expandMethodRow(CONTROLLER + "#update()", CONTROLLER, view.expanderPathFor(CONTROLLER));
  view.render();
  return view;
}

function drawnLinks(view) {
  return view.viewport.nodes[0].children.filter((node) => node.tag === "path");
}

function run() {
  testOpenBoxKeepsOneLinkPerCall();
  testCollapsedBoxGetsASingleLink();
  testHeaderControlsDoNotOverlap();
  testCollapsedBoxIsWideEnoughForItsName();
  testDraggingMovesOnlyThatBox();
  testDragOffsetSurvivesRelayout();
  testDragDoesNotSwallowControlClicks();
  testACollapsedBoxCanBeReopened();
  testDroppingOnANeighbourPushesItOutOfTheWay();
  testRoutesKeepClearOfBoxBordersWhenThereIsRoom();
  testHeaderControlsHaveAFingerSizedHitArea();

  console.log("box-arrangement.test.js: all assertions passed");
}

function testOpenBoxKeepsOneLinkPerCall() {
  const view = openedView();

  assert.strictEqual(drawnLinks(view).length, 4,
      "three distinct calls into an open box stay three lines, plus the pill link");
}

/** Once folded, every link lands on the same header point; ten arrowheads there say nothing. */
function testCollapsedBoxGetsASingleLink() {
  const view = openedView();

  view.toggleBoxCollapsed(DOMAIN);

  assert.strictEqual(drawnLinks(view).length, 2,
      "a collapsed box takes one merged link per class pair, plus the pill link");
}

/** The fold control was unclickable because the three header controls sat on top of each other. */
function testHeaderControlsDoNotOverlap() {
  const view = openedView();
  const box = view.viewport.nodes[0].children
      .find((node) => (node.getAttribute("class") || "").includes("class-box"));

  // Header controls only: member rows carry their own expander further down,
  // which shares the class name but never shares a line with these.
  const headerRowY = 16;
  const controlXs = box.children
      .filter((child) => Number(child.getAttribute("y")) === headerRowY)
      .filter((child) => ["expander", "collapse-toggle", "status-glyph"].some((name) =>
          (child.getAttribute("class") || "").split(" ").includes(name)))
      .map((child) => Number(child.getAttribute("x")))
      .sort((left, right) => left - right);

  assert.ok(controlXs.length >= 2, "the header must carry at least the expander and the fold control");
  for (let i = 1; i < controlXs.length; i++) {
    assert.ok(controlXs[i] - controlXs[i - 1] >= 24,
        `header controls must not overlap, got ${controlXs.join(", ")}`);
  }
}

function testCollapsedBoxIsWideEnoughForItsName() {
  const internal = loadReportScript(EMPTY);
  const empty = { constructors: [], publicMethods: [], revealedPrivateMethods: [] };

  const narrow = internal.boxWidthFor(empty, "Task");
  const wide = internal.boxWidthFor(empty, "ScheduleConfigurationRepositoryAdapter");

  assert.ok(wide > narrow, "a long class name must widen even an otherwise empty box");
}

function testDraggingMovesOnlyThatBox() {
  const internal = loadReportScript(EMPTY);
  const layout = {
    pillRect: { x: 0, y: 0, width: 10, height: 10 },
    boxPositions: new Map([
      ["A", { rect: { x: 100, y: 100, width: 220, height: 60 }, column: 1, compartments: {} }],
      ["B", { rect: { x: 400, y: 100, width: 220, height: 60 }, column: 2, compartments: {} }]
    ])
  };

  const moved = internal.applyBoxOffsets(layout, new Map([["A", { x: 50, y: -30 }]]));

  assert.deepStrictEqual(
      { x: moved.boxPositions.get("A").rect.x, y: moved.boxPositions.get("A").rect.y },
      { x: 150, y: 70 }, "the dragged box moves by its offset");
  assert.deepStrictEqual(
      { x: moved.boxPositions.get("B").rect.x, y: moved.boxPositions.get("B").rect.y },
      { x: 400, y: 100 }, "its neighbours stay exactly where the layout put them");
}

/** Expanding something else must not undo an arrangement the reader made by hand. */
function testDragOffsetSurvivesRelayout() {
  const view = openedView();
  const before = view.lastLayout.boxPositions.get(DOMAIN).rect.x;
  view.boxOffsets.set(DOMAIN, { x: 120, y: 40 });

  view.render();
  const afterFirst = view.lastLayout.boxPositions.get(DOMAIN).rect.x;
  view.toggleClassHeader(CONTROLLER);
  const afterExpand = view.lastLayout.boxPositions.get(DOMAIN).rect.x;

  assert.strictEqual(afterFirst, before + 120, "the offset applies on top of the computed layout");
  assert.strictEqual(afterExpand, afterFirst, "a later expansion must not discard it");
}

/**
 * A drag behaviour bound to the whole box group swallows the mousedown of
 * everything inside it, which left a folded box impossible to re-open.
 */
function testDragDoesNotSwallowControlClicks() {
  const internal = loadReportScript(EMPTY);
  const target = (cssClass) => ({ getAttribute: (name) => (name === "class" ? cssClass : null) });

  assert.ok(internal.isDragHandle(target("box-rect")), "the outline is the grab handle");
  assert.ok(internal.isDragHandle(target("box-header-fill")), "so is the header fill behind it");

  for (const control of ["collapse-toggle", "expander", "box-header", "member-row underlined"]) {
    assert.ok(!internal.isDragHandle(target(control)),
        `${control} must keep its click rather than start a drag`);
  }
}

/** The whole point of folding: you can unfold it again. */
function testACollapsedBoxCanBeReopened() {
  const view = openedView();
  const rowsWhenOpen = view.lastLayout.boxPositions.get(DOMAIN).compartments.publicMethods.length;
  assert.ok(rowsWhenOpen > 0, "precondition: the domain box has rows to begin with");

  view.toggleBoxCollapsed(DOMAIN);
  assert.strictEqual(view.lastLayout.boxPositions.get(DOMAIN).compartments.publicMethods.length, 0,
      "folded");

  view.toggleBoxCollapsed(DOMAIN);

  assert.strictEqual(view.lastLayout.boxPositions.get(DOMAIN).compartments.publicMethods.length, rowsWhenOpen,
      "clicking the fold control again must bring the rows back");
}

/**
 * Dropping a box on a neighbour left the two sharing the same space, and the
 * router — which treats boxes as walls — was then asked for a path through
 * solid ground, so links ran along the seam.
 */
function testDroppingOnANeighbourPushesItOutOfTheWay() {
  const internal = loadReportScript(EMPTY);
  const layout = {
    pillRect: { x: 0, y: 0, width: 10, height: 10 },
    boxPositions: new Map([
      ["dragged", { rect: { x: 100, y: 100, width: 220, height: 60 }, column: 1, compartments: {} }],
      ["settled", { rect: { x: 100, y: 200, width: 220, height: 60 }, column: 1, compartments: {} }]
    ])
  };

  // Drop "dragged" squarely on top of "settled".
  const moved = internal.applyBoxOffsets(layout, new Map([["dragged", { x: 0, y: 100 }]]));

  const dragged = moved.boxPositions.get("dragged").rect;
  const settled = moved.boxPositions.get("settled").rect;

  assert.strictEqual(dragged.y, 200, "the dragged box stays exactly where it was dropped");
  const overlapping = dragged.x < settled.x + settled.width && dragged.x + dragged.width > settled.x
      && dragged.y < settled.y + settled.height && dragged.y + dragged.height > settled.y;
  assert.ok(!overlapping, "the box that was not dragged gives way instead of being sat on");
}

/** A line grazing a border reads as if drawn on it. */
function testRoutesKeepClearOfBoxBordersWhenThereIsRoom() {
  const internal = loadReportScript(EMPTY);
  const link = {
    lane: 0, selfLink: false,
    from: { rect: { x: 0, y: 0, width: 200, height: 60 }, rowY: 30 },
    to: { rect: { x: 600, y: 0, width: 200, height: 60 }, rowY: 30 }
  };
  const blocker = { rect: { x: 300, y: -50, width: 200, height: 200 } };

  const points = internal.routeOrthogonalLink(link, [blocker], new Set());

  for (const point of points) {
    const outsideX = Math.max(blocker.rect.x - point.x, point.x - (blocker.rect.x + blocker.rect.width), 0);
    const outsideY = Math.max(blocker.rect.y - point.y, point.y - (blocker.rect.y + blocker.rect.height), 0);
    if (outsideX > 0 || outsideY > 0) {
      assert.ok(Math.max(outsideX, outsideY) >= 6,
          `a turn at (${point.x},${point.y}) hugs the blocker's border`);
    }
  }
}

/**
 * The glyphs are about 16x13 — a small target, and missing one silently does
 * nothing, which reads as the control being broken. Each carries an invisible
 * hit area so the clickable region is the size a pointer expects.
 */
function testHeaderControlsHaveAFingerSizedHitArea() {
  const view = openedView();
  const box = view.viewport.nodes[0].children
      .find((node) => (node.getAttribute("class") || "").includes("class-box"));

  const hitAreas = box.children.filter((child) =>
      (child.getAttribute("class") || "").split(" ").includes("header-hit-area"));

  assert.ok(hitAreas.length >= 2, "the expander and the fold control each need a hit area");
  for (const area of hitAreas) {
    assert.ok(Number(area.getAttribute("width")) >= 24,
        "a hit area must be wider than the glyph it stands behind");
    assert.ok(Number(area.getAttribute("height")) >= 20,
        "a hit area must be taller than the glyph it stands behind");
  }
}

run();
