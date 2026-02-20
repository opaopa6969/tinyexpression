package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class NumberLessExpressionParser extends JavaStyleDelimitedLazyChain implements BinaryOperatorParser , BooleanExpression{

	private static final long serialVersionUID = 5279950291952122038L;
	
	public NumberLessExpressionParser() {
		super();
	}

	@Override
	public org.unlaxer.parser.Parsers getLazyParsers() {
	  return
      new Parsers(
        Parser.get(NumberExpressionParser.class),
        Parser.get(LessParser.class),
        Parser.get(NumberExpressionParser.class)
      );
	}

	public static Token getLeftExpression(Token thisParserParsed) {
    return thisParserParsed.getChildrenWithParserAsList(NumberExpressionParser.class).get(0); //0
	}
	
	public static Token getRightExpression(Token thisParserParsed) {
    return thisParserParsed.getChildrenWithParserAsList(NumberExpressionParser.class).get(1); //2
	}
	
}