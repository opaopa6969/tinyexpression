package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.SuggestableParser;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.tinyexpression.parser.bool.BooleanExpression;
import org.unlaxer.tinyexpression.parser.bool.BooleanExpressionParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class NotBooleanExpressionParser extends JavaStyleDelimitedLazyChain implements BooleanExpression{

	private static final long serialVersionUID = 8963678631912521273L;
	
	public NotBooleanExpressionParser() {
		super();
	}

	public static class NotFuctionNameParser extends SuggestableParser{

		private static final long serialVersionUID = -2967834676097977017L;

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
        Parser.get(BooleanExpressionParser.class),//2
        Parser.get(RightParenthesisParser.class)
      );
	}
	
	@TokenExtractor
	public static Token getBooleanExpression(Token thisParserParsed) {
		return thisParserParsed.getChildWithParser(BooleanExpressionParser.class); //2
	}
}