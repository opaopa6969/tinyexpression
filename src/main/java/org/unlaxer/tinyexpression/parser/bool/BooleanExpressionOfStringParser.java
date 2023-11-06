package org.unlaxer.tinyexpression.parser.bool;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.tinyexpression.parser.string.StringContainsParser;
import org.unlaxer.tinyexpression.parser.string.StringEndsWithParser;
import org.unlaxer.tinyexpression.parser.string.StringEqualsExpressionParser;
import org.unlaxer.tinyexpression.parser.string.StringInParser;
import org.unlaxer.tinyexpression.parser.string.StringNotEqualsExpressionParser;
import org.unlaxer.tinyexpression.parser.string.StringStartsWithParser;

public class BooleanExpressionOfStringParser extends LazyChoice implements BooleanExpression{
	
	private static final long serialVersionUID = 6027456197308442793L;


	public BooleanExpressionOfStringParser() {
		super();
	}

	@Override
	public Parsers getLazyParsers() {
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