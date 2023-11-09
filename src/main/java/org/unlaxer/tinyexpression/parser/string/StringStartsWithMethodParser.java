package org.unlaxer.tinyexpression.parser.string;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;

public class StringStartsWithMethodParser extends StringExpressionMethodParser{

	private static final long serialVersionUID = -1482143516561082311L;
	
	public StringStartsWithMethodParser() {
		super("startsWith");
	}
	
  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

}