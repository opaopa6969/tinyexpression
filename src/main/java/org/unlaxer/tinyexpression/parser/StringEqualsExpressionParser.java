package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;

public class StringEqualsExpressionParser extends WhiteSpaceDelimitedLazyChain implements BinaryOperatorParser ,BooleanExpression{
	
	private static final long serialVersionUID = -1451866679195094560L;
	
	
	public StringEqualsExpressionParser() {
		super();
	}

	@Override
	public List<Parser> getLazyParsers() {
	  return
      new Parsers(
        Parser.get(StringExpressionParser.class),
        Parser.get(EqualEqualParser.class),
        Parser.get(StringExpressionParser.class)
      );
	}
	
	public static Token getLeftExpression(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(0);
	}
	
	public static Token getRightExpression(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(2);
	}

}