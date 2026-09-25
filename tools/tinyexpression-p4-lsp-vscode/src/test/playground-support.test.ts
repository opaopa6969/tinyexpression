// node --test (npm test): the VS Code-independent parts of the playground webview and the
// catalog import (issue #201, stages 4 and 5).
import { test } from "node:test";
import * as assert from "node:assert/strict";
import * as fs from "fs";
import * as path from "path";
import {
  checkCatalogExport, rewritePlaygroundHtml, formulaAt, resolveOverridePath, catalogLabel,
  WORKSPACE_OVERRIDE_PATH
} from "../playground-support";

const root = path.resolve(__dirname, "..", "..", "..");

test("the golden playground override is an importable partial export", () => {
  const text = fs.readFileSync(path.join(root, "tools/tinyexpression-p4-lsp-vscode/src/test/resources/catalog-golden/playground-export.override.json"), "utf8");
  const check = checkCatalogExport(text);
  assert.equal(check.ok, true, check.problems.join("\n"));
  assert.equal(check.kind, "override");
  assert.deepEqual(check.sections, ["variables", "functions", "errorCodes"]);
});

test("the repository catalog is an importable full export", () => {
  const check = checkCatalogExport(fs.readFileSync(path.join(root, "catalog/tinyexpression-catalog.json"), "utf8"));
  assert.equal(check.ok, true, check.problems.join("\n"));
  assert.equal(check.kind, "full");
  assert.ok(check.entries > 1400);
});

test("malformed exports are rejected with a reason", () => {
  assert.equal(checkCatalogExport("[1]").ok, false);
  assert.equal(checkCatalogExport("{").ok, false);
  assert.match(checkCatalogExport('{"functions": {}}').problems.join(), /must be an array/);
  assert.match(checkCatalogExport('{"functionz": []}').problems.join(), /unknown key functionz/);
  assert.match(checkCatalogExport("{}").problems.join(), /no catalog section/);
});

test("the playground page is rewritten for the webview", () => {
  const html = '<!doctype html><html><head><meta charset="utf-8">\n<script type="module" crossorigin src="./assets/index-x.js"></script>\n<link rel="stylesheet" crossorigin href="./assets/index-x.css"></head><body></body></html>';
  const out = rewritePlaygroundHtml(html, { baseUri: "https://file+.vscode-resource.vscode-cdn.net/ext/playground-dist", cspSource: "https://*.vscode-cdn.net", nonce: "N0NCE" });
  assert.match(out, /<base href="https:\/\/file\+\.vscode-resource\.vscode-cdn\.net\/ext\/playground-dist\/">/);
  assert.match(out, /script-src 'nonce-N0NCE' 'wasm-unsafe-eval'/);
  assert.match(out, /<script nonce="N0NCE" type="module" src="\.\/assets\/index-x\.js">/);
  assert.doesNotMatch(out, /crossorigin/);
  assert.ok(out.indexOf("<base") < out.indexOf("<script"));
});

test("the built playground bundle, when present, rewrites cleanly", () => {
  const dist = path.join(root, "playground/dist/index.html");
  if (!fs.existsSync(dist)) return;
  const out = rewritePlaygroundHtml(fs.readFileSync(dist, "utf8"), { baseUri: "https://x/", cspSource: "https://x", nonce: "n" });
  assert.equal((out.match(/<script /g) ?? []).length, (out.match(/nonce="n"/g) ?? []).length);
});

test("FormulaInfo documents preload the block at the cursor", () => {
  const doc = "calculatorName:a\nformula:\n1 + 1\n---END_OF_PART---\ncalculatorName:b\nformula:\n$x * 2\n---END_OF_PART---\n";
  assert.equal(formulaAt(doc, 3).formula, "1 + 1");
  assert.equal(formulaAt(doc, doc.indexOf("$x")).formula, "$x * 2");
  assert.equal(formulaAt(doc, 0).formulaInfo, doc);
  assert.deepEqual(formulaAt("$a + 1", 0), { formula: "$a + 1", formulaInfo: null });
});

test("override paths and labels", () => {
  assert.equal(resolveOverridePath(WORKSPACE_OVERRIDE_PATH, "/ws", path.posix.join, path.posix.isAbsolute), "/ws/.vscode/tinyexpression-catalog.override.json");
  assert.equal(resolveOverridePath("/abs/c.json", "/ws", path.posix.join, path.posix.isAbsolute), "/abs/c.json");
  assert.equal(resolveOverridePath("", "/ws", path.posix.join, path.posix.isAbsolute), "");
  assert.equal(catalogLabel(""), "bundled");
  assert.equal(catalogLabel(WORKSPACE_OVERRIDE_PATH), "bundled + tinyexpression-catalog.override.json");
});
