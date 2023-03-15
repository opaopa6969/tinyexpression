package org.unlaxer.tinyexpression;

import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.StringSource;
import org.unlaxer.context.ParseContext;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.tinyexpression.CalculationContext.Angle;

public abstract class BackTrackingStressTest extends ParserTestBase{
	
	@Test
	public void testCalculate() {
		
		Calculator<?> calculator = calculator();
		
		setLevel(OutputLevel.detail);
		
		CalculationContext context = new NormalCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
		for(int i = 0 ; i < 50;i++){
			StringBuilder formula = new StringBuilder();
			for(int r = 0 ; r < i;r++){
				formula.append("(");
			}
			formula.append("1");
			for(int r = 0 ; r < i;r++){
				formula.append(")");
			}
			assertTrue(calc(calculator,context,formula.toString(),new BigDecimal("1")));
		}
		
	}
	
	boolean calc(Calculator<?> calculator , CalculationContext calculationContext , String formula , BigDecimal expected){
		long start = System.nanoTime();
		testAllMatch(calculator.getParser(), formula);
		CalculateResult calculateResult = calculator.calculate(calculationContext , formula);
		BigDecimal x = calculateResult.answer.get();
//		System.out.println(formula+":" + (System.nanoTime()-start)+"nsec");
		System.out.println((System.nanoTime()-start));
//		System.out.format(" %s = %s \n" , formula , x.toString());
		return expected.compareTo(x) ==0;
	}
	
	@Test
	public void testParse() {
		
		Calculator<?> calculator = calculator();
		
		setLevel(OutputLevel.none);
		
		for(int i = 0 ; i < 50;i++){
			StringBuilder formula = new StringBuilder();
			for(int r = 0 ; r < i;r++){
				formula.append("(");
			}
			formula.append("1");
			for(int r = 0 ; r < i;r++){
				formula.append(")");
			}
			parse(calculator,formula.toString());
		}
		System.out.println();
		
	}
	
	void parse(Calculator<?> calculator , String formula ){
		long start = System.nanoTime();
		StringSource stringSource ;
		for(int i =0 ; i < 1000;i++){
			stringSource = new StringSource(formula);
			calculator.getParser().parse(new ParseContext(stringSource));
		}
		System.out.println((System.nanoTime()-start));
	}
	
	public abstract Calculator<?> calculator();

}
