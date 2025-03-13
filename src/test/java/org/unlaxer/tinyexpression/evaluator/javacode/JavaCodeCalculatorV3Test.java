package org.unlaxer.tinyexpression.evaluator.javacode;

import org.unlaxer.Name;
import org.unlaxer.tinyexpression.CalculatorImplTest;
import org.unlaxer.tinyexpression.PreConstructedCalculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class JavaCodeCalculatorV3Test extends CalculatorImplTest{


	@Override
	public PreConstructedCalculator preConstructedCalculator(Source formula) {
		return new JavaCodeCalculatorV3(Name.of("V3Test") , formula ,
		    new SpecifiedExpressionTypes(ExpressionTypes._float,ExpressionTypes._float),
		    Thread.currentThread().getContextClassLoader());
	}
}
