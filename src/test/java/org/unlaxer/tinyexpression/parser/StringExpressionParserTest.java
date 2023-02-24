package org.unlaxer.tinyexpression.parser;

import org.junit.Test;

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
		
		StringExpressionParser stringLiteralParser = new StringExpressionParser();
		
		assertAllConsumed("", stringLiteralParser);
		assertAllConsumed("opa", stringLiteralParser);
		assertAllConsumed("\\\"opa\\\"", stringLiteralParser);
		assertAllConsumed("'opa'", stringLiteralParser);
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
