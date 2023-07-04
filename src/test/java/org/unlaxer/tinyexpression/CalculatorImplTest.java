package org.unlaxer.tinyexpression;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.TestResult;
import org.unlaxer.Token;
import org.unlaxer.TokenKind;
import org.unlaxer.TokenPrinter;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.parser.ParseException;
import org.unlaxer.tinyexpression.CalculationContext.Angle;
import org.unlaxer.tinyexpression.Calculator.CalculationException;
import org.unlaxer.tinyexpression.evaluator.javacode.SimpleBuilder;
import org.unlaxer.tinyexpression.evaluator.javacode.TinyExpressionTokens;
import org.unlaxer.tinyexpression.formatter.Formatter;
import org.unlaxer.tinyexpression.parser.NumberIfExpressionParser;
import org.unlaxer.tinyexpression.parser.TestSideEffector;

import net.arnx.jsonic.JSON;

public abstract class CalculatorImplTest<T> extends ParserTestBase{

	@Test
	public void test() {
		
		setLevel(OutputLevel.detail);
		
		CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
		
		assertTrue(calc(context,"0",new BigDecimal("0")));
		assertTrue(calc(context,"1+1",new BigDecimal("2")));
		assertTrue(calc(context,"1+1/5",new BigDecimal("1.20")));
		assertTrue(calc(context,"1+1/5",new BigDecimal("1.20")));
		assertTrue(calc(context,"(1+1)/5",new BigDecimal("0.40")));
		assertTrue(calc(context,"(1e1+1)*5",new BigDecimal("55")));
		assertTrue(calc(context,"(1e1+1)*(5-3)",new BigDecimal("22")));
		assertTrue(calc(context,"10/2/2.5*4.5",new BigDecimal("9.00")));
		assertTrue(calc(context,"10/2+2.5*4.5",new BigDecimal("16.25")));
		assertTrue(calc(context,"-10/2+2.5*4.5",new BigDecimal("6.25")));
		assertTrue(calc(context,"sin(30)",new BigDecimal("0.5")));
		assertTrue(calc(context,"sin(30)*2",new BigDecimal("1")));
		assertTrue(calc(context,"cos(60)",new BigDecimal("0.5")));
		assertTrue(calc(context,"tan(45)",new BigDecimal("1")));
		assertTrue(calc(context,"sqrt(4)",new BigDecimal("2")));
		
		//test recurring decimal
		context = new ConcurrentCalculationContext(10,RoundingMode.HALF_UP,Angle.DEGREE); 

		assertTrue(calc(context,"1/0.11",new BigDecimal("9.0909090909")));
		assertTrue(calc(context,"1/7",new BigDecimal("0.1428571429")));
	}
	
	@Test
	public void testVariable() {
		setLevel(OutputLevel.detail);
		
		CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
		context.set("count", 12);
		assertTrue(calc(context,"$count+10",new BigDecimal("22")));
	}
	
	@Test
	public void testTernary() {
		setLevel(OutputLevel.detail);
		
		CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
		context.set("isExists", true);
//		assertTrue(calc(context,"true?10:0",new BigDecimal("10")));
//		assertTrue(calc(context,"false?10:0",new BigDecimal("0")));
		assertTrue(calc(context,"if($isExists){10}else{0}",new BigDecimal("10")));
	}

	@Test
	public void testTrueOrFalse() {
		setLevel(OutputLevel.detail);
		
		CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
		assertTrue(calc(context,"if(true){10}else{0}",new BigDecimal("10")));
		assertTrue(calc(context,"if(false){10}else{0}",new BigDecimal("0")));
		
		setLevel(OutputLevel.none);// if level is not none , then got stackOverflow Error!
		assertTrue(calc(context,"if(not(false)){10}else{0}",new BigDecimal("10")));
		assertTrue(calc(context,"if(not(true)){10}else{0}",new BigDecimal("0")));
	}
	
	@Test
	public void testGreaterOrLessOrEqual() {
		setLevel(OutputLevel.detail);
		
		CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
		assertTrue(calc(context,"if(10==20){10}else{0}",new BigDecimal("0")));
		assertTrue(calc(context,"if(10==10){10}else{0}",new BigDecimal("10")));
		assertTrue(calc(context,"if(10!=10){10}else{0}",new BigDecimal("0")));
		assertTrue(calc(context,"if(10!=20){10}else{0}",new BigDecimal("10")));
		assertTrue(calc(context,"if(10>=10){10}else{0}",new BigDecimal("10")));
		assertTrue(calc(context,"if(10>=20){10}else{0}",new BigDecimal("0")));
		assertTrue(calc(context,"if(10>20){10}else{0}",new BigDecimal("0")));
		assertTrue(calc(context,"if(10<20){10}else{0}",new BigDecimal("10")));
		assertTrue(calc(context,"if(10<=20){10}else{0}",new BigDecimal("10")));
		assertTrue(calc(context,"if(10<=10){10}else{0}",new BigDecimal("10")));
		assertTrue(calc(context,"if(10>10){10}else{0}",new BigDecimal("0")));
		assertTrue(calc(context,"if(10>5){30}else{0}",new BigDecimal("30")));
		assertTrue(calc(context,"if(not(10>5)){30}else{0}",new BigDecimal("0")));
		assertTrue(calc(context,"if(not(not(10>5))){30}else{0}",new BigDecimal("30")));
		assertTrue(calc(context,"if(not(not(2*5>5))){6*8}else{0}",new BigDecimal("48")));
		assertTrue(calc(context,"if(not(not(0.7*5>5))){6*8}else{0.3*0.2}",new BigDecimal("0.06")));
		assertTrue(calc(context,"if(not(not(0.7*5>5))){6*8}else{0.3*if(1>0){0.3}else{0.2}}",new BigDecimal("0.09")));
	}
	
	@Test
	public void testAndOrOrOrXor() {
		setLevel(OutputLevel.detail);
		
		CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
		assertTrue(calc(context,"if(true|true){10}else{0}",new BigDecimal("10")));
		assertTrue(calc(context,"if(false|true){10}else{0}",new BigDecimal("10")));
		assertTrue(calc(context,"if(false|false){10}else{0}",new BigDecimal("0")));
		assertTrue(calc(context,"if(true|true|false){10}else{0}",new BigDecimal("10")));
		assertTrue(calc(context,"if(false|false|true){10}else{0}",new BigDecimal("10")));
		assertTrue(calc(context,"if(false|false|false|true){10}else{0}",new BigDecimal("10")));
		assertTrue(calc(context,"if(false|false|false|false){10}else{0}",new BigDecimal("0")));
		assertTrue(calc(context,"if(false&true){10}else{0}",new BigDecimal("0")));
		assertTrue(calc(context,"if(false&false){10}else{0}",new BigDecimal("0")));
		assertTrue(calc(context,"if(true^true){10}else{0}",new BigDecimal("0")));
		assertTrue(calc(context,"if(false^true){10}else{0}",new BigDecimal("10")));
		assertTrue(calc(context,"if(false^false){10}else{0}",new BigDecimal("0")));
		assertTrue(calc(context,"if(1>0|10<20){10}else{0}",new BigDecimal("10")));
		assertTrue(calc(context,"if((1>0|10<20)){10}else{0}",new BigDecimal("10")));
		assertTrue(calc(context,"if((1>0|10<20)&(true)){10}else{0}",new BigDecimal("10")));
		
		context.set("number_accessCountByIPAddressInShortPeriod", 0);
		context.set("number_accessCountByCaulisCookieInShortPeriod", 0);
		context.set("number_accessCountByIPAddressInMiddlePeriod", 0);
		context.set("number_accessCountByCaulisCookieInMiddlePeriod", 0);
		
		String formula = "if(($number_accessCountByIPAddressInShortPeriod>=15.0)|($number_accessCountByCaulisCookieInShortPeriod>=10.0)|($number_accessCountByIPAddressInMiddlePeriod>=60.0)|($number_accessCountByCaulisCookieInMiddlePeriod>=30.0)){1}else{0}";
		assertTrue(calc(context,formula,new BigDecimal("0")));
		
		context.set("number_accessCountByCaulisCookieInMiddlePeriod", 100);
		assertTrue(calc(context,formula,new BigDecimal("1")));
		
		context.set("number_accessCountByIPAddressInMiddlePeriod", 100);
		context.set("number_accessCountByCaulisCookieInMiddlePeriod", 0);
		assertTrue(calc(context,formula,new BigDecimal("1")));

		context.set("number_accessCountByCaulisCookieInShortPeriod", 100);
		context.set("number_accessCountByIPAddressInMiddlePeriod", 0);
		context.set("number_accessCountByCaulisCookieInMiddlePeriod", 0);
		assertTrue(calc(context,formula,new BigDecimal("1")));

		context.set("number_accessCountByIPAddressInShortPeriod", 100);
		context.set("number_accessCountByCaulisCookieInShortPeriod", 0);
		context.set("number_accessCountByIPAddressInMiddlePeriod", 0);
		context.set("number_accessCountByCaulisCookieInMiddlePeriod", 0);
		assertTrue(calc(context,formula,new BigDecimal("1")));
		
		context.set("number_accessCountByIPAddressInShortPeriod", 0);
		context.set("number_accessCountByCaulisCookieInShortPeriod", 0);
		context.set("number_accessCountByIPAddressInMiddlePeriod", 100);
		context.set("number_accessCountByCaulisCookieInMiddlePeriod", 100);
		assertTrue(calc(context,formula,new BigDecimal("1")));
		
	}
	
	@Test
	public void testCalculatorSpeed() {
		String formula = "if(($number_accessCountByIPAddressInShortPeriod>=15.0)|($number_accessCountByCaulisCookieInShortPeriod>=10.0)|($number_accessCountByIPAddressInMiddlePeriod>=60.0)|($number_accessCountByCaulisCookieInMiddlePeriod>=30.0)){1}else{0}";
		
		CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
		
		context.set("number_accessCountByIPAddressInShortPeriod", 0);
		context.set("number_accessCountByCaulisCookieInShortPeriod", 0);
		context.set("number_accessCountByIPAddressInMiddlePeriod", 100);
		context.set("number_accessCountByCaulisCookieInMiddlePeriod", 100);
		
		long start = System.nanoTime();
		PreConstructedCalculator<T> preConstructedCalculator = preConstructedCalculator(formula);
		for(int i =0 ; i < 10000000; i++) {
			preConstructedCalculator.calculate(context);
		}
		long duration = System.nanoTime() - start;
		System.out.println(formula);
		System.out.format("calculation time:%f(microsec)\n" , ((float)duration)/10000000000f);
	}
	
	public abstract PreConstructedCalculator<T> preConstructedCalculator(String formula);

	@Test
	public void testMultipleVariableCondition() {
		setLevel(OutputLevel.detail);
		
		CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
		context.set("float_test_random", 0.2f);
		context.set("boolean_test_random", false);
		assertTrue(calc(context,"if($float_test_random<0.3&($boolean_test_random==false)){1}else{0}",new BigDecimal("1")));
		
		context.set("float_test_random", 0.5f);
		context.set("boolean_test_random", false);
		assertTrue(calc(context,"if(($float_test_random<0.3)&($boolean_test_random==false)){1}else{0}",new BigDecimal("0")));
		
		context.set("float_test_random", 0.5f);
		context.set("boolean_test_random", false);
		assertTrue(calc(context,"if(( $float_test_random < 0.3 ) ^ ( $boolean_test_random ==false)){ 1 }else{ 0 }",new BigDecimal("1")));


	}
	
	@Test
	public void testOr() {
		setLevel(OutputLevel.detail);
		
		CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
		
		String formula ="if(($number_accessPeakCountByIPAddressInLongPeriod>=10.0)|($number_accessPeakCountByCaulisCookieInLongPeriod>=5.0)){1}else{0}";
		context.set("number_accessPeakCountByIPAddressInLongPeriod", 0);
		context.set("number_accessPeakCountByCaulisCookieInLongPeriod", 0);
		assertTrue(calc(context,formula,new BigDecimal("0")));

		context.set("number_accessPeakCountByIPAddressInLongPeriod", 10);
		context.set("number_accessPeakCountByCaulisCookieInLongPeriod", 0);
		assertTrue(calc(context,formula,new BigDecimal("1")));

		context.set("number_accessPeakCountByIPAddressInLongPeriod", 0);
		context.set("number_accessPeakCountByCaulisCookieInLongPeriod", 5);
		assertTrue(calc(context,formula,new BigDecimal("1")));
	}

	
	ResultAndMatch calcWithResult(CalculationContext calculateContext , String formula , BigDecimal expected){
	    
	    Calculator<T> calculator = preConstructedCalculator(formula);
	    testAllMatch(calculator.getParser(), formula);
	    CalculateResult calculateResult = calculator.calculateReturningDetails(calculateContext);
	    calculateResult.errors.raisedException.ifPresent(error->error.printStackTrace());
	    BigDecimal x = calculateResult.answer.get();
	    System.out.format(" %s = %s \n" , formula , x.toString());
	    boolean match = 
	      expected.compareTo(x) ==0 ||
	      // this is work around for rounding error on float calculation
	      expected.subtract(x).abs().floatValue() < 0.01;

	    if(false == match) {
	      System.err.println("answer = " + x);
	      System.err.format("formatted error formula:\n  %s \n" , Formatter.format(formula));
	      System.err.println(JSON.encode(calculateContext,true));
	      System.err.println(calculator.javaCode());
	    }
	    
	    return new ResultAndMatch(match, calculateResult,x);
	  }
	 
	public static class ResultAndMatch{
	  public final boolean match;
	  public final CalculateResult calculateResult;
	  public final BigDecimal answer;
    public ResultAndMatch(boolean match, CalculateResult calculateResult, BigDecimal answer) {
      super();
      this.match = match;
      this.calculateResult = calculateResult;
      this.answer = answer;
    }
	}

	
	boolean calc(CalculationContext calculateContext , String formula , BigDecimal expected){
		
		Calculator<T> calculator = preConstructedCalculator(formula);
		testAllMatch(calculator.getParser(), formula);
		CalculateResult calculateResult = calculator.calculateReturningDetails(calculateContext);
		calculateResult.errors.raisedException.ifPresent(error->error.printStackTrace());
		if(calculateResult.errors.raisedException.isPresent()) {
		  throw new CalculationException(calculateResult.errors.raisedException.get());
		}
		BigDecimal x = calculateResult.answer.get();
		System.out.format(" %s = %s \n" , formula , x.toString());
		boolean match = 
			expected.compareTo(x) ==0 ||
			// this is work around for rounding error on float calculation
			expected.subtract(x).abs().floatValue() < 0.01;

		if(false == match) {
		  System.err.println("answer = " + x);
		  System.err.format("formatted error formula:\n  %s \n" , Formatter.format(formula));
		  System.err.println(JSON.encode(calculateContext,true));
      System.err.println(calculator.javaCode());
		}
		return match;
	}
	
	void compileOnly(CalculationContext calculateContext , String formula){
    
    Calculator<T> calculator = preConstructedCalculator(formula);
    testAllMatch(calculator.getParser(), formula);
    CalculateResult calculateResult = calculator.calculateReturningDetails(calculateContext);
    calculateResult.errors.raisedException.ifPresent(error->error.printStackTrace());
    BigDecimal x = calculateResult.answer.get();
    System.out.format(" %s = %s \n" , formula , x.toString());
  }

//	@Test
//	public void testSuggestSinANdSqrt() {
//
//		String formula = "1+1/s";
//		
//		CalculateContext context = new CalculateContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
//		CalculateResult result = calculator.calculate(context, formula);
//		System.out.println(getJson(result.parseContext.suggestsByPosition));
//		
//		int position = formula.indexOf("s");//s is invalid token in '1+1/s'
//		assertEquals(2, result.parseContext.suggestsByPosition.get(position).size());
//
//	}
	
	static String getJson(Object object){
		JSON json = new JSON();
		json.setPrettyPrint(true);
		return json.format(object);
	}
	
	@Test(expected=ParseException.class)
	public void testUnfinishedFormula() {

		String formula = "1+1/s";
		
		PreConstructedCalculator<T> calculator = preConstructedCalculator(formula);
		
		CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
		CalculateResult result = calculator.calculateReturningDetails(context);
		BigDecimal answer = result.answer.get();
		assertEquals(new BigDecimal("2"), answer);
		assertFalse(result.success);
		assertEquals("1+1", result.parseContext.getConsumed(TokenKind.consumed));
		assertEquals("/s", result.parseContext.getRemain(TokenKind.consumed));
	}
	
	@Test(expected=ParseException.class)
	public void testNotStartedFormula() {

		String formula = "s/1+1";

		PreConstructedCalculator<T> calculator = preConstructedCalculator(formula);

		CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
		CalculateResult result = calculator.calculateReturningDetails(context);
		assertFalse(result.answer.isPresent());
		assertFalse(result.success);
		assertEquals("", result.parseContext.getConsumed(TokenKind.consumed));
		assertEquals(formula, result.parseContext.getRemain(TokenKind.consumed));
		//s 's successor was already inputed , suggests is empty
		// FIXME
//		assertTrue(result.parseContext.suggestsByPosition.isEmpty());
	}
	
	@Test
	public void testTokenPosition() {

		String formula = "(1+1)/3+sin(30)";
		
		PreConstructedCalculator<T> calculator = preConstructedCalculator(formula);
		
		CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
		CalculateResult result = calculator.calculateReturningDetails(context);
		assertTrue(result.answer.isPresent());
		assertTrue(result.success);
		assertEquals("(1+1)/3+sin(30)", result.parseContext.getConsumed(TokenKind.consumed));
		assertEquals("", result.parseContext.getRemain(TokenKind.consumed));
		assertTrue(result.tokenAst.isPresent());
		Token token = result.tokenAst.get();
		TokenPrinter.output(token,System.out);
		
	}
	
	@Test
	public void testVariableIsNotPresent() {

		CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
		
		assertTrue(calc(context,"if($isExists){1}else{0}",new BigDecimal("0")));
		context.set("isExists", true);
		assertTrue(calc(context,"if($isExists){1}else{0}",new BigDecimal("1")));
		
		assertTrue(calc(context,"$count",new BigDecimal("0")));
		context.set("count", 5);
		assertTrue(calc(context,"$count",new BigDecimal("5")));
	}
	
	@Test
	public void testVariableIsPresentOperator() {

		CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
		
		assertTrue(calc(context,"if(isPresent($isExists)){5}else{10}",new BigDecimal("10")));
		assertTrue(calc(context,"if(not(isPresent($isExists))){5}else{10}",new BigDecimal("5")));
		context.set("isExists", false);
		assertTrue(calc(context,"if(isPresent($isExists)){5}else{10}",new BigDecimal("5")));
		assertTrue(calc(context,"if(not(isPresent($isExists))){5}else{10}",new BigDecimal("10")));
		
		
		assertTrue(calc(context,"if(isPresent($value)){5}else{10}",new BigDecimal("10")));
		assertTrue(calc(context,"if(not(isPresent($value))){5}else{10}",new BigDecimal("5")));
		context.set("value", 10);
		assertTrue(calc(context,"if(isPresent($value)){5}else{10}",new BigDecimal("5")));
		assertTrue(calc(context,"if(not(isPresent($value))){5}else{10}",new BigDecimal("10")));
		
		assertTrue(calc(context,"if(isPresent($name)){5}else{10}",new BigDecimal("10")));
		assertTrue(calc(context,"if(not(isPresent($name))){5}else{10}",new BigDecimal("5")));
		context.set("name", "opa");
		assertTrue(calc(context,"if(isPresent($name)){5}else{10}",new BigDecimal("5")));
		assertTrue(calc(context,"if(not(isPresent($name))){5}else{10}",new BigDecimal("10")));


		
	}
	
	@Test
	public void testStringVariable() {

		setLevel(OutputLevel.detail);

		CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
//		assertTrue(calc(context,"if($name==''){1}else{0}",new BigDecimal("1")));
		context.set("name", "opa");
		//                                  12345678901234567890123456 
		assertTrue(calc(context,"if($name==\"opa\"){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if($name!='opa'){1}else{0}",new BigDecimal("0")));
		assertTrue(calc(context,"if($name==\"opaopa\"){1}else{0}",new BigDecimal("0")));
		assertTrue(calc(context,"if($name!='opaopa'){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if($nameNoSpecified==\"opa\"){1}else{0}",new BigDecimal("0")));
	}
	
	@Test
	public void testStringLiteral() {

		setLevel(OutputLevel.detail);

		CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
//		assertTrue(calc(context,"if($name==''){1}else{0}",new BigDecimal("1")));
		context.set("name", "opa");
		//                                  12345678901234567890123456 
		assertTrue(calc(context,"if(\"opa\"==\"opa\"){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if(\"opa\"!=\"opa\"){1}else{0}",new BigDecimal("0")));
		assertTrue(calc(context,"if(\"opa\"==\"opaopa\"){1}else{0}",new BigDecimal("0")));
		assertTrue(calc(context,"if(\"opa\"!=\"opaopa\"){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if(\"opa\"==$name){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if(\"opa\"!=$name){1}else{0}",new BigDecimal("0")));
		assertTrue(calc(context,"if($name!='opa'){1}else{0}",new BigDecimal("0")));
		assertTrue(calc(context,"if($name==\"opaopa\"){1}else{0}",new BigDecimal("0")));
		assertTrue(calc(context,"if($name!='opaopa'){1}else{0}",new BigDecimal("1")));
	}
	
	@Test
	public void testStringConcat() {

		setLevel(OutputLevel.detail);

		CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
		assertTrue(calc(context,"if((\"opa\"+'opa'+\"6969\")=='opaopa6969'){1}else{0}",new BigDecimal("1")));
	}
	
	@Test
	public void testStringSlice() {

		setLevel(OutputLevel.detail);

		CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
		assertTrue(calc(context,"if('deadbeaf'[1:3]=='ea'){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if('gateman'[::-1]=='nametag'){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if('1a2b3'[::2]=='123'){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if('1a2b3'[1::2]=='ab'){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if('gateman'[::-1][0:4]=='name'){1}else{0}",new BigDecimal("1")));
	}

	
	@Test
	public void testStringTrim() {

		setLevel(OutputLevel.detail);

		CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
		context.set("name", "  opa 133\t");
		
		assertTrue(calc(context,"if(trim($name)=='opa 133'){1}else{0}",new BigDecimal("1")));
	}
	
	@Test
	public void testStringUpperAndLowerCase() {

		setLevel(OutputLevel.detail);

		CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
		context.set("name", "AlmondChocolate");
		
		assertTrue(calc(context,"if(toUpperCase($name)=='ALMONDCHOCOLATE'){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if(toUpperCase(\"AlmondChocolate\")=='ALMONDCHOCOLATE'){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if(toUpperCase(($name))=='ALMONDCHOCOLATE'){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if(toUpperCase($name[0:6])=='ALMOND'){1}else{0}",new BigDecimal("1")));

		assertTrue(calc(context,"if(toLowerCase($name)!='almondchocolate'){1}else{0}",new BigDecimal("0")));
		assertTrue(calc(context,"if(toLowerCase(\"AlmondChocolate\")!='almondchocolate'){1}else{0}",new BigDecimal("0")));
		assertTrue(calc(context,"if(toLowerCase(($name))!='almondchocolate'){1}else{0}",new BigDecimal("0")));
		assertTrue(calc(context,"if(toLowerCase(($name)[0:6]+' is delicious')!='almond is delicious'){1}else{0}",new BigDecimal("0")));
	}
	
	@Test
	public void testStringLength() {

		setLevel(OutputLevel.detail);

		CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
		context.set("name", "AlmondChocolate");
		
		assertTrue(calc(context,"if(len($name)==15){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if(len(\"AlmondChocolate\")==15){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if(len($name)-5==10){1}else{0}",new BigDecimal("1")));
	}

	@Test
	public void testStringIn() {

		setLevel(OutputLevel.detail);

		CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
		context.set("country", "jp");
		
		{
			String formula = "if($country.in('jp','ca','us')){1}else{0}";
			
			PreConstructedCalculator<T> calculator = preConstructedCalculator(formula);

			
			Token rootToken = parse(calculator.getParser(),formula).getRootToken();
			TokenPrinter.output(rootToken,System.out);
		}
		
		assertTrue(calc(context,"if($country.in('jp','ca','us')){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if(false|$country.in('jp','ca','us')){1}else{0}",new BigDecimal("1")));
		
		{
			String formula = "if((isPresent($country)&$country.in('russian-federation','china','taiwan-province-of-china','ukraine','korea-democratic-peoples-republic-of'))|(isPresent($calculated_TorNode)&$calculated_TorNode>0.0)|((isPresent($calculated_BlackIPAddressInThisSite)&$calculated_BlackIPAddressInThisSite>0.0)|(isPresent($calculated_BlackIPAddressInOtherSites)&$calculated_BlackIPAddressInOtherSites>0.0))|(isPresent($calculated_BrowserTypeIsTool)&$calculated_BrowserTypeIsTool>0.0)){1}else{0}";
			
			PreConstructedCalculator<T> calculator = preConstructedCalculator(formula);
			
			Token rootToken = parse(calculator.getParser(),formula).getRootToken();
			TokenPrinter.output(rootToken,System.out);
			assertTrue(calc(context,formula,new BigDecimal("0")));
		}
		
		assertTrue(calc(context,"if($country.in('ca','jp','us')){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if($country.in('en','ca','us')){1}else{0}",new BigDecimal("0")));
		assertTrue(calc(context,"if('cnjpuszn'[0:2].in('en','ca','us')){1}else{0}",new BigDecimal("0")));
		assertTrue(calc(context,"if('cnjpuszn'[4:6].in('en','ca','us')){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if(not('cnjpuszn'[4:6].in('en','ca','us'))){1}else{0}",new BigDecimal("0")));
		assertTrue(calc(context,"if(not(toUpperCase('cnjpuszn')[4:6].in(toUpperCase('en'),toUpperCase('ca'),toUpperCase('us')))){1}else{0}",new BigDecimal("0")));
		assertTrue(calc(context,"if(not(not(toUpperCase('cnjpuszn')[4:6].in(toUpperCase('en'),toUpperCase('ca'),toUpperCase('us'))))){1}else{0}",new BigDecimal("1")));
		
	}
	
	@Test
	public void testStringMethods() {

		setLevel(OutputLevel.detail);

		CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
		context.set("message", "I even lost my cat.");
		
		assertTrue(calc(context,"if($message.startsWith('I even')){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if($message.startsWith('i even')){1}else{0}",new BigDecimal("0")));
		assertTrue(calc(context,"if($message.endsWith('my cat.')){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if($message.endsWith('my cat')){1}else{0}",new BigDecimal("0")));
		assertTrue(calc(context,"if($message.contains('lost my ')){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if($message.contains('lose my ')){1}else{0}",new BigDecimal("0")));
		assertTrue(calc(context,"if($message.contains('lose my '[0:3]+'t')){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if($message.contains('lose my '[0:3]+'e')){1}else{0}",new BigDecimal("0")));
	}

	
	@Test
	public void testMatchCase() {

		setLevel(OutputLevel.detail);

		CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
		
		String formula="match { $country == 'en' -> 1 , $country == 'jp' -> 2 , $country == 'cn' -> 3 , \n default -> 0}";
		assertTrue(calc(context,formula ,new BigDecimal("0")));
		
		context.set("country", "en");
		assertTrue(calc(context,formula ,new BigDecimal("1")));
		
		context.set("country", "jp");
		assertTrue(calc(context,formula ,new BigDecimal("2")));

		context.set("country", "cn");
		assertTrue(calc(context,formula ,new BigDecimal("3")));
		
		context.set("country", "ca");
		assertTrue(calc(context,formula ,new BigDecimal("0")));
		
		formula="match { $country == 'en' -> 1*9 , $country == 'jp' -> 2*9 , $country == 'cn' -> 3*9 , \n default -> 30*3}";

		assertTrue(calc(context,formula ,new BigDecimal("90")));
		
		context.set("country", "en");
		assertTrue(calc(context,formula ,new BigDecimal("9")));
		
		context.set("country", "jp");
		assertTrue(calc(context,formula ,new BigDecimal("18")));

		context.set("country", "cn");
		assertTrue(calc(context,formula ,new BigDecimal("27")));
		
		context.set("country", "ca");
		assertTrue(calc(context,formula ,new BigDecimal("90")));
	}
	
	@Test
	public void testMin() {

		setLevel(OutputLevel.detail);

		CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
		context.set("count", 10);
		
		assertTrue(calc(context,"if(min(0,0)==0){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if(min(-1,0)==-1){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if(min(-1,-2)==-2){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if(min(1,0)==0){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if(min($count,5)==5){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if(min($count,2*3)==6){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if(min($count*0.5,2*3)==5){1}else{0}",new BigDecimal("1")));
	}

	@Test
	public void testMax() {

		setLevel(OutputLevel.detail);

		CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
		context.set("count", 10);

		assertTrue(calc(context,"if(max(0,0)==0){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if(max(-1,0)==0){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if(max(-1,-2)==-1){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if(max(1,0)==1){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if(max($count,5)==10){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if(max($count,4*3)==12){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if(max($count*2,4*3)==20){1}else{0}",new BigDecimal("1")));
	}
	
	@Test
	public void testRandom() {

		setLevel(OutputLevel.detail);

		CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
		context.set("count", 10);

		assertTrue(calc(context,"if(random()<=1.0){1}else{0}",new BigDecimal("1")));
		assertTrue(calc(context,"if(random()>=0){1}else{0}",new BigDecimal("1")));
	}
	
	@Test
	public void testFunctions() {
		String[][] functions = new String[][]{
			{"OriginalSpec_AnyBlackList", "if((isPresent($calculated_BlackIPAddressInThisSite)&$calculated_BlackIPAddressInThisSite>0.0)|(isPresent($calculated_BlackCaulisCookieInThisSite)&$calculated_BlackCaulisCookieInThisSite>0.0)){10}else{if((isPresent($calculated_BlackIPAddressInOtherSites)&$calculated_BlackIPAddressInOtherSites>0.0)|(isPresent($calculated_BlackCaulisCookieInOtherSites)&$calculated_BlackCaulisCookieInOtherSites>0.0)){5}else{0}}"},
			{"OriginalSpec_SuspiciousProvider", "if((isPresent($calculated_AccessFromDataCenter)&$calculated_AccessFromDataCenter>0.0)|(isPresent($calculated_TorNode)&$calculated_TorNode>0.0)|(isPresent($calculated_AnonymizingVpn)&$calculated_AnonymizingVpn>0.0)){10}else{0}"},
			{"OriginalSpec_OtherProvider", "0"},
			{"UnknownUsingEmulator", "if((isPresent($calculated_UnknownUsingEmulator)&$calculated_UnknownUsingEmulator>0.0)){10}else{0}"},
			{"Jailbreak", "if((isPresent($calculated_Jailbreak)&$calculated_Jailbreak>0.0)){5}else{0}"},
			{"OriginalSpec_OverseaAccess", "if(isPresent($country)&$country!='japan'){5}else{0}"},
			{"OriginalSpec_NotJapaneseLanguage", "if(isPresent($splanguage)&not($splanguage.in('ja','ja-JP'))){5}else{0}"},
			{"OriginalSpec_MultipleAccess", "if((isPresent($number_accessCountByIPAddressInShortPeriod)&$number_accessCountByIPAddressInShortPeriod>0.0)|(isPresent($number_accessCountByIPAddressInMiddlePeriod)&$number_accessCountByIPAddressInMiddlePeriod>0.0)|(isPresent($number_accessCountByCaulisCookieInShortPeriod)&$number_accessCountByCaulisCookieInShortPeriod>0.0)|(isPresent($number_accessCountByCaulisCookieInMiddlePeriod)&$number_accessCountByCaulisCookieInMiddlePeriod>0.0)|(isPresent($userCountGroupedByCookieOnThisSite)&$userCountGroupedByCookieOnThisSite>0.0)){5}else{0}"},
			{"POST_PROCESS_OriginalSpec_totalScore", "$calculated_OriginalSpec_AnyBlackList+$calculated_OriginalSpec_SuspiciousProvider+$calculated_OriginalSpec_OtherProvider+if(isPresent($calculated_UnknownUsingEmulator)){$calculated_UnknownUsingEmulator}else{0}+if(isPresent($calculated_Jailbreak)){$calculated_Jailbreak}else{0}+$calculated_OriginalSpec_OverseaAccess+$calculated_OriginalSpec_NotJapaneseLanguage+$calculated_OriginalSpec_MultipleAccess"},
			{"POST_PROCESS_OriginalSpec_ChineseLanguageOrNotJapanTimezone", "if((isPresent($priorityLanguage)&$priorityLanguage==\"zh-CN\")|(isPresent($timezone)&$timezone!=\"+9\")){1}else{0}"},
			{"POST_PROCESS_OriginalSpec_RiskyCountry", "if(isPresent($country)&$country.in(\"korea-democratic-peoples-republic-of\",\"iran\",\"iran-islamic-republic-of\",\"cuba\",\"syria\",\"syrian-arab-republic\",\"sudan\")){1}else{0}"},
			{"POST_PROCESS_RELATIVE_SUSPICIOUS", "if($ForcedRelativeSuspiciousValue1){1919}else{if($ForcedRelativeSuspiciousValue5){5}else{if(($POST_PROCESS_OriginalSpec_RiskyCountry>0.0)|(isPresent($calculated_TorNode)&$calculated_TorNode>0.0)|(isPresent($calculated_BrowserTypeIsTool)&$calculated_BrowserTypeIsTool>0.0)){5}else{if(($POST_PROCESS_OriginalSpec_ChineseLanguageOrNotJapanTimezone>0.0)){4}else{if($default_RelativeSuspiciousValue==5){4}else{$default_RelativeSuspiciousValue}}}}}"},
			{"POST_PROCESS_OriginalSpec_OverseaAccess", "if(isPresent($country)&$country!=\"japan\"){1}else{0}"},
			{"POST_PROCESS_OriginalSpec_MultiUserAccessToOneAccount", "if(isPresent($cookieCountGroupedByUser)&$cookieCountGroupedByUser>=10.0){1}else{0}"},
			{"POST_PROCESS_OriginalSpec_OneUserAccessToMultiAccount", "if(isPresent($userCountGroupedByCookieOnThisSite)&$userCountGroupedByCookieOnThisSite>=5){1}else{0}"},
			{"POST_PROCESS_OriginalSpec_BlackListOnOtherSites", "if((isPresent($calculated_BlackIPAddressInOtherSites)&$calculated_BlackIPAddressInOtherSites>0.0)|(isPresent($calculated_BlackCaulisCookieInOtherSites)&$calculated_BlackCaulisCookieInOtherSites>0.0)){1}else{0}"},
			{"POST_PROCESS_OriginalSpec_RiskyCountry", "if(isPresent($country)&$country.in(\"korea-democratic-peoples-republic-of\",\"iran\",\"iran-islamic-republic-of\")){1}else{0}"},
			{"POST_PROCESS_RELATIVE_SUSPICIOUS", "if($ForcedRelativeSuspiciousValue1){1}else{if($ForcedRelativeSuspiciousValue5){5}else{if($default_RelativeSuspiciousValue==5){5}else{if($POST_PROCESS_OriginalSpec_RiskyCountry>0.0){5}else{if(($POST_PROCESS_OriginalSpec_OverseaAccess>0.0)|($POST_PROCESS_OriginalSpec_MultiUserAccessToOneAccount>0.0)|($POST_PROCESS_OriginalSpec_OneUserAccessToMultiAccount>0.0)|($POST_PROCESS_OriginalSpec_BlackListOnOtherSites>0.0)|(isPresent($calculated_BrowserTypeIsTool)&$calculated_BrowserTypeIsTool>0.0)){4}else{$default_RelativeSuspiciousValue}}}}}"},
			{"OriginalSpec_ChineseLanguage", "if(isPresent($priorityLanguage)&$priorityLanguage==\"zh-CN\"){50}else{0}"},
			{"OriginalSpec_AnyBlackList", "if((isPresent($calculated_BlackIPAddressInThisSite)&$calculated_BlackIPAddressInThisSite>0.0)|(isPresent($calculated_BlackCaulisCookieInThisSite)&$calculated_BlackCaulisCookieInThisSite>0.0)){10}else{if((isPresent($calculated_BlackIPAddressInOtherSites)&$calculated_BlackIPAddressInOtherSites>0.0)|(isPresent($calculated_BlackCaulisCookieInOtherSites)&$calculated_BlackCaulisCookieInOtherSites>0.0)){5}else{0}}"},
			{"OriginalSpec_SuspiciousProvider", "if((isPresent($calculated_AccessFromDataCenter)&$calculated_AccessFromDataCenter>0.0)|(isPresent($calculated_TorNode)&$calculated_TorNode>0.0)|(isPresent($calculated_AnonymizingVpn)&$calculated_AnonymizingVpn>0.0)){10}else{0}"},
			{"OriginalSpec_OtherProvider", "0"},
			{"UnknownUsingEmulator", "if((isPresent($calculated_UnknownUsingEmulator)&$calculated_UnknownUsingEmulator>0.0)){10}else{0}"},
			{"Jailbreak", "if((isPresent($calculated_Jailbreak)&$calculated_Jailbreak>0.0)){5}else{0}"},
			{"OriginalSpec_OverseaAccess", "if(isPresent($country)&$country!='japan'){5}else{0}"},
			{"OriginalSpec_NotJapaneseLanguage", "if(isPresent($splanguage)&not($splanguage.in('ja','ja-JP'))){5}else{0}"},
			{"OriginalSpec_MultipleAccess", "if((isPresent($number_accessCountByIPAddressInShortPeriod)&$number_accessCountByIPAddressInShortPeriod>0.0)|(isPresent($number_accessCountByIPAddressInMiddlePeriod)&$number_accessCountByIPAddressInMiddlePeriod>0.0)|(isPresent($number_accessCountByCaulisCookieInShortPeriod)&$number_accessCountByCaulisCookieInShortPeriod>0.0)|(isPresent($number_accessCountByCaulisCookieInMiddlePeriod)&$number_accessCountByCaulisCookieInMiddlePeriod>0.0)|(isPresent($userCountGroupedByCookieOnThisSite)&$userCountGroupedByCookieOnThisSite>0.0)){5}else{0}"},
			{"POST_PROCESS_OriginalSpec_totalScore", "$calculated_OriginalSpec_AnyBlackList+$calculated_OriginalSpec_SuspiciousProvider+$calculated_OriginalSpec_OtherProvider+if(isPresent($calculated_UnknownUsingEmulator)){$calculated_UnknownUsingEmulator}else{0}+if(isPresent($calculated_Jailbreak)){$calculated_Jailbreak}else{0}+$calculated_OriginalSpec_OverseaAccess+$calculated_OriginalSpec_NotJapaneseLanguage+$calculated_OriginalSpec_MultipleAccess"},
			{"japanese","if(isPresent($splanguage)&not($splanguage.in('ja','ja-JP','日本語'))){5}else{0}"}			
		};
		
		CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
		setLevel(OutputLevel.detail);
		for (String[] tuple : functions) {
			String title = tuple[0];
			String function = tuple[1];
			System.out.println(title);
			assertTrue(calc(context,function ,new BigDecimal("0")));
		}
	}
	
	public Class<?> getTestClass(){
		return CalculatorImplTest.class;
	}

  @Test
  public void testCompilationFailedFunctions() {
	
    String[][] functions = new String[][]{
      {"POST_PROCESS_OriginalSpec_CountryIsNotJapan", "if((isPresent($countryCode)&$countryCode!=\"JP\")&((isPresent($osGroup)&toLowerCase($osGroup).in(\"ios\"))&(isPresent($browserGroup)&toLowerCase($browserGroup).contains(\"safari\")))&(((isPresent($timezone)&$timezone=='+9')&(isPresent($priorityLanguage)&not($priorityLanguage.contains('ja'))))|((isPresent($timezone)&$timezone!='+9')&(isPresent($priorityLanguage)&$priorityLanguage.contains('ja'))))){1}else{0}"},
      {"POST_PROCESS_OriginalSpec_BlackListOnOtherSites", "if((isPresent($calculated_BlackIPAddressInOtherSites)&$calculated_BlackIPAddressInOtherSites>0.0)|(isPresent($calculated_BlackCaulisCookieInOtherSites)&$calculated_BlackCaulisCookieInOtherSites>0.0)){1}else{0}"},
      {"POST_PROCESS_OriginalSpec_SuspiciousProvider", "if(isPresent($calculated_TorNode)&$calculated_TorNode>0.0){1}else{0}"},
      {"POST_PROCESS_OriginalSpec_OneUserAccessToMultiAccount", "if((isPresent($userCountGroupedByCookieOnThisSite)&$userCountGroupedByCookieOnThisSite>=2)&((isPresent($os)&(not(toLowerCase($os).contains(\"linux\"))|not(toLowerCase($os).contains(\"Fire OS\"))))|(isPresent($number_accountCreationCountByIpAddress)&isPresent($userCountGroupedByCookieOnThisSite)&not($number_accountCreationCountByIpAddress - $userCountGroupedByCookieOnThisSite>=1))|(isPresent($userCountGroupedByCookieOnAllSite)&isPresent($userCountGroupedByCookieOnThisSite)&isPresent($userCountGroupedByCookieOnThisSiteOn12H)&(not($userCountGroupedByCookieOnAllSite - $userCountGroupedByCookieOnThisSite>=1)&not($userCountGroupedByCookieOnThisSite - $userCountGroupedByCookieOnThisSiteOn12H==0))))){1}else{0}"},
      {"POST_PROCESS_RELATIVE_SUSPICIOUS", "if(not(isPresent($calculated_FirstAccessUserHash))){1}else{if($ForcedRelativeSuspiciousValue1){1}else{if($ForcedRelativeSuspiciousValue5){5}else{if($default_RelativeSuspiciousValue==5){5}else{if(($POST_PROCESS_OriginalSpec_CountryIsNotJapan>0.0)|($POST_PROCESS_OriginalSpec_BlackListOnOtherSites>0.0)|($POST_PROCESS_OriginalSpec_SuspiciousProvider>0.0)|($POST_PROCESS_OriginalSpec_OneUserAccessToMultiAccount>0.0)){5}else{$default_RelativeSuspiciousValue}}}}}"}
    };
    CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
    setLevel(OutputLevel.detail);
    for (String[] tuple : functions) {
      String title = tuple[0];
      String function = tuple[1];
      System.out.println(title);
      compileOnly(context,function);
    }
  }
  
  @Test
  public void testNotEqEQ() {

    setLevel(OutputLevel.detail);

    CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
    context.set("userCountGroupedByCookieOnThisSite", 10);
    context.set("userCountGroupedByCookieOnThisSite", 10);

    assertTrue(calc(context,"if(not(($userCountGroupedByCookieOnThisSite - $userCountGroupedByCookieOnThisSiteOn12H)==0)){1}else{0}",new BigDecimal("1")));
//    assertTrue(calc(context,"if(not(0==($userCountGroupedByCookieOnThisSite - $userCountGroupedByCookieOnThisSiteOn12H))){1}else{0}",new BigDecimal("1")));
  }
  
  @Test
  public void testSideEffectWithSpecifiedArgumnent() {

    /* 以下のメソッドをよびだす
     * 
    public float booleanToFloatMethod(CalculationContext calculationContext, boolean inputValue) {
      
      return inputValue ? 69f:6969f;
    }
     */


    setLevel(OutputLevel.detail);

    CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
    context.set("isMale", true);

    //以下のテストではエラーが出るようにした。Contextにorg.unlaxer.tinyexpression.parser.TestSideEffectorをセットしていないので
    assertThrows(CalculationException.class, ()->
      calc(context,
        "external returning as number : org.unlaxer.tinyexpression.parser.TestSideEffector#booleanToFloatMethod($isMale as boolean)",
        new BigDecimal("0")));

    // defaultは廃止
    assertThrows(ParseException.class, ()->
      calc(context,
        "external returning as number default 9+(8/4): org.unlaxer.tinyexpression.parser.TestSideEffector#booleanToFloatMethod($isMale as boolean)",
        new BigDecimal("11")));

    assertThrows(CalculationException.class, ()->
      calc(context,
        "external returning as number : org.unlaxer.tinyexpression.parser.TestSideEffector#booleanToFloatMethod(1==0)",
        new BigDecimal("0")));

    // parse時にはTestSIでEffector＃booleanToFloatMethodの戻り値の型が何であるかは特定できないためreturningを指定する必要がある
    // しかし後方互換性の為にreturningを無くした場合にreturnの型はfloatになるようにしている。
    // なのでRuntimeExceptionの動きは正しい
//    /V1Test_CalculatorClass8968366204185308402.java:13: エラー: 不適合な型: java.lang.Booleanをjava.lang.Floatに変換できません:
//      function0.map(_function->_function.booleanToFloatMethod(calculateContext , (calculateContext.getBoolean("isMale").orElse(false)))).orElse((calculateContext.getBoolean("isMale").orElse(false)))
    assertThrows(RuntimeException.class, ()->
      calc(context,
          "external : org.unlaxer.tinyexpression.parser.TestSideEffector#booleanToFloatMethod($isMale as boolean)",
          new BigDecimal("0"))
    );
    
    context.set(new TestSideEffector());
    
    assertTrue(calc(context,
        "external returning as number : org.unlaxer.tinyexpression.parser.TestSideEffector#booleanToFloatMethod('a'=='a')",
        new BigDecimal("69")));
    
    assertTrue(calc(context,
        "external returning as number : org.unlaxer.tinyexpression.parser.TestSideEffector#booleanToFloatMethod('a'!='a')",
        new BigDecimal("6969")));
    
    assertTrue(calc(context,
        "external returning as number : org.unlaxer.tinyexpression.parser.TestSideEffector#booleanToFloatMethod($isMale as boolean)",
        new BigDecimal("69")));
    
    context.set("isMale", false);
    
    assertTrue(calc(context,
        "external returning as number : org.unlaxer.tinyexpression.parser.TestSideEffector#booleanToFloatMethod($isMale as boolean)",
        new BigDecimal("6969")));

  }
  
  @Test
  public void testSideEffectWithSpecifiedArgumnent2() {
    
    /* 以下のメソッドをよびだす
     * 
  public float salary(CalculationContext calculationContext, float averageSalary , String name) {
    
    return name.contains("Dr.") ? averageSalary * 2 : averageSalary;
  }
     */
    setLevel(OutputLevel.detail);

    CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
    context.set(new TestSideEffector());

    assertTrue(calc(context,
        "external /*comment */ returning as number : org.unlaxer.tinyexpression.parser.TestSideEffector#salary(12*300000,'Mr.robot')",
        new BigDecimal(12*300000)));
    
    assertTrue(calc(context,
        "external /*comment */ returning as number : org.unlaxer.tinyexpression.parser.TestSideEffector#salary(12*300000,'Dr.house')",
        new BigDecimal(12*300000*2)));
  }
  
  @Test
  public void testStringIfExpression() {
    setLevel(OutputLevel.detail);
    
    CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
    assertTrue(calc(context,
        "if('niku' == if(1==1){'niku'}else{'sushi'}){100}else{0}",
        new BigDecimal(100)));
    
    assertTrue(calc(context,
        "if('niku' == if(1==1){'nikuniku'[0:4]}else{'sushi'}){100}else{0}",
        new BigDecimal(100)));

    assertTrue(calc(context,
        "if('niku' == if(1==1){'nikuniku'[0:3]}else{'sushi'}){100}else{0}",
        new BigDecimal(0)));
    
    assertTrue(calc(context,
        "if('niku' == if(1==0){'niku'}else{'sushi'}){100}else{0}",
        new BigDecimal(0)));

  }
  
  @Test
  public void testBooleanIfExpression() {
    setLevel(OutputLevel.detail);
    
    CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
    assertTrue(calc(context,
        "if((1==1) == if(1==1){'niku'=='niku'}else{'niku'=='sushi'}){100}else{0}",
        new BigDecimal(100)));
    
    assertTrue(calc(context,
        "if((1==1) == if(1==1){'nikuniku'[0:4]=='niku'}else{1*3==4}){100}else{0}",
        new BigDecimal(100)));

    assertTrue(calc(context,
        "if((10==10) == if(1==1){false}else{true}){100}else{0}",
        new BigDecimal(0)));
  }
  
  @Test
  public void testIfExpression() {
    
    CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
    String formula = "if($ForcedRelativeSuspiciousValue1){1919}else{if($ForcedRelativeSuspiciousValue5){5}else{if(($POST_PROCESS_OriginalSpec_RiskyCountry>0.0)|(isPresent($calculated_TorNode)&$calculated_TorNode>0.0)|(isPresent($calculated_BrowserTypeIsTool)&$calculated_BrowserTypeIsTool>0.0)){5}else{if(($POST_PROCESS_OriginalSpec_ChineseLanguageOrNotJapanTimezone>0.0)){4}else{if($default_RelativeSuspiciousValue==5){4}else{$default_RelativeSuspiciousValue}}}}}";

    NumberIfExpressionParser numberIfExpressionParser = new NumberIfExpressionParser();
    TestResult testAllMatch = testAllMatch(numberIfExpressionParser, formula);
    String string = TokenPrinter.get(testAllMatch.parsed.getRootToken(true));
    System.out.println(string);
    
    ResultAndMatch calcWithResult = calcWithResult(context, formula, new BigDecimal("0"));
    
    calcWithResult.calculateResult.operatorOperandTreeToken
      .map(TinyExpressionTokens::getTinyExpressionToken)
      .map(TokenPrinter::get)
      .ifPresent(System.out::println);;
    
    assertTrue(calc(context,
        formula,
        new BigDecimal("0")));
    
  }
  
  
  @Test
  public void testNumberMatch() {
    
    setLevel(OutputLevel.detail);

    CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
    context.set("count", 10);
    context.set("defaultValue", 0);

    assertTrue(calc(context,"match{1==1->1,default->0}",new BigDecimal("1")));
    assertTrue(calc(context,"match{1==1->$count as number ,default->$defaultValue}",new BigDecimal("10")));
//    assertFalse(calc(context,"match{1==1->$count,default->$defaultValue}",new BigDecimal("10")));
  }
  
  @Test
  public void testBooleanMatch() {
    
    setLevel(OutputLevel.detail);

    CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
    context.set("isMale", true);
    context.set("defaultValue", false);

    assertTrue(calc(context,"if(match{1==1->1==1,default->1==0}){1}else{0}",new BigDecimal("1")));
    assertTrue(calc(context,"if(match{1==1->$isMale as boolean ,default->$defaultValue}){10}else{0}",new BigDecimal("10")));
//    assertFalse(calc(context,"match{1==1->$count,default->$defaultValue}",new BigDecimal("10")));
  }
  
  @Test
  public void testStringMatch() {
    
    setLevel(OutputLevel.detail);

    CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
    context.set("lunch", "yakiniku");
    context.set("defaultValue", "gyuudon");

    assertTrue(calc(context,"if('niku'== match{1==1->'niku',default->'sushi'}){1}else{0}",new BigDecimal("1")));
    assertTrue(calc(context,"if('yakiniku'==match{1==1->$lunch as string,default->$defaultValue}){10}else{0}",new BigDecimal("10")));
//    assertFalse(calc(context,"match{1==1->$count,default->$defaultValue}",new BigDecimal("10")));
  }

  
  @Test
  public void testImportClass() {
    
    setLevel(OutputLevel.detail);
    
    CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
    context.set(new Fee());
    context.set("age", 18);
    context.set("taxRate", 0.1f);

    {
      SimpleBuilder simpleBuilder = new SimpleBuilder();
      
      simpleBuilder
        .line("import org.unlaxer.tinyexpression.Fee#calculate as calculate;")
        .n()
        .line("  external returning number : calculate($age as number ,1000,$taxRate as number)");
      
      assertTrue(calc(context,simpleBuilder.toString(),new BigDecimal("1100")));
    }
    
    {
      SimpleBuilder simpleBuilder = new SimpleBuilder();
      
      simpleBuilder
//      .line("import org.unlaxer.tinyexpression.Fee as Fee;")
        .n()
        .line("  external returning number : org.unlaxer.tinyexpression.Fee#calculate($age as number ,1000,$taxRate as number)");
      
      assertTrue(calc(context,simpleBuilder.toString(),new BigDecimal("1100")));
    }
    
    {
      SimpleBuilder simpleBuilder = new SimpleBuilder();
      
      simpleBuilder
        .line("import org.unlaxer.tinyexpression.Fee as Fee;")
        .n()
        .line("  external returning number : Fee#calculate($age as number ,1000,$taxRate as number)");
      
      assertTrue(calc(context,simpleBuilder.toString(),new BigDecimal("1100")));
    }
  }
  
  @Test
  public void testVariableDeclarations() {
    
    setLevel(OutputLevel.detail);
    
    CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
    context.set(new Fee());
//    context.set("age", 18);
    context.set("taxRate", 0.1f);

    //18才未満はタダ。18才以上は定価*税金
    {
      SimpleBuilder simpleBuilder = new SimpleBuilder();
      
      simpleBuilder
        .line("import org.unlaxer.tinyexpression.Fee#calculate as calculate;")
        .line("var $age as number set if not exists 10+8 description='年齢';")
        .n()
        .line("external number calculate($age as number ,1000,$taxRate as number)");
      
      assertTrue(calc(context,simpleBuilder.toString(),new BigDecimal("1100")));
    }
    
    {
      SimpleBuilder simpleBuilder = new SimpleBuilder();
      
      simpleBuilder
//      .line("import org.unlaxer.tinyexpression.Fee as Fee;")
      .n()
      .line("  external returning number : org.unlaxer.tinyexpression.Fee#calculate($age as number ,1000,$taxRate as number)");
      
      assertTrue(calc(context,simpleBuilder.toString(),new BigDecimal("0")));
    }
    
    context.set("age", 18);
    {
      SimpleBuilder simpleBuilder = new SimpleBuilder();
      
      simpleBuilder
        .line("import org.unlaxer.tinyexpression.Fee as Fee;")
        .n()
        .line("  external returning number : Fee#calculate($age as number ,1000,$taxRate as number)");
      
      assertTrue(calc(context,simpleBuilder.toString(),new BigDecimal("1100")));
    }
    
    {
      SimpleBuilder simpleBuilder = new SimpleBuilder();
      
      simpleBuilder
        .line("import org.unlaxer.tinyexpression.Fee as Fee;")
        .line("var $age as number set if not exists 5 description='年齢';")
        .n()
        .line("  external returning number : Fee#calculate($age as number ,1000,$taxRate as number)");
      
      assertTrue(calc(context,simpleBuilder.toString(),new BigDecimal("1100")));
    }
    
    {
      SimpleBuilder simpleBuilder = new SimpleBuilder();
      
      simpleBuilder
        .line("import org.unlaxer.tinyexpression.Fee as Fee;")
        .line("var $age as number set 3+5 description='年齢';")
        .n()
        .line("  external returning number : Fee#calculate($age as number ,1000,$taxRate as number)");
      
      assertTrue(calc(context,simpleBuilder.toString(),new BigDecimal("0")));
    }
  }
  
  @Test
  public void testBooleanVariableDeclarations() {
    
    setLevel(OutputLevel.detail);
    
    CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
    context.set(new Fee());
    context.set("age", 18);
    context.set("taxRate", 0.1f);
    {
      SimpleBuilder simpleBuilder = new SimpleBuilder();
      
      simpleBuilder
        .line("import org.unlaxer.tinyexpression.Fee#calculate as calculate;")
        .line("var $free as boolean set if not exists true description='タダかどうか';")
        .n()
        .line("external number calculate($age as number ,if($free){0}else{1000},$taxRate as number)");
      
      assertTrue(calc(context,simpleBuilder.toString(),new BigDecimal("0")));
    }
    
    {
      SimpleBuilder simpleBuilder = new SimpleBuilder();
      
      simpleBuilder
        .line("import org.unlaxer.tinyexpression.Fee#calculate as calculate;")
        .line("var $free as boolean set false description='タダかどうか';")
        .n()
        .line("external number calculate($age as number ,if($free){0}else{1000},$taxRate as number)");
      
      assertTrue(calc(context,simpleBuilder.toString(),new BigDecimal("1100")));
    }
  }
  
  @Test
  public void testStringVariableDeclarations() {
    
    setLevel(OutputLevel.detail);
    
    CalculationContext context = new ConcurrentCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
    context.set(new Fee());
    context.set("age", 18);
    context.set("taxRate", 0.1f);
    {
      SimpleBuilder simpleBuilder = new SimpleBuilder();
      
      simpleBuilder
        .line("import org.unlaxer.tinyexpression.Fee#calculate as calculate;")
        .line("var $name as string set if not exists '渡辺' description='苗字を設定します';")
        .n()
        .line("external number calculate($age as number ,if($name=='渡辺'){0}else{1000},$taxRate as number)");
      
      assertTrue(calc(context,simpleBuilder.toString(),new BigDecimal("0")));
    }
    
    {
      SimpleBuilder simpleBuilder = new SimpleBuilder();
      
      simpleBuilder
        .line("import org.unlaxer.tinyexpression.Fee#calculate as calculate;")
        .line("var $name as string set '鈴木' description='苗字を設定します';")
        .n()
        .line("external number calculate($age as number ,if($name=='渡辺'){0}else{1000},$taxRate as number)");
      
      assertTrue(calc(context,simpleBuilder.toString(),new BigDecimal("1100")));
    }
  }
}