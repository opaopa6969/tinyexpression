package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleNamedParenthesesParser;

public class InMethodParser extends JavaStyleNamedParenthesesParser{

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
}