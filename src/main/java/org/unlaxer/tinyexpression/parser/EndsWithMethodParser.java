package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleNamedParenthesesParser;
import org.unlaxer.util.annotation.TokenExtractor;

public class EndsWithMethodParser extends JavaStyleNamedParenthesesParser{

	public EndsWithMethodParser() {
		super();
	}

	@Override
	public Parser nameParser() {
		return Parser.get(()->new WordParser(".endsWith"));
	}

	@Override
	public Parser innerParser() {
		return Parser.get(CommaSeparatedStringExpressionParser.class);
	}
	
	@TokenExtractor
	public static Token getStringExpressions(Token thisParserParsed) {
		return getInnerParserParsed(thisParserParsed);
	}
}