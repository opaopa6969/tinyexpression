package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.TestResult;
import org.unlaxer.Token;
import org.unlaxer.TokenPrinter;
import org.unlaxer.listener.OutputLevel;


public class SideEffectBooleanExpressionParserTest extends ParserTestBase{

	@Test
	public void test() {
		
		setLevel(OutputLevel.detail);
		
		@SuppressWarnings("deprecation")
    SideEffectBooleanExpressionParser sideEffectBooleanExpressionParser = new SideEffectBooleanExpressionParser();
		
		testAllMatch(sideEffectBooleanExpressionParser,("with side effect:org.unlaxer.tinyexpression.parser.TestSideEffector#setWhiteList(true)"));
		testAllMatch(sideEffectBooleanExpressionParser,("with side effect:org.unlaxer.tinyexpression.parser.TestSideEffector#setWhiteList(1==1)"));
		testAllMatch(sideEffectBooleanExpressionParser,("with side effect:org.unlaxer.tinyexpression.parser.TestSideEffector#setWhiteList(not(true))"));
		testAllMatch(sideEffectBooleanExpressionParser,("with side effect:org.unlaxer.tinyexpression.parser.TestSideEffector#setWhiteList(with side effect:org.unlaxer.tinyexpression.parser.TestSideEffector#setWhiteList(true))"));
		testAllMatch(sideEffectBooleanExpressionParser,("with side effect:org.unlaxer.tinyexpression.parser.TestSideEffector#setWhiteList(true,'niku',0,true)"));
//		testAllMatch(sideEffectExpressionParser,("with side effect:jp.caulis.calc.Effects#foo(1+3*4+len('foo'),true|1==3,'niku')"));
//		testAllMatch(sideEffectExpressionParser,("with side effect:jp.caulis.calc.Effects#foo(0,true,'niku')"));
//		testUnMatch(sideEffectExpressionParser,("with side effect::jp.caulis.calc.Effects#foo(0,true,'niku')"));
//
//		testAllMatch(sideEffectExpressionParser,("with side effect:jp.caulis.calc.Effects#foo(10,$hour>0)"));
//		testAllMatch(sideEffectExpressionParser,("with side effect:jp.caulis.calc.Effects#foo(10,$hour>0 & $hour<5)"));
		
		{
			String formula =
//					"with side effect:jp.caulis.calc.Effects#foo(true,0,'niku')";
					"with side effect:org.unlaxer.tinyexpression.parser.TestSideEffector#setWhiteList(with side effect:org.unlaxer.tinyexpression.parser.TestSideEffector#setWhiteList(true))";
			
			TestResult testAllMatch = testAllMatch(sideEffectBooleanExpressionParser, formula,false);
			
			Token rootToken = testAllMatch.parsed.getRootToken();
			
			String string = TokenPrinter.get(rootToken);
			System.out.println(string);
		}
	}
	
//	@Test
//	public void testFormula() {
//		
//		SideEffectExpressionParser sideEffectExpressionParser = new SideEffectExpressionParser();
//		
//		String[] formulas= {
//				"with side effect:jp.caulis.calc.Effects#foo(true)",
//				"with side effect:jp.caulis.calc.Effects#foo(true)",
//				"with side effect:jp.caulis.calc.Effects#foo(true)"
//		};
//		
//		for (String formula : formulas) {
//			
//			System.out.println(formula);
//			System.out.println();
//			
//			Parsed parsed = parse(sideEffectExpressionParser, formula);
//			Token rootToken = parsed.getRootToken();
//			TokenPrinter.output(rootToken, System.out);
//			
//			System.out.println();
//		}
////		String formula = "with side effect:jp.caulis.calc.Effects#foo(1+3*4+len('foo'),1==3,'niku')";
////		String formula = "with side effect:jp.caulis.calc.Effects#foo(1+3*4,1==3,'niku')";
////		String formula = "with side effect:jp.caulis.calc.Effects#foo(1,true | false ,'niku')";
////	TestResult result = testAllMatch(sideEffectExpressionParser,formula,false);
//		
//	}

}
