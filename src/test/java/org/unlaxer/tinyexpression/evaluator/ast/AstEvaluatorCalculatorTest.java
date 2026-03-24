package org.unlaxer.tinyexpression.evaluator.ast;

import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.CalculatorImplTest;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class AstEvaluatorCalculatorTest extends CalculatorImplTest {

    @Override
    public Calculator preConstructedCalculator(Source formula) {
        String className = "AstTest_CalculatorClass" + Math.abs(formula.source().hashCode());
        return new AstEvaluatorCalculator(
            formula,
            className,
            new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
            Thread.currentThread().getContextClassLoader());
    }
}
