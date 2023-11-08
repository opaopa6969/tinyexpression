package org.unlaxer.tinyexpression;

import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.Source;
import org.unlaxer.StringSource;
import org.unlaxer.context.ParseContext;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.tinyexpression.CalculationContext.Angle;

public abstract class BackTrackingStressTest extends ParserTestBase{
	
	@Test
	public void testCalculate() {
		
		
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
			
			Source source = StringSource.createRootSource(formula.toString());
			Calculator<?> calculator = calculator(source);
			assertTrue(calc(calculator,context,source,new BigDecimal("1")));
		}
		
	}
	
	boolean calc(Calculator<?> calculator , CalculationContext calculationContext , Source formula , BigDecimal expected){
		long start = System.nanoTime();
		testAllMatch(calculator.getParser(), formula.sourceAsString());
		CalculateResult calculateResult = calculator.calculate(calculationContext,formula);
		BigDecimal x = calculateResult.answer.get();
//		System.out.println(formula+":" + (System.nanoTime()-start)+"nsec");
		System.out.println((System.nanoTime()-start));
//		System.out.format(" %s = %s \n" , formula , x.toString());
		return expected.compareTo(x) ==0;
	}
	
	@Test
	public void testParse() {
		
		
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
			
	    Source source = StringSource.createRootSource(formula.toString());

			Calculator<?> calculator = calculator(source);
			parse(calculator,formula.toString());
		}
		System.out.println();
		
	}
	
	void parse(Calculator<?> calculator , String formula ){
		long start = System.nanoTime();
		StringSource stringSource ;
		for(int i =0 ; i < 1000;i++){
			stringSource = StringSource.createRootSource(formula);
			calculator.getParser().parse(new ParseContext(stringSource));
		}
		System.out.println((System.nanoTime()-start));
	}
	
	public abstract Calculator<?> calculator(Source formula);

}
