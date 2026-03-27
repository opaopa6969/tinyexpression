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

public class RoundParser extends JavaStyleDelimitedLazyChain implements NumberExpression {

	private static final long serialVersionUID = 1L;

	public RoundParser() {
		super();
	}

	public static class RoundFuctionNameParser extends SuggestableParser {

		private static final long serialVersionUID = 1L;

		public RoundFuctionNameParser() {
			super(true, "round");
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
	public Parsers getLazyParsers() {
		return new Parsers(
			Parser.get(RoundFuctionNameParser.class),
			Parser.get(LeftParenthesisParser.class),
			Parser.get(NumberExpressionParser.class),
			Parser.get(RightParenthesisParser.class)
		);
	}
}
