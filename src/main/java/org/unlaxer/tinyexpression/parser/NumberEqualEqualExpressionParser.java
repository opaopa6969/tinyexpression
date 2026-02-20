package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class NumberEqualEqualExpressionParser extends JavaStyleDelimitedLazyChain implements BinaryOperatorParser , BooleanExpression{

	private static final long serialVersionUID = -6741015597671479922L;

	
	public NumberEqualEqualExpressionParser() {
		super();
	}
	
	@Override
	public org.unlaxer.parser.Parsers getLazyParsers() {
	  return 
      new Parsers(
        Parser.get(NumberExpressionParser.class),//0
        Parser.get(EqualEqualParser.class),
        Parser.get(NumberExpressionParser.class)//2
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