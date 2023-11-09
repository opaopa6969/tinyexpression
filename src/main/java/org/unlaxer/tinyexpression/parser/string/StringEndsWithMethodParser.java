package org.unlaxer.tinyexpression.parser.string;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;

public class StringEndsWithMethodParser extends StringExpressionMethodParser{

	private static final long serialVersionUID = -3805879844440530633L;

	public StringEndsWithMethodParser() {
		super("endsWith");
	}
	
  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

}