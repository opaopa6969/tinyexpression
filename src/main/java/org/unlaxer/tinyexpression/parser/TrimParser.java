package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleNamedParenthesesParser;
import org.unlaxer.tinyexpression.parser.string.StringExpression;
import org.unlaxer.tinyexpression.parser.string.StringFactorParser;

public class TrimParser extends JavaStyleNamedParenthesesParser implements StringExpression{

	private static final long serialVersionUID = 602905276788324190L;
	
	public TrimParser() {
		super();
	}

	@Override
	public Parser nameParser() {
		return Parser.get(()-> new WordParser("trim"));
	}
	
	@Override
	public Parser innerParser() {
		return Parser.get(StringFactorParser.NESTED);
	}

}