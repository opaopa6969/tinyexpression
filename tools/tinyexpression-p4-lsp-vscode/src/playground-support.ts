/**
 * VS Code-independent helpers for the playground webview and the catalog import (issue #201,
 * stages 4 and 5). Kept free of the `vscode` module so that `npm test` runs them under node.
 */

/** Catalog sections an export may contain (the keys CatalogProvider.withOverride reads). */
export const CATALOG_SECTIONS = [
  "variableGroups", "variables", "functions", "keywords", "values", "externals",
  "errorCodes", "diagnosticRules", "runtimeErrors", "settings"
] as const;

const OTHER_KEYS = new Set(["$schema", "schemaVersion", "description", "defaultErrorCode"]);

/** Where "Import catalog from playground export" puts the file, relative to the workspace. */
export const WORKSPACE_OVERRIDE_PATH = ".vscode/tinyexpression-catalog.override.json";

export interface CatalogExportCheck {
  ok: boolean;
  /** "full": a whole catalog (every required section); "override": a partial one. */
  kind: "full" | "override";
  sections: string[];
  entries: number;
  problems: string[];
}

/**
 * Checks that a JSON text is a catalog export the LSP can merge: an object whose known sections
 * are arrays of objects. Unknown top-level keys are reported (they would be ignored silently).
 */
export function checkCatalogExport(text: string): CatalogExportCheck {
  const problems: string[] = [];
  let json: unknown;
  try {
    json = JSON.parse(text);
  } catch (err: unknown) {
    return { ok: false, kind: "override", sections: [], entries: 0, problems: [`not JSON: ${String(err)}`] };
  }
  if (json === null || typeof json !== "object" || Array.isArray(json)) {
    return { ok: false, kind: "override", sections: [], entries: 0, problems: ["a catalog must be a JSON object"] };
  }
  const object = json as Record<string, unknown>;
  const sections: string[] = [];
  let entries = 0;
  for (const [key, value] of Object.entries(object)) {
    if ((CATALOG_SECTIONS as readonly string[]).includes(key)) {
      if (!Array.isArray(value)) {
        problems.push(`${key} must be an array`);
        continue;
      }
      if (value.some((e) => e === null || typeof e !== "object" || Array.isArray(e))) {
        problems.push(`${key} must contain objects`);
      }
      sections.push(key);
      entries += value.length;
    } else if (!OTHER_KEYS.has(key)) {
      problems.push(`unknown key ${key}`);
    }
  }
  if (sections.length === 0 && object.defaultErrorCode === undefined) {
    problems.push("no catalog section (variables, functions, errorCodes, ...)");
  }
  const full = CATALOG_SECTIONS.every((s) => sections.includes(s));
  return { ok: problems.length === 0, kind: full ? "full" : "override", sections, entries, problems };
}

/** A relative override path is relative to the (first) workspace folder. */
export function resolveOverridePath(value: string, workspaceFolder: string | undefined,
  join: (...parts: string[]) => string, isAbsolute: (p: string) => boolean): string {
  const trimmed = value.trim();
  if (trimmed === "" || isAbsolute(trimmed) || workspaceFolder === undefined) return trimmed;
  return join(workspaceFolder, trimmed);
}

/** Status bar / webview label of the active catalog. */
export function catalogLabel(overridePath: string): string {
  if (overridePath.trim() === "") return "bundled";
  const name = overridePath.replace(/\\/g, "/").split("/").pop();
  return `bundled + ${name}`;
}

/**
 * The built playground's index.html (Vite, relative asset paths) prepared for a webview:
 * a <base> pointing at the packaged `playground-dist/`, a content security policy, the nonce
 * on the module script, and no `crossorigin` (webview resources are same-origin by URI).
 */
export function rewritePlaygroundHtml(html: string, options: { baseUri: string; cspSource: string; nonce: string }): string {
  const base = options.baseUri.endsWith("/") ? options.baseUri : `${options.baseUri}/`;
  const csp = [
    "default-src 'none'",
    `img-src ${options.cspSource} data:`,
    `style-src ${options.cspSource} 'unsafe-inline'`,
    `font-src ${options.cspSource}`,
    // 'wasm-unsafe-eval': WebAssembly.instantiate for tinyexpression.wasm (no JS eval).
    `script-src 'nonce-${options.nonce}' 'wasm-unsafe-eval'`,
    // The wasm file, and the Catalog panel's optional "Create PR" through the GitHub API.
    `connect-src ${options.cspSource} https://api.github.com`
  ].join("; ");
  const head = `<base href="${base}">\n  <meta http-equiv="Content-Security-Policy" content="${csp}">`;
  let out = html.replace(/<head>/i, `<head>\n  ${head}`);
  out = out.replace(/\s+crossorigin(=("[^"]*"|'[^']*'))?/g, "");
  out = out.replace(/<script\b/g, `<script nonce="${options.nonce}"`);
  return out;
}

/**
 * The formula to preload from a document: the whole text, or for a FormulaInfo document the
 * `formula:` body of the block around `offset`.
 */
export function formulaAt(text: string, offset: number): { formula: string; formulaInfo: string | null } {
  if (!/^formula:\s*$/m.test(text) || !/---END_OF_PART---/.test(text)) {
    return { formula: text, formulaInfo: null };
  }
  const blocks = text.split(/^---END_OF_PART---\s*$/m);
  let start = 0;
  let chosen = blocks[0];
  for (const block of blocks) {
    const end = start + block.length;
    if (offset <= end) {
      chosen = block;
      break;
    }
    start = end + "---END_OF_PART---".length;
    chosen = block;
  }
  const match = /^formula:\s*$([\s\S]*)/m.exec(chosen);
  const formula = match ? match[1].replace(/^\r?\n/, "").replace(/\s+$/, "") : "";
  return { formula, formulaInfo: text };
}

/** A random nonce for the webview's script tag. */
export function nonce(random: () => number = Math.random): string {
  const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
  let out = "";
  for (let i = 0; i < 32; i++) out += chars.charAt(Math.floor(random() * chars.length));
  return out;
}
