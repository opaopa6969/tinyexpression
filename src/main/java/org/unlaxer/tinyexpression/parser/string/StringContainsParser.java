package org.unlaxer.tinyexpression.parser.string;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.bool.BooleanExpression;

public class StringContainsParser extends StringMethodExpressionParser implements BooleanExpression{
	
	private static final long serialVersionUID = 6896630990248605254L;
	
	public StringContainsParser() {
		super();
	}

	@Override
	public Parsers getLazyParsers() {
	  return
      //  StringContains:=StringExpression'.contains('StringExpression')';
      new Parsers(
        Parser.get(StringExpressionParser.class),
        Parser.get(StringContainsMethodParser.class)
      );

	}
}