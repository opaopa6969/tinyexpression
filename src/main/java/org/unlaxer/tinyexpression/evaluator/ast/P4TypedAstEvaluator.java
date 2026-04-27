package org.unlaxer.tinyexpression.evaluator.ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.DayOfWeek;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.evaluator.p4.P4StrictMatchTypingValidator;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.function.EmbeddedFunction;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.*;
import org.unlaxer.tinyexpression.p4.P4IfSourceSupport;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Evaluator;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper;
import org.unlaxer.tinyexpression.p4.P4PreferredAstMapper;
import org.unlaxer.tinyexpression.p4.P4TernarySourceSupport;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

/**
 * GGP concrete implementation: AST traversal evaluator.
 * <p>
 * Extends the generated {@link TinyExpressionP4Evaluator}{@code <Object>} base class
 * and implements each {@code evalXxx()} method to evaluate the sealed P4 AST nodes
 * directly — no reflection needed.
 */
public class P4TypedAstEvaluator extends TinyExpressionP4Evaluator<Object> {

  private final ExpressionType resultType;
  private final ExpressionType numberType;
  private final CalculationContext context;
  private final SpecifiedExpressionTypes specifiedExpressionTypes;
  private final String sourceFormula;
  private final String lookupFormulaSource;
  private final ClassLoader classLoader;

  public P4TypedAstEvaluator(SpecifiedExpressionTypes types, CalculationContext context) {
    this(types, context, null, null);
  }

  public P4TypedAstEvaluator(SpecifiedExpressionTypes types, CalculationContext context,
      String sourceFormula, ClassLoader classLoader) {
    this(types, context, sourceFormula, sourceFormula, classLoader);
  }

  public P4TypedAstEvaluator(SpecifiedExpressionTypes types, CalculationContext context,
      String sourceFormula, String lookupFormulaSource, ClassLoader classLoader) {
    this.resultType = types.resultType() != null ? types.resultType() : ExpressionTypes.object;
    this.numberType = resolveNumberType(types);
    this.context = context;
    this.specifiedExpressionTypes = types;
    this.sourceFormula = sourceFormula;
    this.lookupFormulaSource = lookupFormulaSource;
    this.classLoader = classLoader;
  }

  private static ExpressionType resolveNumberType(SpecifiedExpressionTypes types) {
    if (types.numberType() != null) {
      return types.numberType();
    }
    if (types.resultType() != null && types.resultType().isNumber()) {
      return types.resultType();
    }
    return ExpressionTypes._float;
  }

  // =========================================================================
  // BinaryExpr — numeric arithmetic
  // =========================================================================

  @Override
  protected Object evalBinaryExpr(BinaryExpr node) {
    Object variableLike = tryResolveVariableLikeValue(node);
    if (variableLike != null) {
      return variableLike;
    }
    return evalBinaryAsNumber(node);
  }

  private Number evalBinaryAsNumber(BinaryExpr node) {
    Number sourceAware = tryEvaluateStructuredBinaryNode(node);
    if (sourceAware != null) {
      return sourceAware;
    }
    BinaryExpr left = node.left();
    List<String> op = node.op();
    List<BinaryExpr> right = node.right();

    // Leaf: left==null, op=[literal], right=[]
    if (left == null && right.isEmpty() && op.size() == 1) {
      return resolveLeafLiteral(op.get(0));
    }
    // Wrap: left!=null, op=[], right=[] — unwrap
    if (left != null && op.isEmpty() && right.isEmpty()) {
      return evalBinaryAsNumber(left);
    }
    if (left == null) {
      if (op.size() == 1) {
        return resolveLeafLiteral(op.get(0));
      }
      throw new IllegalArgumentException("left is null for non-leaf BinaryExpr");
    }

    Number current = evalBinaryAsNumber(left);
    int count = Math.min(op.size(), right.size());
    for (int i = 0; i < count; i++) {
      Number r = evalBinaryAsNumber(right.get(i));
      current = applyBinary(op.get(i), current, r);
    }
    return current;
  }

  private Number resolveLeafLiteral(String rawLiteral) {
    String literal = rawLiteral == null ? "" : rawLiteral.strip();
    if (isExactVariableReference(literal)) {
      String varName = extractVariableName(literal);
      if (varName != null) {
        Optional<? extends Number> number = context.getNumber(varName);
        if (number.isPresent()) {
          return number.get();
        }
      }
    }
    Number structured = tryEvaluateStructuredNumberLeaf(literal);
    if (structured != null) {
      return structured;
    }
    // P4 mapper collapses term-level ops (e.g., "3*4") into a single leaf.
    // Evaluate simple term expressions manually.
    if (literal.contains("*") || literal.contains("/")) {
      return evaluateCollapsedTerm(literal);
    }
    return numberType.parseNumber(literal);
  }

  private Number tryEvaluateStructuredBinaryNode(BinaryExpr node) {
    if (node == null || sourceFormula == null || sourceFormula.isBlank()) {
      return null;
    }
    Optional<String> snippet = sourceSnippetOfNode(node);
    if (snippet.isEmpty()) {
      return null;
    }
    return tryEvaluateStructuredBinarySourceSnippet(snippet.get());
  }

  private Number tryEvaluateStructuredBinarySourceSnippet(String sourceSnippet) {
    if (sourceSnippet == null) {
      return null;
    }
    String normalized = sourceSnippet.strip();
    if (normalized.isEmpty()) {
      return null;
    }
    if (isExactVariableReference(normalized) || isPlainNumericLiteral(normalized)) {
      return null;
    }
    String unwrapped = unwrapWholeParentheses(normalized);
    if (!unwrapped.equals(normalized)) {
      Number inner = tryEvaluateStructuredBinarySourceSnippet(unwrapped);
      if (inner != null) {
        return inner;
      }
    }
    if (!hasStructuredNumericAlternative(normalized)) {
      return null;
    }
    Optional<Object> direct = AstEmbeddedExpressionRuntime.tryEvaluateFormulaDirect(
        normalized,
        numberType,
        new SpecifiedExpressionTypes(numberType, numberType),
        context,
        classLoader);
    if (direct.isPresent() && direct.get() instanceof Number directNumber) {
      return directNumber;
    }
    try {
      String parseSource = P4PreferredAstMapper.normalizeExpressionSnippetForParsing(normalized);
      TinyExpressionP4AST ast = P4PreferredAstMapper.parseDetailed(parseSource, numberType).ast();
      if (ast instanceof BinaryExpr) {
        return null;
      }
      Object value = new P4TypedAstEvaluator(
          new SpecifiedExpressionTypes(numberType, numberType),
          context,
          parseSource,
          classLoader).eval(ast);
      return value instanceof Number number ? number : null;
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private boolean hasStructuredNumericAlternative(String text) {
    return !P4PreferredAstMapper.astEvaluatorCandidateAstSimpleNames(text, numberType).isEmpty();
  }

  private Number tryEvaluateStructuredNumberLeaf(String text) {
    if (!looksLikeStructuredNumberLeaf(text)) {
      return null;
    }
    try {
      String normalized = text.strip();
      String parseSource = P4PreferredAstMapper.normalizeExpressionSnippetForParsing(normalized);
      TinyExpressionP4AST ast = P4PreferredAstMapper.parseDetailed(parseSource, numberType).ast();
      Object value = new P4TypedAstEvaluator(
          new SpecifiedExpressionTypes(numberType, numberType),
          context,
          parseSource,
          classLoader).eval(ast);
      return value instanceof Number number ? number : null;
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private boolean looksLikeStructuredNumberLeaf(String text) {
    if (text == null || text.isEmpty()) {
      return false;
    }
    String normalized = text.strip();
    if (normalized.isEmpty() || isExactVariableReference(normalized) || isPlainNumericLiteral(normalized)) {
      return false;
    }
    String unwrapped = unwrapWholeParentheses(normalized);
    if (!unwrapped.equals(normalized)) {
      return looksLikeStructuredNumberLeaf(unwrapped);
    }
    return normalized.startsWith("call ")
        || normalized.startsWith("internal ")
        || normalized.startsWith("external ")
        || normalized.indexOf('(') >= 0
        || normalized.indexOf('[') >= 0
        || normalized.indexOf('*') >= 0
        || normalized.indexOf('/') >= 0
        || normalized.indexOf(',') >= 0
        || normalized.indexOf('?') >= 0;
  }

  private boolean isPlainNumericLiteral(String text) {
    try {
      numberType.parseNumber(text);
      return true;
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  private Number evaluateCollapsedTerm(String term) {
    // Split by * and / while preserving operator order
    List<String> tokens = new java.util.ArrayList<>();
    List<Character> ops = new java.util.ArrayList<>();
    int start = 0;
    for (int i = 0; i < term.length(); i++) {
      char c = term.charAt(i);
      if ((c == '*' || c == '/') && i > start) {
        tokens.add(term.substring(start, i).strip());
        ops.add(c);
        start = i + 1;
      }
    }
    tokens.add(term.substring(start).strip());
    Number result = numberType.parseNumber(tokens.get(0));
    for (int i = 0; i < ops.size(); i++) {
      Number right = numberType.parseNumber(tokens.get(i + 1));
      result = applyBinary(String.valueOf(ops.get(i)), result, right);
    }
    return result;
  }

  private Number applyBinary(String operator, Number left, Number right) {
    if (numberType.isBigInteger()) {
      BigInteger l = (left instanceof BigInteger bi) ? bi : BigInteger.valueOf(left.longValue());
      BigInteger r = (right instanceof BigInteger bi) ? bi : BigInteger.valueOf(right.longValue());
      return switch (operator) {
        case "+" -> l.add(r);
        case "-" -> l.subtract(r);
        case "*" -> l.multiply(r);
        case "/" -> l.divide(r);
        default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
      };
    }
    if (numberType.isBigDecimal()) {
      BigDecimal l = toBigDecimal(left);
      BigDecimal r = toBigDecimal(right);
      return switch (operator) {
        case "+" -> l.add(r);
        case "-" -> l.subtract(r);
        case "*" -> l.multiply(r);
        case "/" -> l.divide(r, context.scale(), context.roundingMode());
        default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
      };
    }
    if (numberType.isInt()) {
      return switch (operator) {
        case "+" -> left.intValue() + right.intValue();
        case "-" -> left.intValue() - right.intValue();
        case "*" -> left.intValue() * right.intValue();
        case "/" -> left.intValue() / right.intValue();
        default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
      };
    }
    if (numberType.isLong()) {
      return switch (operator) {
        case "+" -> left.longValue() + right.longValue();
        case "-" -> left.longValue() - right.longValue();
        case "*" -> left.longValue() * right.longValue();
        case "/" -> left.longValue() / right.longValue();
        default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
      };
    }
    if (numberType.isDouble()) {
      return switch (operator) {
        case "+" -> left.doubleValue() + right.doubleValue();
        case "-" -> left.doubleValue() - right.doubleValue();
        case "*" -> left.doubleValue() * right.doubleValue();
        case "/" -> left.doubleValue() / right.doubleValue();
        default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
      };
    }
    if (numberType.isShort()) {
      int l = left.shortValue(), r = right.shortValue();
      return switch (operator) {
        case "+" -> (short) (l + r);
        case "-" -> (short) (l - r);
        case "*" -> (short) (l * r);
        case "/" -> (short) (l / r);
        default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
      };
    }
    if (numberType.isByte()) {
      int l = left.byteValue(), r = right.byteValue();
      return switch (operator) {
        case "+" -> (byte) (l + r);
        case "-" -> (byte) (l - r);
        case "*" -> (byte) (l * r);
        case "/" -> (byte) (l / r);
        default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
      };
    }
    // default: float
    return switch (operator) {
      case "+" -> left.floatValue() + right.floatValue();
      case "-" -> left.floatValue() - right.floatValue();
      case "*" -> left.floatValue() * right.floatValue();
      case "/" -> left.floatValue() / right.floatValue();
      default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
    };
  }

  // =========================================================================
  // VariableRefExpr
  // =========================================================================

  @Override
  protected Object evalVariableRefExpr(VariableRefExpr node) {
    String varName = resolveVariableRefName(node);
    if (varName == null) {
      return null;
    }
    if (resultType.isNumber()) {
      Object result = context.getNumber(varName).orElse(null);
      if (result != null) return result;
    }
    if (resultType.isBoolean()) {
      Object result = context.getBoolean(varName).orElse(null);
      if (result != null) return result;
    }
    if (resultType.isString()) {
      Object result = context.getString(varName).orElse(null);
      if (result != null) return result;
    }
    // Fallback: try all types (handles cross-type contexts like boolean vars in number expressions)
    return resolveVariableAny(varName);
  }

  private String resolveVariableRefName(VariableRefExpr node) {
    if (node == null) {
      return null;
    }
    String rawName = node.name();
    String varName = extractVariableName(rawName);
    // Generated mapper may emit an empty VariableRefExpr name.
    if ((varName == null || varName.isEmpty()) && rawName != null && !rawName.isBlank()) {
      String stripped = rawName.strip();
      varName = stripped.startsWith("$") ? extractVariableName(stripped) : stripped;
    }
    if ((varName == null || varName.isEmpty())
        && sourceFormula != null
        && !sourceFormula.isBlank()) {
      Optional<String> snippet = sourceSnippetOfNode(node);
      if (snippet.isPresent()) {
        String stripped = snippet.get().strip();
        String snippetVarName = extractVariableName(stripped);
        if (snippetVarName != null && !snippetVarName.isEmpty()) {
          return snippetVarName;
        }
        if (!stripped.isEmpty()) {
          return stripped;
        }
      }
    }
    return (varName == null || varName.isEmpty()) ? null : varName;
  }

  private Object resolveVariableAny(String varName) {
    Optional<? extends Number> number = context.getNumber(varName);
    if (number.isPresent()) return number.get();
    Optional<String> string = context.getString(varName);
    if (string.isPresent()) return string.get();
    Optional<Boolean> bool = context.getBoolean(varName);
    if (bool.isPresent()) return bool.get();
    return context.getObject(varName, Object.class).orElse(null);
  }

  // =========================================================================
  // StringConcatExpr — string concatenation with '+'
  // =========================================================================

  @Override
  protected Object evalStringConcatExpr(StringConcatExpr node) {
    String leftStr = resolveStringLeaf(node.left());
    List<String> ops = node.op();
    List<String> rights = node.right();
    if (ops == null || ops.isEmpty()) {
      return leftStr;
    }
    StringBuilder sb = new StringBuilder(leftStr);
    int count = Math.min(ops.size(), rights.size());
    for (int i = 0; i < count; i++) {
      sb.append(resolveStringLeaf(rights.get(i)));
    }
    return sb.toString();
  }

  /**
   * Resolve a string leaf value from StringConcatExpr (could be a variable ref, string literal, or AST node).
   */
  private String resolveStringLeaf(Object value) {
    Object variableLike = tryResolveVariableLikeValue(value);
    if (variableLike != null) {
      return String.valueOf(variableLike);
    }
    if (value instanceof TinyExpressionP4AST ast) {
      Object result = eval(ast);
      return result == null ? "" : String.valueOf(result);
    }
    if (value instanceof String text) {
      String stripped = text.strip();
      if (isExactVariableReference(stripped)) {
        Object resolved = resolveVariableAny(extractVariableName(stripped));
        return resolved == null ? "" : String.valueOf(resolved);
      }
      String unquoted = unquoteStringLiteral(stripped);
      if (unquoted != null) {
        return unquoted;
      }
      Object structured = tryEvaluateStructuredStringLeaf(stripped);
      if (structured != null) {
        return String.valueOf(structured);
      }
      return text;
    }
    return value == null ? "" : String.valueOf(value);
  }

  private Object tryEvaluateStructuredStringLeaf(String text) {
    if (!looksLikeStructuredStringLeaf(text)) {
      return null;
    }
    try {
      String normalized = text.strip();
      String unwrapped = unwrapWholeParentheses(normalized);
      if (!unwrapped.equals(normalized)) {
        Object inner = tryEvaluateStructuredStringLeaf(unwrapped);
        if (inner != null) {
          return inner;
        }
      }
      ClassLoader effectiveClassLoader =
          classLoader == null ? Thread.currentThread().getContextClassLoader() : classLoader;
      SpecifiedExpressionTypes leafTypes = new SpecifiedExpressionTypes(ExpressionTypes.string, numberType);
      String parseSource = P4PreferredAstMapper.normalizeExpressionSnippetForParsing(normalized);
      TinyExpressionP4AST ast = P4PreferredAstMapper.parseDetailed(parseSource, ExpressionTypes.string).ast();
      return new P4TypedAstEvaluator(
          leafTypes, context, parseSource, effectiveClassLoader).eval(ast);
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  // =========================================================================
  // BooleanOrExpr / BooleanAndExpr / BooleanXorExpr  (3-level hierarchy)
  // =========================================================================

  @Override
  protected Object evalBooleanOrExpr(BooleanOrExpr node) {
    if (node.op() == null || node.op().isEmpty()) {
      return eval(node.left());
    }
    boolean current = toBoolean(eval(node.left()));
    List<BooleanAndExpr> rights = node.right();
    int count = Math.min(node.op().size(), rights.size());
    for (int i = 0; i < count; i++) {
      boolean r = toBoolean(eval(rights.get(i)));
      current = current | r;
    }
    return current;
  }

  @Override
  protected Object evalBooleanAndExpr(BooleanAndExpr node) {
    if (node.op() == null || node.op().isEmpty()) {
      return eval(node.left());
    }
    boolean current = toBoolean(eval(node.left()));
    List<BooleanXorExpr> rights = node.right();
    int count = Math.min(node.op().size(), rights.size());
    for (int i = 0; i < count; i++) {
      boolean r = toBoolean(eval(rights.get(i)));
      current = current & r;
    }
    return current;
  }

  @Override
  protected Object evalBooleanXorExpr(BooleanXorExpr node) {
    if (node.op() == null || node.op().isEmpty()) {
      return eval(node.left());
    }
    boolean current = toBoolean(eval(node.left()));
    List<BooleanFactorExpr> rights = node.right();
    int count = Math.min(node.op().size(), rights.size());
    for (int i = 0; i < count; i++) {
      boolean r = toBoolean(eval(rights.get(i)));
      current = current ^ r;
    }
    return current;
  }

  @Override
  protected Object evalBooleanFactorExpr(BooleanFactorExpr node) {
    Object value = node.value();
    if (value instanceof ComparisonExpr comp) {
      return eval(comp);
    }
    if (value instanceof StringComparisonExpr scomp) {
      return eval(scomp);
    }
    if (value instanceof VariableRefExpr varRef) {
      return eval(varRef);
    }
    if (value instanceof TinyExpressionP4AST ast) {
      return eval(ast);
    }
    if (value instanceof String text) {
      String stripped = text.strip();
      if (isExactVariableReference(stripped)) {
        String varName = extractVariableName(stripped);
        if (varName == null || varName.isEmpty()) {
          throw new UnsupportedOperationException(
              "Cannot resolve boolean variable from incomplete reference: " + stripped);
        }
        Object resolved = resolveVariableAny(varName);
        return toBoolean(resolved);
      }
      return toBoolean(text);
    }
    if (value instanceof Boolean bool) {
      return bool;
    }
    return toBoolean(value);
  }

  // =========================================================================
  // ComparisonExpr / StringComparisonExpr
  // =========================================================================

  @Override
  protected Object evalComparisonExpr(ComparisonExpr node) {
    Number left = evalBinaryAsNumber(node.left());
    Number right = evalBinaryAsNumber(node.right());
    String op = node.op() == null ? "" : node.op().strip();
    int compare = toBigDecimal(left).compareTo(toBigDecimal(right));
    return switch (op) {
      case "==" -> compare == 0;
      case "!=" -> compare != 0;
      case "<"  -> compare < 0;
      case "<=" -> compare <= 0;
      case ">"  -> compare > 0;
      case ">=" -> compare >= 0;
      default -> false;
    };
  }

  @Override
  protected Object evalStringComparisonExpr(StringComparisonExpr node) {
    String op = node.op() == null ? "" : node.op().strip();
    Optional<StringComparisonSource> sourceComparison = tryExtractStringComparisonFromSourceFormula(op);
    String left = sourceComparison
        .map(StringComparisonSource::left)
        .map(this::tryEvaluateStringSourceSnippet)
        .orElseGet(() -> resolveStringComparisonSide(node.left()));
    String right = sourceComparison
        .map(StringComparisonSource::right)
        .map(this::tryEvaluateStringSourceSnippet)
        .orElseGet(() -> resolveStringComparisonSide(node.right()));
    return switch (op) {
      case "==" -> left.equals(right);
      case "!=" -> !left.equals(right);
      default -> false;
    };
  }

  private String resolveStringComparisonSide(StringConcatExpr node) {
    Optional<String> snippet = GeneratedP4ValueAstEvaluator.sourceSnippetOfNode(node, sourceFormula);
    if (snippet.isPresent()) {
      String exact = tryEvaluateStringSourceSnippet(snippet.get());
      if (exact != null) {
        return exact;
      }
    }
    return String.valueOf(evalStringConcatExpr(node));
  }

  private String tryEvaluateStringSourceSnippet(String source) {
    if (source == null) {
      return null;
    }
    String normalized = source.strip();
    if (normalized.isEmpty()) {
      return "";
    }
    String unquoted = unquoteStringLiteral(normalized);
    if (unquoted != null) {
      return unquoted;
    }
    String unwrapped = unwrapWholeParentheses(normalized);
    if (!unwrapped.equals(normalized)) {
      String inner = tryEvaluateStringSourceSnippet(unwrapped);
      if (inner != null) {
        return inner;
      }
    }
    Object structured = tryEvaluateStructuredStringLeaf(normalized);
    if (structured != null) {
      return String.valueOf(structured);
    }
    return null;
  }

  private Optional<StringComparisonSource> tryExtractStringComparisonFromSourceFormula(String expectedOp) {
    if (sourceFormula == null || sourceFormula.isBlank()) {
      return Optional.empty();
    }
    Optional<StringComparisonSource> direct = splitTopLevelStringComparison(sourceFormula.strip());
    if (direct.isPresent() && matchesComparisonOperator(direct.get(), expectedOp)) {
      return direct;
    }
    String normalized = sourceFormula.strip();
    if (!normalized.startsWith("if")) {
      return Optional.empty();
    }
    int openParen = normalized.indexOf('(');
    if (openParen < 0) {
      return Optional.empty();
    }
    int closeParen = GeneratedP4ValueAstEvaluator.findMatching(normalized, openParen, '(', ')');
    if (closeParen <= openParen) {
      return Optional.empty();
    }
    return splitTopLevelStringComparison(normalized.substring(openParen + 1, closeParen).strip())
        .filter(candidate -> matchesComparisonOperator(candidate, expectedOp));
  }

  private static boolean matchesComparisonOperator(StringComparisonSource candidate, String expectedOp) {
    return expectedOp == null || expectedOp.isBlank() || expectedOp.equals(candidate.op());
  }

  private static Optional<StringComparisonSource> splitTopLevelStringComparison(String text) {
    if (text == null || text.isBlank()) {
      return Optional.empty();
    }
    int parenDepth = 0;
    int braceDepth = 0;
    int bracketDepth = 0;
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    for (int i = 0; i < text.length() - 1; i++) {
      char c = text.charAt(i);
      char next = text.charAt(i + 1);
      char prev = i > 0 ? text.charAt(i - 1) : '\0';
      if (c == '\'' && !inDoubleQuote && prev != '\\') {
        inSingleQuote = !inSingleQuote;
        continue;
      }
      if (c == '"' && !inSingleQuote && prev != '\\') {
        inDoubleQuote = !inDoubleQuote;
        continue;
      }
      if (inSingleQuote || inDoubleQuote) {
        continue;
      }
      switch (c) {
        case '(' -> {
          parenDepth++;
          continue;
        }
        case ')' -> {
          parenDepth = Math.max(0, parenDepth - 1);
          continue;
        }
        case '{' -> {
          braceDepth++;
          continue;
        }
        case '}' -> {
          braceDepth = Math.max(0, braceDepth - 1);
          continue;
        }
        case '[' -> {
          bracketDepth++;
          continue;
        }
        case ']' -> {
          bracketDepth = Math.max(0, bracketDepth - 1);
          continue;
        }
        default -> {
        }
      }
      if (parenDepth != 0 || braceDepth != 0 || bracketDepth != 0) {
        continue;
      }
      if ((c == '=' && next == '=') || (c == '!' && next == '=')) {
        String left = text.substring(0, i).strip();
        String right = text.substring(i + 2).strip();
        if (!left.isEmpty() && !right.isEmpty()) {
          return Optional.of(new StringComparisonSource(left, text.substring(i, i + 2), right));
        }
      }
    }
    return Optional.empty();
  }

  // =========================================================================
  // IfExpr
  // =========================================================================

  @Override
  protected Object evalIfExpr(IfExpr node) {
    Object sourceAware = tryEvaluateIfFromSource(node);
    if (sourceAware != null) {
      return sourceAware;
    }
    Object conditionValue = eval(node.condition());
    boolean cond = Boolean.TRUE.equals(toBoolean(conditionValue));
    ExpressionExpr branch = cond ? node.thenExpr() : node.elseExpr();
    return eval(branch);
  }

  private Object tryEvaluateIfFromSource(IfExpr node) {
    if (node == null || sourceFormula == null || sourceFormula.isBlank()) {
      return null;
    }
    Optional<P4IfSourceSupport.IfParts> parts = P4IfSourceSupport.ifPartsOfNode(node, sourceFormula);
    if (parts.isEmpty()) {
      return null;
    }
    Optional<Object> conditionValue = tryEvaluateIfSourceSnippet(parts.get().conditionSource(), ExpressionTypes._boolean);
    if (conditionValue.isEmpty()) {
      return null;
    }
    String selectedBranch =
        Boolean.TRUE.equals(toBoolean(conditionValue.get())) ? parts.get().thenSource() : parts.get().elseSource();
    return tryEvaluateIfSourceSnippet(selectedBranch, resultType).orElse(null);
  }

  private Optional<Object> tryEvaluateIfSourceSnippet(String snippetSource, ExpressionType expectedType) {
    if (snippetSource == null) {
      return Optional.empty();
    }
    String normalized = snippetSource.strip();
    if (normalized.isEmpty()) {
      return Optional.empty();
    }
    if (isExactVariableReference(normalized)) {
      Object resolved = resolveVariableAny(extractVariableName(normalized));
      if (resolved != null) {
        return Optional.of(resolved);
      }
    }
    if (expectedType != null && expectedType.isNumber()) {
      try {
        return Optional.of(resolveNumberType(
            new SpecifiedExpressionTypes(expectedType, numberType)).parseNumber(normalized));
      } catch (RuntimeException ignored) {
      }
    }
    if (expectedType != null && expectedType.isBoolean()) {
      if ("true".equalsIgnoreCase(normalized) || "false".equalsIgnoreCase(normalized)) {
        return Optional.of(Boolean.parseBoolean(normalized));
      }
    }
    if (expectedType != null && expectedType.isString()) {
      String unquoted = unquoteStringLiteral(normalized);
      if (unquoted != null) {
        return Optional.of(unquoted);
      }
    }
    ExpressionType targetType = expectedType == null ? resultType : expectedType;
    SpecifiedExpressionTypes targetTypes = new SpecifiedExpressionTypes(targetType, numberType);
    Optional<Object> embedded = tryEvaluateWrappedIfSnippet(normalized, targetType, targetTypes);
    if (embedded.isPresent()) {
      return embedded;
    }
    embedded = AstEmbeddedExpressionRuntime.tryEvaluateFormulaDirect(
        normalized, targetType, targetTypes, context, classLoader);
    if (embedded.isPresent()) {
      return embedded;
    }
    embedded = AstEmbeddedExpressionRuntime.tryEvaluate(
        normalized, targetType, targetTypes, context, classLoader, sourceFormula);
    if (embedded.isPresent()) {
      return embedded;
    }
    try {
      String parseSource = P4PreferredAstMapper.normalizeExpressionSnippetForParsing(normalized);
      TinyExpressionP4AST ast = P4PreferredAstMapper.parseDetailed(parseSource, targetType).ast();
      return Optional.ofNullable(new P4TypedAstEvaluator(
          targetTypes, context, parseSource, classLoader).eval(ast));
    } catch (RuntimeException ignored) {
      return Optional.empty();
    }
  }

  private Optional<Object> tryEvaluateWrappedIfSnippet(String normalized, ExpressionType targetType,
      SpecifiedExpressionTypes targetTypes) {
    if (targetType == null || normalized == null || normalized.isEmpty()) {
      return Optional.empty();
    }
    String wrappedFormula;
    if (targetType.isBoolean()) {
      wrappedFormula = "if(" + normalized + "){true}else{false}";
    } else {
      wrappedFormula = "if(true){" + normalized + "}else{" + defaultIfElseLiteral(targetType) + "}";
    }
    return AstEmbeddedExpressionRuntime.tryEvaluateFormulaDirect(
        wrappedFormula, targetType, targetTypes, context, classLoader);
  }

  private String defaultIfElseLiteral(ExpressionType targetType) {
    if (targetType.isNumber()) {
      return "0";
    }
    if (targetType.isBoolean()) {
      return "false";
    }
    return "''";
  }

  // =========================================================================
  // ArgumentExpressionExpr — unwrap (bare ternary or expression inside function args)
  // =========================================================================

  @Override
  protected Object evalArgumentExpressionExpr(ArgumentExpressionExpr node) {
    Object value = node.value();
    Object variableLike = tryResolveVariableLikeValue(value);
    if (variableLike != null) {
      return variableLike;
    }
    if (value instanceof TinyExpressionP4AST ast) {
      return eval(ast);
    }
    if (value instanceof String text) {
      String stripped = text.strip();
      if (isExactVariableReference(stripped)) {
        return resolveVariableAny(extractVariableName(stripped));
      }
    }
    return value;
  }

  // =========================================================================
  // ExpressionExpr — unwrap
  // =========================================================================

  @Override
  protected Object evalExpressionExpr(ExpressionExpr node) {
    Object value = node.value();
    Object variableLike = tryResolveVariableLikeValue(value);
    if (variableLike != null) {
      return variableLike;
    }
    if (value instanceof TinyExpressionP4AST ast) {
      return eval(ast);
    }
    if (value instanceof String text) {
      String stripped = text.strip();
      if (isExactVariableReference(stripped)) {
        return resolveVariableAny(extractVariableName(stripped));
      }
    }
    return value;
  }

  // =========================================================================
  // ObjectExpr
  // =========================================================================

  @Override
  protected Object evalObjectExpr(ObjectExpr node) {
    Object value = node.value();
    Object variableLike = tryResolveVariableLikeValue(value);
    if (variableLike != null) {
      return variableLike;
    }
    if (value instanceof TinyExpressionP4AST ast) {
      return eval(ast);
    }
    if (value instanceof String text) {
      String stripped = text.strip();
      if (isExactVariableReference(stripped)) {
        return resolveVariableAny(extractVariableName(stripped));
      }
    }
    return value;
  }

  // =========================================================================
  // Match expressions (Number/String/Boolean)
  // =========================================================================

  @Override
  protected Object evalNumberMatchExpr(NumberMatchExpr node) {
    return evaluateMatch(node.firstCase(), node.moreCases(), node.defaultCase());
  }

  @Override
  protected Object evalStringMatchExpr(StringMatchExpr node) {
    return evaluateMatch(node.firstCase(), node.moreCases(), node.defaultCase());
  }

  @Override
  protected Object evalBooleanMatchExpr(BooleanMatchExpr node) {
    return evaluateMatch(node.firstCase(), node.moreCases(), node.defaultCase());
  }

  private <C extends TinyExpressionP4AST, D extends TinyExpressionP4AST>
  Object evaluateMatch(C firstCase, List<C> moreCases, D defaultCase) {
    Object result = tryEvalCase(firstCase);
    if (result != null) return result;
    for (C moreCase : moreCases) {
      result = tryEvalCase(moreCase);
      if (result != null) return result;
    }
    return eval(defaultCase);
  }

  private Object tryEvalCase(TinyExpressionP4AST caseNode) {
    // All case nodes have condition() and value()
    if (caseNode instanceof NumberCaseExpr c) {
      if (Boolean.TRUE.equals(toBoolean(eval(c.condition())))) return eval(c.value());
    } else if (caseNode instanceof StringCaseExpr c) {
      if (Boolean.TRUE.equals(toBoolean(eval(c.condition())))) return eval(c.value());
    } else if (caseNode instanceof BooleanCaseExpr c) {
      if (Boolean.TRUE.equals(toBoolean(eval(c.condition())))) return eval(c.value());
    }
    return null;
  }

  @Override
  protected Object evalNumberCaseExpr(NumberCaseExpr node) {
    return eval(node.value());
  }

  @Override
  protected Object evalNumberDefaultCaseExpr(NumberDefaultCaseExpr node) {
    return eval(node.value());
  }

  @Override
  protected Object evalNumberCaseValueExpr(NumberCaseValueExpr node) {
    return evalBinaryAsNumber(node.value());
  }

  @Override
  protected Object evalStringCaseExpr(StringCaseExpr node) {
    return eval(node.value());
  }

  @Override
  protected Object evalStringDefaultCaseExpr(StringDefaultCaseExpr node) {
    return eval(node.value());
  }

  @Override
  protected Object evalStringCaseValueExpr(StringCaseValueExpr node) {
    return evalStringConcatExpr(node.value());
  }

  @Override
  protected Object evalBooleanCaseExpr(BooleanCaseExpr node) {
    return eval(node.value());
  }

  @Override
  protected Object evalBooleanDefaultCaseExpr(BooleanDefaultCaseExpr node) {
    return eval(node.value());
  }

  @Override
  protected Object evalBooleanCaseValueExpr(BooleanCaseValueExpr node) {
    return evalBooleanOrExpr(node.value());
  }

  // =========================================================================
  // MethodInvocationExpr / External invocations / Import / CodeBlock
  // =========================================================================

  @Override
  protected Object evalMethodInvocationExpr(MethodInvocationExpr node) {
    String methodName = node.name() == null ? "" : node.name().strip();
    String effectiveLookupSource = effectiveLookupFormulaSource();
    methodName = GeneratedP4ValueAstEvaluator.resolveInvocationMethodName(
        node, effectiveLookupSource, methodName);
    if (methodName.isEmpty() || effectiveLookupSource == null || effectiveLookupSource.isEmpty()) {
      throw new UnsupportedOperationException(
          "MethodInvocationExpr requires sourceFormula; method=" + methodName);
    }

    // 1. Find the method definition in the source formula
    GeneratedP4ValueAstEvaluator.MethodSource method =
        GeneratedP4ValueAstEvaluator.findMethodSource(effectiveLookupSource, methodName);
    if (method == null || method.expression().isBlank()) {
      throw new UnsupportedOperationException(
          "Method definition not found for: " + methodName);
    }
    if (GeneratedP4ValueAstEvaluator.isDirectSelfCall(method.expression(), method.name())) {
      throw new UnsupportedOperationException(
          "Direct self-call detected for: " + methodName);
    }

    // 2. Parse parameter specs from method definition
    List<GeneratedP4ValueAstEvaluator.MethodParameterSpec> parameterSpecs =
        GeneratedP4ValueAstEvaluator.parseMethodParameterSpecs(method.parameterSection());

    // 3. Resolve invocation arguments from source text
    List<String> argumentExpressions =
        GeneratedP4ValueAstEvaluator.resolveInvocationArgumentExpressions(
            node, effectiveLookupSource, methodName);

    if (parameterSpecs.size() != argumentExpressions.size()) {
      throw new UnsupportedOperationException(
          "Argument count mismatch for method " + methodName
              + ": expected " + parameterSpecs.size() + " but got " + argumentExpressions.size());
    }

    // 4. Evaluate arguments and bind to parameter names
    Map<String, Object> localBindings = evaluateAndBindArguments(
        parameterSpecs, argumentExpressions);

    // 5. Create scoped context with local bindings
    CalculationContext scopedContext = localBindings.isEmpty()
        ? context
        : new GeneratedP4ValueAstEvaluator.ScopedCalculationContext(context, localBindings);

    // 6. Parse method body and evaluate with P4TypedAstEvaluator
    String bodyExpression = method.expression().strip();
    try {
      TinyExpressionP4AST bodyAst = P4PreferredAstMapper.parse(bodyExpression, resultType);
      P4StrictMatchTypingValidator.validateOrThrow(bodyAst, bodyExpression);
      if (bodyAst != null) {
        String bodySourceFormula = selectSnippetSource(bodyExpression);
        P4TypedAstEvaluator bodyEvaluator = new P4TypedAstEvaluator(
            specifiedExpressionTypes, scopedContext, bodySourceFormula, effectiveLookupSource, classLoader);
        Object result = bodyEvaluator.eval(bodyAst);
        if (result != null) {
          return result;
        }
      }
    } catch (Exception ignored) {
      // P4 parse failed for method body; fall through to embedded runtime
    }

    // 7. Fallback: use embedded expression runtime for the body
    ClassLoader effectiveClassLoader = classLoader != null
        ? classLoader : Thread.currentThread().getContextClassLoader();
    ExpressionType expectedType = resultType;
    SpecifiedExpressionTypes evalTypes = new SpecifiedExpressionTypes(
        expectedType,
        GeneratedP4ValueAstEvaluator.resolveNumberTypeForEvaluation(
            expectedType, specifiedExpressionTypes.numberType()));
    Optional<Object> embedded = AstEmbeddedExpressionRuntime.tryEvaluate(
        bodyExpression, expectedType, evalTypes, scopedContext,
        effectiveClassLoader, effectiveLookupSource);
    if (embedded.isPresent()) {
      return embedded.get();
    }

    throw new UnsupportedOperationException(
        "Failed to evaluate method body for: " + methodName);
  }

  private Map<String, Object> evaluateAndBindArguments(
      List<GeneratedP4ValueAstEvaluator.MethodParameterSpec> parameterSpecs,
      List<String> argumentExpressions) {
    if (parameterSpecs.isEmpty()) {
      return Map.of();
    }
    Map<String, Object> bindings = new LinkedHashMap<>();
    for (int i = 0; i < parameterSpecs.size(); i++) {
      GeneratedP4ValueAstEvaluator.MethodParameterSpec param = parameterSpecs.get(i);
      String argExpr = argumentExpressions.get(i) == null ? "" : argumentExpressions.get(i).strip();
      Object value = evaluateArgumentExpression(argExpr, param.type());
      bindings.put(param.name(), value);
    }
    return bindings;
  }

  private Object evaluateArgumentExpression(String argExpr, ExpressionType parameterType) {
    if (argExpr.isEmpty()) {
      return null;
    }
    SpecifiedExpressionTypes argumentTypes =
        new SpecifiedExpressionTypes(parameterType, resolveNumberType(new SpecifiedExpressionTypes(parameterType, numberType)));
    // Try variable reference
    if (argExpr.startsWith("$")) {
      String varName = extractVariableName(argExpr);
      if (varName != null && ("$" + varName).equals(argExpr.strip())) {
        Object resolved = resolveVariableAny(varName);
        if (resolved != null) {
          return coerceToType(resolved, parameterType);
        }
      }
    }
    Optional<P4TernarySourceSupport.TernaryParts> ternaryParts =
        P4TernarySourceSupport.parseTopLevelTernary(argExpr);
    if (ternaryParts.isPresent()) {
      Optional<Boolean> conditionValue = evaluateArgumentCondition(ternaryParts.get().conditionSource());
      if (conditionValue.isPresent()) {
        boolean selected = Boolean.TRUE.equals(conditionValue.get());
        return evaluateArgumentExpression(
            selected ? ternaryParts.get().thenSource() : ternaryParts.get().elseSource(),
            parameterType);
      }
    }
    if (P4PreferredAstMapper.preferredAstSimpleNames(argExpr, parameterType).contains("IfExpr")) {
      Optional<Object> direct = AstEmbeddedExpressionRuntime.tryEvaluateFormulaDirect(
          argExpr, parameterType, argumentTypes, context, classLoader);
      if (direct.isPresent()) {
        return coerceToType(direct.get(), parameterType);
      }
    }
    // Try P4 parse and eval
    try {
      String parseSource = P4PreferredAstMapper.normalizeExpressionSnippetForParsing(argExpr);
      TinyExpressionP4AST argAst = P4PreferredAstMapper.parse(parseSource, parameterType);
      P4StrictMatchTypingValidator.validateOrThrow(argAst, argExpr);
      if (argAst != null) {
        Object result = new P4TypedAstEvaluator(
            argumentTypes,
            context,
            selectSnippetSource(parseSource),
            effectiveLookupFormulaSource(),
            classLoader).eval(argAst);
        if (result != null) {
          return coerceToType(result, parameterType);
        }
      }
    } catch (Exception ignored) {
    }
    // Try literal
    if (parameterType != null && parameterType.isNumber()) {
      try {
        return numberType.parseNumber(argExpr);
      } catch (Exception ignored) {
      }
    }
    if (parameterType != null && parameterType.isBoolean()) {
      if ("true".equalsIgnoreCase(argExpr)) return true;
      if ("false".equalsIgnoreCase(argExpr)) return false;
    }
    // String literal (quoted)
    String unquoted = unquoteStringLiteral(argExpr);
    if (unquoted != null) {
      return unquoted;
    }
    // Return as-is for object type
    if (parameterType != null && parameterType.isString()) {
      return argExpr;
    }
    return argExpr;
  }

  private Object coerceToType(Object value, ExpressionType targetType) {
    if (value == null || targetType == null) return value;
    if (targetType.isString()) return String.valueOf(value);
    if (targetType.isBoolean()) {
      if (value instanceof Boolean) return value;
      return "true".equalsIgnoreCase(String.valueOf(value));
    }
    if (targetType.isNumber() && value instanceof Number number) {
      return castToNumberType(number.doubleValue());
    }
    if (targetType.isNumber() && value instanceof String text) {
      try {
        return resolveNumberType(new SpecifiedExpressionTypes(targetType, numberType)).parseNumber(text.strip());
      } catch (RuntimeException ignored) {
      }
    }
    return value;
  }

  private String effectiveLookupFormulaSource() {
    if (lookupFormulaSource != null && !lookupFormulaSource.isBlank()) {
      return lookupFormulaSource;
    }
    return sourceFormula;
  }

  private Optional<Boolean> evaluateArgumentCondition(String conditionSource) {
    Optional<Boolean> simple = tryEvaluateSimpleCondition(conditionSource);
    if (simple.isPresent()) {
      return simple;
    }
    SpecifiedExpressionTypes booleanTypes =
        new SpecifiedExpressionTypes(ExpressionTypes._boolean, numberType);
    try {
      String parseSource = P4PreferredAstMapper.normalizeExpressionSnippetForParsing(conditionSource);
      TinyExpressionP4AST conditionAst = P4PreferredAstMapper.parse(parseSource, ExpressionTypes._boolean);
      if (conditionAst != null) {
        Object result = new P4TypedAstEvaluator(
            booleanTypes,
            context,
            selectSnippetSource(parseSource),
            effectiveLookupFormulaSource(),
            classLoader).eval(conditionAst);
        if (result != null) {
          return Optional.of(Boolean.TRUE.equals(toBoolean(result)));
        }
      }
    } catch (RuntimeException ignored) {
    }
    return AstEmbeddedExpressionRuntime.tryEvaluateFormulaDirect(
        conditionSource, ExpressionTypes._boolean, booleanTypes, context, classLoader)
        .map(value -> Boolean.TRUE.equals(toBoolean(value)));
  }

  private Optional<Boolean> tryEvaluateSimpleCondition(String conditionSource) {
    String normalized = conditionSource == null ? "" : conditionSource.strip();
    if (normalized.isEmpty()) {
      return Optional.empty();
    }
    if ("true".equalsIgnoreCase(normalized)) {
      return Optional.of(true);
    }
    if ("false".equalsIgnoreCase(normalized)) {
      return Optional.of(false);
    }
    if (isExactVariableReference(normalized)) {
      Object resolved = resolveVariableAny(extractVariableName(normalized));
      if (resolved != null) {
        return Optional.of(Boolean.TRUE.equals(toBoolean(resolved)));
      }
    }
    Optional<SimpleComparisonSource> comparison = splitSimpleComparison(normalized);
    if (comparison.isEmpty()) {
      return Optional.empty();
    }
    Optional<Object> left = evaluateSimpleConditionOperand(comparison.get().left());
    Optional<Object> right = evaluateSimpleConditionOperand(comparison.get().right());
    if (left.isEmpty() || right.isEmpty()) {
      return Optional.empty();
    }
    String op = comparison.get().op();
    if (left.get() instanceof Number leftNumber && right.get() instanceof Number rightNumber) {
      int compare = toBigDecimal(leftNumber).compareTo(toBigDecimal(rightNumber));
      return switch (op) {
        case "==" -> Optional.of(compare == 0);
        case "!=" -> Optional.of(compare != 0);
        case "<" -> Optional.of(compare < 0);
        case "<=" -> Optional.of(compare <= 0);
        case ">" -> Optional.of(compare > 0);
        case ">=" -> Optional.of(compare >= 0);
        default -> Optional.empty();
      };
    }
    return switch (op) {
      case "==" -> Optional.of(left.get().equals(right.get()));
      case "!=" -> Optional.of(!left.get().equals(right.get()));
      default -> Optional.empty();
    };
  }

  private Optional<Object> evaluateSimpleConditionOperand(String operandSource) {
    String normalized = operandSource == null ? "" : operandSource.strip();
    if (normalized.isEmpty()) {
      return Optional.empty();
    }
    if (isExactVariableReference(normalized)) {
      Object resolved = resolveVariableAny(extractVariableName(normalized));
      return Optional.ofNullable(resolved);
    }
    String unquoted = unquoteStringLiteral(normalized);
    if (unquoted != null) {
      return Optional.of(unquoted);
    }
    if ("true".equalsIgnoreCase(normalized) || "false".equalsIgnoreCase(normalized)) {
      return Optional.of(Boolean.parseBoolean(normalized));
    }
    try {
      return Optional.of(resolveNumberType(new SpecifiedExpressionTypes(numberType, numberType)).parseNumber(normalized));
    } catch (RuntimeException ignored) {
      return Optional.empty();
    }
  }

  private Optional<SimpleComparisonSource> splitSimpleComparison(String source) {
    int parenDepth = 0;
    int braceDepth = 0;
    int bracketDepth = 0;
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    for (int i = 0; i < source.length() - 1; i++) {
      char c = source.charAt(i);
      char next = source.charAt(i + 1);
      char prev = i > 0 ? source.charAt(i - 1) : '\0';
      if (c == '\'' && !inDoubleQuote && prev != '\\') {
        inSingleQuote = !inSingleQuote;
        continue;
      }
      if (c == '"' && !inSingleQuote && prev != '\\') {
        inDoubleQuote = !inDoubleQuote;
        continue;
      }
      if (inSingleQuote || inDoubleQuote) {
        continue;
      }
      switch (c) {
        case '(' -> {
          parenDepth++;
          continue;
        }
        case ')' -> {
          parenDepth = Math.max(0, parenDepth - 1);
          continue;
        }
        case '{' -> {
          braceDepth++;
          continue;
        }
        case '}' -> {
          braceDepth = Math.max(0, braceDepth - 1);
          continue;
        }
        case '[' -> {
          bracketDepth++;
          continue;
        }
        case ']' -> {
          bracketDepth = Math.max(0, bracketDepth - 1);
          continue;
        }
        default -> {
        }
      }
      if (parenDepth != 0 || braceDepth != 0 || bracketDepth != 0) {
        continue;
      }
      String op = null;
      if ((c == '=' && next == '=') || (c == '!' && next == '=') || (c == '<' && next == '=') || (c == '>' && next == '=')) {
        op = source.substring(i, i + 2);
      } else if (c == '<' || c == '>') {
        op = source.substring(i, i + 1);
      }
      if (op == null) {
        continue;
      }
      int opLength = op.length();
      String left = source.substring(0, i).strip();
      String right = source.substring(i + opLength).strip();
      if (!left.isEmpty() && !right.isEmpty()) {
        return Optional.of(new SimpleComparisonSource(left, op, right));
      }
    }
    return Optional.empty();
  }

  private String selectSnippetSource(String snippetSource) {
    String normalized = snippetSource == null ? "" : snippetSource.strip();
    if (normalized.isEmpty()) {
      return normalized;
    }
    if (AstEmbeddedExpressionRuntime.hasMethodInvocationHead(normalized)) {
      String lookupSource = effectiveLookupFormulaSource();
      return lookupSource == null ? normalized : lookupSource;
    }
    return normalized;
  }

  private record SimpleComparisonSource(String left, String op, String right) {}

  @Override
  protected Object evalExternalBooleanInvocationExpr(ExternalBooleanInvocationExpr node) {
    return evaluateExternalInvocation(node, ExpressionTypes._boolean);
  }

  @Override
  protected Object evalExternalNumberInvocationExpr(ExternalNumberInvocationExpr node) {
    return evaluateExternalInvocation(node, resultType.isNumber() ? resultType : ExpressionTypes._float);
  }

  @Override
  protected Object evalExternalStringInvocationExpr(ExternalStringInvocationExpr node) {
    return evaluateExternalInvocation(node, ExpressionTypes.string);
  }

  @Override
  protected Object evalExternalObjectInvocationExpr(ExternalObjectInvocationExpr node) {
    return evaluateExternalInvocation(node, ExpressionTypes.object);
  }

  private Object evaluateExternalInvocation(Object node, ExpressionType expectedReturnType) {
    if (sourceFormula == null || sourceFormula.isEmpty()) {
      throw new UnsupportedOperationException(
          "External invocation requires sourceFormula");
    }

    // Get the source snippet for this external invocation node
    String externalSource = null;
    Optional<String> snippet = GeneratedP4ValueAstEvaluator.sourceSnippetOfNode(node, sourceFormula);
    if (snippet.isPresent()) {
      externalSource = snippet.get().strip();
    }
    if (externalSource == null || externalSource.isEmpty()) {
      // Fallback: use the full sourceFormula if it looks like an external invocation
      externalSource = sourceFormula.strip();
    }

    // Parse external invocation: extract class#method and arguments
    // Format: external returning as <type> [default <expr>] [: ] <class>#<method>(<args>)
    ExternalInvocationInfo info = parseExternalInvocation(externalSource);
    if (info == null) {
      // Fallback: delegate to embedded expression runtime
      ClassLoader effectiveClassLoader = classLoader != null
          ? classLoader : Thread.currentThread().getContextClassLoader();
      SpecifiedExpressionTypes evalTypes = new SpecifiedExpressionTypes(
          expectedReturnType,
          GeneratedP4ValueAstEvaluator.resolveNumberTypeForEvaluation(
              expectedReturnType, specifiedExpressionTypes.numberType()));
      Optional<Object> embedded = AstEmbeddedExpressionRuntime.tryEvaluate(
          externalSource, expectedReturnType, evalTypes, context,
          effectiveClassLoader, sourceFormula);
      if (embedded.isPresent()) {
        return embedded.get();
      }
      throw new UnsupportedOperationException(
          "Failed to parse external invocation: " + externalSource);
    }

    // Resolve the class and method via reflection
    ClassLoader effectiveClassLoader = classLoader != null
        ? classLoader : Thread.currentThread().getContextClassLoader();
    try {
      Class<?> clazz = Class.forName(info.className, true, effectiveClassLoader);

      // Evaluate arguments
      List<Object> argValues = new java.util.ArrayList<>();
      List<ExpressionType> argTypes = new java.util.ArrayList<>();
      for (ExternalArgSpec argSpec : info.args) {
        Object value = evaluateArgumentExpression(argSpec.expression, argSpec.type);
        argValues.add(value);
        argTypes.add(argSpec.type);
      }

      // Find matching method
      // External methods have CalculationContext as first parameter
      java.lang.reflect.Method method = findExternalMethod(clazz, info.methodName, argTypes, argValues);
      if (method == null) {
        throw new UnsupportedOperationException(
            "Method not found: " + info.className + "#" + info.methodName);
      }

      // Build actual parameters: CalculationContext + evaluated arguments
      Object instance = clazz.getDeclaredConstructor().newInstance();
      Object[] params = buildMethodParams(method, argValues);
      Object result = method.invoke(instance, params);

      // Coerce result to expected type
      if (result != null) {
        return coerceToType(result, expectedReturnType);
      }

      // If result is null and there's a default expression, evaluate it
      if (info.defaultExpression != null && !info.defaultExpression.isEmpty()) {
        return evaluateArgumentExpression(info.defaultExpression, expectedReturnType);
      }

      return result;
    } catch (UnsupportedOperationException e) {
      throw e;
    } catch (Exception e) {
      // Fallback: delegate to embedded expression runtime
      SpecifiedExpressionTypes evalTypes = new SpecifiedExpressionTypes(
          expectedReturnType,
          GeneratedP4ValueAstEvaluator.resolveNumberTypeForEvaluation(
              expectedReturnType, specifiedExpressionTypes.numberType()));
      Optional<Object> embedded = AstEmbeddedExpressionRuntime.tryEvaluate(
          externalSource, expectedReturnType, evalTypes, context,
          effectiveClassLoader, sourceFormula);
      if (embedded.isPresent()) {
        return embedded.get();
      }
      throw new UnsupportedOperationException(
          "External invocation failed: " + info.className + "#" + info.methodName, e);
    }
  }

  private Object[] buildMethodParams(java.lang.reflect.Method method, List<Object> argValues) {
    Class<?>[] paramTypes = method.getParameterTypes();
    Object[] params = new Object[paramTypes.length];
    int argIndex = 0;
    for (int i = 0; i < paramTypes.length; i++) {
      if (CalculationContext.class.isAssignableFrom(paramTypes[i])) {
        params[i] = context;
      } else {
        if (argIndex < argValues.size()) {
          params[i] = convertToParamType(argValues.get(argIndex), paramTypes[i]);
          argIndex++;
        }
      }
    }
    return params;
  }

  private static Object convertToParamType(Object value, Class<?> targetType) {
    if (value == null) return null;
    if (targetType.isInstance(value)) return value;
    if (targetType == float.class || targetType == Float.class) {
      if (value instanceof Number n) return n.floatValue();
      try { return Float.parseFloat(String.valueOf(value)); } catch (Exception e) { return 0f; }
    }
    if (targetType == double.class || targetType == Double.class) {
      if (value instanceof Number n) return n.doubleValue();
      try { return Double.parseDouble(String.valueOf(value)); } catch (Exception e) { return 0.0; }
    }
    if (targetType == int.class || targetType == Integer.class) {
      if (value instanceof Number n) return n.intValue();
      try { return Integer.parseInt(String.valueOf(value)); } catch (Exception e) { return 0; }
    }
    if (targetType == long.class || targetType == Long.class) {
      if (value instanceof Number n) return n.longValue();
      try { return Long.parseLong(String.valueOf(value)); } catch (Exception e) { return 0L; }
    }
    if (targetType == boolean.class || targetType == Boolean.class) {
      if (value instanceof Boolean b) return b;
      return "true".equalsIgnoreCase(String.valueOf(value));
    }
    if (targetType == String.class) {
      return String.valueOf(value);
    }
    return value;
  }

  private static java.lang.reflect.Method findExternalMethod(Class<?> clazz, String methodName,
      List<ExpressionType> argTypes, List<Object> argValues) {
    java.lang.reflect.Method[] methods = clazz.getMethods();
    for (java.lang.reflect.Method m : methods) {
      if (!m.getName().equals(methodName)) continue;
      Class<?>[] paramTypes = m.getParameterTypes();
      // Count non-CalculationContext params
      int expectedArgs = 0;
      for (Class<?> pt : paramTypes) {
        if (!CalculationContext.class.isAssignableFrom(pt)) {
          expectedArgs++;
        }
      }
      if (expectedArgs == argValues.size()) {
        return m;
      }
    }
    return null;
  }

  // --- External invocation parsing ---

  private record ExternalArgSpec(String expression, ExpressionType type) {}
  private record ExternalInvocationInfo(String className, String methodName,
      List<ExternalArgSpec> args, String defaultExpression) {}

  private ExternalInvocationInfo parseExternalInvocation(String source) {
    if (source == null || !source.startsWith("external")) {
      return null;
    }

    // Find the class#method reference
    int hashIndex = source.indexOf('#');
    if (hashIndex < 0) return null;

    // Extract class name: look backwards from # for the qualified name
    int classNameStart = hashIndex - 1;
    while (classNameStart >= 0) {
      char c = source.charAt(classNameStart);
      if (Character.isJavaIdentifierPart(c) || c == '.') {
        classNameStart--;
      } else {
        break;
      }
    }
    classNameStart++;
    String className = source.substring(classNameStart, hashIndex).strip();

    // Extract method name and arguments
    int methodStart = hashIndex + 1;
    int openParen = source.indexOf('(', methodStart);
    if (openParen < 0) return null;
    String methodName = source.substring(methodStart, openParen).strip();

    int closeParen = GeneratedP4ValueAstEvaluator.findMatching(source, openParen, '(', ')');
    if (closeParen < 0) return null;
    String argsString = source.substring(openParen + 1, closeParen).strip();

    // Parse arguments
    List<String> argStrings = GeneratedP4ValueAstEvaluator.splitTopLevelCommaSeparated(argsString);
    List<ExternalArgSpec> argSpecs = new java.util.ArrayList<>();
    for (String arg : argStrings) {
      String trimmed = arg.strip();
      // Check for type annotation: $var as type
      ExpressionType argType = ExpressionTypes.object;
      String expr = trimmed;
      int asIndex = trimmed.toLowerCase().indexOf(" as ");
      if (asIndex >= 0) {
        expr = trimmed.substring(0, asIndex).strip();
        String typeStr = trimmed.substring(asIndex + 4).strip();
        Optional<ExpressionType> parsed = GeneratedP4ValueAstEvaluator.parseExpressionType(typeStr);
        if (parsed.isPresent()) {
          argType = parsed.get();
        }
      }
      argSpecs.add(new ExternalArgSpec(expr, argType));
    }

    // Parse default expression
    String defaultExpression = null;
    String beforeHash = source.substring(0, classNameStart).strip();
    int defaultIndex = beforeHash.indexOf("default ");
    if (defaultIndex >= 0) {
      int defaultStart = defaultIndex + "default ".length();
      // Default expression ends at ':'
      int colonIndex = beforeHash.indexOf(':', defaultStart);
      if (colonIndex >= 0) {
        defaultExpression = beforeHash.substring(defaultStart, colonIndex).strip();
      } else {
        defaultExpression = beforeHash.substring(defaultStart).strip();
      }
    }

    return new ExternalInvocationInfo(className, methodName, argSpecs, defaultExpression);
  }

  // =========================================================================
  // Math functions
  // =========================================================================

  @Override
  protected Object evalSinExpr(SinExpr node) {
    double arg = ((Number) eval(node.arg())).doubleValue();
    return castToNumberType(Math.sin(context.radianAngle(arg)));
  }

  @Override
  protected Object evalCosExpr(CosExpr node) {
    double arg = ((Number) eval(node.arg())).doubleValue();
    return castToNumberType(Math.cos(context.radianAngle(arg)));
  }

  @Override
  protected Object evalTanExpr(TanExpr node) {
    double arg = ((Number) eval(node.arg())).doubleValue();
    return castToNumberType(Math.tan(context.radianAngle(arg)));
  }

  @Override
  protected Object evalSqrtExpr(SqrtExpr node) {
    return castToNumberType(Math.sqrt(((Number) eval(node.arg())).doubleValue()));
  }

  @Override
  protected Object evalMinExpr(MinExpr node) {
    double min = ((Number) eval(node.first())).doubleValue();
    for (var r : node.rest()) {
      min = Math.min(min, ((Number) eval(r)).doubleValue());
    }
    return castToNumberType(min);
  }

  @Override
  protected Object evalMaxExpr(MaxExpr node) {
    double max = ((Number) eval(node.first())).doubleValue();
    for (var r : node.rest()) {
      max = Math.max(max, ((Number) eval(r)).doubleValue());
    }
    return castToNumberType(max);
  }

  @Override
  protected Object evalRandomExpr(RandomExpr node) {
    return castToNumberType(Math.random());
  }

  @Override
  protected Object evalAbsExpr(AbsExpr node) {
    return castToNumberType(Math.abs(((Number) eval(node.arg())).doubleValue()));
  }

  @Override
  protected Object evalRoundExpr(RoundExpr node) {
    return castToNumberType((double) Math.round(((Number) eval(node.arg())).doubleValue()));
  }

  @Override
  protected Object evalCeilExpr(CeilExpr node) {
    return castToNumberType(Math.ceil(((Number) eval(node.arg())).doubleValue()));
  }

  @Override
  protected Object evalFloorExpr(FloorExpr node) {
    return castToNumberType(Math.floor(((Number) eval(node.arg())).doubleValue()));
  }

  @Override
  protected Object evalPowExpr(PowExpr node) {
    double base = ((Number) eval(node.base())).doubleValue();
    double exponent = ((Number) eval(node.exponent())).doubleValue();
    return castToNumberType(Math.pow(base, exponent));
  }

  @Override
  protected Object evalLogExpr(LogExpr node) {
    return castToNumberType(Math.log(((Number) eval(node.arg())).doubleValue()));
  }

  @Override
  protected Object evalExpExpr(ExpExpr node) {
    return castToNumberType(Math.exp(((Number) eval(node.arg())).doubleValue()));
  }

  // =========================================================================
  // Not operator
  // =========================================================================

  @Override
  protected Object evalNotExpr(NotExpr node) {
    return !Boolean.TRUE.equals(toBoolean(eval(node.value())));
  }

  @Override
  protected Object evalBooleanEqualityExpr(BooleanEqualityExpr node) {
    boolean left = resolveBooleanSourceOperand(node.left());
    boolean right = resolveBooleanSourceOperand(node.right());
    String op = node.op() == null ? "==" : node.op().strip();
    return switch (op) {
      case "==" -> left == right;
      case "!=" -> left != right;
      default -> false;
    };
  }

  // =========================================================================
  // String methods (function form)
  // =========================================================================

  @Override
  protected Object evalToUpperCaseExpr(ToUpperCaseExpr node) {
    return String.valueOf(eval(node.value())).toUpperCase();
  }

  @Override
  protected Object evalToLowerCaseExpr(ToLowerCaseExpr node) {
    return String.valueOf(eval(node.value())).toLowerCase();
  }

  @Override
  protected Object evalTrimExpr(TrimExpr node) {
    return String.valueOf(eval(node.value())).trim();
  }

  @Override
  protected Object evalLengthExpr(LengthExpr node) {
    return castToNumberType(String.valueOf(eval(node.value())).length());
  }

  // =========================================================================
  // String dot methods (delegate to same logic as function form)
  // =========================================================================

  @Override
  protected Object evalToUpperCaseDotExpr(ToUpperCaseDotExpr node) {
    return String.valueOf(eval(node.value())).toUpperCase();
  }

  @Override
  protected Object evalToLowerCaseDotExpr(ToLowerCaseDotExpr node) {
    return String.valueOf(eval(node.value())).toLowerCase();
  }

  @Override
  protected Object evalTrimDotExpr(TrimDotExpr node) {
    return String.valueOf(eval(node.value())).trim();
  }

  @Override
  protected Object evalLengthDotExpr(LengthDotExpr node) {
    return castToNumberType(String.valueOf(eval(node.value())).length());
  }

  // =========================================================================
  // String predicates (function form — boolean-returning)
  // =========================================================================

  @Override
  protected Object evalStartsWithExpr(StartsWithExpr node) {
    return String.valueOf(eval(node.value())).startsWith(String.valueOf(eval(node.pattern())));
  }

  @Override
  protected Object evalEndsWithExpr(EndsWithExpr node) {
    return String.valueOf(eval(node.value())).endsWith(String.valueOf(eval(node.pattern())));
  }

  @Override
  protected Object evalContainsExpr(ContainsExpr node) {
    return String.valueOf(eval(node.value())).contains(String.valueOf(eval(node.pattern())));
  }

  @Override
  protected Object evalInExpr(InExpr node) {
    String value = String.valueOf(eval(node.value()));
    for (StringConcatExpr candidate : node.candidates()) {
      if (value.equals(String.valueOf(eval(candidate)))) {
        return true;
      }
    }
    return false;
  }

  // =========================================================================
  // String predicates (dot form — boolean-returning)
  // =========================================================================

  @Override
  protected Object evalStartsWithDotExpr(StartsWithDotExpr node) {
    return String.valueOf(eval(node.value())).startsWith(String.valueOf(eval(node.pattern())));
  }

  @Override
  protected Object evalEndsWithDotExpr(EndsWithDotExpr node) {
    return String.valueOf(eval(node.value())).endsWith(String.valueOf(eval(node.pattern())));
  }

  @Override
  protected Object evalContainsDotExpr(ContainsDotExpr node) {
    return String.valueOf(eval(node.value())).contains(String.valueOf(eval(node.pattern())));
  }

  // =========================================================================
  // isPresent
  // =========================================================================

  @Override
  protected Object evalIsPresentExpr(IsPresentExpr node) {
    String varName = resolveVariableRefName(node.value());
    return varName != null && context.isExists(varName);
  }

  // =========================================================================
  // InTimeRangeExpr / InDayTimeRangeExpr
  // =========================================================================

  @Override
  protected Object evalInTimeRangeExpr(InTimeRangeExpr node) {
    float startHour = evalBinaryAsNumber(node.startHour()).floatValue();
    float endHour = evalBinaryAsNumber(node.endHour()).floatValue();
    return EmbeddedFunction.inTimeRange(context, startHour, endHour);
  }

  @Override
  protected Object evalInDayTimeRangeExpr(InDayTimeRangeExpr node) {
    String startDayStr = node.startDay().strip();
    float startHour = evalBinaryAsNumber(node.startHour()).floatValue();
    String endDayStr = node.endDay().strip();
    float endHour = evalBinaryAsNumber(node.endHour()).floatValue();
    return context.inDayTimeRange(
        DayOfWeek.valueOf(startDayStr), startHour,
        DayOfWeek.valueOf(endDayStr), endHour);
  }

  // =========================================================================
  // String slice (Python-style)
  // =========================================================================

  @Override
  protected Object evalSliceExpr(SliceExpr node) {
    String value = resolveStringLeaf(node.value());
    int len = value.length();
    SliceParts sliceParts = slicePartsOfNode(node).orElse(null);
    boolean sourceAware = sliceParts != null;
    Integer stepValue = resolveSliceIndex(node.step(), sourceAware, sliceParts == null ? null : sliceParts.stepSource());
    Integer startValue = resolveSliceIndex(node.start(), sourceAware, sliceParts == null ? null : sliceParts.startSource());
    Integer endValue = resolveSliceIndex(node.end(), sourceAware, sliceParts == null ? null : sliceParts.endSource());
    int step = stepValue != null ? stepValue : 1;
    if (step == 0) {
      throw new IllegalArgumentException("slice step cannot be zero");
    }
    int start;
    int end;
    if (step > 0) {
      start = startValue != null ? normalizeIndex(startValue, len) : 0;
      end = endValue != null ? normalizeIndex(endValue, len) : len;
    } else {
      start = startValue != null ? normalizeIndex(startValue, len) : len - 1;
      end = endValue != null ? normalizeIndex(endValue, len) : -1;
    }
    StringBuilder sb = new StringBuilder();
    if (step > 0) {
      for (int i = start; i < end; i += step) {
        sb.append(value.charAt(i));
      }
    } else {
      for (int i = start; i > end; i += step) {
        sb.append(value.charAt(i));
      }
    }
    return sb.toString();
  }

  private Integer resolveSliceIndex(BinaryExpr astNode, boolean sourceAware, String sourceSnippet) {
    if (sourceAware) {
      if (sourceSnippet == null) {
        return null;
      }
      Integer fromSource = evaluateSliceIndexSource(sourceSnippet);
      if (fromSource != null) {
        return fromSource;
      }
    }
    return astNode != null ? evalBinaryAsNumber(astNode).intValue() : null;
  }

  private Integer evaluateSliceIndexSource(String sourceSnippet) {
    try {
      TinyExpressionP4AST ast = P4PreferredAstMapper.parseDetailed(sourceSnippet, numberType).ast();
      Object value = new P4TypedAstEvaluator(
          new SpecifiedExpressionTypes(numberType, numberType),
          context,
          sourceSnippet,
          classLoader).eval(ast);
      return value instanceof Number number ? number.intValue() : null;
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private static int normalizeIndex(int index, int len) {
    if (index < 0) {
      index = len + index;
    }
    return Math.max(0, Math.min(index, len));
  }

  // =========================================================================
  // ToNum conversion
  // =========================================================================

  @Override
  protected Object evalToNumExpr(ToNumExpr node) {
    Object strVal = eval(node.value());
    try {
      return Double.parseDouble(String.valueOf(strVal));
    } catch (NumberFormatException e) {
      return ((Number) eval(node.defaultValue())).doubleValue();
    }
  }

  @Override
  protected Object evalCodeBlockExpr(CodeBlockExpr node) {
    return null;
  }

  @Override
  protected Object evalImportDeclarationExpr(ImportDeclarationExpr node) {
    return null;
  }

  // =========================================================================
  // Utility
  // =========================================================================

  private static boolean looksLikeStructuredStringLeaf(String text) {
    if (text == null || text.isEmpty()) {
      return false;
    }
    String normalized = text.strip();
    if (normalized.isEmpty()) {
      return false;
    }
    if (normalized.indexOf('[') >= 0 && normalized.endsWith("]")) {
      return true;
    }
    String unwrapped = unwrapWholeParentheses(normalized);
    if (!unwrapped.equals(normalized)) {
      return looksLikeStructuredStringLeaf(unwrapped)
          || hasTopLevelStringConcat(unwrapped);
    }
    return normalized.contains(".trim(")
        || normalized.contains(".toUpperCase(")
        || normalized.contains(".toLowerCase(")
        || normalized.contains(".contains(")
        || normalized.contains(".startsWith(")
        || normalized.contains(".endsWith(")
        || normalized.startsWith("trim(")
        || normalized.startsWith("toUpperCase(")
        || normalized.startsWith("toLowerCase(")
        || normalized.startsWith("slice(")
        || normalized.startsWith("call ")
        || normalized.startsWith("internal ")
        || normalized.startsWith("external ")
        || hasTopLevelStringConcat(normalized);
  }

  private static String unwrapWholeParentheses(String text) {
    String current = text;
    while (isWrappedByWholeParentheses(current)) {
      current = current.substring(1, current.length() - 1).strip();
    }
    return current;
  }

  private static boolean isWrappedByWholeParentheses(String text) {
    if (text == null || text.length() < 2 || text.charAt(0) != '(' || text.charAt(text.length() - 1) != ')') {
      return false;
    }
    int parenDepth = 0;
    int bracketDepth = 0;
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      char prev = i > 0 ? text.charAt(i - 1) : '\0';
      if (c == '\'' && !inDoubleQuote && prev != '\\') {
        inSingleQuote = !inSingleQuote;
        continue;
      }
      if (c == '"' && !inSingleQuote && prev != '\\') {
        inDoubleQuote = !inDoubleQuote;
        continue;
      }
      if (inSingleQuote || inDoubleQuote) {
        continue;
      }
      switch (c) {
        case '(' -> parenDepth++;
        case ')' -> {
          parenDepth--;
          if (parenDepth == 0 && i < text.length() - 1) {
            return false;
          }
        }
        case '[' -> bracketDepth++;
        case ']' -> bracketDepth = Math.max(0, bracketDepth - 1);
        default -> {
        }
      }
      if (parenDepth < 0 || bracketDepth < 0) {
        return false;
      }
    }
    return parenDepth == 0 && bracketDepth == 0;
  }

  private static boolean hasTopLevelStringConcat(String text) {
    int parenDepth = 0;
    int bracketDepth = 0;
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      char prev = i > 0 ? text.charAt(i - 1) : '\0';
      if (c == '\'' && !inDoubleQuote && prev != '\\') {
        inSingleQuote = !inSingleQuote;
        continue;
      }
      if (c == '"' && !inSingleQuote && prev != '\\') {
        inDoubleQuote = !inDoubleQuote;
        continue;
      }
      if (inSingleQuote || inDoubleQuote) {
        continue;
      }
      switch (c) {
        case '(' -> parenDepth++;
        case ')' -> parenDepth = Math.max(0, parenDepth - 1);
        case '[' -> bracketDepth++;
        case ']' -> bracketDepth = Math.max(0, bracketDepth - 1);
        case '+' -> {
          if (parenDepth == 0 && bracketDepth == 0) {
            return true;
          }
        }
        default -> {
        }
      }
    }
    return false;
  }

  private record StringComparisonSource(String left, String op, String right) {}

  private boolean resolveBooleanSourceOperand(String rawSource) {
    String normalized = rawSource == null ? "" : rawSource.strip();
    if (normalized.isEmpty()) {
      return false;
    }
    Optional<Object> evaluated = tryEvaluateIfSourceSnippet(normalized, ExpressionTypes._boolean);
    if (evaluated.isPresent()) {
      return Boolean.TRUE.equals(toBoolean(evaluated.get()));
    }
    return Boolean.TRUE.equals(toBoolean(normalized));
  }

  private static String unquoteStringLiteral(String raw) {
    if (raw == null || raw.length() < 2) {
      return null;
    }
    char start = raw.charAt(0);
    char end = raw.charAt(raw.length() - 1);
    if ((start == '\'' && end == '\'') || (start == '"' && end == '"')) {
      for (int i = 1; i < raw.length() - 1; i++) {
        if (raw.charAt(i) == start && raw.charAt(i - 1) != '\\') {
          return null;
        }
      }
      return raw.substring(1, raw.length() - 1);
    }
    return null;
  }

  private static String extractVariableName(String raw) {
    if (raw == null || raw.isEmpty() || raw.charAt(0) != '$') {
      return null;
    }
    int end = 1;
    while (end < raw.length()) {
      char c = raw.charAt(end);
      if (Character.isLetterOrDigit(c) || c == '_') {
        end++;
      } else {
        break;
      }
    }
    return end > 1 ? raw.substring(1, end) : null;
  }

  private static boolean isExactVariableReference(String raw) {
    String varName = extractVariableName(raw);
    return varName != null && ("$" + varName).equals(raw.strip());
  }

  private Object tryResolveVariableLikeValue(Object value) {
    if (value instanceof BinaryExpr binaryExpr) {
      String variableName = extractExactVariableReference(binaryExpr);
      if (variableName != null) {
        return resolveVariableAny(variableName);
      }
    }
    return null;
  }

  private static String extractExactVariableReference(BinaryExpr node) {
    if (node == null) {
      return null;
    }
    BinaryExpr left = node.left();
    List<String> op = node.op();
    List<BinaryExpr> right = node.right();
    if (left == null && right.isEmpty() && op.size() == 1) {
      String literal = op.get(0) == null ? "" : op.get(0).strip();
      return isExactVariableReference(literal) ? extractVariableName(literal) : null;
    }
    if (left != null && op.isEmpty() && right.isEmpty()) {
      return extractExactVariableReference(left);
    }
    return null;
  }

  private Optional<String> sourceSnippetOfNode(Object node) {
    if (node == null || sourceFormula == null || sourceFormula.isEmpty()) {
      return Optional.empty();
    }
    Optional<int[]> span = TinyExpressionP4Mapper.sourceSpanOf(node);
    if (span.isEmpty()) {
      return Optional.empty();
    }
    int[] positions = span.get();
    if (positions.length < 2) {
      return Optional.empty();
    }
    int start = Math.max(0, Math.min(sourceFormula.length(), positions[0]));
    int end = Math.max(0, Math.min(sourceFormula.length(), positions[1]));
    if (end <= start) {
      return Optional.empty();
    }
    return Optional.of(sourceFormula.substring(start, end));
  }

  private Optional<SliceParts> slicePartsOfNode(Object node) {
    return sourceSnippetOfNode(node).flatMap(P4TypedAstEvaluator::parseSliceSnippet);
  }

  private static Optional<SliceParts> parseSliceSnippet(String sliceSource) {
    if (sliceSource == null) {
      return Optional.empty();
    }
    String source = sliceSource.strip();
    if (source.isEmpty()) {
      return Optional.empty();
    }
    int openBracket = findTopLevelSliceOpenBracket(source);
    if (openBracket < 0) {
      return Optional.empty();
    }
    int closeBracket = findMatchingBracket(source, openBracket);
    if (closeBracket < 0 || closeBracket != source.length() - 1) {
      return Optional.empty();
    }
    String valueSource = source.substring(0, openBracket).strip();
    if (valueSource.isEmpty()) {
      return Optional.empty();
    }
    List<String> parts = splitTopLevel(source.substring(openBracket + 1, closeBracket), ':');
    if (parts.size() < 2 || parts.size() > 3) {
      return Optional.empty();
    }
    String startSource = normalizeSliceComponent(parts.get(0));
    String endSource = normalizeSliceComponent(parts.get(1));
    String stepSource = parts.size() == 3 ? normalizeSliceComponent(parts.get(2)) : null;
    return Optional.of(new SliceParts(valueSource, startSource, endSource, stepSource));
  }

  private static String normalizeSliceComponent(String component) {
    if (component == null) {
      return null;
    }
    String normalized = component.strip();
    return normalized.isEmpty() ? null : normalized;
  }

  private static int findTopLevelSliceOpenBracket(String source) {
    int parenDepth = 0;
    int braceDepth = 0;
    int bracketDepth = 0;
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    for (int i = 0; i < source.length(); i++) {
      char c = source.charAt(i);
      char prev = i > 0 ? source.charAt(i - 1) : '\0';
      if (c == '\'' && !inDoubleQuote && prev != '\\') {
        inSingleQuote = !inSingleQuote;
        continue;
      }
      if (c == '"' && !inSingleQuote && prev != '\\') {
        inDoubleQuote = !inDoubleQuote;
        continue;
      }
      if (inSingleQuote || inDoubleQuote) {
        continue;
      }
      switch (c) {
        case '(' -> parenDepth++;
        case ')' -> parenDepth = Math.max(0, parenDepth - 1);
        case '{' -> braceDepth++;
        case '}' -> braceDepth = Math.max(0, braceDepth - 1);
        case '[' -> {
          if (parenDepth == 0 && braceDepth == 0 && bracketDepth == 0) {
            return i;
          }
          bracketDepth++;
        }
        case ']' -> bracketDepth = Math.max(0, bracketDepth - 1);
        default -> {
        }
      }
    }
    return -1;
  }

  private static int findMatchingBracket(String source, int openIndex) {
    int bracketDepth = 0;
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    for (int i = openIndex; i < source.length(); i++) {
      char c = source.charAt(i);
      char prev = i > 0 ? source.charAt(i - 1) : '\0';
      if (c == '\'' && !inDoubleQuote && prev != '\\') {
        inSingleQuote = !inSingleQuote;
        continue;
      }
      if (c == '"' && !inSingleQuote && prev != '\\') {
        inDoubleQuote = !inDoubleQuote;
        continue;
      }
      if (inSingleQuote || inDoubleQuote) {
        continue;
      }
      if (c == '[') {
        bracketDepth++;
      } else if (c == ']') {
        bracketDepth--;
        if (bracketDepth == 0) {
          return i;
        }
      }
    }
    return -1;
  }

  private static List<String> splitTopLevel(String text, char separator) {
    java.util.ArrayList<String> parts = new java.util.ArrayList<>();
    int start = 0;
    int parenDepth = 0;
    int braceDepth = 0;
    int bracketDepth = 0;
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      char prev = i > 0 ? text.charAt(i - 1) : '\0';
      if (c == '\'' && !inDoubleQuote && prev != '\\') {
        inSingleQuote = !inSingleQuote;
      } else if (c == '"' && !inSingleQuote && prev != '\\') {
        inDoubleQuote = !inDoubleQuote;
      }
      if (inSingleQuote || inDoubleQuote) {
        continue;
      }
      switch (c) {
        case '(' -> parenDepth++;
        case ')' -> parenDepth = Math.max(0, parenDepth - 1);
        case '{' -> braceDepth++;
        case '}' -> braceDepth = Math.max(0, braceDepth - 1);
        case '[' -> bracketDepth++;
        case ']' -> bracketDepth = Math.max(0, bracketDepth - 1);
        default -> {
        }
      }
      if (parenDepth == 0 && braceDepth == 0 && bracketDepth == 0 && c == separator) {
        parts.add(text.substring(start, i));
        start = i + 1;
      }
    }
    parts.add(text.substring(start));
    return parts;
  }

  private record SliceParts(
      String valueSource,
      String startSource,
      String endSource,
      String stepSource) {}

  private static Boolean toBoolean(Object value) {
    if (value instanceof Boolean bool) return bool;
    if (value == null) return false;
    String text = String.valueOf(value).strip().toLowerCase();
    if ("true".equals(text)) return true;
    if ("false".equals(text)) return false;
    return false;
  }

  private static BigDecimal toBigDecimal(Number value) {
    if (value instanceof BigDecimal bd) return bd;
    return new BigDecimal(String.valueOf(value));
  }

  /**
   * Cast a raw double result to the configured number type (float, BigDecimal, etc.).
   */
  private Number castToNumberType(double value) {
    if (numberType.isFloat()) return (float) value;
    if (numberType.isDouble()) return value;
    if (numberType.isBigDecimal()) return BigDecimal.valueOf(value);
    if (numberType.isBigInteger()) return BigInteger.valueOf(Math.round(value));
    if (numberType.isInt()) return (int) value;
    if (numberType.isLong()) return (long) value;
    if (numberType.isShort()) return (short) value;
    if (numberType.isByte()) return (byte) value;
    return (float) value;
  }
}
