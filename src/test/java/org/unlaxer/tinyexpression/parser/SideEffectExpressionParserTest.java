package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.Parsed;
import org.unlaxer.ParserTestBase;
import org.unlaxer.TestResult;
import org.unlaxer.Token;
import org.unlaxer.TokenPrinter;
import org.unlaxer.listener.OutputLevel;


public class SideEffectExpressionParserTest extends ParserTestBase{

	@Test
	public void test() {
		
		setLevel(OutputLevel.detail);
		
		SideEffectExpressionParser sideEffectExpressionParser = new SideEffectExpressionParser();
		
		testAllMatch(sideEffectExpressionParser,("with side effect:org.unlaxer.tinyexpression.parser.TestSideEffector#setWhiteList(10)"));
		testAllMatch(sideEffectExpressionParser,("with side effect:jp.caulis.calc.Effects#foo(1+3*4+len('foo'),true|1==3,'niku')"));
		testAllMatch(sideEffectExpressionParser,("with side effect:jp.caulis.calc.Effects#foo(0,true,'niku')"));
		testUnMatch(sideEffectExpressionParser,("with side effect::jp.caulis.calc.Effects#foo(0,true,'niku')"));

		testAllMatch(sideEffectExpressionParser,("with side effect:jp.caulis.calc.Effects#foo(10,$hour>0)"));
		testAllMatch(sideEffectExpressionParser,("with side effect:jp.caulis.calc.Effects#foo(10,$hour>0 & $hour<5)"));
    testAllMatch(sideEffectExpressionParser,("external:jp.caulis.calc.Effects#foo(10,$hour>0 & $hour<5)"));
		
		{
			String formula =
					"with side effect:jp.caulis.calc.Effects#foo(0,true,'niku')";
			
			TestResult testAllMatch = testAllMatch(sideEffectExpressionParser, formula,false);
			
			Token rootToken = testAllMatch.parsed.getRootToken();
			
			String string = TokenPrinter.get(rootToken);
			System.out.println(string);
		}
	}
	
	@Test
	public void testFormula() {
		
		SideEffectExpressionParser sideEffectExpressionParser = new SideEffectExpressionParser();
		
		String[] formulas= {
				"with side effect:jp.caulis.calc.Effects#foo(1+3*4+len('foo'),1==3,'niku')",
				"with side effect:jp.caulis.calc.Effects#foo(1+3*4,1==3,'niku')",
				"with side effect:jp.caulis.calc.Effects#foo(1,true | false ,'niku')"
		};
		
		for (String formula : formulas) {
			
			System.out.println(formula);
			System.out.println();
			
			Parsed parsed = parse(sideEffectExpressionParser, formula);
			Token rootToken = parsed.getRootToken();
			TokenPrinter.output(rootToken, System.out);
			
			System.out.println();
		}
//		String formula = "with side effect:jp.caulis.calc.Effects#foo(1+3*4+len('foo'),1==3,'niku')";
//		String formula = "with side effect:jp.caulis.calc.Effects#foo(1+3*4,1==3,'niku')";
//		String formula = "with side effect:jp.caulis.calc.Effects#foo(1,true | false ,'niku')";
//	TestResult result = testAllMatch(sideEffectExpressionParser,formula,false);
		
	}
	
	@Test
	public void testTypeHints() {
	  
	   SideEffectExpressionParser sideEffectExpressionParser = new SideEffectExpressionParser();
	    
	   testAllMatch(sideEffectExpressionParser,
	       "external returning as float default 0 :jp.caulis.calc.Effects#foo($foo as float , $bar as String , $hoge as boolean)");
	   testAllMatch(sideEffectExpressionParser,
	       "external returning as boolean default false :jp.caulis.calc.Effects#foo($foo as Number , $bar as string , $hoge as Boolean)");
     testAllMatch(sideEffectExpressionParser,
         "external returning as String default '' :jp.caulis.calc.Effects#foo($foo as Number , $bar as string , $hoge as Boolean)");
     
     testAllMatch(sideEffectExpressionParser,
         "external returning as float default 0 :org.unlaxer.tinyexpression.parser.TestSideEffector#floatToFloatMethod(1)");
     
     testAllMatch(sideEffectExpressionParser,
         "external returning as float default 1 :org.unlaxer.tinyexpression.parser.TestSideEffector#booleanToFloatMethod(true)");
     
     testUnMatch(sideEffectExpressionParser,
         "external returning as float default false :org.unlaxer.tinyexpression.parser.TestSideEffector#booleanToFloatMethod(true)");
     
     testAllMatch(sideEffectExpressionParser,
         "external returning as float default 0 :org.unlaxer.tinyexpression.parser.TestSideEffector#booleanToFloatMethod(true)");
     
     
     
     
     //nothing as after returning
     testUnMatch(sideEffectExpressionParser,
          "external returning String :jp.caulis.calc.Effects#foo($foo as Number , $bar as string , $hoge as Boolean)");
     //Stringaa is invalid
     testUnMatch(sideEffectExpressionParser,
          "external returning Stringaa :jp.caulis.calc.Effects#foo($foo as Number , $bar as string , $hoge as Boolean)");
	}

}
