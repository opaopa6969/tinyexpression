package org.unlaxer.tinyexpression.evaluator.javacode;

import java.math.RoundingMode;

import org.junit.Test;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.CalculationContext.Angle;
import org.unlaxer.tinyexpression.NormalCalculationContext;

public class JavaCodeCalculatorSpeedTest {

	@Test
	public void testCalculatorSpeed() {
		String formula = "if(($number_accessCountByIPAddressInShortPeriod>=15.0)|($number_accessCountByCaulisCookieInShortPeriod>=10.0)|($number_accessCountByIPAddressInMiddlePeriod>=60.0)|($number_accessCountByCaulisCookieInMiddlePeriod>=30.0)){1}else{0}";
		
		CalculationContext context = new NormalCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
		
		context.set("number_accessCountByIPAddressInShortPeriod", 0);
		context.set("number_accessCountByCaulisCookieInShortPeriod", 0);
		context.set("number_accessCountByIPAddressInMiddlePeriod", 100);
		context.set("number_accessCountByCaulisCookieInMiddlePeriod", 100);
		
		long start = System.nanoTime();
		JavaCodeNumberCalculatorV2 simpleCalculator = new JavaCodeNumberCalculatorV2(formula , "DummyCalculator", Thread.currentThread().getContextClassLoader());
		
		float count = 100000000;
		float toMicro = count * 10000f;
		
		for(int i =0 ; i < count; i++) {
			simpleCalculator.calculate(context);
		}
		long duration = System.nanoTime() - start;
		System.out.println(formula);
		System.out.format("calculation time:%f(microsec)\n" , ((float)duration)/toMicro);
	}

}
