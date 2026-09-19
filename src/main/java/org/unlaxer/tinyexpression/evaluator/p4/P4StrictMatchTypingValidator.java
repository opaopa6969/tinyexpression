package org.unlaxer.tinyexpression.evaluator.p4;

import java.lang.reflect.RecordComponent;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.BinaryExpr;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.BooleanAndExpr;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.BooleanCaseValueExpr;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.BooleanFactorExpr;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.BooleanOrExpr;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.BooleanXorExpr;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.MethodInvocationExpr;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.NumberCaseValueExpr;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.StringCaseValueExpr;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.StringConcatExpr;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.VariableRefExpr;
import org.unlaxer.tinyexpression.p4.P4SourceText;

/**
 * Semantic strict-typing guard for P4 match expressions.
 * <p>
 * The current UBNF grammar can distinguish match result families
 * (number/string/boolean), but direct {@code $var} and {@code internal foo()}
 * case values are still syntactically ambiguous because {@code VariableRef}
 * and {@code MethodInvocation} participate in multiple expression families.
 * <p>
 * This validator rejects those direct ambiguous shapes from the P4 exact-parse
 * path until declaration-aware type recovery is wired into the generated AST.
 */
public final class P4StrictMatchTypingValidator {

  private P4StrictMatchTypingValidator() {}

  public static Optional<String> firstViolation(TinyExpressionP4AST ast, String formula) {
    return firstViolation(ast, formula, P4SourceText.lexicalOnly());
  }

  public static Optional<String> firstViolation(
      TinyExpressionP4AST ast, String formula, P4SourceText sourceText) {
    return firstViolationDetail(ast, formula, sourceText).map(Violation::message);
  }

  public static void validateOrThrow(TinyExpressionP4AST ast, String formula) {
    validateOrThrow(ast, formula, P4SourceText.lexicalOnly());
  }

  public static void validateOrThrow(
      TinyExpressionP4AST ast, String formula, P4SourceText sourceText) {
    firstViolation(ast, formula, sourceText).ifPresent(message -> {
      throw new IllegalArgumentException(message);
    });
  }

  public static Optional<Violation> firstViolationDetail(TinyExpressionP4AST ast, String formula) {
    return firstViolationDetail(ast, formula, P4SourceText.lexicalOnly());
  }

  public static Optional<Violation> firstViolationDetail(
      TinyExpressionP4AST ast, String formula, P4SourceText sourceText) {
    if (ast == null || formula == null || formula.isBlank()) {
      return Optional.empty();
    }
    P4SourceText owned = sourceText == null ? P4SourceText.lexicalOnly() : sourceText;
    return firstViolationRecursive(
        ast, formula, owned, java.util.Collections.newSetFromMap(new IdentityHashMap<>()));
  }

  private static Optional<Violation> firstViolationRecursive(
      Object node, String formula, P4SourceText sourceText, Set<Object> visited) {
    if (node == null || visited.add(node) == false) {
      return Optional.empty();
    }
    Optional<Violation> directViolation = switch (node) {
      case NumberCaseValueExpr numberCaseValue ->
          validateDirectCaseValue(formula, sourceText, numberCaseValue, ExpectedType.NUMBER);
      case StringCaseValueExpr stringCaseValue ->
          validateDirectCaseValue(formula, sourceText, stringCaseValue, ExpectedType.STRING);
      case BooleanCaseValueExpr booleanCaseValue ->
          validateDirectCaseValue(formula, sourceText, booleanCaseValue, ExpectedType.BOOLEAN);
      default -> Optional.empty();
    };
    if (directViolation.isPresent()) {
      return directViolation;
    }

    if (node instanceof List<?> list) {
      for (Object child : list) {
        Optional<Violation> violation = firstViolationRecursive(child, formula, sourceText, visited);
        if (violation.isPresent()) {
          return violation;
        }
      }
      return Optional.empty();
    }

    Class<?> nodeClass = node.getClass();
    if (!nodeClass.isRecord()) {
      return Optional.empty();
    }
    for (RecordComponent component : nodeClass.getRecordComponents()) {
      try {
        Object child = component.getAccessor().invoke(node);
        Optional<Violation> violation = firstViolationRecursive(child, formula, sourceText, visited);
        if (violation.isPresent()) {
          return violation;
        }
      } catch (ReflectiveOperationException ignored) {
        // Skip inaccessible synthetic components and continue validating others.
      }
    }
    return Optional.empty();
  }

  private static Optional<Violation> validateDirectCaseValue(
      String formula, P4SourceText sourceText, Object caseValueNode, ExpectedType expectedType) {
    TinyExpressionP4AST directValue = directCaseValueNode(caseValueNode);
    if (directValue == null) {
      return Optional.empty();
    }

    Optional<int[]> span = sourceText.spanOf(directValue)
        .or(() -> sourceText.spanOf(caseValueNode));
    int formulaLength = formula.codePointCount(0, formula.length());
    int start = span.map(value -> Math.max(0, Math.min(value[0], formulaLength))).orElse(0);
    int end = span.map(value -> Math.max(start, Math.min(value[1], formulaLength)))
        .orElse(formulaLength);
    String snippet = formula.substring(
        formula.offsetByCodePoints(0, start), formula.offsetByCodePoints(0, end));
    if (directValue instanceof VariableRefExpr variable) {
      Optional<String> actualHint = variable.type();
      if (actualHint.isPresent() && !expectedType.accepts(actualHint.get())) {
        return Optional.of(new Violation(
            "P4 strict match typing rejected direct "
                + expectedType.label + " case value with mismatched type hint: " + snippet,
            start,
            end,
            ViolationKind.DIRECT_VARIABLE_CASE_VALUE,
            snippet));
      }
      return Optional.empty();
    }
    if (directValue instanceof MethodInvocationExpr) {
      return Optional.of(new Violation(
          "P4 strict match typing rejected direct method invocation in "
              + expectedType.label + " match case: " + snippet,
          start,
          end,
          ViolationKind.DIRECT_METHOD_INVOCATION,
          snippet));
    }
    return Optional.empty();
  }

  private static TinyExpressionP4AST directCaseValueNode(Object caseValueNode) {
    return switch (caseValueNode) {
      case NumberCaseValueExpr numberValue -> directValueNode(numberValue.value());
      case StringCaseValueExpr stringValue -> directValueNode(stringValue.value());
      case BooleanCaseValueExpr booleanValue -> directValueNode(booleanValue.value());
      default -> null;
    };
  }

  private static TinyExpressionP4AST directValueNode(Object value) {
    return switch (value) {
      case VariableRefExpr variable -> variable;
      case MethodInvocationExpr invocation -> invocation;
      case BinaryExpr binary when binary.left() != null
          && binary.op().isEmpty() && binary.right().isEmpty() -> directValueNode(binary.left());
      case StringConcatExpr string when string.left() != null
          && string.op().isEmpty() && string.right().isEmpty() -> directValueNode(string.left());
      case BooleanOrExpr expression when expression.op().isEmpty()
          && expression.right().isEmpty() -> directValueNode(expression.left());
      case BooleanAndExpr expression when expression.op().isEmpty()
          && expression.right().isEmpty() -> directValueNode(expression.left());
      case BooleanXorExpr expression when expression.op().isEmpty()
          && expression.right().isEmpty() -> directValueNode(expression.left());
      case BooleanFactorExpr factor -> directValueNode(factor.value());
      default -> null;
    };
  }

  private enum ExpectedType {
    NUMBER("number") {
      @Override
      boolean accepts(String inlineHint) {
        return "number".equalsIgnoreCase(inlineHint) || "float".equalsIgnoreCase(inlineHint);
      }
    },
    STRING("string") {
      @Override
      boolean accepts(String inlineHint) {
        return "string".equalsIgnoreCase(inlineHint);
      }
    },
    BOOLEAN("boolean") {
      @Override
      boolean accepts(String inlineHint) {
        return "boolean".equalsIgnoreCase(inlineHint);
      }
    };

    final String label;

    ExpectedType(String label) {
      this.label = label;
    }

    abstract boolean accepts(String inlineHint);
  }

  public record Violation(
      String message,
      int startOffset,
      int endOffset,
      ViolationKind kind,
      String snippet) {
    public int length() {
      return Math.max(1, endOffset - startOffset);
    }
  }

  public enum ViolationKind {
    DIRECT_VARIABLE_CASE_VALUE,
    DIRECT_METHOD_INVOCATION
  }
}
