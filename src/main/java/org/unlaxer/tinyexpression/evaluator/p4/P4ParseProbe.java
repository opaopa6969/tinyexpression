package org.unlaxer.tinyexpression.evaluator.p4;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

final class P4ParseProbe {

  private P4ParseProbe() {}

  static Result probe(String formula, SpecifiedExpressionTypes specifiedExpressionTypes) {
    for (String preferredAstSimpleName : preferredAstSimpleNames(formula, specifiedExpressionTypes)) {
      Result result = tryParse(formula, preferredAstSimpleName);
      if (result != null) {
        return result;
      }
    }
    Result fallback = tryParse(formula, null);
    if (fallback != null) {
      return fallback;
    }
    Result heuristic = heuristicResult(formula, specifiedExpressionTypes);
    return heuristic != null ? heuristic : Result.parseFailed();
  }

  private static Result tryParse(String formula, String preferredAstSimpleName) {
    try {
      TinyExpressionP4AST ast = preferredAstSimpleName == null
          ? TinyExpressionP4Mapper.parse(formula)
          : TinyExpressionP4Mapper.parse(formula, preferredAstSimpleName);
      Optional<String> violation = P4StrictMatchTypingValidator.firstViolation(ast, formula);
      if (violation.isPresent()) {
        return new Result(false, false, "semantic", ast.getClass().getSimpleName());
      }
      return new Result(true, true, "exact", ast.getClass().getSimpleName());
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static List<String> preferredAstSimpleNames(String formula, SpecifiedExpressionTypes specifiedExpressionTypes) {
    ArrayList<String> names = new ArrayList<>();
    if (formula.contains("match{")) {
      ExpressionType resultType = specifiedExpressionTypes.resultType();
      if (resultType == ExpressionTypes.string) {
        names.add("StringMatchExpr");
      } else if (resultType == ExpressionTypes._boolean) {
        names.add("BooleanMatchExpr");
      } else if (resultType instanceof ExpressionTypes expressionTypes && expressionTypes.isNumber()) {
        names.add("NumberMatchExpr");
      }
    }
    return names;
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
