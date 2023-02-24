package org.unlaxer.tinyexpression.parser.function;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.SuggestableParser;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.combinator.NoneChildCollectingParser;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedChain;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.Expression;
import org.unlaxer.tinyexpression.parser.ExpressionParser;

public class MinParser extends NoneChildCollectingParser implements Expression{

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
	
	Parser parser;
	
	@Override
	public void initialize() {
		parser =
			new WhiteSpaceDelimitedChain(
				Parser.get(MinParser.MinFuctionNameParser.class),
				Parser.get(LeftParenthesisParser.class),
				Parser.get(ExpressionParser.class),//2
				Parser.<WordParser>get(()->new WordParser(",")),
				Parser.get(ExpressionParser.class),//4
				Parser.get(RightParenthesisParser.class)
			);
	}



	@Override
	public Parser createParser() {
		return parser;
	}
	
	public static Token getLeftExpression(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(2);
	}
	
	public static Token getRightExpression(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(4);
	}
}