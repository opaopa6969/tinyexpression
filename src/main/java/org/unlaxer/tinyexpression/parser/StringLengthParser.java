package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.NamedParenthesesParser;
import org.unlaxer.parser.elementary.WordParser;

public class StringLengthParser extends NamedParenthesesParser{

	private static final long serialVersionUID = 6395080815876299422L;


	public StringLengthParser() {
		super();
	}
	
	@Override
	public Parser nameParser() {
		//FactorOfString:='len('StringExpression')'
		return Parser.get(()->new WordParser("len"));
	}


	@Override
	public Parser innerParser() {
		return Parser.get(StringExpressionParser.class);
	}
}