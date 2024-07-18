package org.unlaxer.tinyexpression.evaluator.javacode;

import org.unlaxer.Name;
import org.unlaxer.tinyexpression.CalculatorImplTest;
import org.unlaxer.tinyexpression.PreConstructedNumberCalculator;

public class JavaCodeCalculatorV2Test extends CalculatorImplTest<Float>{
	

	@Override
	public PreConstructedNumberCalculator preConstructedCalculator(String formula) {
		return new JavaCodeNumberCalculatorV2(Name.of("V2Test") , formula , 
		    Thread.currentThread().getContextClassLoader());
	}

}
