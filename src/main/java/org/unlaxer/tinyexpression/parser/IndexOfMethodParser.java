package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleNamedParenthesesParser;
import org.unlaxer.tinyexpression.parser.string.StringExpressionParser;

public class IndexOfMethodParser extends JavaStyleNamedParenthesesParser{

	private static final long serialVersionUID = 1494387780864577363L;
	
	public IndexOfMethodParser() {
		super();
	}
	
  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

	@Override
	public Parser nameParser() {
		return Parser.get(()->new WordParser(".indexOf"));
	}


	@Override
	public Parser innerParser() {
		return Parser.get(StringExpressionParser.class);
	}
}