/*
 * tinyexpression C ABI (issue #181). Hand-written; kept in sync with
 * rust/tinyexpression-ffi/src/lib.rs (tests/c/smoke.c exercises every function).
 *
 * Contract: input is UTF-8 bytes (source, len; no NUL terminator required). Output is a
 * NUL-terminated UTF-8 JSON document stored in *out, identical to what the `tinyexpression`
 * CLI prints for the same command; release it with te_free(). The return value is the CLI
 * exit code (TE_OK ... TE_ERR_LOAD, plus TE_ERR_INTERNAL for a caught panic).
 *
 * Stability: the JSON document is the interface. No struct crosses the boundary; fields may be
 * added to the JSON, existing fields keep their meaning within a major version. The calling
 * convention below changes only with TE_ABI_VERSION ("abi" in te_version()).
 *
 * Thread safety: every call is independent and reentrant.
 */
#ifndef TINYEXPRESSION_H
#define TINYEXPRESSION_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define TE_ABI_VERSION 1

enum te_status {
  TE_OK = 0,
  TE_ERR_USAGE = 2,      /* null out / null source with len > 0 */
  TE_ERR_PARSE = 3,      /* parse failure (also FormulaInfo syntax) */
  TE_ERR_MAPPING = 4,    /* mapping or type failure */
  TE_ERR_EVALUATION = 5, /* evaluation failure (formula_info run: a formula failed) */
  TE_ERR_IO = 6,         /* source is not UTF-8 */
  TE_ERR_LOAD = 7,       /* FormulaInfo load failure that is not a syntax error */
  TE_ERR_INTERNAL = 70   /* panic caught at the boundary */
};

/* {"ok":true,"ast":{...}} */
int32_t te_parse(const uint8_t *source, size_t len, char **out);
/* {"ok":true} -- parse, map and type-check */
int32_t te_check(const uint8_t *source, size_t len, char **out);
/* {"ok":true,"value":{"kind":"number","value":"3","f32Bits":"0x40400000"}} -- context-free evaluator */
int32_t te_eval(const uint8_t *source, size_t len, char **out);
/* FormulaInfo document: run == 0 loads ({"ok":true,"formulas":[{"info":...}]}),
 * run != 0 also evaluates every formula once; random() is seeded with seed. */
int32_t te_formula_info(const uint8_t *source, size_t len, int32_t run, uint64_t seed,
                        char **out);

/* {"name":"tinyexpression","version":"2.0.0","ubnfc":"<commit>","abi":1}; free with te_free */
char *te_version(void);
/* Releases a JSON document returned above. NULL is ignored. */
void te_free(char *json);

/* Linear-memory helpers for wasm hosts (also exported natively). */
uint8_t *te_alloc(size_t len);
void te_dealloc(uint8_t *ptr, size_t len);

#ifdef __cplusplus
}
#endif

#endif /* TINYEXPRESSION_H */
