package org.unlaxer.tinyexpression.parser.function;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.SuggestableParser;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.combinator.NoneChildCollectingParser;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedChain;
import org.unlaxer.tinyexpression.parser.number.NumberExpression;

public class RandomParser extends NoneChildCollectingParser implements NumberExpression{

	private static final long serialVersionUID = 7928239004297872018L;

	public RandomParser() {
		super();
	}
	
	public static class RandomFuctionNameParser extends SuggestableParser{

		private static final long serialVersionUID = 638526069284639995L;

		public RandomFuctionNameParser() {
			super(true, "random");
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
        Parser.get(RandomFuctionNameParser.class),
        Parser.get(LeftParenthesisParser.class),
        Parser.get(RightParenthesisParser.class)
      );

	}
}