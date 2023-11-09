package org.unlaxer.tinyexpression.parser.string;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleNamedParenthesesParser;

public class StringLengthParser extends JavaStyleNamedParenthesesParser{

	private static final long serialVersionUID = 6395080815876299422L;


	public StringLengthParser() {
		super();
	}
	
  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

	@Override
	public Parser nameParser() {
		//FactorOfString:='len('StringExpression')'
		return Parser.get(()->new WordParser("len"));
	}


	@Override
	public Parser innerParser() {
		return Parser.get(StringExpressionParser.class);
	}
}