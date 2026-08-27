package org.unlaxer.tinyexpression.evaluator.ast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

import org.unlaxer.Token;
import org.unlaxer.compiler.InstanceAndByteCode;
import org.unlaxer.parser.ParseException;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.TokenBaseOperator;
import org.unlaxer.tinyexpression.evaluator.javacode.ClassNameAndByteCode;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.p4.P4PreferredAstMapper;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.FormulaParser;
import org.unlaxer.util.digest.MD5;

/**
 * AST evaluator backend entry point.
 * <p>
 * Parses formulas to the generated P4 AST and evaluates them with
 * {@link P4TypedAstEvaluator}. This backend never switches to a handwritten AST
 * evaluator or Java-code compiler when parsing or evaluation fails.
 */
public class AstEvaluatorCalculator implements Calculator {

  private final Source source;
  private final ClassLoader classLoader;
  private final SpecifiedExpressionTypes specifiedExpressionTypes;

  private final String javaCodeFromStore;
  private final byte[] byteCodeFromStore;
  private final String byteCodeHashFromStore;
  private final boolean createdFromByteCode;

  private final Map<String, Object> objectByKey = new LinkedHashMap<>();

  private final List<Calculator> dependsOns = new ArrayList<>();
  private volatile Optional<Calculator> dependsOnBy = Optional.empty();

  private final boolean generatedAstRuntimeAvailable;
  private final TinyExpressionP4AST generatedAst;

  public AstEvaluatorCalculator(Source source, String className,
      SpecifiedExpressionTypes specifiedExpressionTypes, ClassLoader classLoader) {
    this.source = source;
    this.specifiedExpressionTypes = specifiedExpressionTypes;
    this.classLoader = classLoader;
    this.javaCodeFromStore = null;
    this.byteCodeFromStore = new byte[0];
    this.byteCodeHashFromStore = MD5.toHex(this.byteCodeFromStore);
    this.createdFromByteCode = false;
    this.generatedAstRuntimeAvailable = GeneratedAstRuntimeProbe.isAvailable(classLoader);
    this.generatedAst = validateFormulaParseable(source);
  }

  public AstEvaluatorCalculator(Source source, String javaCode, String className,
      SpecifiedExpressionTypes specifiedExpressionTypes, byte[] byteCode, String byteCodeHash,
      List<ClassNameAndByteCode> classNameAndByteCodeList, ClassLoader classLoader) {
    this.source = source;
    this.specifiedExpressionTypes = specifiedExpressionTypes;
    this.classLoader = classLoader;
    this.javaCodeFromStore = javaCode;
    this.byteCodeFromStore = byteCode == null ? new byte[0] : byteCode;
    this.byteCodeHashFromStore = byteCodeHash == null ? MD5.toHex(this.byteCodeFromStore) : byteCodeHash;
    this.createdFromByteCode = true;
    this.generatedAstRuntimeAvailable = GeneratedAstRuntimeProbe.isAvailable(classLoader);
    this.generatedAst = validateFormulaParseable(source);
  }

  public boolean generatedAstRuntimeAvailable() {
    return generatedAstRuntimeAvailable;
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
    // no-op for AST path
  }

  @Override
  public Object apply(CalculationContext calculationContext) {
    String formulaText = source.source() == null ? "" : source.source();
    setObject("_astEvaluatorGeneratedEmbeddedBridgeUsed", false);

    // The generated P4 AST and its typed evaluator are the only execution path.
    if (generatedAstRuntimeAvailable && generatedAst != null) {
      setObject("_astEvaluatorMappedAst", generatedAst);
      try {
        Object p4TypedResult = new P4TypedAstEvaluator(
            specifiedExpressionTypes, calculationContext, source.source(), classLoader)
            .eval(generatedAst);
        if (p4TypedResult != null) {
          setObject("_astEvaluatorRuntime", "p4-typed");
          setObject("_astEvaluatorMapperAvailable", true);
          setObject("_astEvaluatorGeneratedEmbeddedBridgeUsed", false);
          return p4TypedResult;
        } else {
          setObject("_p4FailureFormula", formulaText);
          setObject("_p4FailureReason", "result was null");
        }
      } catch (UnsupportedOperationException | IllegalArgumentException p4Ex) {
        setObject("_p4FailureFormula", formulaText);
        setObject("_p4FailureReason", p4Ex.getClass().getSimpleName() + ": " + p4Ex.getMessage());
      }
      setObject("_astEvaluatorMapperAvailable", true);
    } else {
      setObject("_astEvaluatorMapperAvailable", false);
    }

    throw generatedAstFailure(formulaText);
  }

  private UnsupportedOperationException generatedAstFailure(String formulaText) {
    String reason = String.valueOf(
        objectByKey.getOrDefault("_p4FailureReason", "no P4 AST mapping accepted the formula"));
    return new UnsupportedOperationException(
        "Generated AST backend cannot evaluate formula: " + formulaText + " (" + reason + ")");
  }


  @Override
  public void after(CalculationContext calculationContext) {
  }

  @Override
  public void setObject(String key, Object object) {
    objectByKey.put(key, object);
  }

  @Override
  public <X> X getObject(String key, Class<X> objectClass) {
    Object local = objectByKey.get(key);
    if (local != null) {
      return objectClass.cast(local);
    }
    return null;
  }

  @Override
  public CreatedFrom createdFrom() {
    return createdFromByteCode ? CreatedFrom.byteCode : CreatedFrom.formula;
  }

  @Override
  public void setDependsOnBy(Calculator calculator) {
    dependsOnBy = Optional.ofNullable(calculator);
  }

  @Override
  public List<InstanceAndByteCode> instanceAndByteCodeList() {
    return List.of();
  }

  private TinyExpressionP4AST validateFormulaParseable(Source source) {
    String formula = source.source();
    if (formula == null || formula.isBlank()) {
      return null;
    }
    if (!generatedAstRuntimeAvailable) {
      throw new ParseException("generated P4 runtime is unavailable");
    }
    try {
      Optional<Object> mapped = GeneratedAstRuntimeProbe.tryMapAst(
          formula,
          classLoader,
          P4PreferredAstMapper.astEvaluatorCandidateAstSimpleNames(formula, resultType()));
      if (mapped.isPresent() && mapped.get() instanceof TinyExpressionP4AST typedAst) {
        return typedAst;
      }
      throw new ParseException("generated P4 grammar rejected formula: " + formula);
    } catch (ParseException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new ParseException("generated P4 grammar rejected formula: " + formula, failure);
    }
  }
}
