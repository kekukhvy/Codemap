"use strict";

/**
 * Syntax highlighting for the embedded source (spec 007 §4.2).
 *
 * Hand-written rather than a highlighting library: the report inlines
 * everything and must work over `file://` with no network, so a dependency
 * would have to be vendored whole for one panel.
 *
 * Run with: node src/test/js/source-highlighting.test.js
 */

const assert = require("assert");
const { loadReportScript } = require("./report-test-harness");

const EMPTY = { modules: [], entryPoints: [], classes: [], methods: [], edges: [], moduleDependencies: [], removedMethods: [] };

/**
 * Arrays returned from the script cross a `vm` realm boundary, so
 * `deepStrictEqual` would compare prototypes and never match. Kinds are
 * compared as a joined string instead.
 */
function kindsOf(internal, source) {
  return internal.tokenizeJava(source).filter((token) => token.kind !== null);
}

function run() {
  const internal = loadReportScript(EMPTY);

  testTokenizingIsLossless(internal);
  testRecognisesTheUsualTokens(internal);
  testAKeywordInsideAStringIsNotAKeyword(internal);
  testAKeywordInsideACommentIsNotAKeyword(internal);
  testIdentifiersNamedLikeKeywordsAreLeftAlone(internal);
  testUnterminatedConstructsDoNotHang(internal);

  console.log("source-highlighting.test.js: all assertions passed");
}

/**
 * The panel must show the source exactly as it is. Highlighting that drops or
 * duplicates a character would be worse than no highlighting at all.
 */
function testTokenizingIsLossless(internal) {
  const samples = [
    "public void save(String name) {\n    repo.save(name);\n}",
    "/* block */ int x = 1; // trailing\n",
    "String s = \"a\\\"b\";\nchar c = '\\n';",
    "@Override\npublic String toString() { return \"\"\"\ntext block\n\"\"\"; }",
    ""
  ];

  for (const sample of samples) {
    const rebuilt = internal.tokenizeJava(sample).map((token) => token.text).join("");
    assert.strictEqual(rebuilt, sample, "tokenizing must round-trip exactly");
  }
}

function testRecognisesTheUsualTokens(internal) {
  const kinds = kindsOf(internal, '@Override\npublic int n = 42; // note\nString s = "hi";');

  const byKind = new Set(kinds.map((token) => token.kind));
  for (const expected of ["annotation", "keyword", "number", "comment", "string"]) {
    assert.ok(byKind.has(expected), `expected to recognise a ${expected}, got ${[...byKind].join(", ")}`);
  }
}

/** Ordering matters: strings are consumed whole before anything looks inside them. */
function testAKeywordInsideAStringIsNotAKeyword(internal) {
  const kinds = kindsOf(internal, 'String s = "public static void";');

  assert.strictEqual(kinds.map((token) => token.kind).join(","), "string",
      "the words inside the literal must stay part of the string");
}

function testAKeywordInsideACommentIsNotAKeyword(internal) {
  const kinds = kindsOf(internal, "// return null if absent\n");

  assert.strictEqual(kinds.map((token) => token.kind).join(","), "comment",
      "a comment is one token, whatever words it contains");
}

/** `className` starts with `class` but is not a keyword. */
function testIdentifiersNamedLikeKeywordsAreLeftAlone(internal) {
  const kinds = kindsOf(internal, "var className = interfaceName;");

  assert.strictEqual(kinds.map((token) => token.kind).join(","), "keyword",
      "only the standalone `var` is a keyword: className and interfaceName are identifiers");
}

/** Truncated source is normal — the panel shows a slice of a file. */
function testUnterminatedConstructsDoNotHang(internal) {
  for (const sample of ['String s = "never closed', "/* never closed", "'"]) {
    const rebuilt = internal.tokenizeJava(sample).map((token) => token.text).join("");
    assert.strictEqual(rebuilt, sample, "an unterminated construct must still round-trip, not loop");
  }
}

run();
