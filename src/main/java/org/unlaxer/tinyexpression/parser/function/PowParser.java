package org.unlaxer.tinyexpression.parser.function;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.SuggestableParser;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.NumberExpression;
import org.unlaxer.tinyexpression.parser.NumberExpressionParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class PowParser extends JavaStyleDelimitedLazyChain implements NumberExpression {

	private static final long serialVersionUID = 1L;

	public PowParser() {
		super();
	}

	public static class PowFuctionNameParser extends SuggestableParser {

		private static final long serialVersionUID = 1L;

		public PowFuctionNameParser() {
			super(true, "pow");
		}

		@Override
		public String getSuggestString(String matchedString) {
			return "(".concat(matchedString).concat(")");
		}
	}

	@TokenExtractor
	public static Token getLeftExpression(Token thisParserParsed) {
		return thisParserParsed.getChildrenWithParserAsList(NumberExpressionParser.class).get(0);
	}

	@TokenExtractor
	public static Token getRightExpression(Token thisParserParsed) {
		return thisParserParsed.getChildrenWithParserAsList(NumberExpressionParser.class).get(1);
	}

	@Override
	public Parsers getLazyParsers() {
		return new Parsers(
			Parser.get(PowFuctionNameParser.class),
			Parser.get(LeftParenthesisParser.class),
			Parser.get(NumberExpressionParser.class),
			Parser.<WordParser>get(() -> new WordParser(",")),
			Parser.get(NumberExpressionParser.class),
			Parser.get(RightParenthesisParser.class)
		);
	}
}
