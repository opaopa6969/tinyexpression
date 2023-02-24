package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.SuggestableParser;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;

public class MatchExpressionParser extends WhiteSpaceDelimitedLazyChain implements Expression{
	
	private static final long serialVersionUID = -9078041069929701034L;


	public MatchExpressionParser() {
		super();
	}
	
	public static class MatchFuctionNameParser extends SuggestableParser{

		private static final long serialVersionUID = -705291952548250790L;

		public MatchFuctionNameParser() {
			super(true, "match");
		}
		
		@Override
		public String getSuggestString(String matchedString) {
			return "{".concat(matchedString).concat("}");
		}
	}
	
	List<Parser> parsers;

	
	@Override
	public void initialize() {
		//MatchExpression:='match{'CaseExpression,DefaultCaseFactor'}';

		parsers = 
			new Parsers(
				Parser.get(MatchFuctionNameParser.class),
				Parser.get(LeftCurlyBraceParser.class),
				Parser.get(CaseExpressionParser.class),//2
				Parser.get(DefaultCaseFactorParser.class),//3
				Parser.get(RightCurlyBraceParser.class)
			);
	}


	@Override
	public List<Parser> getLazyParsers() {
		return parsers;
	}
	
	public static Token getCaseExpression(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(2);
	}
	
	public static Token getDefaultExpression(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(3);
	}
}