package org.unlaxer.tinyexpression.parser.string;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.bool.BooleanExpression;

public class StringEndsWithParser extends StringMethodExpressionParser implements BooleanExpression{
	
	private static final long serialVersionUID = 6896630990248605254L;
	
	List<Parser> parsers;
	
	public StringEndsWithParser() {
		super();
	}

	@Override
	public Parsers getLazyParsers() {
	  return
	      //  StringEndsWith:=StringExpression'.startsWith('StringExpression')';
        new Parsers(
          Parser.get(StringExpressionParser.class),
          Parser.get(StringEndsWithMethodParser.class)
        );

	}
}