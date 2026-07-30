"use strict";

/**
 * Assertions on the light/dark theme toggle (spec §7, kept from feature/6):
 * default theme, `localStorage` persistence, and `prefers-color-scheme`
 * honoured only when nothing is stored yet.
 *
 * Run with: node src/test/js/theme.test.js
 */

const assert = require("assert");
const { loadReportScript } = require("./report-test-harness");

function emptyFixture() {
  return {
    modules: [], entryPoints: [], classes: [], methods: [], edges: [],
    moduleDependencies: [], removedMethods: []
  };
}

function run() {
  const internal = loadReportScript(emptyFixture());

  assert.strictEqual(internal.THEME_DARK, "dark");
  assert.strictEqual(internal.THEME_LIGHT, "light");

  testDefaultsToDarkWithNoStoredPreferenceAndNoOsPreference(internal);
  testHonoursStoredPreferenceOverOsPreference(internal);
  testHonoursOsLightPreferenceWhenNothingStored(internal);
  testApplyThemeSetsTheDocumentAttribute(internal);

  console.log("theme.test.js: all assertions passed");
}

function testDefaultsToDarkWithNoStoredPreferenceAndNoOsPreference(internal) {
  const theme = internal.initialTheme({ getStoredTheme: () => null, prefersLight: () => false });
  assert.strictEqual(theme, internal.THEME_DARK, "with nothing stored and no OS light preference, default stays dark");
}

function testHonoursStoredPreferenceOverOsPreference(internal) {
  const theme = internal.initialTheme({ getStoredTheme: () => internal.THEME_LIGHT, prefersLight: () => false });
  assert.strictEqual(theme, internal.THEME_LIGHT, "an explicit stored choice must win over recomputing from the OS");
}

function testHonoursOsLightPreferenceWhenNothingStored(internal) {
  const theme = internal.initialTheme({ getStoredTheme: () => null, prefersLight: () => true });
  assert.strictEqual(theme, internal.THEME_LIGHT, "with nothing stored yet, the OS preference should be respected");
}

function testApplyThemeSetsTheDocumentAttribute(internal) {
  const attributes = {};
  const fakeRoot = { setAttribute: (name, value) => { attributes[name] = value; } };
  const fakeButton = { textContent: "" };

  internal.applyTheme(internal.THEME_LIGHT, fakeButton, fakeRoot);

  assert.strictEqual(attributes[internal.THEME_ATTRIBUTE], internal.THEME_LIGHT,
      "applying a theme must set it as an attribute on the document root, so CSS custom properties switch");
}

run();
