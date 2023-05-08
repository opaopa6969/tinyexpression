package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.SuggestableParser;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.elementary.WordParser;

public class IfExpressionParser extends WhiteSpaceDelimitedLazyChain implements Expression{
	
	private static final long serialVersionUID = 8228933717392969866L;
	
	
	public IfExpressionParser() {
		super();
	}
	
	public static class IfFuctionNameParser extends SuggestableParser{

		private static final long serialVersionUID = -6045428101193616423L;

		public IfFuctionNameParser() {
			super(true, "if");
		}
		
		@Override
		public String getSuggestString(String matchedString) {
			return "(".concat(matchedString).concat("){ }else{ }");
		}
	}
	
	@Override
	public List<Parser> getLazyParsers() {
	  return 
	      new Parsers(
	        Parser.get(IfFuctionNameParser.class),
	        Parser.get(LeftParenthesisParser.class),
	        Parser.get(BooleanClauseParser.class),//2
	        Parser.get(RightParenthesisParser.class),
	        Parser.get(LeftCurlyBraceParser.class),
	        Parser.get(ExpressionParser.class),//5
	        Parser.get(RightCurlyBraceParser.class),
	        Parser.get(()->new WordParser("else")),
	        Parser.get(LeftCurlyBraceParser.class),
	        Parser.get(ExpressionParser.class),//9
	        Parser.get(RightCurlyBraceParser.class)
	      );
	}
	
	public static Token getBooleanClause(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(2);
	}
	
	public static Token getThenExpression(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(5);
	}
	
	public static Token getElseExpression(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(9);
	}
}