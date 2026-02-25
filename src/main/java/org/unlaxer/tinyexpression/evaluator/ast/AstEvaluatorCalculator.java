package org.unlaxer.tinyexpression.evaluator.ast;

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
    Optional<Object> astEvaluated = AstNumberExpressionEvaluator.tryEvaluate(
        source.source(), specifiedExpressionTypes, calculationContext);

    if (generatedAstRuntimeAvailable) {
      Optional<Object> mapped = GeneratedAstRuntimeProbe.tryMapAst(source.source(), classLoader);
      mapped.ifPresent(value -> setObject("_astEvaluatorMappedAst", value));
      setObject("_astEvaluatorMapperAvailable", true);
    } else {
      setObject("_astEvaluatorMapperAvailable", false);
    }

    if (astEvaluated.isPresent()) {
      setObject("_astEvaluatorRuntime", "token-ast");
      return astEvaluated.get();
    }

    setObject("_astEvaluatorRuntime", "javacode-fallback");
    return ensureDelegate().apply(calculationContext);
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
}
