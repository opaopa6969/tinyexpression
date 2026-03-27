package org.unlaxer.tinyexpression.evaluator.p4;

import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.CalculatorImplTest;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class P4AstEvaluatorCalculatorTest extends CalculatorImplTest {

    @Override
    public Calculator preConstructedCalculator(Source formula) {
        String className = "P4AstTest_CalculatorClass" + Math.abs(formula.source().hashCode());
        return new P4AstEvaluatorCalculator(
            formula,
            className,
            new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
            Thread.currentThread().getContextClassLoader());
    }
}
