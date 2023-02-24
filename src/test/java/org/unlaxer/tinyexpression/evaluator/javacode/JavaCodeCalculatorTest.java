package org.unlaxer.tinyexpression.evaluator.javacode;

import org.unlaxer.tinyexpression.CalculatorImplTest;
import org.unlaxer.tinyexpression.PreConstructedCalculator;

public class JavaCodeCalculatorTest extends CalculatorImplTest<Float>{
	

	@Override
	public PreConstructedCalculator<Float> preConstructedCalculator(String formula) {
		return new JavaCodeCalculator(formula);
	}

}
