package org.unlaxer.tinyexpression.parser.string;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;

public class StringContainsMethodParser extends StringExpressionMethodParser{

	private static final long serialVersionUID = 1907488130118447199L;

	public StringContainsMethodParser() {
		super("contains");
	}
	
  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

}