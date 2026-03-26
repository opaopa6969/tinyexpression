package org.unlaxer.tinyexpression.parser;

import org.unlaxer.tinyexpression.parser.booltype.*;
import org.unlaxer.tinyexpression.parser.numbertype.*;
import org.unlaxer.tinyexpression.parser.stringtype.*;
import org.unlaxer.tinyexpression.parser.javatype.*;

import org.junit.Test;

public class StringLiteralParserTest extends StringContentsTest{
	
	@Test
	public void testAllMatch() {
		
		StringLiteralParser stringLiteralParser = new StringLiteralParser();
		
		testAllMatch(stringLiteralParser, d(""));
		testAllMatch(stringLiteralParser, d("opa"));
		testAllMatch(stringLiteralParser, d("'opa'"));
		testAllMatch(stringLiteralParser, d("\\\"opa\\\""));
    testAllMatch(stringLiteralParser, d("\'opa\'"));
    testAllMatch(stringLiteralParser, d("\\'opa\\'"));
	}

	@Test
	public void testAllConsumedString() {
		
		StringLiteralParser stringLiteralParser = new StringLiteralParser();
		
		assertAllConsumed("", stringLiteralParser);
		assertAllConsumed("opa", stringLiteralParser);
		assertAllConsumed("\\\"opa\\\"", stringLiteralParser);
		assertAllConsumed("'opa'", stringLiteralParser);
	}
	
	@Test
	public void testContents() {
		
		StringLiteralParser stringLiteralParser = new StringLiteralParser();
		
		assertDContents("", stringLiteralParser);
		assertDContents("opa", stringLiteralParser);
		assertSContents("", stringLiteralParser);
		assertSContents("opa", stringLiteralParser);

		assertDContents("\\\"opa\\\"", stringLiteralParser);
		assertSContents("\\'opa\\'", stringLiteralParser);
	}
}
