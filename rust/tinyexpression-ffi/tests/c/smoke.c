/* C ABI smoke test (issue #181): rust/tinyexpression-ffi/tests/c/run.sh builds and runs it. */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "tinyexpression.h"

static int failures = 0;

static void expect(int condition, const char *what, const char *json) {
  if (!condition) {
    fprintf(stderr, "FAIL: %s: %s\n", what, json ? json : "(null)");
    failures++;
  }
}

static int32_t call(int32_t (*fn)(const uint8_t *, size_t, char **), const char *source,
                    char **out) {
  return fn((const uint8_t *)source, strlen(source), out);
}

int main(void) {
  char *json = NULL;

  int32_t code = call(te_eval, "1 + 2", &json);
  printf("te_eval(\"1 + 2\") = %d %s\n", code, json);
  expect(code == TE_OK, "eval status", json);
  expect(strstr(json, "\"ok\":true") && strstr(json, "\"value\":\"3\""), "eval value", json);
  te_free(json);

  code = call(te_eval, "1 +", &json);
  expect(code == TE_ERR_PARSE && strstr(json, "\"stage\":\"parse\""), "parse error", json);
  te_free(json);

  code = call(te_parse, "$a * 2", &json);
  expect(code == TE_OK && strstr(json, "\"ast\":{\"type\":\"FormulaExpr\""), "parse", json);
  te_free(json);

  code = call(te_check, "if($x > 1){2}else{3}", &json);
  expect(code == TE_OK && strcmp(json, "{\"ok\":true}") == 0, "check", json);
  te_free(json);

  const char *document = "calculatorName:base\nformula:\n1 + 1\n---END_OF_PART---\n";
  code = te_formula_info((const uint8_t *)document, strlen(document), 1, 7, &json);
  printf("te_formula_info(run) = %d %s\n", code, json);
  expect(code == TE_OK && strstr(json, "\"formulas\":[{\"info\""), "formula info", json);
  te_free(json);

  code = call(te_eval_context,
              "{\"formula\":\"$price * 2\",\"variables\":[{\"name\":\"price\",\"type\":\"float\",\"value\":\"1.5\"}]}",
              &json);
  printf("te_eval_context = %d %s\n", code, json);
  expect(code == TE_OK && strstr(json, "\"text\":\"3.0\""), "eval context", json);
  te_free(json);

  code = call(te_eval_trace,
              "{\"formula\":\"$price * 2\",\"variables\":[{\"name\":\"price\",\"type\":\"float\",\"value\":\"1.5\"}]}",
              &json);
  printf("te_eval_trace = %d %s\n", code, json);
  expect(code == TE_OK && strstr(json, "\"text\":\"3.0\"") && strstr(json, "\"trace\":{\"steps\":"), "eval trace", json);
  te_free(json);

  code = call(te_formula_info_context,
              "{\"document\":\"formula:\\n$x + 1\\n---END_OF_PART---\\n\",\"variables\":[{\"name\":\"x\",\"value\":2}]}",
              &json);
  expect(code == TE_OK && strstr(json, "\"value\":\"3\""), "formula info context", json);
  te_free(json);

  const uint8_t invalid[] = {0xff, 0xfe};
  code = te_eval(invalid, sizeof invalid, &json);
  expect(code == TE_ERR_IO, "invalid UTF-8", json);
  te_free(json);

  expect(te_eval(NULL, 0, NULL) == TE_ERR_USAGE, "null out", NULL);

  char *version = te_version();
  printf("te_version() = %s\n", version);
  expect(strstr(version, "\"abi\":1}") != NULL, "version", version);
  te_free(version);

  if (failures) {
    fprintf(stderr, "%d failure(s)\n", failures);
    return 1;
  }
  puts("C ABI smoke test: OK");
  return 0;
}
