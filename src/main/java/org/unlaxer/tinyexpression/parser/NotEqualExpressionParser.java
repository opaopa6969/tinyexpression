package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;

public class NotEqualExpressionParser extends WhiteSpaceDelimitedLazyChain implements BinaryOperatorParser ,BooleanExpression{

	private static final long serialVersionUID = -6741015597671479922L;
	
	public NotEqualExpressionParser() {
		super();
	}

	@Override
	public List<Parser> getLazyParsers() {
	  return
      new Parsers(
        Parser.get(ExpressionParser.class),
        Parser.get(NotEqualParser.class),
        Parser.get(ExpressionParser.class)
      );
	}
	
	public static Token getLeftExpression(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(0);
	}
	
	public static Token getRightExpression(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(2);
	}
}