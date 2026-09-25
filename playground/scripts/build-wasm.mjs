#!/usr/bin/env node
// Builds tinyexpression.wasm from rust/ and copies it to public/ (served next to index.html).
//   npm run build:wasm            # cargo build --profile release-small --target wasm32-unknown-unknown
//   npm run build:wasm -- --release  # the plain release profile instead (larger, faster)
// release-small (opt-level "z", LTO) is what CI and the rust README publish: ~2.1 MB vs ~3.5 MB.
import { execFileSync } from 'node:child_process';
import { copyFileSync, mkdirSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const rust = join(here, '..', '..', 'rust');
const profile = process.argv.includes('--release') ? 'release' : 'release-small';
execFileSync('cargo', [
  'build', '--locked', '--profile', profile, '--target', 'wasm32-unknown-unknown',
  '--manifest-path', join(rust, 'Cargo.toml'), '-p', 'tinyexpression-ffi',
], { stdio: 'inherit' });
const built = join(rust, 'target', 'wasm32-unknown-unknown', profile, 'tinyexpression.wasm');
const target = join(here, '..', 'public', 'tinyexpression.wasm');
mkdirSync(dirname(target), { recursive: true });
copyFileSync(built, target);
console.log(`copied ${built} -> public/tinyexpression.wasm (${statSync(target).size} bytes)`);
