package org.unlaxer.tinyexpression.evaluator.ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.DayOfWeek;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.CalculationException;
import org.unlaxer.tinyexpression.evaluator.p4.P4StrictMatchTypingValidator;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.function.EmbeddedFunction;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.*;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Evaluator;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper;
import org.unlaxer.tinyexpression.p4.P4PreferredAstMapper;
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
  /**
   * Declared variable types collected from {@code var}/{@code variable} declarations in the
   * preamble (e.g. {@code var $name as string ...}). The generated P4 AST drops declaration
   * tokens (they carry {@code @declares}, not {@code @mapping}), so this map is the only way the
   * pure-AST path learns that a bare {@code $name} should compare as a string rather than a number.
   * Empty when the formula has no declarations. (#32 / handoff #44 "C")
   */
  private final Map<String, ExpressionType> declaredVariableTypes;
  private final Map<String, ImportTarget> imports = new LinkedHashMap<>();

  public P4TypedAstEvaluator(SpecifiedExpressionTypes types, CalculationContext context) {
    this(types, context, null, null);
  }

  public P4TypedAstEvaluator(SpecifiedExpressionTypes types, CalculationContext context,
      String sourceFormula, ClassLoader classLoader) {
    this(types, context, sourceFormula, sourceFormula, classLoader);
  }

  public P4TypedAstEvaluator(SpecifiedExpressionTypes types, CalculationContext context,
      String sourceFormula, ClassLoader classLoader, Map<String, ExpressionType> declaredVariableTypes) {
    this(types, context, sourceFormula, sourceFormula, classLoader, declaredVariableTypes);
  }

  public P4TypedAstEvaluator(SpecifiedExpressionTypes types, CalculationContext context,
      String sourceFormula, String lookupFormulaSource, ClassLoader classLoader) {
    this(types, context, sourceFormula, lookupFormulaSource, classLoader, Map.of());
  }

  public P4TypedAstEvaluator(SpecifiedExpressionTypes types, CalculationContext context,
      String sourceFormula, String lookupFormulaSource, ClassLoader classLoader,
      Map<String, ExpressionType> declaredVariableTypes) {
    this.resultType = types.resultType() != null ? types.resultType() : ExpressionTypes.object;
    this.numberType = resolveNumberType(types);
    this.context = context;
    this.specifiedExpressionTypes = types;
    this.sourceFormula = sourceFormula;
    this.lookupFormulaSource = lookupFormulaSource;
    this.classLoader = classLoader;
    this.declaredVariableTypes = declaredVariableTypes == null
        ? new LinkedHashMap<>() : new LinkedHashMap<>(declaredVariableTypes);
  }

  private record ImportTarget(String className, String methodName) {}

  @Override
  protected Object evalFormulaExpr(FormulaExpr node) {
    for (ImportDeclarationExpr declaration : node.imports()) {
      eval(declaration);
    }
    for (Object declaration : node.declarations()) {
      if (declaration instanceof TinyExpressionP4AST ast) {
        eval(ast);
      }
    }
    return eval(node.expression());
  }

  @Override
  protected Object evalNumberVariableDeclarationExpr(NumberVariableDeclarationExpr node) {
    declaredVariableTypes.put(node.varName(), numberType);
    if (shouldApplyDeclaration(node.varName(), node.onlyIfAbsent()) && node.value().isPresent()) {
      context.set(node.varName(), (Number) eval(node.value().get()));
    }
    return null;
  }

  @Override
  protected Object evalStringVariableDeclarationExpr(StringVariableDeclarationExpr node) {
    declaredVariableTypes.put(node.varName(), ExpressionTypes.string);
    if (shouldApplyDeclaration(node.varName(), node.onlyIfAbsent()) && node.value().isPresent()) {
      context.set(node.varName(), String.valueOf(eval(node.value().get())));
    }
    return null;
  }

  @Override
  protected Object evalBooleanVariableDeclarationExpr(BooleanVariableDeclarationExpr node) {
    declaredVariableTypes.put(node.varName(), ExpressionTypes._boolean);
    if (shouldApplyDeclaration(node.varName(), node.onlyIfAbsent()) && node.value().isPresent()) {
      context.set(node.varName(), Boolean.TRUE.equals(eval(node.value().get())));
    }
    return null;
  }

  @Override
  protected Object evalObjectVariableDeclarationExpr(ObjectVariableDeclarationExpr node) {
    declaredVariableTypes.put(node.varName(), ExpressionTypes.object);
    if (shouldApplyDeclaration(node.varName(), node.onlyIfAbsent()) && node.value().isPresent()) {
      context.setObject(node.varName(), eval(node.value().get()));
    }
    return null;
  }

  private boolean shouldApplyDeclaration(String name, Optional<OnlyIfAbsentExpr> onlyIfAbsent) {
    return onlyIfAbsent.isEmpty() || !context.isExists(name);
  }

  @Override protected Object evalOnlyIfAbsentExpr(OnlyIfAbsentExpr node) { return Boolean.TRUE; }

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
    // #35: the post-#44 mapper maps every arithmetic operand to a real AST node
    // (mapAssocOperandToBinaryExpr), so the AST walk below is the single source of
    // truth. The former source-snippet shadow (tryEvaluateStructuredBinaryNode) is gone.
    // left/right are the base AST interface (#43): an operand may be another BinaryExpr
    // (the arithmetic spine) OR a directly-mapped factor such as AbsExpr/PowExpr/IfExpr.
    TinyExpressionP4AST left = node.left();
    List<String> op = node.op();
    List<TinyExpressionP4AST> right = node.right();

    // Leaf: left==null, op=[literal], right=[]
    if (left == null && right.isEmpty() && op.size() == 1) {
      return resolveLeafLiteral(op.get(0));
    }
    // Wrap: left!=null, op=[], right=[] — unwrap
    if (left != null && op.isEmpty() && right.isEmpty()) {
      return evalOperandAsNumber(left);
    }
    if (left == null) {
      if (op.size() == 1) {
        return resolveLeafLiteral(op.get(0));
      }
      throw new IllegalArgumentException("left is null for non-leaf BinaryExpr");
    }

    Number current = evalOperandAsNumber(left);
    int count = Math.min(op.size(), right.size());
    for (int i = 0; i < count; i++) {
      Number r = evalOperandAsNumber(right.get(i));
      current = applyBinary(op.get(i), current, r);
    }
    return current;
  }

  /**
   * Evaluate a single arithmetic operand. A BinaryExpr operand stays on the numeric spine;
   * any other node (AbsExpr, PowExpr, MinExpr, IfExpr, …) is dispatched through {@link #eval}
   * so function/conditional factors are honoured instead of dropped. (#43)
   */
  private Number evalOperandAsNumber(TinyExpressionP4AST operand) {
    if (operand instanceof BinaryExpr binary) {
      return evalBinaryAsNumber(binary);
    }
    Object value = eval(operand);
    if (value instanceof Number number) {
      return number;
    }
    if (value == null) {
      return zeroNumber();
    }
    return numberType.parseNumber(String.valueOf(value));
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
        return zeroNumber();
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

  private Number zeroNumber() {
    return numberType.parseNumber("0");
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
    List<Object> rights = node.right();
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
    // The generated mapper has no @mapping recursion into NotExpression, so a
    // top-level "not(...)" boolean factor is mis-mapped: the outer "not" is
    // dropped and node.value() holds only the inner operand (as raw text for
    // "not(false)", or as a ComparisonExpr node for "not(1>2)"). Detect this via
    // the node's source snippet and re-parse the whole factor as a NotExpr so the
    // negation is honoured. (issue #25)
    Optional<Boolean> negated = tryEvaluateNotFactor(node);
    if (negated.isPresent()) {
      return negated.get();
    }
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

  /**
   * If this boolean factor's source snippet is a top-level {@code not(...)}
   * expression, re-parse it as a {@link NotExpr} and evaluate. The generated
   * mapper drops the outer negation (see {@link #evalBooleanFactorExpr}), so this
   * source-snippet-driven re-parse restores correct semantics. Returns empty when
   * the factor is not a negation or cannot be re-parsed. (issue #25)
   */
  private Optional<Boolean> tryEvaluateNotFactor(BooleanFactorExpr node) {
    Optional<String> snippet = sourceSnippetOfNode(node).map(String::strip);
    if (snippet.isEmpty()) {
      return Optional.empty();
    }
    String candidate = snippet.get();
    if (!isTopLevelNotExpression(candidate)) {
      return Optional.empty();
    }
    SpecifiedExpressionTypes booleanTypes =
        new SpecifiedExpressionTypes(ExpressionTypes._boolean, numberType);
    try {
      String parseSource = P4PreferredAstMapper.normalizeExpressionSnippetForParsing(candidate);
      // Request the NotExpr node directly. The default mapper would otherwise wrap
      // the formula back into BooleanOrExpr → BooleanFactorExpr, re-entering this
      // same branch (infinite recursion).
      TinyExpressionP4AST ast = P4PreferredAstMapper.parseByAstSimpleName(parseSource, "NotExpr");
      if (!(ast instanceof NotExpr)) {
        return Optional.empty();
      }
      Object result = new P4TypedAstEvaluator(
          booleanTypes, context, parseSource, effectiveLookupFormulaSource(), classLoader).eval(ast);
      return result == null ? Optional.empty() : Optional.of(Boolean.TRUE.equals(toBoolean(result)));
    } catch (RuntimeException ignored) {
      return Optional.empty();
    }
  }

  /** True if {@code text} is exactly a top-level {@code not( ... )} expression. */
  private static boolean isTopLevelNotExpression(String text) {
    if (text == null) {
      return false;
    }
    String normalized = text.strip();
    if (!normalized.startsWith("not")) {
      return false;
    }
    int i = 3;
    while (i < normalized.length() && Character.isWhitespace(normalized.charAt(i))) {
      i++;
    }
    if (i >= normalized.length() || normalized.charAt(i) != '(') {
      return false;
    }
    int close = GeneratedP4ValueAstEvaluator.findMatching(normalized, i, '(', ')');
    return close == normalized.length() - 1;
  }

  // =========================================================================
  // ComparisonExpr / StringComparisonExpr
  // =========================================================================

  @Override
  protected Object evalComparisonExpr(ComparisonExpr node) {
    Number left = evalBinaryAsNumber(node.left());
    Number right = evalBinaryAsNumber(node.right());
    String op = node.op() == null ? "" : node.op().strip();
    int compare = compareNumbers(left, right);
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

  /**
   * Compare two Numbers. Uses a fast path for same-typed primitives (float, double, int, long,
   * short, byte) to avoid the per-call {@code new BigDecimal(String.valueOf(...))} allocation in
   * {@link #toBigDecimal}. Mixed primitive types widen to double. BigDecimal/BigInteger and any
   * heterogeneous pairing fall back to the BigDecimal comparison, which preserves scale-aware
   * semantics.
   */
  private static int compareNumbers(Number left, Number right) {
    if (left instanceof Float lf && right instanceof Float rf) {
      return Float.compare(lf, rf);
    }
    if (left instanceof Double ld && right instanceof Double rd) {
      return Double.compare(ld, rd);
    }
    if (left instanceof Integer li && right instanceof Integer ri) {
      return Integer.compare(li, ri);
    }
    if (left instanceof Long ll && right instanceof Long rl) {
      return Long.compare(ll, rl);
    }
    if (left instanceof Short ls && right instanceof Short rs) {
      return Short.compare(ls, rs);
    }
    if (left instanceof Byte lb && right instanceof Byte rb) {
      return Byte.compare(lb, rb);
    }
    // Mixed primitive widening: both are primitive-boxed (no BigDecimal/BigInteger)
    if (isPrimitiveBoxed(left) && isPrimitiveBoxed(right)) {
      return Double.compare(left.doubleValue(), right.doubleValue());
    }
    return toBigDecimal(left).compareTo(toBigDecimal(right));
  }

  private static boolean isPrimitiveBoxed(Number n) {
    return n instanceof Float || n instanceof Double || n instanceof Integer || n instanceof Long
        || n instanceof Short || n instanceof Byte;
  }

  @Override
  protected Object evalStringComparisonExpr(StringComparisonExpr node) {
    String op = node.op() == null ? "" : node.op().strip();
    // node.left()/node.right() are the correctly-mapped operand nodes, so evaluate them
    // directly instead of re-splitting the source text by hand.
    String left = String.valueOf(eval(node.left()));
    String right = String.valueOf(eval(node.right()));
    return switch (op) {
      case "==" -> left.equals(right);
      case "!=" -> !left.equals(right);
      default -> false;
    };
  }

  // =========================================================================
  // IfExpr
  // =========================================================================

  @Override
  protected Object evalIfExpr(IfExpr node) {
    // Pure-AST evaluation. The former source-snippet shadow (tryEvaluateIfFromSource / P4IfSourceSupport,
    // a char-scanning re-parse of the original formula) was a correctness crutch from when the mapped
    // IfExpr sub-trees were unfaithful; with the mapper fixes (#43/#32/#35/#49) and memoization the
    // condition/branch nodes evaluate correctly on their own, so the shadow is removed (#49 follow-up).
    Object conditionValue = eval(node.condition());
    boolean cond = Boolean.TRUE.equals(toBoolean(conditionValue));
    ExpressionExpr branch = cond ? node.thenExpr() : node.elseExpr();
    return eval(branch);
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
    // [EXPERIMENT] ternary-arg source shadow removed; general P4 parse below handles it
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
    if (node instanceof ExternalBooleanInvocationExpr invocation) {
      return evaluateExternalInvocation(
          invocation.name(), invocation.args().map(ArgumentsExpr::values).orElseGet(List::of), expectedReturnType);
    }
    if (node instanceof ExternalNumberInvocationExpr invocation) {
      return evaluateExternalInvocation(
          invocation.name(), invocation.args().map(ArgumentsExpr::values).orElseGet(List::of), expectedReturnType);
    }
    if (node instanceof ExternalStringInvocationExpr invocation) {
      return evaluateExternalInvocation(
          invocation.name(), invocation.args().map(ArgumentsExpr::values).orElseGet(List::of), expectedReturnType);
    }
    if (node instanceof ExternalObjectInvocationExpr invocation) {
      return evaluateExternalInvocation(
          invocation.name(), invocation.args().map(ArgumentsExpr::values).orElseGet(List::of), expectedReturnType);
    }
    throw new UnsupportedOperationException("Unknown external invocation AST: " + node);
  }

  private Object evaluateExternalInvocation(String target,
      List<ArgumentExpressionExpr> args, ExpressionType expectedReturnType) {
    String name = target == null ? "" : target.strip();
    String qualifier = "";
    ImportTarget imported = imports.get(name);
    String className = normalizeQualifier(qualifier);
    String methodName = name;
    if (className.isEmpty() && imported != null && imported.methodName() != null) {
      className = imported.className();
      methodName = imported.methodName();
    } else if (!className.isEmpty()) {
      ImportTarget classImport = imports.get(className);
      if (classImport != null) {
        className = classImport.className();
      }
    }
    if (className.isEmpty()) {
      Optional<ImportTarget> qualified = qualifiedExternalTarget();
      if (qualified.isPresent()) {
        className = qualified.get().className();
        methodName = qualified.get().methodName();
      }
    }
    if (className.isEmpty()) {
      throw new UnsupportedOperationException("External target is not imported: " + name);
    }
    final String resolvedClassName = className;
    final String resolvedMethodName = methodName;

    ClassLoader effectiveClassLoader = classLoader != null
        ? classLoader : Thread.currentThread().getContextClassLoader();
    try {
      Class<?> clazz = Class.forName(resolvedClassName, true, effectiveClassLoader);
      List<Object> argValues = new java.util.ArrayList<>();
      List<ExpressionType> argTypes = new java.util.ArrayList<>();
      for (ArgumentExpressionExpr arg : args) {
        Object value = eval(arg);
        argValues.add(value);
        argTypes.add(expressionTypeOf(value));
      }
      java.lang.reflect.Method method = findExternalMethod(clazz, resolvedMethodName, argTypes, argValues);
      if (method == null) {
        throw new UnsupportedOperationException("Method not found: " + resolvedClassName + "#" + resolvedMethodName);
      }
      Object instance = context.getObject(clazz.getName(), Object.class)
          .orElseThrow(() -> new CalculationException(
              "class not found in CalculationContext. please set :" + clazz.getName()));
      Object result = method.invoke(instance, buildMethodParams(method, argValues));
      return result == null ? null : coerceToType(result, expectedReturnType);
    } catch (CalculationException | UnsupportedOperationException e) {
      throw e;
    } catch (ReflectiveOperationException e) {
      throw new UnsupportedOperationException(
          "External invocation failed: " + resolvedClassName + "#" + resolvedMethodName, e);
    }
  }

  private static String normalizeQualifier(String qualifier) {
    return qualifier == null ? "" : qualifier.strip();
  }

  private Optional<ImportTarget> qualifiedExternalTarget() {
    String source = effectiveLookupFormulaSource();
    if (source == null) return Optional.empty();
    int external = source.indexOf("external");
    int hash = source.indexOf('#', Math.max(0, external));
    if (hash < 0) return Optional.empty();
    int start = hash - 1;
    while (start >= 0) {
      char c = source.charAt(start);
      if (Character.isJavaIdentifierPart(c) || c == '.') start--;
      else break;
    }
    String className = source.substring(start + 1, hash);
    int methodEnd = hash + 1;
    while (methodEnd < source.length() && Character.isJavaIdentifierPart(source.charAt(methodEnd))) {
      methodEnd++;
    }
    String methodName = source.substring(hash + 1, methodEnd);
    return className.isBlank() || methodName.isBlank()
        ? Optional.empty() : Optional.of(new ImportTarget(className, methodName));
  }


  private ExpressionType expressionTypeOf(Object value) {
    if (value instanceof Boolean) return ExpressionTypes._boolean;
    if (value instanceof String) return ExpressionTypes.string;
    if (value instanceof Number) return numberType;
    return ExpressionTypes.object;
  }

  @Override
  protected Object evalArgumentsExpr(ArgumentsExpr node) {
    return node.values().stream().map(this::eval).toList();
  }

  private Object evaluateExternalInvocationFromSource(Object node, ExpressionType expectedReturnType) {
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
      Object instance = context.getObject(clazz.getName(), Object.class)
          .orElseThrow(() -> new CalculationException(
              "class not found in CalculationContext. please set :" + clazz.getName()));
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
    } catch (CalculationException | UnsupportedOperationException e) {
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
    String op = node.op() == null ? "==" : node.op().strip();
    // A declared-string operand (e.g. `var $name as string ...`) forces string equality on the
    // pure-AST path. The grammar maps `$a == $b` to BooleanEqualityExpr regardless of declared
    // type (declarations are dropped from the AST), so without this the operands would be coerced
    // to boolean — the legacy/source path consults the same declared type. (#32 / handoff #44 "C")
    if (isDeclaredStringEquality(node.left(), node.right())) {
      String left = stringValueOfEqualityOperand(node.left());
      String right = stringValueOfEqualityOperand(node.right());
      return switch (op) {
        case "==" -> left.equals(right);
        case "!=" -> !left.equals(right);
        default -> false;
      };
    }
    boolean left = resolveBooleanSourceOperand(node.left());
    boolean right = resolveBooleanSourceOperand(node.right());
    return switch (op) {
      case "==" -> left == right;
      case "!=" -> left != right;
      default -> false;
    };
  }

  /**
   * True when either operand is a bare variable reference declared as {@code string} in the
   * preamble. Mirrors the legacy {@code VariableTypeResolver}, which makes a comparison string-typed
   * when a declared-string variable participates.
   */
  private boolean isDeclaredStringEquality(Object left, Object right) {
    return isDeclaredStringOperand(left) || isDeclaredStringOperand(right);
  }

  private boolean isDeclaredStringOperand(Object operand) {
    if (declaredVariableTypes.isEmpty()) {
      return false;
    }
    String varName = equalityOperandVariableName(operand);
    if (varName == null) {
      return false;
    }
    ExpressionType declared = declaredVariableTypes.get(varName);
    return declared != null && declared.isString();
  }

  private String equalityOperandVariableName(Object operand) {
    if (operand instanceof VariableRefExpr varRef) {
      return resolveVariableRefName(varRef);
    }
    if (operand instanceof BinaryExpr binaryExpr) {
      return extractExactVariableReference(binaryExpr);
    }
    return null;
  }

  private String stringValueOfEqualityOperand(Object operand) {
    if (operand instanceof VariableRefExpr varRef) {
      String varName = resolveVariableRefName(varRef);
      Object resolved = varName == null ? null : resolveVariableAny(varName);
      return resolved == null ? "" : String.valueOf(resolved);
    }
    if (operand instanceof TinyExpressionP4AST ast) {
      Object resolved = eval(ast);
      return resolved == null ? "" : String.valueOf(resolved);
    }
    return operand == null ? "" : String.valueOf(operand);
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
    // #35: start/end/step are grammar-disambiguated index literals (SliceStartIndex /
    // SliceEndIndex / SliceStepIndex wrapper rules), so read them straight off the AST.
    // The former source-text colon splitting (P4SliceSourceSupport) is gone.
    Integer startValue = parseSliceIndex(node.start());
    Integer endValue = parseSliceIndex(node.end());
    Integer stepValue = parseSliceIndex(node.step());
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

  /** Slice indices are integer literals (optionally signed), per the SliceXxxIndex rules. */
  private static Integer parseSliceIndex(String index) {
    if (index == null) {
      return null;
    }
    String stripped = index.strip();
    if (stripped.isEmpty()) {
      return null;
    }
    return Integer.valueOf(stripped);
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
    String alias = node.alias();
    if (alias == null || alias.isBlank()) {
      alias = node.method().orElseGet(() -> {
        int dot = node.className().lastIndexOf('.');
        return dot < 0 ? node.className() : node.className().substring(dot + 1);
      });
    }
    imports.put(alias, new ImportTarget(node.className(), node.method().orElse(null)));
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


  /**
   * BooleanComparable は透過 mapped choice のため、operand は実 AST ノード（Object）または
   * source テキスト（String）として届く。ノードなら直接 eval（AST 経路に忠実）、
   * それ以外は従来の source-snippet 経路にフォールバックする。(tinyexpression #32)
   */
  private boolean resolveBooleanSourceOperand(Object operand) {
    if (operand instanceof TinyExpressionP4AST ast) {
      return Boolean.TRUE.equals(toBoolean(eval(ast)));
    }
    if (operand instanceof String text) {
      return resolveBooleanSourceOperand(text);
    }
    return operand != null && Boolean.TRUE.equals(toBoolean(operand));
  }

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

  private String extractExactVariableReference(BinaryExpr node) {
    if (node == null) {
      return null;
    }
    TinyExpressionP4AST left = node.left();
    List<String> op = node.op();
    List<TinyExpressionP4AST> right = node.right();
    if (left == null && right.isEmpty() && op.size() == 1) {
      String literal = op.get(0) == null ? "" : op.get(0).strip();
      return isExactVariableReference(literal) ? extractVariableName(literal) : null;
    }
    if (left instanceof BinaryExpr binaryLeft && op.isEmpty() && right.isEmpty()) {
      return extractExactVariableReference(binaryLeft);
    }
    if (left instanceof VariableRefExpr variableRef && op.isEmpty() && right.isEmpty()) {
      return resolveVariableRefName(variableRef);
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
