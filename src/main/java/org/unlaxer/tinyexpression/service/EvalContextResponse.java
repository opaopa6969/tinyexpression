package org.unlaxer.tinyexpression.service;

/**
 * One response of {@link EvalContextService} (issue #221): the JSON document (the contract) and
 * the exit code the Rust CLI would use for it ({@code rust/tinyexpression-rs/src/api.rs}:
 * 0 success, 2 request error, 3 parse failure, 4 other creation failure, 5 evaluation failure,
 * 7 FormulaInfo load failure). HTTP hosts normally answer 200 with {@link #json()} whatever the
 * exit code is: success and failure are in the document ({@code "ok"}).
 */
public record EvalContextResponse(int exitCode, String json) {

  public static final int EXIT_SUCCESS = 0;
  public static final int EXIT_USAGE = 2;
  public static final int EXIT_PARSE = 3;
  public static final int EXIT_MAPPING = 4;
  public static final int EXIT_EVALUATION = 5;
  public static final int EXIT_LOAD = 7;
  public static final int EXIT_INTERNAL = 70;
}
