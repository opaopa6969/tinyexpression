package org.unlaxer.tinyexpression.parser.number;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.BinaryOperatorParser;
import org.unlaxer.tinyexpression.parser.GreaterParser;
import org.unlaxer.tinyexpression.parser.bool.BooleanExpression;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class NumberGreaterExpressionParser extends JavaStyleDelimitedLazyChain implements BinaryOperatorParser ,BooleanExpression{

	private static final long serialVersionUID = -7037973346958970512L;
	
	
	public NumberGreaterExpressionParser() {
		super();
	}
	
	@Override
	public Parsers getLazyParsers() {
	  return 
      new Parsers(
        Parser.get(NumberExpressionParser.class),
        Parser.get(GreaterParser.class),
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