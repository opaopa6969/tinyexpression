package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleNamedParenthesesParser;

public class ToLowerCaseParser extends JavaStyleNamedParenthesesParser implements StringExpression{

	private static final long serialVersionUID = -8254948523741795502L;

	public ToLowerCaseParser() {
		super();
	}

	@Override
	public Parser nameParser() {
		return Parser.get(()-> new WordParser("toLowerCase"));
	}

	@Override
	public Parser innerParser() {
		return Parser.get(StringFactorParser.NESTED);
	}
}