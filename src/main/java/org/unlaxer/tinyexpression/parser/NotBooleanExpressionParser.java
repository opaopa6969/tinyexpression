package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.SuggestableParser;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;

public class NotBooleanExpressionParser extends WhiteSpaceDelimitedLazyChain implements BooleanExpression{

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
	public List<Parser> getLazyParsers() {
	  return
      new Parsers(
        Parser.get(NotFuctionNameParser.class),
        Parser.get(LeftParenthesisParser.class),
        Parser.get(BooleanClauseParser.class),//2
        Parser.get(RightParenthesisParser.class)
      );
	}
	
	public static Token getBooleanClause(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(2);
	}
}