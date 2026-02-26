package org.unlaxer.tinyexpression.evaluator.javacode.legacy;

import java.util.List;
import java.util.function.UnaryOperator;

import org.unlaxer.Token;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.ClassNameAndByteCode;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeCalculatorV3;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;

/**
 * JavaCode backend variant that uses the pre-refactor AST creator algorithm.
 */
public class LegacyAstCreatorJavaCodeCalculator extends JavaCodeCalculatorV3 {

  public LegacyAstCreatorJavaCodeCalculator(Source source, String className,
      SpecifiedExpressionTypes specifiedExpressionTypes, ClassLoader classLoader) {
    super(source, className, specifiedExpressionTypes, classLoader);
  }

  public LegacyAstCreatorJavaCodeCalculator(Source source, String javaCode, String className,
      SpecifiedExpressionTypes specifiedExpressionTypes, byte[] byteCode, String byteCodeHash,
      List<ClassNameAndByteCode> classNameAndByteCodeList, ClassLoader classLoader) {
    super(source, javaCode, className, specifiedExpressionTypes,
        byteCode, byteCodeHash, classNameAndByteCodeList, classLoader);
  }

  @Override
  public UnaryOperator<Token> tokenReduer() {
    return LegacyOperatorOperandTreeCreator.SINGLETON;
  }
}
