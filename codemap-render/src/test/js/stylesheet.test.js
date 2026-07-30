"use strict";

/**
 * Guards the report stylesheet against the two failure modes that make a rule
 * silently do nothing: a selector that no code ever applies, and a rule that a
 * later, equally specific rule overrides.
 *
 * Both are invisible to every other test in this module — the page still
 * renders, the JS still runs, no error is logged anywhere. The change overlay
 * simply comes out grey, which is the one thing spec §5 asks the report to get
 * right. Only reading the stylesheet in order catches it.
 */

const fs = require("fs");
const path = require("path");
const assert = require("assert");

const RESOURCE_DIR = path.join(__dirname, "..", "..", "main", "resources", "dev", "codemap", "render");
const CSS = fs.readFileSync(path.join(RESOURCE_DIR, "report.css"), "utf8");
const JS = fs.readFileSync(path.join(RESOURCE_DIR, "report.js"), "utf8");

/** The index of a selector's rule in the stylesheet, or -1 when absent. */
function ruleIndex(selector) {
  return CSS.indexOf(selector + " {");
}

function assertDeclaredAfter(specific, general) {
  const specificAt = ruleIndex(specific);
  const generalAt = ruleIndex(general);
  assert.notStrictEqual(specificAt, -1, "missing rule: " + specific);
  assert.notStrictEqual(generalAt, -1, "missing rule: " + general);
  assert.ok(
      specificAt > generalAt,
      "`" + specific + "` must be declared after `" + general + "`: they have equal specificity, "
          + "so the later rule wins and `" + general + "` would otherwise override it");
}

// Equal-specificity overrides: every status rule has to outrank the base rule,
// and source order is the only thing deciding that.
assertDeclaredAfter(".node-changed > circle", ".node circle");
assertDeclaredAfter(".node-removed > circle", ".node circle");
assertDeclaredAfter(".node-affected > circle", ".node circle");
assertDeclaredAfter(".node-revisit > circle", ".node circle");
assertDeclaredAfter(".node-revisit > text", ".node text");
assertDeclaredAfter(".node.selected > circle", ".node circle");
assertDeclaredAfter(".node-focused > circle", ".node circle");

// Every status/edge class the stylesheet targets must be one the script really
// applies — a rule for a class nobody sets is dead styling that reads as though
// the feature works.
const STYLED_CLASSES = [
  "node-changed", "node-removed", "node-affected", "node-revisit", "node-focused",
  "link-call-internal", "link-call-external", "link-cross-module",
  "link-uses-type", "link-implements", "link-class-member"
];
for (const className of STYLED_CLASSES) {
  assert.ok(CSS.includes("." + className), "stylesheet is missing a rule for ." + className);
  assert.ok(
      JS.includes('"' + className + '"'),
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
assert.ok(ruleIndex("pre.source") !== -1, "missing rule: pre.source");
assert.ok(
    JS.includes('SOURCE_CLASS = "source"'),
    "pre.source is styled but report.js does not set the `source` class");

// Labels must stay clickable: a 6px circle is a hard target next to a label
// that is often ten times wider.
const labelRule = CSS.slice(ruleIndex(".node text"), ruleIndex(".node text") + 200);
assert.ok(
    !/pointer-events:\s*none/.test(labelRule),
    "`.node text` must not disable pointer events — the label is the main click target");

console.log("stylesheet.test.js: all assertions passed");
