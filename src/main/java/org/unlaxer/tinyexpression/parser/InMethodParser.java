package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Parsed;
import org.unlaxer.Token;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleNamedParenthesesParser;
import org.unlaxer.util.annotation.TokenExtractor;

public class InMethodParser extends JavaStyleNamedParenthesesParser{

	private static final long serialVersionUID = 2125829726691345233L;
	
	public InMethodParser() {
		super();
	}

  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

	@Override
	public Parser nameParser() {
		return Parser.get(()->new WordParser(".in"));
	}

	@Override
	public Parser innerParser() {
		return Parser.get(CommaSeparatedStringExpressionParser.class);
	}
	
	@TokenExtractor
	public static Token getStringExpressions(Token thisParserParsed) {
		return getInnerParserParsed(thisParserParsed);
	}
}