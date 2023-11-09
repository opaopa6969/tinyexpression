package org.unlaxer.tinyexpression.parser.function;

import org.unlaxer.Parsed;
import org.unlaxer.Token;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.SuggestableParser;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.number.NumberExpression;
import org.unlaxer.tinyexpression.parser.number.NumberExpressionParser;
import org.unlaxer.util.annotation.TokenExtractor;

public class MaxParser extends JavaStyleDelimitedLazyChain implements NumberExpression {

	private static final long serialVersionUID = 3935309660712275736L;

	public MaxParser() {
		super();
	}
	
  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

	public static class MaxFuctionNameParser extends SuggestableParser{

		private static final long serialVersionUID = 796425119167968517L;

		public MaxFuctionNameParser() {
			super(true, "max");
		}
		
		@Override
		public String getSuggestString(String matchedString) {
			return "(".concat(matchedString).concat(")");
		}
	}

	@TokenExtractor
	public static Token getLeftExpression(Token thisParserParsed) {
		return thisParserParsed.getChildrenWithParserAsList(NumberExpressionParser.class).get(0); //2
	}
	
  @TokenExtractor
	public static Token getRightExpression(Token thisParserParsed) {
    return thisParserParsed.getChildrenWithParserAsList(NumberExpressionParser.class).get(1); //4
	}

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(MaxFuctionNameParser.class),
        Parser.get(LeftParenthesisParser.class),
        Parser.get(NumberExpressionParser.class),//2
        Parser.<WordParser>get(()->new WordParser(",")),
        Parser.get(NumberExpressionParser.class),//4
        Parser.get(RightParenthesisParser.class)
    );
  }
}