package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleNamedParenthesesParser;

public class ToUpperCaseParser extends JavaStyleNamedParenthesesParser implements StringExpression{

	private static final long serialVersionUID = -4655663314424530186L;

	public ToUpperCaseParser() {
		super();
	}

	@Override
	public Parser nameParser() {
		return Parser.get(()-> new WordParser("toUpperCase"));
	}
	
	@Override
	public Parser innerParser() {
		return Parser.get(StringFactorParser.NESTED);
	}

}