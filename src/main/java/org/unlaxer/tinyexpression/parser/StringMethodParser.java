package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.NamedParenthesesParser;
import org.unlaxer.parser.elementary.WordParser;

public class StringMethodParser extends NamedParenthesesParser{

	private static final long serialVersionUID = 7921036779259818380L;
	
	String methodName;

	public StringMethodParser(String methodName) {
		super();
		this.methodName = methodName;
	}

	@Override
	public Parser nameParser() {
		return Parser.get(()->new WordParser("."+methodName));
	}

	@Override
	public Parser innerParser() {
		return Parser.get(StringExpressionParser.class);
	}
	
	public static Token getStringExpressions(Token thisParserParsed) {
		
		return getInnerParserParsed(thisParserParsed);
	}
}