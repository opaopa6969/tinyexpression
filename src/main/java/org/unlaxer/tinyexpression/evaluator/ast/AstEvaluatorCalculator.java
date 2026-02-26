package org.unlaxer.tinyexpression.evaluator.ast;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

import org.unlaxer.Token;
import org.unlaxer.compiler.InstanceAndByteCode;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.CalculateResult;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.TokenBaseOperator;
import org.unlaxer.tinyexpression.evaluator.javacode.ClassNameAndByteCode;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeCalculatorV3;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.FormulaParser;
import org.unlaxer.util.digest.MD5;

/**
 * AST evaluator backend entry point.
 * <p>
 * Runtime policy:
 * 1) try AST-first execution,
 * 2) if unsupported, lazily fallback to JavaCode calculator.
 */
public class AstEvaluatorCalculator implements Calculator {

  private final Source source;
  private final ClassLoader classLoader;
  private final SpecifiedExpressionTypes specifiedExpressionTypes;

  private final String className;
  private final String javaCodeFromStore;
  private final byte[] byteCodeFromStore;
  private final String byteCodeHashFromStore;
  private final List<ClassNameAndByteCode> classNameAndByteCodeListFromStore;
  private final boolean createdFromByteCode;

  private volatile JavaCodeCalculatorV3 delegate;
  private final Map<String, Object> objectByKey = new LinkedHashMap<>();

  private final List<Calculator> dependsOns = new ArrayList<>();
  private volatile Optional<Calculator> dependsOnBy = Optional.empty();

  private final boolean generatedAstRuntimeAvailable;

  public AstEvaluatorCalculator(Source source, String className,
      SpecifiedExpressionTypes specifiedExpressionTypes, ClassLoader classLoader) {
    this.source = source;
    this.className = className;
    this.specifiedExpressionTypes = specifiedExpressionTypes;
    this.classLoader = classLoader;
    this.javaCodeFromStore = null;
    this.byteCodeFromStore = new byte[0];
    this.byteCodeHashFromStore = MD5.toHex(this.byteCodeFromStore);
    this.classNameAndByteCodeListFromStore = List.of();
    this.createdFromByteCode = false;
    this.generatedAstRuntimeAvailable = GeneratedAstRuntimeProbe.isAvailable(classLoader);
  }

  public AstEvaluatorCalculator(Source source, String javaCode, String className,
      SpecifiedExpressionTypes specifiedExpressionTypes, byte[] byteCode, String byteCodeHash,
      List<ClassNameAndByteCode> classNameAndByteCodeList, ClassLoader classLoader) {
    this.source = source;
    this.className = className;
    this.specifiedExpressionTypes = specifiedExpressionTypes;
    this.classLoader = classLoader;
    this.javaCodeFromStore = javaCode;
    this.byteCodeFromStore = byteCode == null ? new byte[0] : byteCode;
    this.byteCodeHashFromStore = byteCodeHash == null ? MD5.toHex(this.byteCodeFromStore) : byteCodeHash;
    this.classNameAndByteCodeListFromStore = classNameAndByteCodeList == null
        ? List.of() : List.copyOf(classNameAndByteCodeList);
    this.createdFromByteCode = true;
    this.generatedAstRuntimeAvailable = GeneratedAstRuntimeProbe.isAvailable(classLoader);
  }

  public boolean generatedAstRuntimeAvailable() {
    return generatedAstRuntimeAvailable;
  }

  private JavaCodeCalculatorV3 ensureDelegate() {
    JavaCodeCalculatorV3 local = delegate;
    if (local != null) {
      return local;
    }
    synchronized (this) {
      local = delegate;
      if (local != null) {
        return local;
      }
      if (createdFromByteCode) {
        local = new JavaCodeCalculatorV3(
            source,
            javaCodeFromStore == null ? "" : javaCodeFromStore,
            className,
            specifiedExpressionTypes,
            byteCodeFromStore,
            byteCodeHashFromStore,
            classNameAndByteCodeListFromStore,
            classLoader);
      } else {
        local = new JavaCodeCalculatorV3(source, className, specifiedExpressionTypes, classLoader);
      }
      local.dependsOns().addAll(dependsOns);
      dependsOnBy.ifPresent(local::setDependsOnBy);
      objectByKey.forEach(local::setObject);
      delegate = local;
      return local;
    }
  }

  @Override
  public InstanceKind instanceKind() {
    return source.formulaInfo().isPresent() ? InstanceKind.fromFormulaInfo : InstanceKind.fromSource;
  }

  @Override
  public ExpressionType resultType() {
    return specifiedExpressionTypes.resultType();
  }

  @Override
  public Parser getParser() {
    return Parser.get(FormulaParser.class);
  }

  @Override
  public TokenBaseOperator<CalculationContext> getCalculatorOperator() {
    return (context, token) -> apply(context);
  }

  @Override
  public UnaryOperator<Token> tokenReduer() {
    return UnaryOperator.identity();
  }

  @Override
  public Source source() {
    return source;
  }

  @Override
  public String returningTypeAsString() {
    return resultType().javaTypeAsString();
  }

  @Override
  public String javaCode() {
    return javaCodeFromStore == null ? "/* AST_EVALUATOR */" : javaCodeFromStore;
  }

  @Override
  public String formula() {
    return source.source();
  }

  @Override
  public byte[] byteCode() {
    return byteCodeFromStore;
  }

  @Override
  public String formulaHash() {
    return MD5.toHex(formula());
  }

  @Override
  public String byteCodeHash() {
    return byteCodeHashFromStore;
  }

  @Override
  public List<Calculator> dependsOns() {
    return dependsOns;
  }

  @Override
  public Optional<Calculator> dependsOnBy() {
    return dependsOnBy;
  }

  @Override
  public void before(CalculationContext calculationContext) {
    // no-op for AST path; delegate will handle this on fallback if needed
  }

  @Override
  public Object apply(CalculationContext calculationContext) {
    Optional<Object> tokenAstEvaluated = Optional.empty();
    if (generatedAstRuntimeAvailable) {
      boolean declarationsApplied = false;
      for (String preferredAstSimpleName : preferredAstSimpleNames()) {
        Optional<Object> mapped = GeneratedAstRuntimeProbe.tryMapAst(
            source.source(), classLoader, preferredAstSimpleName);
        if (mapped.isEmpty()) {
          continue;
        }
        setObject("_astEvaluatorMappedAst", mapped.get());
        setObject("_astEvaluatorGeneratedAstNodeCount", GeneratedP4NumberAstEvaluator.countAstNodes(mapped.get()));
        Optional<Object> generatedAstEvaluated = GeneratedP4ValueAstEvaluator.tryEvaluate(
            mapped.get(), specifiedExpressionTypes, calculationContext, classLoader, source.source());
        if (generatedAstEvaluated.isEmpty() && !declarationsApplied) {
          AstDeclarationRuntime.applyDeclarations(source.source(), specifiedExpressionTypes, calculationContext, classLoader);
          declarationsApplied = true;
          generatedAstEvaluated = GeneratedP4ValueAstEvaluator.tryEvaluate(
              mapped.get(), specifiedExpressionTypes, calculationContext, classLoader, source.source());
        }
        ExpressionType evaluatedResultType = resultType();
        if (generatedAstEvaluated.isPresent() && evaluatedResultType != null && evaluatedResultType.isNumber()) {
          tokenAstEvaluated = AstNumberExpressionEvaluator.tryEvaluate(
              source.source(), specifiedExpressionTypes, calculationContext);
          if (tokenAstEvaluated.isPresent()
              && generatedAstEvaluated.get() instanceof Number generatedNumber
              && tokenAstEvaluated.get() instanceof Number tokenNumber
              && !numbersEquivalent(generatedNumber, tokenNumber)) {
            generatedAstEvaluated = Optional.empty();
          }
        }
        if (generatedAstEvaluated.isPresent()) {
          setObject("_astEvaluatorRuntime", "generated-ast");
          setObject("_astEvaluatorMapperAvailable", true);
          return generatedAstEvaluated.get();
        }
      }
      setObject("_astEvaluatorMapperAvailable", true);
    } else {
      setObject("_astEvaluatorMapperAvailable", false);
    }

    Optional<Object> simpleLiteralOrVariable = tryEvaluateSimpleLiteralOrVariable(calculationContext);
    if (simpleLiteralOrVariable.isPresent()) {
      setObject("_astEvaluatorRuntime", "token-ast");
      return simpleLiteralOrVariable.get();
    }

    if (shouldTryDeclarationShortcut(source.source())) {
      Optional<AstDeclarationRuntime.MainExpressionEvaluation> declarationEvaluated =
          AstDeclarationRuntime.tryEvaluateMainExpression(
              source.source(), specifiedExpressionTypes, calculationContext, classLoader);
      if (declarationEvaluated.isPresent()) {
        setObject("_astEvaluatorRuntime", declarationEvaluated.get().runtime());
        return declarationEvaluated.get().value();
      }
    }

    Optional<Object> astEvaluated = tokenAstEvaluated.isPresent()
        ? tokenAstEvaluated
        : AstNumberExpressionEvaluator.tryEvaluate(source.source(), specifiedExpressionTypes, calculationContext);
    if (astEvaluated.isPresent()) {
      setObject("_astEvaluatorRuntime", "token-ast");
      return astEvaluated.get();
    }

    setObject("_astEvaluatorRuntime", "javacode-fallback");
    return ensureDelegate().apply(calculationContext);
  }

  private Optional<Object> tryEvaluateSimpleLiteralOrVariable(CalculationContext calculationContext) {
    String formula = source.source() == null ? "" : source.source().strip();
    if (formula.isEmpty()) {
      return Optional.empty();
    }
    if (formula.startsWith("$")) {
      return resolveVariable(extractVariableName(formula), calculationContext);
    }
    if (containsExpressionSyntax(formula)) {
      return Optional.empty();
    }
    ExpressionType resultType = resultType();
    if (resultType != null && resultType.isString()) {
      return parseStringLiteral(formula).map(v -> (Object) v);
    }
    if (resultType != null && resultType.isBoolean()) {
      return parseBooleanLiteral(formula).map(v -> (Object) v);
    }
    if (resultType != null && resultType.isNumber()) {
      return parseNumberLiteral(formula, resultType).map(v -> (Object) v);
    }
    if (resultType != null && resultType.isObject()) {
      Optional<String> string = parseStringLiteral(formula);
      if (string.isPresent()) {
        return string.map(v -> (Object) v);
      }
      Optional<Boolean> bool = parseBooleanLiteral(formula);
      if (bool.isPresent()) {
        return bool.map(v -> (Object) v);
      }
      Optional<Number> number = parseNumberLiteral(formula, specifiedExpressionTypes.numberType());
      if (number.isPresent()) {
        return number.map(v -> (Object) v);
      }
    }
    return Optional.empty();
  }

  private Optional<Object> resolveVariable(String variableName, CalculationContext calculationContext) {
    if (variableName == null || variableName.isBlank()) {
      return Optional.empty();
    }
    ExpressionType resultType = resultType();
    if (resultType != null && resultType.isNumber()) {
      return calculationContext.getNumber(variableName).map(v -> (Object) v);
    }
    if (resultType != null && resultType.isBoolean()) {
      return calculationContext.getBoolean(variableName).map(v -> (Object) v);
    }
    if (resultType != null && resultType.isString()) {
      return calculationContext.getString(variableName).map(v -> (Object) v);
    }
    Optional<? extends Number> number = calculationContext.getNumber(variableName);
    if (number.isPresent()) {
      return number.map(v -> (Object) v);
    }
    Optional<String> string = calculationContext.getString(variableName);
    if (string.isPresent()) {
      return string.map(v -> (Object) v);
    }
    Optional<Boolean> bool = calculationContext.getBoolean(variableName);
    if (bool.isPresent()) {
      return bool.map(v -> (Object) v);
    }
    return calculationContext.getObject(variableName, Object.class).map(v -> (Object) v);
  }

  private Optional<String> parseStringLiteral(String formula) {
    if (formula.length() < 2) {
      return Optional.empty();
    }
    char start = formula.charAt(0);
    char end = formula.charAt(formula.length() - 1);
    if ((start == '\'' && end == '\'') || (start == '"' && end == '"')) {
      return Optional.of(formula.substring(1, formula.length() - 1));
    }
    return Optional.empty();
  }

  private Optional<Boolean> parseBooleanLiteral(String formula) {
    if ("true".equalsIgnoreCase(formula)) {
      return Optional.of(true);
    }
    if ("false".equalsIgnoreCase(formula)) {
      return Optional.of(false);
    }
    return Optional.empty();
  }

  private Optional<Number> parseNumberLiteral(String formula, ExpressionType preferredType) {
    if (formula.isEmpty()) {
      return Optional.empty();
    }
    ExpressionType numberType = preferredType;
    if (numberType == null || !numberType.isNumber() || numberType == org.unlaxer.tinyexpression.parser.ExpressionTypes.number) {
      numberType = specifiedExpressionTypes.numberType();
    }
    if (numberType == null || !numberType.isNumber() || numberType == org.unlaxer.tinyexpression.parser.ExpressionTypes.number) {
      numberType = org.unlaxer.tinyexpression.parser.ExpressionTypes._float;
    }
    try {
      return Optional.of(numberType.parseNumber(formula));
    } catch (Throwable ignored) {
      return Optional.empty();
    }
  }

  private boolean containsExpressionSyntax(String formula) {
    for (int i = 0; i < formula.length(); i++) {
      char c = formula.charAt(i);
      if (Character.isWhitespace(c)) {
        continue;
      }
      if (Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '\'' || c == '"' || c == '$') {
        continue;
      }
      if (c == '-' && i + 1 < formula.length() && Character.isDigit(formula.charAt(i + 1))) {
        continue;
      }
      return true;
    }
    return false;
  }

  private boolean shouldTryDeclarationShortcut(String formula) {
    if (formula == null || formula.isBlank()) {
      return false;
    }
    String normalized = formula.strip();
    String trimmedLeading = trimLeadingJavaDelimiters(normalized);
    if (trimmedLeading.contains("/*") || trimmedLeading.contains("//")) {
      return false;
    }
    return trimmedLeading.contains(";") || trimmedLeading.contains("\n");
  }

  private String trimLeadingJavaDelimiters(String source) {
    if (source == null || source.isEmpty()) {
      return "";
    }
    int i = 0;
    while (i < source.length()) {
      char c = source.charAt(i);
      if (Character.isWhitespace(c)) {
        i++;
        continue;
      }
      if (c == '/' && i + 1 < source.length()) {
        char next = source.charAt(i + 1);
        if (next == '/') {
          i += 2;
          while (i < source.length() && source.charAt(i) != '\n') {
            i++;
          }
          continue;
        }
        if (next == '*') {
          int end = source.indexOf("*/", i + 2);
          if (end < 0) {
            return "";
          }
          i = end + 2;
          continue;
        }
      }
      break;
    }
    return i == 0 ? source : source.substring(i);
  }

  private String extractVariableName(String formula) {
    if (formula == null || formula.isEmpty()) {
      return null;
    }
    String text = formula.strip();
    if (!text.startsWith("$")) {
      return text;
    }
    int end = 1;
    while (end < text.length()) {
      char c = text.charAt(end);
      if (Character.isLetterOrDigit(c) || c == '_') {
        end++;
      } else {
        break;
      }
    }
    return end > 1 ? text.substring(1, end) : null;
  }

  @Override
  public void after(CalculationContext calculationContext) {
  }

  @Override
  public void setObject(String key, Object object) {
    objectByKey.put(key, object);
    JavaCodeCalculatorV3 local = delegate;
    if (local != null) {
      local.setObject(key, object);
    }
  }

  @Override
  public <X> X getObject(String key, Class<X> objectClass) {
    Object local = objectByKey.get(key);
    if (local != null) {
      return objectClass.cast(local);
    }
    JavaCodeCalculatorV3 delegateLocal = delegate;
    if (delegateLocal == null) {
      return null;
    }
    return delegateLocal.getObject(key, objectClass);
  }

  @Override
  public CreatedFrom createdFrom() {
    return createdFromByteCode ? CreatedFrom.byteCode : CreatedFrom.formula;
  }

  @Override
  public void setDependsOnBy(Calculator calculator) {
    dependsOnBy = Optional.ofNullable(calculator);
    if (delegate != null && calculator != null) {
      delegate.setDependsOnBy(calculator);
    }
  }

  @Override
  public List<InstanceAndByteCode> instanceAndByteCodeList() {
    return List.of();
  }

  @Override
  public CalculateResult calculate(CalculationContext calculateContext, String formula, ExpressionType resultType) {
    return ensureDelegate().calculate(calculateContext, formula, resultType);
  }

  private List<String> preferredAstSimpleNames() {
    List<String> preferred = new ArrayList<>();
    String formula = source.source() == null ? "" : source.source().strip();
    boolean methodInvocationHead = AstEmbeddedExpressionRuntime.hasMethodInvocationHead(formula);
    boolean ifHead = AstEmbeddedExpressionRuntime.hasIfHead(formula);
    boolean matchHead = AstEmbeddedExpressionRuntime.hasMatchHead(formula);
    if (methodInvocationHead) {
      preferred.add("MethodInvocationExpr");
    }
    if (ifHead) {
      preferred.add("IfExpr");
    }
    ExpressionType type = resultType();
    if (type == null) {
      preferred.add(null);
      return preferred;
    }
    if (type.isNumber()) {
      if (matchHead) {
        preferred.add("NumberMatchExpr");
      }
      preferred.add("BinaryExpr");
    } else if (type.isString()) {
      if (matchHead) {
        preferred.add("StringMatchExpr");
      }
      preferred.add("StringExpr");
    } else if (type.isBoolean()) {
      if (matchHead) {
        preferred.add("BooleanMatchExpr");
      }
      preferred.add("BooleanExpr");
    } else if (type.isObject()) {
      if (matchHead) {
        preferred.add("StringMatchExpr");
        preferred.add("BooleanMatchExpr");
        preferred.add("NumberMatchExpr");
      }
      preferred.add("ObjectExpr");
    } else {
      preferred.add(null);
    }
    preferred.add("MethodInvocationExpr");
    preferred.add("VariableRefExpr");
    preferred.add("BinaryExpr");
    return preferred.stream().distinct().toList();
  }

  private boolean numbersEquivalent(Number left, Number right) {
    BigDecimal l = toBigDecimal(left);
    BigDecimal r = toBigDecimal(right);
    return l.compareTo(r) == 0;
  }

  private BigDecimal toBigDecimal(Number value) {
    if (value == null) {
      return BigDecimal.ZERO;
    }
    if (value instanceof BigDecimal decimal) {
      return decimal;
    }
    return new BigDecimal(value.toString());
  }
}
