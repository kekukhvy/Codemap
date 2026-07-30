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
    each(callback) {
      return chainable;
    },
    classed: () => chainable,
    node: () => null
  };
  return Object.assign(element, chainable);
}

function stubD3Selection(recordedTransforms) {
  return stubElement(recordedTransforms);
}

// -------------------------------------------------------------------------
// Joinable D3 stub: a small, real enter/update/exit join, faithful enough for
// report.js's own usage (selectAll().data(arr, keyFn).enter()/.merge()/.each())
// so DiagramView's actual SVG-building methods (renderBoxContent,
// renderCompartment, renderMemberRow, drawLinks/routedLink) run for real under
// Node, instead of being no-ops. Kept separate from the plain `stubElement`
// harness above so every existing test's behaviour is unchanged.
// -------------------------------------------------------------------------

/** One joinable stub SVG/DOM node: real attr/text storage, a child registry, and click handlers. */
function joinableNode(tag) {
  const attributes = new Map();
  const classesByTarget = new Map();
  const node = {
    tag,
    children: [],
    textContent: "",
    handlers: new Map(),
    attr(name, value) {
      if (value === undefined) {
        return attributes.get(name);
      }
      attributes.set(name, typeof value === "function" ? value(node.datum) : value);
      return node;
    },
    // A real DOM element exposes getAttribute/setAttribute, NOT D3's .attr().
    // report.js receives raw elements in a `.filter((d, i, nodes) => …)`
    // callback, so the stub must not offer .attr() as the only way in — that
    // masked a TypeError that fired on every hover in a real browser.
    getAttribute(name) {
      return attributes.has(name) ? attributes.get(name) : null;
    },
    setAttribute(name, value) {
      attributes.set(name, value);
    },
    getAttribute: (name) => (attributes.has(name) ? attributes.get(name) : null),
    text(value) {
      if (value === undefined) {
        return node.textContent;
      }
      node.textContent = typeof value === "function" ? value(node.datum) : value;
      return node;
    },
    on(event, handler) {
      node.handlers.set(event, handler);
      return node;
    },
    // Mirrors real D3's `.classed()`: toggles the class in the live `class`
    // attribute string too, not just a side ledger — so a test reading
    // `getAttribute("class")` after a `.classed()` call sees the same thing a
    // real browser's `className` would.
    classed(className, on) {
      classesByTarget.set(className, on);
      syncClassAttribute(node, attributes, classesByTarget);
      return node;
    },
    hasClass: (className) => classesByTarget.get(className) === true
  };
  return node;
}

/** Rewrites the `class` attribute from its static token(s) plus whichever `.classed()` toggles are currently on. */
function syncClassAttribute(node, attributes, classesByTarget) {
  const staticTokens = (attributes.get("class") || "").split(" ").filter((token) =>
      token !== "" && !classesByTarget.has(token));
  const toggledOnTokens = [...classesByTarget.entries()].filter(([, on]) => on).map(([className]) => className);
  attributes.set("class", [...staticTokens, ...toggledOnTokens].join(" "));
}

/**
 * A joinable selection over zero or more real {@link joinableNode}s, faithful
 * to the report.js call pattern. `parentsOf[i]` is the DOM node that
 * `nodes[i]` should be appended under/removed from — carried per-node (not as
 * one shared value) so a selection produced by `.selectAll()` over several
 * boxes still appends each new child under its own box, not the first one.
 */
function joinableSelection(nodes, parentsOf) {
  const owners = parentsOf || nodes.map(() => null);
  const selection = {
    nodes,
    _enterData: [],
    _enterOwners: [],
    _exitNodes: [],
    _exitOwners: [],
    append(tag) {
      const created = selection.nodes.map((node, i) => {
        const child = joinableNode(tag);
        child.datum = node.datum;
        if (owners[i]) {
          owners[i].children.push(child);
        }
        return child;
      });
      return joinableSelection(created, selection.nodes);
    },
    attr(name, value) {
      selection.nodes.forEach((node) => node.attr(name, value === undefined ? undefined : resolvePerDatum(value, node)));
      return selection;
    },
    text(value) {
      selection.nodes.forEach((node) => node.text(resolvePerDatum(value, node)));
      return selection;
    },
    on(event, handler) {
      selection.nodes.forEach((node) => node.on(event, handler));
      return selection;
    },
    classed(className, value) {
      selection.nodes.forEach((node) => node.classed(className, resolvePerDatum(value, node)));
      return selection;
    },
    call(behaviorOrFunction, ...args) {
      if (typeof behaviorOrFunction === "function") {
        behaviorOrFunction.call(selection, ...args);
      }
      return selection;
    },
    select(subselector) {
      const found = selection.nodes.map((node) => node.children.find((child) => matchesTag(child, subselector)));
      const keep = found.map((n, i) => (n !== undefined ? i : -1)).filter((i) => i !== -1);
      return joinableSelection(keep.map((i) => found[i]), keep.map((i) => selection.nodes[i]));
    },
    // A descendant selector, like real D3/CSS `selectAll` (not just direct
    // children) — report.js's hover highlighting reaches for member rows
    // nested two levels under the viewport (`viewport > g.class-box >
    // text.member-row`), the same way a real browser's `querySelectorAll`
    // would.
    selectAll(subselector) {
      const found = [];
      const foundOwners = [];
      for (const node of selection.nodes) {
        collectMatchingDescendants(node, subselector, found, foundOwners);
      }
      const result = joinableSelection(found, foundOwners);
      // The nodes `selectAll` was called on, so `.data()` still knows where to
      // append `.enter()` nodes even when zero children currently match (the
      // very first render) — mirrors real D3's parent tracking on a selection.
      result._sourceParents = selection.nodes;
      return result;
    },
    remove() {
      selection.nodes.forEach((node, i) => {
        if (owners[i]) {
          owners[i].children = owners[i].children.filter((child) => child !== node);
        }
      });
      return selection;
    },
    each(callback) {
      selection.nodes.forEach((node, i) => callback(node.datum, i, selection.nodes));
      return selection;
    },
    // Mirrors real D3's `.filter((d, i, nodes) => ...)` signature (not just
    // `(d) => ...`) — report.js's row-hover lookup needs the raw node itself
    // to read an attribute that was never put on `.datum`.
    filter(predicate) {
      const kept = selection.nodes.map((node, i) => i)
          .filter((i) => predicate(selection.nodes[i].datum, i, selection.nodes));
      return joinableSelection(kept.map((i) => selection.nodes[i]), kept.map((i) => owners[i]));
    },
    /**
     * Real enter/update/exit join, keyed if a key function is given (mirrors
     * `selection.data(array, keyFn)`). Every node in this selection is
     * assumed to share the same owner (report.js only ever calls `.data()`
     * on a single-node selection like `this.viewport`).
     */
    data(items, keyFn) {
      const sourceParents = selection._sourceParents || [];
      const owner = owners[0] || sourceParents[0] || null;
      const existingByKey = new Map(selection.nodes.map((node) => [keyFn ? keyFn(node.datum) : node.datum, node]));
      const nextKeys = new Set(items.map((item) => (keyFn ? keyFn(item) : item)));
      const updateNodes = [];
      const enterData = [];
      for (const item of items) {
        const key = keyFn ? keyFn(item) : item;
        if (existingByKey.has(key)) {
          const node = existingByKey.get(key);
          node.datum = item;
          updateNodes.push(node);
        } else {
          enterData.push(item);
        }
      }
      const exitNodes = selection.nodes.filter((node) => !nextKeys.has(keyFn ? keyFn(node.datum) : node.datum));
      const joined = joinableSelection(updateNodes, updateNodes.map(() => owner));
      joined._enterData = enterData;
      joined._enterOwners = enterData.map(() => owner);
      joined._exitNodes = exitNodes;
      joined._exitOwners = exitNodes.map(() => owner);
      return joined;
    },
    enter() {
      return {
        append(tag) {
          const created = selection._enterData.map((datum, i) => {
            const child = joinableNode(tag);
            child.datum = datum;
            const owner = selection._enterOwners[i];
            if (owner) {
              owner.children.push(child);
            }
            return child;
          });
          return joinableSelection(created, selection._enterOwners);
        }
      };
    },
    exit() {
      return joinableSelection(selection._exitNodes, selection._exitOwners);
    },
    merge(other) {
      return joinableSelection([...selection.nodes, ...other.nodes], [...owners, ...(other._ownersRef || other.nodes.map(() => owners[0]))]);
    },
    node: () => selection.nodes[0] || null
  };
  selection._ownersRef = owners;
  return selection;
}

function resolvePerDatum(value, node) {
  return typeof value === "function" ? value(node.datum) : value;
}

/**
 * `"g.class-box"` / `"path.class-link"` / `"text.member-row"` -> tag plus,
 * when given, one required class token (checked against the node's current
 * `class` attribute, not a separate ledger, so it sees whatever `.classed()`
 * last synced); `"*"` matches any tag. Enough for report.js's own selectors.
 */
function matchesTag(node, selector) {
  if (selector === "*") {
    return true;
  }
  const [tag, requiredClass] = selector.split(".");
  if (node.tag !== tag) {
    return false;
  }
  if (!requiredClass) {
    return true;
  }
  return (node.getAttribute("class") || "").split(" ").includes(requiredClass);
}

/** Depth-first descendant search, matching real D3/CSS `selectAll` (not just direct children). */
function collectMatchingDescendants(node, selector, found, foundOwners) {
  for (const child of node.children) {
    if (matchesTag(child, selector)) {
      found.push(child);
      foundOwners.push(node);
    }
    collectMatchingDescendants(child, selector, found, foundOwners);
  }
}

/**
 * Loads report.js with a real (if minimal) D3 join implementation, so
 * {@code DiagramView.render()} actually builds its SVG tree — box headers,
 * compartment rows, and routed links — instead of no-op stubs. Use this when
 * a test needs to see what got rendered, not just the pure model underneath.
 *
 * @param data a view-model payload shaped like {@code window.__CODEMAP_DATA__}
 * @return the script's {@code window.CodemapInternal} object
 */
function loadReportScriptWithJoinableD3(data) {
  const source = fs.readFileSync(SCRIPT_PATH, "utf8");
  const documentElement = stubElement();
  const svgRoot = joinableNode("svg");
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
      // `d3.select("#graph")` (a CSS selector string) means "the SVG root",
      // owned by itself for append purposes (matches real D3: appending to
      // the root selection appends into that root element).
      // `d3.select(someNode)` (an actual node, as `renderBoxContent` does with
      // the node D3 handed its `.each()` callback) wraps that node — a later
      // `.append()` on this selection must append INTO that node, so its own
      // owner is itself, not the node it was appended under.
      select: (target) => (typeof target === "string"
          ? joinableSelection([svgRoot], [svgRoot])
          : joinableSelection([target], [target])),
      zoom: () => stubZoomBehavior([]),
      zoomIdentity: makeZoomTransform(0, 0, 1)
    },
    console
  };
  vm.createContext(sandbox);
  vm.runInContext(source, sandbox);
  // report.js auto-bootstraps one DiagramView against `window.__CODEMAP_DATA__`
  // once it loads (`if (DATA) bootstrap(DATA)`); reuse that instance — and its
  // already-appended `g.viewport` — rather than constructing a second one,
  // which would leave a stray, unused viewport under `svgRoot`.
  return { internal: sandbox.window.CodemapInternal, svgRoot, view: sandbox.window.CodemapReport.view };
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

module.exports = { loadReportScript, loadReportScriptWithJoinableD3, groupBy, makeZoomTransform };
