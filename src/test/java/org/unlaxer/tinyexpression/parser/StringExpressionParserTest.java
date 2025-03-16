package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.tinyexpression.parser.stringtype.StringExpressionParser;

public class StringExpressionParserTest extends StringContentsTest{

	@Test
	public void testAllMatch() {
		
		StringExpressionParser stringLiteralParser = new StringExpressionParser();
		
		testAllMatch(stringLiteralParser, d(""));
		testAllMatch(stringLiteralParser, d("opa"));
		testAllMatch(stringLiteralParser, d("'opa'"));
		testAllMatch(stringLiteralParser, d("\\\"opa\\\""));
	}

	@Test
	public void testAllConsumedString() {
		
		StringExpressionParser parser = new StringExpressionParser();
		
		assertAllConsumed("", parser);
		assertAllConsumed("opa", parser);
		assertAllConsumed("\\\"opa\\\"", parser);
		assertAllConsumed("'opa'", parser);
		
		testAllMatch(parser, "'niku'");
	}
	
	@Test
	public void testContents() {
		
		StringExpressionParser stringLiteralParser = new StringExpressionParser();
		
		assertDContents("", stringLiteralParser);
		assertDContents("opa", stringLiteralParser);
		assertSContents("", stringLiteralParser);
		assertSContents("opa", stringLiteralParser);

		assertDContents("\\\"opa\\\"", stringLiteralParser);
		assertSContents("\\'opa\\'", stringLiteralParser);
	}

}
