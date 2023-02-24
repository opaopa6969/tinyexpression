package org.unlaxer.tinyexpression.parser.operator;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.BooleanClauseParser;

public class BooleanClauseOperatorTest {

	@Test
	public void test() {
		BooleanClauseParser booleanClauseParser = Parser.get(BooleanClauseParser.class);
		StringSource source = new StringSource("not(false)");
		ParseContext parseContext = new ParseContext(source);
		
		Parsed parsed = booleanClauseParser.parse(parseContext);
		assertTrue(parsed.isSucceeded());
	}

}
