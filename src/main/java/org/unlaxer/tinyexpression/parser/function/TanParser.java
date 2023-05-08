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



public class TanParser extends NoneChildCollectingParser implements Expression{

	private static final long serialVersionUID = -6157422092009884988L;

	public TanParser() {
		super();
	}
	
	public static class TanFuctionNameParser extends SuggestableParser{

		private static final long serialVersionUID = -8336790883062156195L;

		public TanFuctionNameParser() {
			super(true, "tan");
		}
		
		@Override
		public String getSuggestString(String matchedString) {
			return "(".concat(matchedString).concat(")");
		}
		
	}
	
	@Override
	public Parser createParser() {
	  return 
	      new WhiteSpaceDelimitedChain(
	        Parser.get(TanFuctionNameParser.class),
	        Parser.get(LeftParenthesisParser.class),
	        Parser.get(ExpressionParser.class),//2
	        Parser.get(RightParenthesisParser.class)
	      );	}
	
	public static Token getExpression(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(2);
	}
}
