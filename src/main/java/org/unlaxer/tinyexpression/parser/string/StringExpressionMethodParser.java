package org.unlaxer.tinyexpression.parser.string;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleNamedParenthesesParser;
import org.unlaxer.util.annotation.TokenExtractor;

public class StringExpressionMethodParser extends JavaStyleNamedParenthesesParser{

	private static final long serialVersionUID = 7921036779259818380L;
	
	String methodName;

	public StringExpressionMethodParser(String methodName) {
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

	@TokenExtractor
	public static Token getStringExpressions(Token thisParserParsed) {
		return getInnerParserParsed(thisParserParsed);
	}
}