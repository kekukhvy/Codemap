"use strict";

/**
 * Guards the report stylesheet against the two failure modes that make a rule
 * silently do nothing: a selector that no code ever applies, and a rule that a
 * later, equally specific rule overrides.
 *
 * Both are invisible to every other test in this module — the page still
 * renders, the JS still runs, no error is logged anywhere. The change overlay
 * simply comes out grey, which is the one thing spec 007 §3 asks the report
 * to get right. Only reading the stylesheet in order catches it.
 */

const fs = require("fs");
const path = require("path");
const assert = require("assert");

const RESOURCE_DIR = path.join(__dirname, "..", "..", "main", "resources", "dev", "codemap", "render");
const CSS = fs.readFileSync(path.join(RESOURCE_DIR, "report.css"), "utf8");
const JS = fs.readFileSync(path.join(RESOURCE_DIR, "report.js"), "utf8");

// Every status/link class the stylesheet targets must be one the script really
// applies — a rule for a class nobody sets is dead styling that reads as though
// the feature works.
const STYLED_CLASSES = [
  "status-added", "status-changed", "status-affected",
  "class-link-dashed", "class-link-cross-module", "underlined"
];
for (const className of STYLED_CLASSES) {
  assert.ok(CSS.includes("." + className), "stylesheet is missing a rule for ." + className);
  assert.ok(
      JS.includes('"' + className + '"') || JS.includes("'" + className + "'"),
      "`." + className + "` is styled but report.js never applies it — dead styling");
}

// Every custom property one theme defines must be defined by the other too —
// a variable missing from a theme resolves to nothing, and the rule using it
// silently loses its colour instead of erroring.
function customPropertiesDeclaredIn(themeSelector) {
  const start = CSS.indexOf(themeSelector);
  assert.notStrictEqual(start, -1, "missing theme block: " + themeSelector);
  const blockEnd = CSS.indexOf("}", start);
  const block = CSS.slice(start, blockEnd);
  const matches = block.match(/--[a-z0-9-]+(?=:)/g) || [];
  return new Set(matches);
}

const darkProperties = customPropertiesDeclaredIn(':root[data-theme="dark"]');
const lightProperties = customPropertiesDeclaredIn(':root[data-theme="light"]');
for (const property of darkProperties) {
  assert.ok(lightProperties.has(property), "light theme is missing " + property + ", defined by dark");
}
for (const property of lightProperties) {
  assert.ok(darkProperties.has(property), "dark theme is missing " + property + ", defined by light");
}

// The source block is styled through `pre.source`, so the script has to set
// that class; a bare <pre> silently loses the panel's monospace formatting.
assert.ok(CSS.includes("pre.source {"), "missing rule: pre.source");
assert.ok(
    JS.includes('SOURCE_CLASS = "source"'),
    "pre.source is styled but report.js does not set the `source` class");

// Labels must stay clickable: class-box headers, member rows, and expanders
// are all click targets (spec 007 §4) and must not disable pointer events.
for (const selector of [".box-header", ".member-row", ".expander"]) {
  const at = CSS.indexOf(".class-box " + selector);
  assert.notStrictEqual(at, -1, "missing rule: .class-box " + selector);
  const rule = CSS.slice(at, CSS.indexOf("}", at));
  assert.ok(!/pointer-events:\s*none/.test(rule), "`" + selector + "` must not disable pointer events");
}

console.log("stylesheet.test.js: all assertions passed");
