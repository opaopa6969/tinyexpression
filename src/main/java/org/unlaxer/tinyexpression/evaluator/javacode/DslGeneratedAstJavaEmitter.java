package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.Optional;

import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.p4.P4PreferredAstMapper;
import org.unlaxer.tinyexpression.parser.ExpressionType;

/** Emits Java exclusively from the generated, typed P4 AST. */
final class DslGeneratedAstJavaEmitter {

  record EmittedJava(String javaCode, String mode) {}

  private DslGeneratedAstJavaEmitter() {}

  static Optional<EmittedJava> tryEmit(String className, Source source,
      SpecifiedExpressionTypes specifiedExpressionTypes, ClassLoader classLoader) {
    if (className == null || className.isBlank() || source == null
        || specifiedExpressionTypes == null) {
      return Optional.empty();
    }
    ExpressionType resultType = specifiedExpressionTypes.resultType();
    String formula = source.source() == null ? "" : source.source().strip();
    if (resultType == null || formula.isEmpty()) {
      return Optional.empty();
    }

    P4PreferredAstMapper.ParsedAst parsed;
    try {
      parsed = P4PreferredAstMapper.parseDetailed(formula, resultType);
    } catch (RuntimeException parseFailure) {
      return Optional.empty();
    }
    TinyExpressionP4AST ast = parsed.ast();
    if (ast == null) {
      return Optional.empty();
    }

    P4TypedJavaCodeEmitter emitter = new P4TypedJavaCodeEmitter(
        specifiedExpressionTypes, formula, parsed.sourceText());
    String expression = emitter.eval(ast);
    if (expression == null || expression.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(new EmittedJava(
        emitter.buildJavaClass(className, expression), "p4-typed-emitter"));
  }
}
