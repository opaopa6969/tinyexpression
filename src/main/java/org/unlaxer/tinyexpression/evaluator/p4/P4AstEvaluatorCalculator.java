package org.unlaxer.tinyexpression.evaluator.p4;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.TokenBaseOperator;
import org.unlaxer.tinyexpression.evaluator.ast.AstEvaluatorCalculator;
import org.unlaxer.tinyexpression.evaluator.javacode.ClassNameAndByteCode;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.parser.Parser;

/**
 * P4 AST Evaluator backend.
 * <p>
 * Strategy:
 * 1) Attempt to parse formula via type-safe {@link TinyExpressionP4Mapper} (no reflection, no regex).
 * 2) If successful, set {@code _tinyP4ParserUsed=true} and evaluate via {@link AstEvaluatorCalculator}.
 * 3) If P4 parse fails (formula uses syntax not yet covered by P4 grammar), fall back
 *    to {@link AstEvaluatorCalculator} directly and set {@code _tinyP4ParserUsed=false}.
 * <p>
 * Runtime markers set:
 * <ul>
 *   <li>{@code _tinyP4ParserUsed} — whether the P4 grammar successfully parsed the formula</li>
 *   <li>{@code _tinyP4AstNodeType} — simple class name of the mapped P4 AST root node</li>
 * </ul>
 */
public class P4AstEvaluatorCalculator implements Calculator {

  private final AstEvaluatorCalculator delegate;
  private final Map<String, Object> p4Markers = new LinkedHashMap<>();

  // =========================================================================
  // Constructors
  // =========================================================================

  public P4AstEvaluatorCalculator(Source source, String className,
      SpecifiedExpressionTypes specifiedExpressionTypes, ClassLoader classLoader) {
    this.delegate = new AstEvaluatorCalculator(source, className, specifiedExpressionTypes, classLoader);
    initP4Markers(source.source());
  }

  public P4AstEvaluatorCalculator(Source source, String javaCode, String className,
      SpecifiedExpressionTypes specifiedExpressionTypes, byte[] byteCode, String byteCodeHash,
      List<ClassNameAndByteCode> classNameAndByteCodeList, ClassLoader classLoader) {
    this.delegate = new AstEvaluatorCalculator(source, javaCode, className, specifiedExpressionTypes,
        byteCode, byteCodeHash, classNameAndByteCodeList, classLoader);
    initP4Markers(source.source());
  }

  private void initP4Markers(String formula) {
    try {
      TinyExpressionP4AST ast = TinyExpressionP4Mapper.parse(formula);
      p4Markers.put("_tinyP4ParserUsed", true);
      p4Markers.put("_tinyP4AstNodeType", ast.getClass().getSimpleName());
    } catch (Exception e) {
      p4Markers.put("_tinyP4ParserUsed", false);
      p4Markers.put("_tinyP4AstNodeType", "parse-failed");
    }
  }

  // =========================================================================
  // Calculator interface — delegate to AstEvaluatorCalculator
  // =========================================================================

  @Override
  public Object apply(CalculationContext calculationContext) {
    p4Markers.forEach(delegate::setObject);
    return delegate.apply(calculationContext);
  }

  @Override
  public Source source() { return delegate.source(); }

  @Override
  public String formula() { return delegate.formula(); }

  @Override
  public String javaCode() { return delegate.javaCode(); }

  @Override
  public byte[] byteCode() { return delegate.byteCode(); }

  @Override
  public String formulaHash() { return delegate.formulaHash(); }

  @Override
  public String byteCodeHash() { return delegate.byteCodeHash(); }

  @Override
  public ExpressionType resultType() { return delegate.resultType(); }

  @Override
  public Parser getParser() { return delegate.getParser(); }

  @Override
  public TokenBaseOperator<CalculationContext> getCalculatorOperator() { return delegate.getCalculatorOperator(); }

  @Override
  public Calculator.InstanceKind instanceKind() { return delegate.instanceKind(); }

  @Override
  public String returningTypeAsString() { return delegate.returningTypeAsString(); }

  @Override
  public Calculator.CreatedFrom createdFrom() { return delegate.createdFrom(); }

  @Override
  public List<Calculator> dependsOns() { return delegate.dependsOns(); }

  @Override
  public Optional<Calculator> dependsOnBy() { return delegate.dependsOnBy(); }

  @Override
  public void setDependsOnBy(Calculator calculator) { delegate.setDependsOnBy(calculator); }

  @Override
  public void setObject(String key, Object value) { delegate.setObject(key, value); }

  /**
   * Check p4Markers first so callers (e.g. TinyExpressionDapRuntimeBridge)
   * can read _tinyP4ParserUsed / _tinyP4AstNodeType even before apply() is called.
   */
  @Override
  public <X> X getObject(String key, Class<X> objectClass) {
    Object marker = p4Markers.get(key);
    if (marker != null && objectClass.isInstance(marker)) {
      return objectClass.cast(marker);
    }
    return delegate.getObject(key, objectClass);
  }

  @Override
  public void before(CalculationContext calculationContext) { delegate.before(calculationContext); }

  @Override
  public void after(CalculationContext calculationContext) { delegate.after(calculationContext); }
}
