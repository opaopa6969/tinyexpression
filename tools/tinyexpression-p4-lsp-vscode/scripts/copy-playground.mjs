#!/usr/bin/env node
// Copies the built playground (playground/dist: index.html, assets, tinyexpression.wasm) into
// playground-dist/ so that `vsce package` puts it into the VSIX (issue #201, stage 5). The
// webview of "TinyExpression: Open playground" loads it: one web build, no fork of the UI.
//
// Build it first: (cd playground && npm ci && npm run build:wasm && npm run build).
// Without it the VSIX gets a placeholder page, unless TE_REQUIRE_PLAYGROUND=1 (CI), which fails.
import { cpSync, existsSync, mkdirSync, rmSync, writeFileSync, statSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const source = join(here, '..', '..', '..', 'playground', 'dist');
const target = join(here, '..', 'playground-dist');
rmSync(target, { recursive: true, force: true });
if (!existsSync(join(source, 'index.html')) || !existsSync(join(source, 'tinyexpression.wasm'))) {
  if (process.env.TE_REQUIRE_PLAYGROUND === '1') {
    console.error(`copy-playground: ${source} is missing (build the playground first)`);
    process.exit(1);
  }
  mkdirSync(target, { recursive: true });
  writeFileSync(join(target, 'index.html'), '<!doctype html><html lang="ja"><head><meta charset="utf-8"><title>tinyexpression playground</title></head>'
    + '<body><p>この VSIX には playground が同梱されていません（playground/dist を build してから package してください）。'
    + ' <a href="https://opaopa6969.github.io/tinyexpression/">https://opaopa6969.github.io/tinyexpression/</a></p></body></html>\n');
  console.warn('copy-playground: playground/dist not built; packaged a placeholder page');
  process.exit(0);
}
cpSync(source, target, { recursive: true });
let bytes = 0;
const walk = (dir) => { for (const name of readdirSync(dir)) { const p = join(dir, name); const s = statSync(p); if (s.isDirectory()) walk(p); else bytes += s.size; } };
walk(target);
console.log(`copy-playground: playground/dist -> playground-dist (${(bytes / 1024 / 1024).toFixed(2)} MB)`);
