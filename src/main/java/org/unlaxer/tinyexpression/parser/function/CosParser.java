package org.unlaxer.tinyexpression.parser.function;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.SuggestableParser;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.combinator.NoneChildCollectingParser;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedChain;
import org.unlaxer.tinyexpression.parser.Expression;
import org.unlaxer.tinyexpression.parser.ExpressionParser;



public class CosParser extends NoneChildCollectingParser implements Expression{

	private static final long serialVersionUID = -7555523412735694127L;

	public CosParser() {
		super();
	}
	
	public static class CosFuctionNameParser extends SuggestableParser{

		private static final long serialVersionUID = 6594507116737484751L;

		public CosFuctionNameParser() {
			super(true, "cos");
		}

		@Override
		public String getSuggestString(String matchedString) {
			return "(".concat(matchedString).concat(")");
		}
		
	}
	
	Parser parser;
	
	@Override
	public void initialize() {
		parser =
			new WhiteSpaceDelimitedChain(
				Parser.get(CosFuctionNameParser.class),
				Parser.get(LeftParenthesisParser.class),
				Parser.get(ExpressionParser.class),//2
				Parser.get(RightParenthesisParser.class)
			);
	}



	@Override
	public Parser createParser() {
		return parser;
	}
	
	public static Token getExpression(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(2);
	}
}
