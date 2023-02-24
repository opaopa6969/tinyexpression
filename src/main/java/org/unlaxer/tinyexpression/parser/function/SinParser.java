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



public class SinParser extends NoneChildCollectingParser implements Expression {

	private static final long serialVersionUID = -3850642715787195734L;


	public SinParser() {
		super();
	}
	
	public static class SinFuctionNameParser extends SuggestableParser{

		private static final long serialVersionUID = 7566722912623536752L;

		public SinFuctionNameParser() {
			super(true, "sin");
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
				Parser.get(SinFuctionNameParser.class),
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
