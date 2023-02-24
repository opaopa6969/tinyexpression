package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Token;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;

public abstract class StringMethodExpressionParser extends WhiteSpaceDelimitedLazyChain{
	
	private static final long serialVersionUID = 1992822145990889756L;

	public static Token getLeftExpression(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(0);
	}
	
	public static Token getMethod(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(1);
	}
}