package org.unlaxer.tinyexpression.parser.function;

import java.util.List;

import org.unlaxer.Token;
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

public class MinParser extends JavaStyleDelimitedLazyChain implements NumberExpression{

	private static final long serialVersionUID = 3309794696125275646L;

	public MinParser() {
		super();
	}
	
	public static class MinFuctionNameParser extends SuggestableParser{

		private static final long serialVersionUID = -6464998496807462185L;

		public MinFuctionNameParser() {
			super(true, "min");
		}
		
		@Override
		public String getSuggestString(String matchedString) {
			return "(".concat(matchedString).concat(")");
		}
	}
	

	@TokenExtractor
	public static Token getLeftExpression(Token thisParserParsed) {
		return thisParserParsed.getChildrenWithParserAsList(NumberExpressionParser.class).get(0);//2
	}
	
  @TokenExtractor
	public static Token getRightExpression(Token thisParserParsed) {
    return thisParserParsed.getChildrenWithParserAsList(NumberExpressionParser.class).get(1);//4
	}

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(MinParser.MinFuctionNameParser.class),
        Parser.get(LeftParenthesisParser.class),
        Parser.get(NumberExpressionParser.class),//2
        Parser.<WordParser>get(()->new WordParser(",")),
        Parser.get(NumberExpressionParser.class),//4
        Parser.get(RightParenthesisParser.class)
        
    );
  }
}