"use strict";

/**
 * Minimal D3/DOM stand-ins shared by the report.js Node smoke tests.
 *
 * These are not a D3 or DOM reimplementation — just enough chainable no-ops
 * for report.js to construct its {@code GraphView} and run its tree/layout
 * logic without a browser, plus a couple of recording spies so tests can
 * assert on the transform actually applied to the viewport. Rendering itself
 * (actual SVG output) is verified separately, in a real browser, by hand
 * against Kairos.
 */

const fs = require("fs");
const path = require("path");
const vm = require("vm");

const SCRIPT_PATH = path.join(__dirname, "..", "..", "main", "resources", "dev", "codemap", "render", "report.js");

/** A real, inspectable stand-in for D3's zoom transform (translate + scale). */
function makeZoomTransform(x, y, k) {
  return {
    x, y, k: k === undefined ? 1 : k,
    translate(dx, dy) {
      return makeZoomTransform(x + dx, y + dy, this.k);
    },
    scale(factor) {
      return makeZoomTransform(x, y, factor);
    }
  };
}

/** A chainable stub selection that records every `attr("transform", ...)` call. */
function stubElement(recordedTransforms) {
  const attributes = new Map();
  const element = {
    style: {},
    classList: { toggle: () => false, add: () => {}, remove: () => {} },
    dataset: {},
    children: [],
    appendChild: () => {},
    addEventListener: () => {},
    getBoundingClientRect: () => ({ width: 0, height: 0 }),
    setAttribute: (name, value) => attributes.set(name, value),
    getAttribute: (name) => (attributes.has(name) ? attributes.get(name) : null)
  };
  const chainable = {
    attr(name, value) {
      if (name === "transform" && value !== undefined && recordedTransforms) {
        recordedTransforms.push(value);
      }
      return chainable;
    },
    append: () => stubD3Selection(recordedTransforms),
    call(behaviorOrFunction, ...args) {
      if (typeof behaviorOrFunction === "function") {
        behaviorOrFunction.call(chainable, ...args);
      }
      return chainable;
    },
    on: () => chainable,
    select: () => stubD3Selection(recordedTransforms),
    selectAll: () => stubD3Selection(recordedTransforms),
    data: () => stubD3Selection(recordedTransforms),
    enter: () => stubD3Selection(recordedTransforms),
    exit: () => stubD3Selection(recordedTransforms),
    merge: () => stubD3Selection(recordedTransforms),
    remove: () => chainable,
    filter: () => stubD3Selection(recordedTransforms),
    transition: () => chainable,
    duration: () => chainable,
    text: () => chainable,
    node: () => null
  };
  return Object.assign(element, chainable);
}

function stubD3Selection(recordedTransforms) {
  return stubElement(recordedTransforms);
}

/** A minimal in-memory `localStorage`-shaped store for the theme toggle. */
function stubLocalStorage() {
  const store = new Map();
  return {
    getItem: (key) => (store.has(key) ? store.get(key) : null),
    setItem: (key, value) => store.set(key, String(value)),
    removeItem: (key) => store.delete(key)
  };
}

function stubZoomBehavior(recordedZoomBehaviorTransforms) {
  const behavior = {
    scaleExtent: () => behavior,
    on: () => behavior,
    // Mirrors real D3: `selection.call(zoomBehavior.transform, value)` invokes
    // this as `transform.call(selection, value)` — the selection arrives as
    // `this`, and `value` is the sole argument.
    transform(value) {
      recordedZoomBehaviorTransforms.push(value);
    }
  };
  return behavior;
}

/**
 * Loads report.js in a sandboxed VM context seeded with the given view-model
 * data, and returns its exported {@code CodemapInternal} test surface.
 *
 * @param data a view-model payload shaped like {@code window.__CODEMAP_DATA__}
 * @param recording optional {@code {viewportTransforms, zoomBehaviorTransforms}}
 *        arrays that capture every transform applied, for assertions on
 *        initial placement
 * @return the script's {@code window.CodemapInternal} object
 */
function loadReportScript(data, recording) {
  const viewportTransforms = (recording && recording.viewportTransforms) || [];
  const zoomBehaviorTransforms = (recording && recording.zoomBehaviorTransforms) || [];
  const source = fs.readFileSync(SCRIPT_PATH, "utf8");
  const documentElement = stubElement();
  const windowRef = { __CODEMAP_DATA__: data, localStorage: stubLocalStorage() };
  windowRef.matchMedia = () => ({ matches: false });
  const sandbox = {
    window: windowRef,
    document: {
      documentElement,
      getElementById: () => stubElement(),
      createElement: () => stubElement(),
      createDocumentFragment: () => stubElement(),
      querySelector: () => stubElement()
    },
    d3: {
      select: () => stubD3Selection(viewportTransforms),
      zoom: () => stubZoomBehavior(zoomBehaviorTransforms),
      zoomIdentity: makeZoomTransform(0, 0, 1)
    },
    console
  };
  vm.createContext(sandbox);
  vm.runInContext(source, sandbox);
  return sandbox.window.CodemapInternal;
}

function groupBy(items, keyFn) {
  const map = new Map();
  for (const item of items) {
    const key = keyFn(item);
    if (!map.has(key)) {
      map.set(key, []);
    }
    map.get(key).push(item);
  }
  return map;
}

module.exports = { loadReportScript, groupBy, makeZoomTransform };
