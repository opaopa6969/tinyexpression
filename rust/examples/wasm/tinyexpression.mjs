// Minimal JS binding for tinyexpression.wasm (issue #181): no wasm-bindgen, no imports.
// Works in browsers and node. Each call returns {code, result}: the CLI exit code and the
// parsed JSON document (the same JSON the `tinyexpression` CLI prints).
export async function loadTinyExpression(bytesOrResponse) {
  const { instance } = bytesOrResponse instanceof Response
    ? await WebAssembly.instantiateStreaming(bytesOrResponse, {})
    : await WebAssembly.instantiate(bytesOrResponse, {});
  const te = instance.exports;
  const encoder = new TextEncoder();
  const decoder = new TextDecoder();

  const readJson = (pointer) => {
    const bytes = new Uint8Array(te.memory.buffer);
    let end = pointer;
    while (bytes[end] !== 0) end++;
    const json = decoder.decode(bytes.subarray(pointer, end));
    te.te_free(pointer);
    return JSON.parse(json);
  };

  const call = (fn, source, ...extra) => {
    const input = encoder.encode(source);
    const src = te.te_alloc(input.length);
    new Uint8Array(te.memory.buffer, src, input.length).set(input);
    const out = te.te_alloc(4); // char **out: one wasm32 pointer
    try {
      const code = fn(src, input.length, ...extra, out);
      const pointer = new DataView(te.memory.buffer).getUint32(out, true);
      return { code, result: readJson(pointer) };
    } finally {
      te.te_dealloc(src, input.length);
      te.te_dealloc(out, 4);
    }
  };

  return {
    parse: (formula) => call(te.te_parse, formula),
    check: (formula) => call(te.te_check, formula),
    eval: (formula) => call(te.te_eval, formula),
    load: (document) => call(te.te_formula_info, document, 0, 0n),
    run: (document, seed = 1n) => call(te.te_formula_info, document, 1, BigInt(seed)),
    version: () => readJson(te.te_version()),
  };
}
