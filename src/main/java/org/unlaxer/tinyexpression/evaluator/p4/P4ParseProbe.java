package org.unlaxer.tinyexpression.evaluator.p4;

import java.util.Optional;

import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.p4.P4PreferredAstMapper;

final class P4ParseProbe {

  private P4ParseProbe() {}

  static Result probe(String formula, SpecifiedExpressionTypes specifiedExpressionTypes) {
    try {
      P4PreferredAstMapper.ParsedAst parsed =
          P4PreferredAstMapper.parseDetailed(formula, specifiedExpressionTypes.resultType());
      Optional<String> violation = P4StrictMatchTypingValidator.firstViolation(parsed.ast(), formula);
      if (violation.isPresent()) {
        return new Result(false, false, "semantic", parsed.ast().getClass().getSimpleName());
      }
      return new Result(true, true, "exact", parsed.ast().getClass().getSimpleName());
    } catch (Throwable ignored) {
      // Fall through to heuristic probe for formulas the mapper still cannot root exactly.
    }
    Result heuristic = heuristicResult(formula, specifiedExpressionTypes);
    return heuristic != null ? heuristic : Result.parseFailed();
  }

  private static Result heuristicResult(String formula, SpecifiedExpressionTypes specifiedExpressionTypes) {
    String normalized = formula == null ? "" : formula.strip();
    ExpressionType resultType = specifiedExpressionTypes.resultType();
    if (normalized.startsWith("match{") || normalized.startsWith("match {")) {
      Optional<String> violation = P4StrictMatchTypingValidator.firstHeuristicViolation(normalized, resultType);
      if (violation.isPresent()) {
        if (resultType == ExpressionTypes.string) {
          return new Result(false, false, "semantic", "StringMatchExpr");
        }
        if (resultType == ExpressionTypes._boolean) {
          return new Result(false, false, "semantic", "BooleanMatchExpr");
        }
        if (resultType instanceof ExpressionTypes expressionTypes && expressionTypes.isNumber()) {
          return new Result(false, false, "semantic", "NumberMatchExpr");
        }
        return Result.parseFailed();
      }
      if (resultType == ExpressionTypes.string) {
        return new Result(true, false, "heuristic", "StringMatchExpr");
      }
      if (resultType == ExpressionTypes._boolean) {
        return new Result(true, false, "heuristic", "BooleanMatchExpr");
      }
      if (resultType instanceof ExpressionTypes expressionTypes && expressionTypes.isNumber()) {
        return new Result(true, false, "heuristic", "NumberMatchExpr");
      }
      return null;
    }
    if (resultType instanceof ExpressionTypes expressionTypes && expressionTypes.isNumber()) {
      if (normalized.matches("[0-9+\\-*/().\\s]+")) {
        return new Result(true, false, "heuristic", "BinaryExpr");
      }
      return null;
    }
    if (resultType == ExpressionTypes.string) {
      return null;
    }
    if (resultType == ExpressionTypes._boolean) {
      if ("true".equals(normalized) || "false".equals(normalized)) {
        return new Result(true, false, "heuristic", "BooleanOrExpr");
      }
    }
    return null;
  }

  static final class Result {
    final boolean parserUsed;
    final boolean exactParse;
    final String probeMode;
    final String astNodeType;

    Result(boolean parserUsed, boolean exactParse, String probeMode, String astNodeType) {
      this.parserUsed = parserUsed;
      this.exactParse = exactParse;
      this.probeMode = probeMode;
      this.astNodeType = astNodeType;
    }

    static Result parseFailed() {
      return new Result(false, false, "failed", "parse-failed");
    }
  }
}
