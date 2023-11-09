package org.unlaxer.tinyexpression.parser.operator;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.TokenPrinter;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.bool.BooleanExpressionParser;

public class BooleanExpressionOperatorTest {

	@Test
	public void test() {
		BooleanExpressionParser booleanExpressionParser = Parser.get(BooleanExpressionParser.class);
		StringSource source = StringSource.createRootSource("not(false)");
		ParseContext parseContext = new ParseContext(source);
		
		Parsed parsed = booleanExpressionParser.parse(parseContext);
		assertTrue(parsed.isSucceeded());
	}
	
	@Test
	public void testSomeConditions() {
		
		String[] trueConditions = {
				 "1==1",
				 "true | false ",
				"true | false | true",
				"true | 1==1 ",
				"true & 1==1 & 1 != 2",
				"true & 1==1 ^ len('aaa') == 3",
				"true & 1==1 & len('aaa') == 3",
				"true & 1==1  & len('bbb') == 3",
				"len('niku') != len('sushi')",
		};
		
		
		BooleanExpressionParser booleanExpressionParser = Parser.get(BooleanExpressionParser.class);
		
		for (String condition: trueConditions) {
			
			System.out.format("\n\ncondition = %s\n" ,condition);
			
			StringSource source = StringSource.createRootSource(condition);
			ParseContext parseContext = new ParseContext(source);
			
			Parsed parsed = booleanExpressionParser.parse(parseContext);
			assertTrue(parsed.isSucceeded());
			
			Token rootToken = parsed.getRootToken();
			TokenPrinter.output(rootToken, System.out);
			
			
		}
		
		
	}

}
