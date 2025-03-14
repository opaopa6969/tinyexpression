package org.unlaxer.tinyexpression.parser.function;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.SuggestableParser;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.numbertype.NumberExpression;
import org.unlaxer.tinyexpression.parser.numbertype.NumberExpressionParser;



public class SinParser extends JavaStyleDelimitedLazyChain implements NumberExpression {

	private static final long serialVersionUID = -3850642715787195734L;


	public SinParser() {
		super();
	}
	
	public static class SinFuctionNameParser extends SuggestableParser{

		private static final long serialVersionUID = 7566722912623536752L;

		public SinFuctionNameParser() {
			super(true, "sin");
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
        Parser.get(SinFuctionNameParser.class),
        Parser.get(LeftParenthesisParser.class),
        Parser.get(NumberExpressionParser.class),//2
        Parser.get(RightParenthesisParser.class)
    );
  }

}
