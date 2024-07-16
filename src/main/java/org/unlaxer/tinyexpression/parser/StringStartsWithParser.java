package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;

public class StringStartsWithParser extends StringMethodExpressionParser implements BooleanExpression{
	
	private static final long serialVersionUID = 4961342621488883708L;
	
	public StringStartsWithParser() {
		super();
	}

	@Override
	public org.unlaxer.parser.Parsers getLazyParsers() {
    //  StringStartsWith:=StringExpression'.startsWith('StringExpression')';
    return
      new Parsers(
        Parser.get(StringExpressionParser.class),
        Parser.get(StringStartsWithMethodParser.class)
      );
	}
}