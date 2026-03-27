package org.unlaxer.tinyexpression.parser.function;

import java.util.List;
import java.util.stream.Collectors;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.SuggestableParser;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.parser.posix.CommaParser;
import org.unlaxer.tinyexpression.parser.NumberExpression;
import org.unlaxer.tinyexpression.parser.NumberExpressionParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
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

	/** Successor element: comma followed by a NumberExpression */
	public static class MinArgSuccessor extends JavaStyleDelimitedLazyChain {
		private static final long serialVersionUID = 1L;

		@Override
		public Parsers getLazyParsers() {
			return new Parsers(
				Parser.get(CommaParser.class),
				Parser.get(NumberExpressionParser.class)
			);
		}
	}

	@TokenExtractor
	public static Token getFirstExpression(Token thisParserParsed) {
		return thisParserParsed.getChildrenWithParserAsList(NumberExpressionParser.class).get(0);
	}

	@TokenExtractor
	public static List<Token> getRestExpressions(Token thisParserParsed) {
		List<Token> all = thisParserParsed.getChildrenWithParserAsList(NumberExpressionParser.class);
		return all.stream().skip(1).collect(Collectors.toList());
	}

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(MinParser.MinFuctionNameParser.class),
        Parser.get(LeftParenthesisParser.class),
        Parser.get(NumberExpressionParser.class),
        new ZeroOrMore(Parser.get(MinArgSuccessor.class)),
        Parser.get(RightParenthesisParser.class)
    );
  }
}
