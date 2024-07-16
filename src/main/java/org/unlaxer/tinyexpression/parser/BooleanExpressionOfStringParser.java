package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;

public class BooleanExpressionOfStringParser extends LazyChoice implements BooleanExpression{
	
	private static final long serialVersionUID = 6027456197308442793L;


	public BooleanExpressionOfStringParser() {
		super();
	}

	@Override
	public org.unlaxer.parser.Parsers getLazyParsers() {
    // BooleanExpressionOfString:=
    //  (StringExpression'=='StringExpression)|
    //  (StringExpression'!='StringExpression)|
    //  StringExpression'.in('StringExpression(','StringExpression)*')'|
    //  StringStartsWith:=StringExpression'.startsWith('StringExpression')'|
    //  StringEndsWith:=StringExpression'.endsWith('StringExpression')'|
    //  StringContains:=StringExpression'.contains('StringExpression')';
    return  
      new Parsers(
        Parser.get(StringEqualsExpressionParser.class),
        Parser.get(StringNotEqualsExpressionParser.class),
        Parser.get(StringInParser.class),
        Parser.get(StringStartsWithParser.class),
        Parser.get(StringEndsWithParser.class),
        Parser.get(StringContainsParser.class)
      );
	}
}