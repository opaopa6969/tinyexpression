import { defineConfig } from 'vite';

// Relative base so the build works under https://<owner>.github.io/tinyexpression/ and locally.
// The playground imports ../rust/examples/wasm/tinyexpression.mjs and ../catalog/*.json.
export default defineConfig({
  base: './',
  server: { fs: { allow: ['..'] } },
  build: { target: 'es2022', chunkSizeWarningLimit: 2000 },
});
