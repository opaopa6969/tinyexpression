package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class GreaterExpressionParser extends JavaStyleDelimitedLazyChain implements BinaryOperatorParser ,BooleanExpression{

	private static final long serialVersionUID = -7037973346958970512L;
	
	
	public GreaterExpressionParser() {
		super();
	}
	
	@Override
	public List<Parser> getLazyParsers() {
	  return 
      new Parsers(
        Parser.get(ExpressionParser.class),
        Parser.get(GreaterParser.class),
        Parser.get(ExpressionParser.class)
      );
	}

	@TokenExtractor
	public static Token getLeftExpression(Token thisParserParsed) {
		return thisParserParsed.getChildrenWithParserAsList(ExpressionParser.class).get(0); //0
	}
	
  @TokenExtractor
	public static Token getRightExpression(Token thisParserParsed) {
    return thisParserParsed.getChildrenWithParserAsList(ExpressionParser.class).get(1); //2
	}

}