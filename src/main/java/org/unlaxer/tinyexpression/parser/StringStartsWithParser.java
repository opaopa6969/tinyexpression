package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;

public class StringStartsWithParser extends StringMethodExpressionParser implements BooleanExpression{
	
	private static final long serialVersionUID = 4961342621488883708L;
	
	List<Parser> parsers;
	
	public StringStartsWithParser() {
		super();
	}

	@Override
	public void initialize() {
		//  StringStartsWith:=StringExpression'.startsWith('StringExpression')';
		parsers = 
			new Parsers(
				Parser.get(StringExpressionParser.class),
				Parser.get(StringStartsWithMethodParser.class)
			);
	}


	@Override
	public List<Parser> getLazyParsers() {
		return parsers;
	}
}