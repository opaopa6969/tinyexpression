package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class EqualEqualExpressionParser extends JavaStyleDelimitedLazyChain implements BinaryOperatorParser , BooleanExpression{

	private static final long serialVersionUID = -6741015597671479922L;

	
	public EqualEqualExpressionParser() {
		super();
	}
	
	@Override
	public List<Parser> getLazyParsers() {
	  return 
      new Parsers(
        Parser.get(ExpressionParser.class),//0
        Parser.get(EqualEqualParser.class),
        Parser.get(ExpressionParser.class)//2
      );

	}
	
	public static Token getLeftExpression(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(0);
	}
	
	public static Token getRightExpression(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(2);
	}
}