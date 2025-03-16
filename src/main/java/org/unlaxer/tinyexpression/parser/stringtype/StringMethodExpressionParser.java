package org.unlaxer.tinyexpression.parser.stringtype;

import org.unlaxer.Token;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public abstract class StringMethodExpressionParser extends JavaStyleDelimitedLazyChain{
	
	private static final long serialVersionUID = 1992822145990889756L;

	@TokenExtractor // 何これ
	public static Token getLeftExpression(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(0);
	}
	
  @TokenExtractor
	public static Token getMethod(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(1);
	}
}