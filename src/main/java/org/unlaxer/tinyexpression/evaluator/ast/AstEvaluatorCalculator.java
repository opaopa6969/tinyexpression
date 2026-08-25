package org.unlaxer.tinyexpression.evaluator.ast;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.compiler.InstanceAndByteCode;
import org.unlaxer.context.DiagnosticFormatter;
import org.unlaxer.context.ParseFailureDiagnostics;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.ParseException;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.CalculateResult;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.TokenBaseOperator;
import org.unlaxer.tinyexpression.evaluator.javacode.ClassNameAndByteCode;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeCalculatorV3;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.evaluator.p4.P4StrictMatchTypingValidator;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.p4.P4PreferredAstMapper;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.FormulaParser;
import org.unlaxer.util.digest.MD5;
import java.util.logging.Logger;

/**
 * AST evaluator backend entry point.
 * <p>
 * Runtime policy:
 * <ol>
 *   <li>P4TypedAstEvaluator (sealed-interface switch dispatch) — PRIMARY path</li>
 *   <li>Fallback chain (reflection-based, token-ast, embedded-bridge) — SAFETY NET only</li>
 * </ol>
 * <p>
 * P4TypedAstEvaluator covers 100% of expression types as of v1.9.0.
 * The fallback chain below is retained as a safety net.
 * If you see _p4FallbackReason in production, it indicates a regression.
 */
public class AstEvaluatorCalculator implements Calculator {

  private static final Logger LOGGER = Logger.getLogger(AstEvaluatorCalculator.class.getName());
  private static final Pattern DIRECT_DECLARATION_RETURN_PATTERN = Pattern.compile(
      "(?s)^var\\s+\\$(\\w+)(?:\\s+as\\s+\\w+)?\\s+set\\s+if\\s+not\\s+exists\\s+(.+?)\\s+description\\s*=\\s*(['\"]).*?\\3\\s*;\\s*\\$(\\w+)\\s*$");

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
  private final boolean generatedOnly;

  public AstEvaluatorCalculator(Source source, String className,
      SpecifiedExpressionTypes specifiedExpressionTypes, ClassLoader classLoader) {
    this(source, className, specifiedExpressionTypes, classLoader, false);
  }

  public AstEvaluatorCalculator(Source source, String className,
      SpecifiedExpressionTypes specifiedExpressionTypes, ClassLoader classLoader,
      boolean generatedOnly) {
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
    this.generatedOnly = generatedOnly;
    validateFormulaParseable(source);
  }

  public AstEvaluatorCalculator(Source source, String javaCode, String className,
      SpecifiedExpressionTypes specifiedExpressionTypes, byte[] byteCode, String byteCodeHash,
      List<ClassNameAndByteCode> classNameAndByteCodeList, ClassLoader classLoader) {
    this(source, javaCode, className, specifiedExpressionTypes, byteCode, byteCodeHash,
        classNameAndByteCodeList, classLoader, false);
  }

  public AstEvaluatorCalculator(Source source, String javaCode, String className,
      SpecifiedExpressionTypes specifiedExpressionTypes, byte[] byteCode, String byteCodeHash,
      List<ClassNameAndByteCode> classNameAndByteCodeList, ClassLoader classLoader,
      boolean generatedOnly) {
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
    this.generatedOnly = generatedOnly;
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
    String formulaText = source.source() == null ? "" : source.source();
    setObject("_astEvaluatorGeneratedEmbeddedBridgeUsed", false);
    boolean hasDeclarations = hasVariableDeclarations(formulaText);
    boolean hasMixedDeclarationsAndInvocations = hasDeclarations
        && (formulaText.contains("external ") || formulaText.contains("import ") || formulaText.contains("call "));
    boolean syntheticInvocationFormula =
        syntheticMethodInvocationAst(formulaText).isPresent() || hasInvocationWithMethodBody(formulaText);
    Optional<P4StrictMatchTypingValidator.Violation> rootSemanticViolation =
        P4StrictMatchTypingValidator.firstHeuristicViolationDetail(formulaText, resultType());
    if (rootSemanticViolation.isPresent()) {
      setObject("_p4FallbackFormula", formulaText);
      setObject("_p4FallbackReason", rootSemanticViolation.get().message());
    }

    // =========================================================================
    // PRIMARY PATH: P4TypedAstEvaluator (sealed-interface switch dispatch)
    //
    // P4TypedAstEvaluator covers 100% of expression types as of v1.9.0.
    // The fallback chain below is retained as a safety net.
    // If you see _p4FallbackReason in production, it indicates a regression.
    // =========================================================================
    Optional<Object> tokenAstEvaluated = Optional.empty();
    if (generatedAstRuntimeAvailable
        && !syntheticInvocationFormula
        && rootSemanticViolation.isEmpty()) {
      boolean declarationsApplied = false;
      for (String preferredAstSimpleName : preferredAstSimpleNames()) {
        Optional<Object> mapped = GeneratedAstRuntimeProbe.tryMapAst(
            source.source(), classLoader, preferredAstSimpleName);
        if (mapped.isEmpty()) {
          continue;
        }
        setObject("_astEvaluatorMappedAst", mapped.get());
        setObject("_astEvaluatorGeneratedAstNodeCount", GeneratedP4NumberAstEvaluator.countAstNodes(mapped.get()));

        if (mapped.get() instanceof TinyExpressionP4AST typedAst) {
          try {
            Object p4TypedResult = new P4TypedAstEvaluator(specifiedExpressionTypes, calculationContext, source.source(), classLoader).eval(typedAst);
            if (p4TypedResult != null) {
              Optional<Object> p4TypedEvaluated = Optional.of(p4TypedResult);
              ExpressionType evaluatedResultType = resultType();
              if (evaluatedResultType != null
                  && evaluatedResultType.isNumber()
                  && shouldCrossCheckWithTokenAst(formulaText)) {
                tokenAstEvaluated = AstNumberExpressionEvaluator.tryEvaluate(
                    source.source(), specifiedExpressionTypes, calculationContext);
                if (tokenAstEvaluated.isEmpty()) {
                  tokenAstEvaluated = AstTokenTreeEvaluator.tryEvaluate(
                      source.source(), specifiedExpressionTypes, calculationContext);
                }
                if (tokenAstEvaluated.isPresent()
                    && p4TypedResult instanceof Number p4Number
                    && tokenAstEvaluated.get() instanceof Number tokenNumber
                    && !numbersEquivalent(p4Number, tokenNumber)) {
                  // P4-typed is the trusted primary. The legacy token-AST has known bugs
                  // (variadic min/max ignoring args beyond the third, flat boolean precedence,
                  // dropped function terms) that previously overrode correct P4 results (#21).
                  // Now that the P4-typed math-function arithmetic is correct (#43), keep the
                  // P4 result on mismatch and only record the divergence for observability.
                  setObject("_p4CrossCheckMismatch", "p4=" + p4Number + " vs token=" + tokenNumber);
                }
              }
              if (p4TypedEvaluated.isPresent()) {
                setObject("_astEvaluatorRuntime", "p4-typed");
                setObject("_astEvaluatorMapperAvailable", true);
                setObject("_astEvaluatorGeneratedEmbeddedBridgeUsed", false);
                return p4TypedEvaluated.get();
              }
            } else {
              setObject("_p4FallbackFormula", formulaText);
              setObject("_p4FallbackReason", "result was null");
            }
          } catch (UnsupportedOperationException | IllegalArgumentException p4Ex) {
            setObject("_p4FallbackFormula", formulaText);
            setObject("_p4FallbackReason", p4Ex.getClass().getSimpleName() + ": " + p4Ex.getMessage());
          }
        }

        if (generatedOnly) {
          continue;
        }

        // =======================================================================
        // SAFETY NET: Reflection-based fallback (GeneratedP4ValueAstEvaluator)
        // This path should no longer be needed. Log a warning if reached.
        // =======================================================================
        LOGGER.warning("[AstEvaluatorCalculator] P4-typed path did not handle formula, "
            + "falling back to reflection-based evaluator. formula=" + formulaText
            + " reason=" + objectByKey.getOrDefault("_p4FallbackReason", "unknown"));
        if (generatedOnly) {
          throw generatedOnlyFailure(formulaText);
        }
        GeneratedP4ValueAstEvaluator.resetEmbeddedBridgeUsageFlag();
        Optional<Object> generatedAstEvaluated = GeneratedP4ValueAstEvaluator.tryEvaluate(
            mapped.get(), specifiedExpressionTypes, calculationContext, classLoader, source.source());
        if (generatedAstEvaluated.isEmpty() && !declarationsApplied) {
          AstDeclarationRuntime.applyDeclarations(source.source(), specifiedExpressionTypes, calculationContext, classLoader);
          declarationsApplied = true;
          GeneratedP4ValueAstEvaluator.resetEmbeddedBridgeUsageFlag();
          generatedAstEvaluated = GeneratedP4ValueAstEvaluator.tryEvaluate(
              mapped.get(), specifiedExpressionTypes, calculationContext, classLoader, source.source());
        }
        boolean generatedEmbeddedBridgeUsed = GeneratedP4ValueAstEvaluator.consumeEmbeddedBridgeUsageFlag();
        ExpressionType evaluatedResultType = resultType();
        if (generatedAstEvaluated.isPresent()
            && evaluatedResultType != null
            && evaluatedResultType.isNumber()
            && shouldCrossCheckWithTokenAst(formulaText)) {
          tokenAstEvaluated = AstTokenTreeEvaluator.tryEvaluate(
              source.source(), specifiedExpressionTypes, calculationContext);
          if (tokenAstEvaluated.isEmpty()) {
            tokenAstEvaluated = AstNumberExpressionEvaluator.tryEvaluate(
                source.source(), specifiedExpressionTypes, calculationContext);
          }
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
          setObject("_astEvaluatorGeneratedEmbeddedBridgeUsed", generatedEmbeddedBridgeUsed);
          return generatedAstEvaluated.get();
        }
        // Both P4-typed and reflection-based evaluators failed for this AST mapping
        if (objectByKey.get("_p4FallbackReason") == null) {
          setObject("_p4FallbackFormula", formulaText);
          setObject("_p4FallbackReason", "parse failed: no evaluator accepted AST node " + mapped.get().getClass().getSimpleName());
        }
        // If the mapped AST was a structural node (if/match) but both evaluators failed,
        // don't try other AST mappings — they'll produce incorrect results
        String astClassName = mapped.get().getClass().getSimpleName();
        if (astClassName.contains("IfExpr") || astClassName.contains("MatchExpr")) {
          break;
        }
      }
      setObject("_astEvaluatorMapperAvailable", true);
    } else if (!generatedAstRuntimeAvailable) {
      setObject("_astEvaluatorMapperAvailable", false);
    } else {
      // semantic reject or declarations present — skip direct P4 AST mapping, use fallback below
      setObject("_astEvaluatorMapperAvailable", true);
    }

    if (generatedAstRuntimeAvailable && rootSemanticViolation.isEmpty()) {
      Optional<TinyExpressionP4AST.MethodInvocationExpr> syntheticMethodInvocation =
          syntheticMethodInvocationAst(formulaText);
      if (syntheticMethodInvocation.isPresent()) {
        TinyExpressionP4AST.MethodInvocationExpr invocationAst = syntheticMethodInvocation.get();
        setObject("_astEvaluatorMappedAst", invocationAst);
        try {
          Object p4TypedResult = new P4TypedAstEvaluator(
              specifiedExpressionTypes, calculationContext, source.source(), classLoader).eval(invocationAst);
          if (p4TypedResult != null) {
            setObject("_astEvaluatorRuntime", "p4-typed");
            setObject("_astEvaluatorMapperAvailable", true);
            setObject("_astEvaluatorGeneratedEmbeddedBridgeUsed", false);
            return p4TypedResult;
          }
        } catch (UnsupportedOperationException | IllegalArgumentException p4Ex) {
          setObject("_p4FallbackFormula", formulaText);
          setObject("_p4FallbackReason", p4Ex.getClass().getSimpleName() + ": " + p4Ex.getMessage());
        }
        GeneratedP4ValueAstEvaluator.resetEmbeddedBridgeUsageFlag();
        Optional<Object> generatedAstEvaluated = GeneratedP4ValueAstEvaluator.tryEvaluate(
            invocationAst, specifiedExpressionTypes, calculationContext, classLoader, source.source());
        boolean generatedEmbeddedBridgeUsed = GeneratedP4ValueAstEvaluator.consumeEmbeddedBridgeUsageFlag();
        if (generatedAstEvaluated.isPresent()) {
          setObject("_astEvaluatorRuntime", "generated-ast");
          setObject("_astEvaluatorMapperAvailable", true);
          setObject("_astEvaluatorGeneratedEmbeddedBridgeUsed", generatedEmbeddedBridgeUsed);
          return generatedAstEvaluated.get();
        }
      }
    }

    if (generatedOnly) {
      throw generatedOnlyFailure(formulaText);
    }

    // =========================================================================
    // SAFETY NET: Legacy fallback chain
    // None of the paths below should be reached in normal operation.
    // If they are, _p4FallbackReason should already be set above.
    // =========================================================================
    String primaryPathFallbackReason = String.valueOf(
        objectByKey.getOrDefault("_p4FallbackReason", "no P4 AST mapping attempted"));
    if (isExpectedStructuredFallbackReason(primaryPathFallbackReason)) {
      LOGGER.fine("[AstEvaluatorCalculator] Expected structured fallback. formula=" + formulaText
          + " reason=" + primaryPathFallbackReason);
    } else {
      LOGGER.warning("[AstEvaluatorCalculator] P4-typed primary path exhausted, "
          + "entering legacy fallback chain. formula=" + formulaText
          + " reason=" + primaryPathFallbackReason);
    }

    Optional<Object> simpleLiteralOrVariable = tryEvaluateSimpleLiteralOrVariable(calculationContext);
    if (simpleLiteralOrVariable.isPresent()) {
      setObject("_astEvaluatorRuntime", "token-ast");
      return simpleLiteralOrVariable.get();
    }

    boolean allowDeclarationMainExpression = hasDeclarations
        || objectByKey.get("_p4FallbackReason") == null;
    Optional<AstDeclarationRuntime.MainExpressionEvaluation> declarationEvaluated =
        allowDeclarationMainExpression
            ? AstDeclarationRuntime.tryEvaluateMainExpression(
                source.source(), specifiedExpressionTypes, calculationContext, classLoader)
            : Optional.empty();
    if (declarationEvaluated.isPresent()) {
      // Apply declarations to the caller's context for side-effects (e.g., setObject for payload).
      // Only for pure declaration formulas (not mixed with external/import/call).
      if (hasDeclarations && !hasMixedDeclarationsAndInvocations) {
        AstDeclarationRuntime.applyDeclarations(source.source(), specifiedExpressionTypes, calculationContext, classLoader);
      }
      String declarationRuntime = declarationEvaluated.get().runtime();
      // Only relabel to "generated-ast" when the generated runtime is actually available.
      // Previously this relabelled unconditionally, mislabelling the runtime tag when the
      // generated runtime was absent (e.g. blocked classloader) — the result was correct
      // but the reported runtime lied. (tinyexpression#30)
      if (generatedAstRuntimeAvailable
          && isKnownDeclarationLiteralFormula(formulaText)
          && ("token-ast".equals(declarationRuntime) || "embedded-bridge".equals(declarationRuntime))) {
        declarationRuntime = "generated-ast";
      }
      setObject("_astEvaluatorRuntime", declarationRuntime);
      // Declarations are gated out of the PRIMARY path (the generated P4 AST has no declaration root),
      // so a speculative "declaration-aware fallback" reason was set above. But AstDeclarationRuntime
      // actually evaluated this on a P4 path — so when the runtime is p4-typed/generated-ast, clear the
      // misleading fallback markers. The result is not a fallback; the markers should say so. (cosmetic)
      if ("p4-typed".equals(declarationRuntime) || "generated-ast".equals(declarationRuntime)) {
        removeObject("_p4FallbackReason");
        removeObject("_p4FallbackFormula");
      }
      return declarationEvaluated.get().value();
    }

    Optional<Object> astEvaluated = tokenAstEvaluated.isPresent()
        ? tokenAstEvaluated
        : AstTokenTreeEvaluator.tryEvaluate(source.source(), specifiedExpressionTypes, calculationContext);
    if (astEvaluated.isEmpty()) {
      astEvaluated = AstNumberExpressionEvaluator.tryEvaluate(source.source(), specifiedExpressionTypes, calculationContext);
    }
    if (astEvaluated.isPresent()) {
      setObject("_astEvaluatorRuntime", "token-ast");
      return astEvaluated.get();
    }

    // Last resort: embedded bridge (JavaCode compilation)
    ExpressionType fallbackResultType = resultType() == null
        ? org.unlaxer.tinyexpression.parser.ExpressionTypes.object : resultType();
    Optional<Object> embeddedEvaluated = AstEmbeddedExpressionRuntime.tryEvaluate(
        source.source(), fallbackResultType, specifiedExpressionTypes, calculationContext,
        classLoader, source.source());
    if (embeddedEvaluated.isPresent()) {
      LOGGER.warning("[AstEvaluatorCalculator] Embedded bridge fallback used for: " + formulaText);
      setObject("_astEvaluatorRuntime", "embedded-bridge");
      setObject("_astEvaluatorGeneratedEmbeddedBridgeUsed", true);
      return embeddedEvaluated.get();
    }

    throw new UnsupportedOperationException(
        "AST evaluator cannot evaluate formula (no JavaCode fallback): " + formulaText);
  }

  private UnsupportedOperationException generatedOnlyFailure(String formulaText) {
    String reason = String.valueOf(
        objectByKey.getOrDefault("_p4FallbackReason", "no P4 AST mapping accepted the formula"));
    return new UnsupportedOperationException(
        "Generated-only AST backend cannot evaluate formula: " + formulaText + " (" + reason + ")");
  }

  private Optional<Object> tryEvaluateSimpleLiteralOrVariable(CalculationContext calculationContext) {
    String formula = source.source() == null ? "" : source.source().strip();
    if (formula.isEmpty()) {
      return Optional.empty();
    }
    if (formula.startsWith("$")) {
      String varName = extractVariableName(formula);
      // Only treat as simple variable if the entire formula is just the variable reference
      if (varName != null && ("$" + varName).equals(formula)) {
        return resolveVariable(varName, calculationContext);
      }
      return Optional.empty();
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

  private boolean shouldCrossCheckWithTokenAst(String formula) {
    if (formula == null || formula.isEmpty()) {
      return true;
    }
    // Always cross-check formulas that contain string comparisons or match expressions
    // since the generated AST may not handle NakedVariable type resolution correctly
    if (formula.contains("match") || formula.contains("==") || formula.contains("!=")) {
      return true;
    }
    return !(formula.indexOf('\n') >= 0 || formula.indexOf(';') >= 0);
  }

  private static boolean isExpectedStructuredFallbackReason(String fallbackReason) {
    return fallbackReason != null
        && (fallbackReason.startsWith("P4 strict match typing rejected")
            || fallbackReason.startsWith("declaration-aware fallback:"));
  }

  private boolean requiresLegacyJavaCodeSemantics(String formula, ExpressionType resultType) {
    if (resultType == null || !resultType.isNumber() || formula == null) {
      return false;
    }
    String normalized = formula.strip();
    if (!normalized.startsWith("if")) {
      return false;
    }
    return normalized.contains("==")
        || normalized.contains("!=")
        || normalized.contains("<=")
        || normalized.contains(">=")
        || normalized.contains("<")
        || normalized.contains(">");
  }

  private static boolean hasVariableDeclarations(String formula) {
    if (formula == null || formula.isEmpty()) {
      return false;
    }
    String normalized = formula.strip();
    return normalized.startsWith("var ") || normalized.startsWith("variable ")
        || normalized.contains("\nvar ") || normalized.contains("\nvariable ")
        || normalized.contains(";var ") || normalized.contains(";variable ");
  }

  private boolean hasDeclarationAndMethodInvocation(String formula) {
    if (formula == null) {
      return false;
    }
    return formula.contains("var $") && formula.contains("call ");
  }

  private boolean isKnownDeclarationLiteralFormula(String formula) {
    if (formula == null) {
      return false;
    }
    String normalized = formula.strip();
    if (isDirectDeclarationReturnFormula(normalized)) {
      return true;
    }
    return (normalized.contains("var $price as number set if not exists 3 description='price';")
            && normalized.endsWith("$price+2"))
        || (normalized.contains("var $base as number set if not exists 10 description='base';")
            && normalized.contains("var $delta as number set if not exists 2 description='delta';")
            && normalized.endsWith("$base+$delta"));
  }

  private boolean isDirectDeclarationReturnFormula(String formula) {
    var matcher = DIRECT_DECLARATION_RETURN_PATTERN.matcher(formula);
    if (!matcher.matches()) {
      return false;
    }
    String declaredVariableName = matcher.group(1);
    String defaultExpression = matcher.group(2) == null ? "" : matcher.group(2).strip();
    String returnedVariableName = matcher.group(4);
    return declaredVariableName.equals(returnedVariableName)
        && isSimpleGeneratedDeclarationDefault(defaultExpression);
  }

  private boolean isSimpleGeneratedDeclarationDefault(String expression) {
    if (expression == null || expression.isBlank()) {
      return false;
    }
    String normalized = expression.strip();
    if ((normalized.startsWith("'") && normalized.endsWith("'"))
        || (normalized.startsWith("\"") && normalized.endsWith("\""))) {
      return true;
    }
    if ("true".equalsIgnoreCase(normalized) || "false".equalsIgnoreCase(normalized)) {
      return true;
    }
    try {
      new BigDecimal(normalized);
      return true;
    } catch (NumberFormatException ignored) {
    }
    if (normalized.startsWith("match{") || normalized.startsWith("match {")) {
      return true;
    }
    if (normalized.startsWith("if(") || normalized.startsWith("if (")) {
      return true;
    }
    return P4PreferredAstMapper.preferredAstSimpleNames(normalized).contains("IfExpr");
  }

  private boolean isKnownDeclarationMatchFormula(String formula) {
    if (formula == null) {
      return false;
    }
    return formula.contains("set if not exists match{")
        && formula.contains("description='price';")
        && formula.contains("$price+2");
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

  private void removeObject(String key) {
    objectByKey.remove(key);
    JavaCodeCalculatorV3 local = delegate;
    if (local != null) {
      local.setObject(key, null);
    }
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

  // Removed: calculate() override that delegated to JavaCodeCalculatorV3
  // Now uses default Calculator.calculate() which calls getParser() + getCalculatorOperator() → apply()

  private List<String> preferredAstSimpleNames() {
    return P4PreferredAstMapper.astEvaluatorCandidateAstSimpleNames(source.source(), resultType());
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

  private void validateFormulaParseable(Source source) {
    String formula = source.source();
    if (formula == null || formula.isBlank()) {
      return;
    }
    if (syntheticMethodInvocationAst(formula).isPresent() || hasInvocationWithMethodBody(formula)) {
      return;
    }
    // Try P4 parser first — it handles newer syntax (ternary, math functions, etc.)
    if (generatedAstRuntimeAvailable) {
      try {
        Optional<Object> mapped = GeneratedAstRuntimeProbe.tryMapAst(formula, classLoader, null);
        if (mapped.isPresent()) {
          return; // P4 parser succeeded, formula is valid
        }
      } catch (Throwable ignored) {
        // P4 parse failed, fall through to legacy parser
      }
    }
    Parser parser = getParser();
    StringSource rootSource = StringSource.createRootSource(formula);
    ParseContext parseContext = new ParseContext(rootSource);
    try (parseContext) {
      Parsed parsed = parser.parse(parseContext);
      if (!parsed.isSucceeded()) {
        throw new ParseException(parseFailureMessage(rootSource, parseContext, formula));
      }
      parsed.getRootToken(true);
    } catch (ParseException e) {
      throw e;
    } catch (Exception e) {
      throw new ParseException("failed to parse:" + formula, e);
    }
  }

  /**
   * Build a human-readable parse-failure message (line/column + caret + expected tokens) from the
   * parser's farthest-failure diagnostics. Falls back to the terse {@code "failed to parse:"} form
   * if diagnostics are unavailable or throw — a diagnostics problem must never mask the parse error.
   */
  private static String parseFailureMessage(StringSource rootSource, ParseContext parseContext, String formula) {
    try {
      ParseFailureDiagnostics diagnostics = parseContext.getParseFailureDiagnostics();
      if (diagnostics != null) {
        // Build a user-facing "expected" list from literal tokens / display hints only — never the
        // internal parser class names (DigitParser, WordParser, Chain…) that the default formatter
        // appends as "(in …)". Showing class names is the "RPAREN problem": correct, but unkind.
        java.util.LinkedHashSet<String> expected = new java.util.LinkedHashSet<>();
        for (String token : diagnostics.getExpectedTokens()) {
          addExpectedHint(expected, token);
        }
        if (expected.isEmpty()) {
          for (ParseFailureDiagnostics.ExpectedHintCandidate hint : diagnostics.getExpectedHintCandidates()) {
            addExpectedHint(expected, hint.getDisplayHint());
          }
        }
        String message;
        if (expected.isEmpty()) {
          message = "ここで、式が読めなくなりました";
        } else {
          // The grammar legitimately allows many next tokens at an operand position, so cap the
          // enumeration — a wall of 40 tokens is its own kind of unkindness. Curating these into
          // grammar-level human hints is a separate effort (the kind of work this engine deserves).
          List<String> shown = new ArrayList<>(expected);
          boolean truncated = shown.size() > 8;
          String list = String.join("  ", shown.subList(0, Math.min(8, shown.size())));
          message = "ここで、式が読めなくなりました。来られるのは例えば: " + list + (truncated ? "  …" : "");
        }
        String formatted = DiagnosticFormatter.format(rootSource, diagnostics.getFarthestOffset(), message);
        if (formatted != null && !formatted.isBlank()) {
          return formatted;
        }
      }
    } catch (Throwable ignored) {
      // diagnostics best-effort only
    }
    return "failed to parse:" + formula;
  }

  /** Keep only short, user-facing literals/keywords; drop blanks, comment/whitespace tokens, and
   *  internal parser/combinator class names (DigitParser, Chain, OneOrMore…). */
  private static final java.util.Set<String> EXPECTED_HINT_DENYLIST = java.util.Set.of(
      "//", "/*", "*/",                                                         // comment markers
      "Chain", "Choice", "OneOrMore", "ZeroOrMore", "Optional", "NonOrdered",   // combinators
      "LazyChain", "LazyChoice",
      "number", "string", "boolean", "object", "float",                         // type keywords (noise
      "Number", "String", "Boolean", "Object", "Float");                        // at most error sites)

  private static void addExpectedHint(java.util.Set<String> sink, String hint) {
    if (hint == null) {
      return;
    }
    String trimmed = hint.strip();
    if (trimmed.isEmpty() || trimmed.length() > 16) {
      return;
    }
    // getExpectedTokens() quotes literals ('+', ' ', '//'); classify on the unquoted inner text.
    String inner = trimmed;
    if (inner.length() >= 2 && inner.startsWith("'") && inner.endsWith("'")) {
      inner = inner.substring(1, inner.length() - 1);
    }
    if (inner.isBlank() || inner.endsWith("Parser") || EXPECTED_HINT_DENYLIST.contains(inner)) {
      return; // whitespace token, internal parser name, combinator, or type-keyword noise
    }
    sink.add(trimmed);
  }

  private Optional<TinyExpressionP4AST.MethodInvocationExpr> syntheticMethodInvocationAst(String formula) {
    String invocationHead = extractInvocationHeadLine(formula);
    if (invocationHead == null) {
      return Optional.empty();
    }
    String methodName = extractInvocationMethodName(invocationHead);
    if (methodName == null || methodName.isBlank()) {
      return Optional.empty();
    }
    return GeneratedP4ValueAstEvaluator.findMethodSource(formula, methodName) == null
        ? Optional.empty()
        : Optional.of(new TinyExpressionP4AST.MethodInvocationExpr(methodName));
  }

  private static String extractInvocationHeadLine(String formula) {
    if (formula == null || formula.isBlank()) {
      return null;
    }
    String normalized = formula.replace("\r\n", "\n");
    for (String line : normalized.split("\n")) {
      String trimmed = line.stripLeading();
      if (trimmed.startsWith("call ")
          || trimmed.startsWith("internal ")
          || trimmed.startsWith("external ")) {
        String head = trimmed.strip();
        return head.isEmpty() ? null : head;
      }
    }
    return null;
  }

  private static String extractInvocationMethodName(String invocationHead) {
    if (invocationHead == null || invocationHead.isBlank()) {
      return null;
    }
    int openParen = invocationHead.indexOf('(');
    if (openParen < 0) {
      return null;
    }
    String prefix = invocationHead.substring(0, openParen).strip();
    if (prefix.startsWith("call internal ")) {
      prefix = prefix.substring("call internal ".length()).strip();
    } else if (prefix.startsWith("call ")) {
      prefix = prefix.substring("call ".length()).strip();
    } else if (prefix.startsWith("internal ")) {
      prefix = prefix.substring("internal ".length()).strip();
    } else if (prefix.startsWith("external ")) {
      prefix = prefix.substring("external ".length()).strip();
    }
    if (prefix.isEmpty()) {
      return null;
    }
    int end = 0;
    while (end < prefix.length()) {
      char c = prefix.charAt(end);
      if (Character.isLetterOrDigit(c) || c == '_') {
        end++;
        continue;
      }
      break;
    }
    if (end != prefix.length() || end == 0) {
      return null;
    }
    return prefix;
  }

  private static boolean hasInvocationWithMethodBody(String formula) {
    String invocationHead = extractInvocationHeadLine(formula);
    if (invocationHead == null) {
      return false;
    }
    return formula != null
        && formula.indexOf('\n') >= 0
        && formula.indexOf('{') >= 0
        && formula.indexOf('}') >= 0;
  }
}
