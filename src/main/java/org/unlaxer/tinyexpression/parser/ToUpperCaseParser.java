package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleNamedParenthesesParser;
import org.unlaxer.tinyexpression.parser.string.StringExpression;
import org.unlaxer.tinyexpression.parser.string.StringFactorParser;

public class ToUpperCaseParser extends JavaStyleNamedParenthesesParser implements StringExpression{

	private static final long serialVersionUID = -4655663314424530186L;

	public ToUpperCaseParser() {
		super();
	}

  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

	@Override
	public Parser nameParser() {
		return Parser.get(()-> new WordParser("toUpperCase"));
	}
	
	@Override
	public Parser innerParser() {
		return Parser.get(StringFactorParser.NESTED);
	}

}