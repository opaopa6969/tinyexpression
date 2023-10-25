package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class NumberLessOrEqualExpressionParser extends JavaStyleDelimitedLazyChain implements BinaryOperatorParser ,BooleanExpression{

	private static final long serialVersionUID = -2671029620875247010L;
	
	public NumberLessOrEqualExpressionParser() {
		super();
	}
	
	@Override
	public org.unlaxer.parser.Parsers getLazyParsers() {
	  return 
      new Parsers(
        Parser.get(NumberExpressionParser.class),
        Parser.get(LessOrEqualParser.class),
        Parser.get(NumberExpressionParser.class)
      );
	}
	
  @TokenExtractor
	public static Token getLeftExpression(Token thisParserParsed) {
    return thisParserParsed.getChildrenWithParserAsList(NumberExpressionParser.class).get(0); //0
	}
	
  @TokenExtractor
	public static Token getRightExpression(Token thisParserParsed) {
    return thisParserParsed.getChildrenWithParserAsList(NumberExpressionParser.class).get(1); //2
	}

}