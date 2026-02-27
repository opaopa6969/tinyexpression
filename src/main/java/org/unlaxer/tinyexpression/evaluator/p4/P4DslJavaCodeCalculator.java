package org.unlaxer.tinyexpression.evaluator.p4;

import java.util.List;

import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.ClassNameAndByteCode;
import org.unlaxer.tinyexpression.evaluator.javacode.DslJavaCodeCalculator;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper;

/**
 * P4 DSL Java Code backend.
 * <p>
 * Extends {@link DslJavaCodeCalculator} with a P4-grammar parse check:
 * before delegating to the DSL Java code generation pipeline, the formula is
 * parsed via the type-safe {@link TinyExpressionP4Mapper}.
 * <p>
 * Runtime markers set (in addition to DslJavaCodeCalculator markers):
 * <ul>
 *   <li>{@code _tinyP4ParserUsed} — whether the P4 grammar successfully parsed the formula</li>
 *   <li>{@code _tinyP4AstNodeType} — simple class name of the mapped P4 AST root node</li>
 * </ul>
 */
public class P4DslJavaCodeCalculator extends DslJavaCodeCalculator {

  private boolean p4ParserUsed = false;
  private String p4AstNodeType = "not-evaluated";

  // =========================================================================
  // Constructors
  // =========================================================================

  public P4DslJavaCodeCalculator(Source source, String className,
      SpecifiedExpressionTypes specifiedExpressionTypes, ClassLoader classLoader) {
    super(source, className, specifiedExpressionTypes, classLoader);
  }

  public P4DslJavaCodeCalculator(Source source, String javaCode, String className,
      SpecifiedExpressionTypes specifiedExpressionTypes, byte[] byteCode, String byteCodeHash,
      List<ClassNameAndByteCode> classNameAndByteCodeList, ClassLoader classLoader) {
    super(source, javaCode, className, specifiedExpressionTypes,
        byteCode, byteCodeHash, classNameAndByteCodeList, classLoader);
  }

  // =========================================================================
  // P4 parse check — runs before DSL Java code generation
  // =========================================================================

  @Override
  public Object apply(CalculationContext calculationContext) {
    probeP4Parser();
    setObject("_tinyP4ParserUsed", p4ParserUsed);
    setObject("_tinyP4AstNodeType", p4AstNodeType);
    return super.apply(calculationContext);
  }

  private void probeP4Parser() {
    if (!"not-evaluated".equals(p4AstNodeType)) {
      return; // already probed
    }
    try {
      TinyExpressionP4AST ast = TinyExpressionP4Mapper.parse(source().source());
      p4ParserUsed = true;
      p4AstNodeType = ast.getClass().getSimpleName();
    } catch (Exception e) {
      p4ParserUsed = false;
      p4AstNodeType = "parse-failed";
    }
  }

  public boolean p4ParserUsed() { return p4ParserUsed; }
  public String p4AstNodeType() { return p4AstNodeType; }
}
