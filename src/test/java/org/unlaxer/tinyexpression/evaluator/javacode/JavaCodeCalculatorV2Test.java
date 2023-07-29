package org.unlaxer.tinyexpression.evaluator.javacode;

import org.unlaxer.Name;
import org.unlaxer.tinyexpression.CalculatorImplTest;
import org.unlaxer.tinyexpression.PreConstructedCalculator;

public class JavaCodeCalculatorV2Test extends CalculatorImplTest<Float>{
	

	@Override
	public PreConstructedCalculator<Float> preConstructedCalculator(String formula) {
		return new JavaCodeCalculatorV2(Name.of("V2Test") , formula , 
		    Thread.currentThread().getContextClassLoader());
	}

}
