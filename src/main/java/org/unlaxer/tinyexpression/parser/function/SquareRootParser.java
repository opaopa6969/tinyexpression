package org.unlaxer.tinyexpression.parser.function;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.SuggestableParser;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.tinyexpression.parser.NumberExpression;
import org.unlaxer.tinyexpression.parser.NumberExpressionParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;


public class SquareRootParser extends JavaStyleDelimitedLazyChain implements NumberExpression{


	private static final long serialVersionUID = -6474424638106932748L;

	
	public SquareRootParser() {
		super();
	}
		
	public static class SqrtFuctionNameParser extends SuggestableParser{

		private static final long serialVersionUID = -8150024353325310449L;

		public SqrtFuctionNameParser() {
			super(true, "sqrt");
		}
		
		@Override
		public String getSuggestString(String matchedString) {
			return "(".concat(matchedString).concat(")");
		}
		
	}
	
	public static Token getExpression(Token thisParserParsed) {
		return thisParserParsed.getChildWithParser(NumberExpressionParser.class);
	}

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return new Parsers(
      Parser.get(SqrtFuctionNameParser.class),
      Parser.get(LeftParenthesisParser.class),
      Parser.get(NumberExpressionParser.class),//2
      Parser.get(RightParenthesisParser.class)
    );
  }
}
