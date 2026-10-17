package org.unlaxer.tinyexpression.p4;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.*;

/** Projects the already recognized expression into its explicitly hinted family. No parser calls. */
public final class P4ResultFamilyProjection {
  public record Result(TinyExpressionP4AST ast, P4SourceText sourceText) {}
  private final String family;
  private final P4SourceText source;
  private P4SourceText.ProjectionTextReader textReader;
  private final Map<Object, Object> origins = new IdentityHashMap<>();

  private P4ResultFamilyProjection(String family, P4SourceText source) {
    this.family = family;
    this.source = source;
  }

  public static Result project(TinyExpressionP4AST expression, String family, P4SourceText source,
      String parserSource) {
    if (!List.of("NUMBER", "STRING", "BOOLEAN", "OBJECT").contains(family)) {
      throw new IllegalArgumentException("Unknown explicit result family: " + family);
    }
    var projection = new P4ResultFamilyProjection(family, source);
    TinyExpressionP4AST result = projection.expression(expression);
    P4SourceText snapshot = P4SourceText.fromSnapshot(parserSource,
        value -> source.spanOf(projection.origins.getOrDefault(value, value)));
    return new Result(result, snapshot);
  }

  private <T extends TinyExpressionP4AST> T from(T value, Object original) {
    origins.put(value, origins.getOrDefault(original, original));
    return value;
  }

  private TinyExpressionP4AST expression(TinyExpressionP4AST value) {
    if (value instanceof FormulaExpr formula) return expression(formula.expression());
    if (value instanceof ExpressionExpr expression
        && expression.value() instanceof TinyExpressionP4AST ast) return expression(ast);
    if (family.equals("OBJECT")) return value instanceof ObjectExpr ? value
        : from(new ObjectExpr(value), value);
    if (value instanceof StringMatchExpr) {
      if (!family.equals("STRING")) throw invalid();
      return value;
    }
    if (value instanceof BooleanMatchExpr) {
      if (!family.equals("BOOLEAN")) throw invalid();
      return value;
    }
    if (family.equals("NUMBER")) return value;
    if (value instanceof NumberMatchExpr match) return family.equals("STRING")
        ? stringMatch(match) : booleanMatch(match);
    if (family.equals("STRING")) return stringExpression(value);
    return booleanExpression(value);
  }

  private StringConcatExpr stringExpression(TinyExpressionP4AST value) {
    if (value instanceof StringConcatExpr string) return string;
    if (value instanceof BinaryExpr binary) {
      if (binary.op().stream().anyMatch(op -> !op.equals("+"))) throw invalid();
      Object left = stringTerm(binary.left());
      var right = new ArrayList<Object>();
      for (Object child : binary.right()) right.add(stringTerm(child));
      return from(new StringConcatExpr(left, binary.op(), List.copyOf(right)), value);
    }
    return from(new StringConcatExpr(stringAtom(value), List.of(), List.of()), value);
  }

  private Object stringTerm(Object value) {
    if (value instanceof BinaryExpr term) {
      if (!term.op().isEmpty()) throw invalid();
      if (term.left() instanceof BinaryExpr nested) return stringExpression(nested);
      return stringAtom(term.left());
    }
    return stringAtom(value);
  }

  private Object stringAtom(Object value) {
    if (value instanceof VariableRefExpr variable) {
      if (textReader == null) textReader = source.projectionTextReader();
      String text = textReader.text(variable).strip();
      // '$' and IDENTIFIER are separate grammar tokens; layout may occur between them.
      String body = text.substring(1).stripLeading();
      String tail = body.substring(variable.name().length()).strip();
      if (tail.startsWith("as") && variable.type().map(v -> v.equalsIgnoreCase("string")).orElse(false)) {
        return from(new StringTypedVariableRefExpr(variable.name()), variable);
      }
      return variable;
    }
    if (value instanceof StringTypedVariableRefExpr || value instanceof StringCastVariableRefExpr
        || value instanceof MethodInvocationExpr || value instanceof IfExpr) return value;
    if (value instanceof NumberMatchExpr match) return stringMatch(match);
    throw invalid();
  }

  private BooleanOrExpr booleanExpression(TinyExpressionP4AST value) {
    if (value instanceof BooleanOrExpr bool) return bool;
    Object atom = value;
    Object factorOrigin = value;
    if (value instanceof BinaryExpr expression) {
      if (!expression.op().isEmpty()) throw invalid();
      atom = expression.left();
      if (atom instanceof BinaryExpr term) {
        if (!term.op().isEmpty()) throw invalid();
        factorOrigin = term;
        atom = term.left();
        if (atom instanceof BinaryExpr nested) atom = booleanExpression(nested);
      }
    }
    if (atom instanceof NumberMatchExpr match) atom = booleanMatch(match);
    if (!(atom instanceof VariableRefExpr || atom instanceof MethodInvocationExpr
        || atom instanceof IfExpr || atom instanceof BooleanMatchExpr || atom instanceof BooleanOrExpr)) throw invalid();
    var factor = from(new BooleanFactorExpr(atom), factorOrigin);
    var exclusive = from(new BooleanXorExpr(factor, List.of(), List.of()), value);
    var conjunction = from(new BooleanAndExpr(exclusive, List.of(), List.of()), value);
    return from(new BooleanOrExpr(conjunction, List.of(), List.of()), value);
  }

  private StringCaseValueExpr stringValue(NumberCaseValueExpr value) {
    return from(new StringCaseValueExpr(stringExpression(value.value())), value);
  }

  private StringCaseExpr stringCase(NumberCaseExpr value) {
    return from(new StringCaseExpr(value.condition(), stringValue(value.value())), value);
  }

  private StringMatchExpr stringMatch(NumberMatchExpr value) {
    return from(new StringMatchExpr(stringCase(value.firstCase()),
        value.moreCases().stream().map(this::stringCase).toList(),
        from(new StringDefaultCaseExpr(stringValue(value.defaultCase().value())), value.defaultCase())), value);
  }

  private BooleanCaseValueExpr booleanValue(NumberCaseValueExpr value) {
    return from(new BooleanCaseValueExpr(booleanExpression(value.value())), value);
  }

  private BooleanCaseExpr booleanCase(NumberCaseExpr value) {
    return from(new BooleanCaseExpr(value.condition(), booleanValue(value.value())), value);
  }

  private BooleanMatchExpr booleanMatch(NumberMatchExpr value) {
    return from(new BooleanMatchExpr(booleanCase(value.firstCase()),
        value.moreCases().stream().map(this::booleanCase).toList(),
        from(new BooleanDefaultCaseExpr(booleanValue(value.defaultCase().value())), value.defaultCase())), value);
  }

  private IllegalArgumentException invalid() {
    return new IllegalArgumentException("Expression cannot use explicit result family " + family);
  }
}
