package org.unlaxer.tinyexpression.evaluator.p4;

import java.util.Optional;

import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
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
      return Result.parseFailed();
    }
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
