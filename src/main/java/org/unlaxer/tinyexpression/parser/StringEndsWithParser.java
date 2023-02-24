package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;

public class StringEndsWithParser extends StringMethodExpressionParser implements BooleanExpression{
	
	private static final long serialVersionUID = 6896630990248605254L;
	
	List<Parser> parsers;
	
	public StringEndsWithParser() {
		super();
	}

	@Override
	public void initialize() {
		//  StringEndsWith:=StringExpression'.startsWith('StringExpression')';
		parsers = 
			new Parsers(
				Parser.get(StringExpressionParser.class),
				Parser.get(StringEndsWithMethodParser.class)
			);
	}


	@Override
	public List<Parser> getLazyParsers() {
		return parsers;
	}
}