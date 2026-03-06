package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleNamedParenthesesParser;

public class IndexOfMethodParser extends JavaStyleNamedParenthesesParser{

	private static final long serialVersionUID = 1494387780864577363L;
	
	public IndexOfMethodParser() {
		super();
	}

	@Override
	public Parser nameParser() {
		return Parser.get(()->new WordParser(".indexOf"));
	}


	@Override
	public Parser innerParser() {
		return Parser.get(StringExpressionParser.class);
	}
}