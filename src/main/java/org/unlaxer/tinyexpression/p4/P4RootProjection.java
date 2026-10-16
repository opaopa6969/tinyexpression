package org.unlaxer.tinyexpression.p4;

import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.*;

/** Compatibility projection of the single Formula recognition, without another parser entry. */
public final class P4RootProjection {
  private P4RootProjection() {}

  /** A longer string/boolean result formerly reached a subtree when the numeric prefix hit EOF. */
  public static TinyExpressionP4AST canonicalRoot(
      TinyExpressionP4AST ast, P4SourceText spans, String source) {
    if (!(ast instanceof FormulaExpr formula)
        || !formula.imports().isEmpty() || !formula.declarations().isEmpty()
        || !formula.methods().isEmpty() || !formula.codeBlocks().isEmpty()
        || !source.strip().equals(spans.text(formula.expression()).strip())) return null;
    Object expression = formula.expression().value();
    if (expression instanceof BooleanOrExpr disjunction && numericBooleanPrefix(disjunction)) {
      return disjunction;
    }
    if (expression instanceof StringConcatExpr concat && !concat.op().isEmpty()
        && numericAtom(concat.left())) return concat;
    return null;
  }

  private static boolean numericBooleanPrefix(BooleanOrExpr expression) {
    Object value = expression.left().left().left().value();
    if (value instanceof ComparisonExpr) return true;
    if (value instanceof StringComparisonExpr comparison) return numericAtom(comparison.left().left());
    if (value instanceof BooleanEqualityExpr equality) return numericAtom(equality.left());
    return numericAtom(value);
  }

  /** Whether a complete operand belongs to NumberFactor, based on the recognized AST. */
  private static boolean numericAtom(Object value) {
    if (value instanceof VariableRefExpr || value instanceof StringTypedVariableRefExpr
        || value instanceof MethodInvocationExpr || value instanceof IfExpr) return true;
    if (value instanceof StringConcatExpr concat) {
      return numericAtom(concat.left()) && concat.right().stream().allMatch(P4RootProjection::numericAtom);
    }
    if (value instanceof StringMatchExpr match) {
      return numericAtom(match.firstCase().value().value())
          && match.moreCases().stream().allMatch(entry -> numericAtom(entry.value().value()))
          && numericAtom(match.defaultCase().value().value());
    }
    if (value instanceof BooleanMatchExpr match) {
      return numericAtom(match.firstCase().value().value())
          && match.moreCases().stream().allMatch(entry -> numericAtom(entry.value().value()))
          && numericAtom(match.defaultCase().value().value());
    }
    if (value instanceof BooleanOrExpr disjunction && disjunction.op().isEmpty()) {
      var conjunction = disjunction.left();
      var exclusive = conjunction.left();
      return conjunction.op().isEmpty() && exclusive.op().isEmpty()
          && numericAtom(exclusive.left().value());
    }
    return false;
  }
}
