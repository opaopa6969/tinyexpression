package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.NamedParenthesesParser;
import org.unlaxer.parser.elementary.WordParser;

public class InMethodParser extends NamedParenthesesParser{

	private static final long serialVersionUID = 2125829726691345233L;
	
	public InMethodParser() {
		super();
	}

	@Override
	public Parser nameParser() {
		return Parser.get(()->new WordParser(".in"));
	}

	@Override
	public Parser innerParser() {
		return Parser.get(CommaSeparatedStringExpressionParser.class);
	}
	
	public static Token getStringExpressions(Token thisParserParsed) {
		
		return getInnerParserParsed(thisParserParsed);
	}
}