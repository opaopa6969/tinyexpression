package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedChain;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.string.StringExpressionParser;

public class CommaSeparatedStringExpressionParser extends JavaStyleDelimitedLazyChain{

	private static final long serialVersionUID = -145498364741083714L;
	
	public CommaSeparatedStringExpressionParser() {
		super();
	}

  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

	@Override
	public Parsers getLazyParsers() {
	  return 
	      //  CommaSeparatedStringExpression:=StringExpression(','StringExpression)*')';
      new Parsers(
        Parser.get(StringExpressionParser.class),
        new ZeroOrMore(
          new WhiteSpaceDelimitedChain(
            Parser.<WordParser>get(()->new WordParser(",")),
            Parser.get(StringExpressionParser.class)
          )
        )
      );
	}
}