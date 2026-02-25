package org.unlaxer.tinyexpression.evaluator.ast;

import java.util.List;
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

/**
 * AST evaluator backend entry point.
 * <p>
 * Current implementation keeps behavioral compatibility by delegating execution to JavaCode path
 * while reserving this class as the integration point for generated parser/mapper/evaluator runtime.
 */
public class AstEvaluatorCalculator implements Calculator {

  private final Calculator delegate;
  private final boolean generatedAstRuntimeAvailable;
  private final ClassLoader classLoader;
  private final Source source;
  private final SpecifiedExpressionTypes specifiedExpressionTypes;

  public AstEvaluatorCalculator(Source source, String className,
      SpecifiedExpressionTypes specifiedExpressionTypes, ClassLoader classLoader) {
    this.source = source;
    this.classLoader = classLoader;
    this.specifiedExpressionTypes = specifiedExpressionTypes;
    this.delegate = new JavaCodeCalculatorV3(source, className, specifiedExpressionTypes, classLoader);
    this.generatedAstRuntimeAvailable = GeneratedAstRuntimeProbe.isAvailable(classLoader);
  }

  public AstEvaluatorCalculator(Source source, String javaCode, String className,
      SpecifiedExpressionTypes specifiedExpressionTypes, byte[] byteCode, String byteCodeHash,
      List<ClassNameAndByteCode> classNameAndByteCodeList, ClassLoader classLoader) {
    this.source = source;
    this.classLoader = classLoader;
    this.specifiedExpressionTypes = specifiedExpressionTypes;
    this.delegate = new JavaCodeCalculatorV3(source, javaCode, className, specifiedExpressionTypes,
        byteCode, byteCodeHash, classNameAndByteCodeList, classLoader);
    this.generatedAstRuntimeAvailable = GeneratedAstRuntimeProbe.isAvailable(classLoader);
  }

  public boolean generatedAstRuntimeAvailable() {
    return generatedAstRuntimeAvailable;
  }

  @Override
  public InstanceKind instanceKind() {
    return delegate.instanceKind();
  }

  @Override
  public ExpressionType resultType() {
    return delegate.resultType();
  }

  @Override
  public Parser getParser() {
    return delegate.getParser();
  }

  @Override
  public TokenBaseOperator<CalculationContext> getCalculatorOperator() {
    return delegate.getCalculatorOperator();
  }

  @Override
  public UnaryOperator<Token> tokenReduer() {
    return delegate.tokenReduer();
  }

  @Override
  public Source source() {
    return delegate.source();
  }

  @Override
  public String returningTypeAsString() {
    return delegate.returningTypeAsString();
  }

  @Override
  public String javaCode() {
    return delegate.javaCode();
  }

  @Override
  public String formula() {
    return delegate.formula();
  }

  @Override
  public byte[] byteCode() {
    return delegate.byteCode();
  }

  @Override
  public String formulaHash() {
    return delegate.formulaHash();
  }

  @Override
  public String byteCodeHash() {
    return delegate.byteCodeHash();
  }

  @Override
  public List<Calculator> dependsOns() {
    return delegate.dependsOns();
  }

  @Override
  public Optional<Calculator> dependsOnBy() {
    return delegate.dependsOnBy();
  }

  @Override
  public void before(CalculationContext calculationContext) {
    delegate.before(calculationContext);
  }

  @Override
  public Object apply(CalculationContext calculationContext) {
    Optional<Object> astEvaluated = AstNumberExpressionEvaluator.tryEvaluate(
        source.source(), specifiedExpressionTypes, calculationContext);
    if (generatedAstRuntimeAvailable) {
      // Probe generated mapper runtime path; execution still delegates to JavaCode path for compatibility.
      Optional<Object> ast = GeneratedAstRuntimeProbe.tryMapAst(source.source(), classLoader);
      ast.ifPresent(mapped -> setObject("_astEvaluatorMappedAst", mapped));
      setObject("_astEvaluatorMapperAvailable", true);
    } else {
      setObject("_astEvaluatorMapperAvailable", false);
    }
    if (astEvaluated.isPresent()) {
      setObject("_astEvaluatorRuntime", "token-ast");
      return astEvaluated.get();
    }
    setObject("_astEvaluatorRuntime", "javacode-fallback");
    return delegate.apply(calculationContext);
  }

  @Override
  public void after(CalculationContext calculationContext) {
    delegate.after(calculationContext);
  }

  @Override
  public void setObject(String key, Object object) {
    delegate.setObject(key, object);
  }

  @Override
  public <X> X getObject(String key, Class<X> objectClass) {
    return delegate.getObject(key, objectClass);
  }

  @Override
  public CreatedFrom createdFrom() {
    return delegate.createdFrom();
  }

  @Override
  public void setDependsOnBy(Calculator calculator) {
    delegate.setDependsOnBy(calculator);
  }

  @Override
  public List<InstanceAndByteCode> instanceAndByteCodeList() {
    return delegate.instanceAndByteCodeList();
  }

  @Override
  public CalculateResult calculate(CalculationContext calculateContext, String formula, ExpressionType resultType) {
    return delegate.calculate(calculateContext, formula, resultType);
  }
}
