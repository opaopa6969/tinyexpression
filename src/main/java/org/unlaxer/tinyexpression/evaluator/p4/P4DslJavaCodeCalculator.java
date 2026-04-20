package org.unlaxer.tinyexpression.evaluator.p4;

import java.util.List;

import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.ClassNameAndByteCode;
import org.unlaxer.tinyexpression.evaluator.javacode.DslJavaCodeCalculator;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
/**
 * P4 DSL Java Code backend.
 * <p>
 * Extends {@link DslJavaCodeCalculator} with a P4-grammar parse check:
 * before delegating to the DSL Java code generation pipeline, the formula is
 * parsed via the type-safe {@link TinyExpressionP4Mapper}.
 * <p>
 * Runtime markers set (in addition to DslJavaCodeCalculator markers):
 * <ul>
 *   <li>{@code _tinyP4ParserUsed} — whether the formula is considered P4-compatible</li>
 *   <li>{@code _tinyP4ParserExact} — whether the decision came from an exact mapper parse</li>
 *   <li>{@code _tinyP4ParserProbeMode} — {@code exact}, {@code heuristic}, or {@code failed}</li>
 *   <li>{@code _tinyP4AstNodeType} — simple class name of the mapped P4 AST root node</li>
 * </ul>
 */
public class P4DslJavaCodeCalculator extends DslJavaCodeCalculator {

  private final SpecifiedExpressionTypes specifiedExpressionTypes;
  private boolean p4ParserUsed = false;
  private boolean p4ParserExact = false;
  private String p4ParserProbeMode = "not-evaluated";
  private String p4AstNodeType = "not-evaluated";

  // =========================================================================
  // Constructors
  // =========================================================================

  public P4DslJavaCodeCalculator(Source source, String className,
      SpecifiedExpressionTypes specifiedExpressionTypes, ClassLoader classLoader) {
    super(source, className, specifiedExpressionTypes, classLoader);
    this.specifiedExpressionTypes = specifiedExpressionTypes;
  }

  public P4DslJavaCodeCalculator(Source source, String javaCode, String className,
      SpecifiedExpressionTypes specifiedExpressionTypes, byte[] byteCode, String byteCodeHash,
      List<ClassNameAndByteCode> classNameAndByteCodeList, ClassLoader classLoader) {
    super(source, javaCode, className, specifiedExpressionTypes,
        byteCode, byteCodeHash, classNameAndByteCodeList, classLoader);
    this.specifiedExpressionTypes = specifiedExpressionTypes;
  }

  // =========================================================================
  // P4 parse check — runs before DSL Java code generation
  // =========================================================================

  @Override
  public Object apply(CalculationContext calculationContext) {
    probeP4Parser();
    setObject("_tinyP4ParserUsed", p4ParserUsed);
    setObject("_tinyP4ParserExact", p4ParserExact);
    setObject("_tinyP4ParserProbeMode", p4ParserProbeMode);
    setObject("_tinyP4AstNodeType", p4AstNodeType);
    return super.apply(calculationContext);
  }

  private void probeP4Parser() {
    if (!"not-evaluated".equals(p4AstNodeType)) {
      return; // already probed
    }
    P4ParseProbe.Result probe = P4ParseProbe.probe(source().source(), specifiedExpressionTypes);
    p4ParserUsed = probe.parserUsed;
    p4ParserExact = probe.exactParse;
    p4ParserProbeMode = probe.probeMode;
    p4AstNodeType = probe.astNodeType;
  }

  public boolean p4ParserUsed() { return p4ParserUsed; }
  public String p4AstNodeType() { return p4AstNodeType; }
}
