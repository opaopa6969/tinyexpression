package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class SideEffectName1Parser extends JavaStyleDelimitedLazyChain{

	private static final long serialVersionUID = -5885382161035099103L;
	

	public SideEffectName1Parser() {
		super();
	}
	
  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }
	
	@Override
	public Parsers getLazyParsers() {
	  return
      new Parsers(
        new Optional(Parser.get(()->new WordParser("call"))),
        Parser.get(()->new WordParser("with")),
        Parser.get(()->new WordParser("side")),
        Parser.get(()->new WordParser("effect"))
      );

	}
	
//	public static class ReturningParser extends WhiteSpaceDelimitedLazyChain{
//	  
//	}
}