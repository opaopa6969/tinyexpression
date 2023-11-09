package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.SuggestableParser;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class NotImmediatesBooleanExpressionParser extends JavaStyleDelimitedLazyChain{


	private static final long serialVersionUID = -2338119726686825460L;
	
	public NotImmediatesBooleanExpressionParser() {
		super();
	}

  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

	public static class NotFuctionNameParser extends SuggestableParser{

		private static final long serialVersionUID = -2912145665090968481L;

		public NotFuctionNameParser() {
			super(true, "not");
		}
		
		@Override
		public String getSuggestString(String matchedString) {
			return "(".concat(matchedString).concat(")");
		}
	}
	
	@Override
	public Parsers getLazyParsers() {
	  
	  return 
      new Parsers(
        Parser.get(NotFuctionNameParser.class),
        Parser.get(LeftParenthesisParser.class),
        Parser.get(ImmediatesBooleanExpressionParser.class),
        Parser.get(RightParenthesisParser.class)
      );
	}
}