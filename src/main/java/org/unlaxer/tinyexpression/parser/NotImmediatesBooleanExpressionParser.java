package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.SuggestableParser;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class NotImmediatesBooleanExpressionParser extends JavaStyleDelimitedLazyChain{


	private static final long serialVersionUID = -2338119726686825460L;
	
	public NotImmediatesBooleanExpressionParser() {
		super();
	}

	
	public static class NotFuctionNameParser extends SuggestableParser{

		private static final long serialVersionUID = -2912145665090968481L;

		public NotFuctionNameParser() {
			super(true, "not");
		}
		
		@Override
		public String getSuggestString(String matchedString) {
			return "(".concat(matchedString).concat(")");
		}
	}
	
	@Override
	public org.unlaxer.parser.Parsers getLazyParsers() {
	  
	  return 
      new Parsers(
        Parser.get(NotFuctionNameParser.class),
        Parser.get(LeftParenthesisParser.class),
        Parser.get(ImmediatesBooleanExpressionParser.class),
        Parser.get(RightParenthesisParser.class)
      );
	}
}