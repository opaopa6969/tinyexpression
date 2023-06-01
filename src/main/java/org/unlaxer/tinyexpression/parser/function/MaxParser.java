package org.unlaxer.tinyexpression.parser.function;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.SuggestableParser;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.Expression;
import org.unlaxer.tinyexpression.parser.ExpressionParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class MaxParser extends JavaStyleDelimitedLazyChain implements Expression {

	private static final long serialVersionUID = 3935309660712275736L;

	public MaxParser() {
		super();
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
		return thisParserParsed.getChildrenWithParserAsList(ExpressionParser.class).get(0); //2
	}
	
  @TokenExtractor
	public static Token getRightExpression(Token thisParserParsed) {
    return thisParserParsed.getChildrenWithParserAsList(ExpressionParser.class).get(1); //4
	}

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        Parser.get(MaxFuctionNameParser.class),
        Parser.get(LeftParenthesisParser.class),
        Parser.get(ExpressionParser.class),//2
        Parser.<WordParser>get(()->new WordParser(",")),
        Parser.get(ExpressionParser.class),//4
        Parser.get(RightParenthesisParser.class)
    );
  }
}