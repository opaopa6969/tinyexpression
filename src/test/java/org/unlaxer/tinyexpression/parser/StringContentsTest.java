package org.unlaxer.tinyexpression.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.function.Supplier;

import org.unlaxer.Parsed;
import org.unlaxer.ParserTestBase;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.QuotedParser;

public class StringContentsTest extends ParserTestBase{
	
	static String d(String contents) {
		
		return "\"" + contents + "\"";
	}
	
	static String s(String contents) {
		
		return "'" + contents + "'";
	}
	
	static Supplier<ParseContext> create(String source) {
		StringSource stringSource = new StringSource(source);
		return ()->new ParseContext(stringSource);
		
	}
	
	static void assertAllConsumed(String source , Parser parser) {
		
		
		try(ParseContext parseContext = create(d(source)).get()){
			Parsed parsed = parser.parse(parseContext);
			assertTrue(parsed.isSucceeded());
		}
		try(ParseContext parseContext = create(s(source)).get()){
			Parsed parsed = parser.parse(parseContext);
			assertTrue(parsed.isSucceeded());
		}
	}
	
	static void assertDContents(String source , Parser parser) {
		
		try(ParseContext parseContext = create(d(source)).get()){
			Parsed parsed = parser.parse(parseContext);
			Token rootToken = parsed.getRootToken();
			String contents = QuotedParser.contents(rootToken);
			assertEquals(source, contents);
		}
	}
	
	static void assertSContents(String source , Parser parser) {
		
		try(ParseContext parseContext = create(s(source)).get()){
			Parsed parsed = parser.parse(parseContext);
			Token rootToken = parsed.getRootToken();
			String contents = QuotedParser.contents(rootToken);
			assertEquals(source, contents);
		}
	}
}