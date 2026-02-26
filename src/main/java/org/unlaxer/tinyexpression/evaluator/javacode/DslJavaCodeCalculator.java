package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.List;

import org.unlaxer.tinyexpression.Source;

/**
 * Dedicated DSL backend seam.
 * <p>
 * Current runtime behavior intentionally bridges to the legacy JavaCode runtime.
 */
public class DslJavaCodeCalculator extends JavaCodeCalculatorV3 {

  public DslJavaCodeCalculator(Source source, String className,
      SpecifiedExpressionTypes specifiedExpressionTypes, ClassLoader classLoader) {
    super(source, className, specifiedExpressionTypes, classLoader);
  }

  public DslJavaCodeCalculator(Source source, String javaCode, String className,
      SpecifiedExpressionTypes specifiedExpressionTypes, byte[] byteCode, String byteCodeHash,
      List<ClassNameAndByteCode> classNameAndByteCodeList, ClassLoader classLoader) {
    super(source, javaCode, className, specifiedExpressionTypes,
        byteCode, byteCodeHash, classNameAndByteCodeList, classLoader);
  }
}
