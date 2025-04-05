package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.Tag;
import org.unlaxer.Token;
import org.unlaxer.TypedToken;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.SuggestableParser;
import org.unlaxer.tinyexpression.parser.ExpressionTags;
import org.unlaxer.tinyexpression.parser.LeftCurlyBraceParser;
import org.unlaxer.tinyexpression.parser.RightCurlyBraceParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class NumberMatchExpressionParser extends JavaStyleDelimitedLazyChain implements NumberExpression{
	
	private static final long serialVersionUID = -9078041069929701034L;

	public static Tag choiceTag= Tag.of("numberChoice");
	
	public NumberMatchExpressionParser() {
		super();
		addTag(ExpressionTags.matchExpression.tag());
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
	
	@Override
	public org.unlaxer.parser.Parsers getLazyParsers() {
	  return
      //MatchExpression:='match{'CaseExpression,DefaultCaseFactor'}';
      new Parsers(
        Parser.get(MatchFuctionNameParser.class),
        Parser.get(LeftCurlyBraceParser.class),
        Parser.get(NumberCaseExpressionParser.class),//2
        Parser.get(NumberDefaultCaseFactorParser.class),//3
        Parser.get(RightCurlyBraceParser.class)
      );

	}
	
	@TokenExtractor
	public static TypedToken<NumberCaseExpressionParser> getCaseExpression(Token thisParserParsed) {
		TypedToken<NumberCaseExpressionParser> childWithParserTyped = 
		    thisParserParsed.getChildWithParserTyped(NumberCaseExpressionParser.class); //2
		return childWithParserTyped;
	}
	
  @TokenExtractor
	public static TypedToken<NumberDefaultCaseFactorParser> getDefaultExpression(Token thisParserParsed) {
		TypedToken<NumberDefaultCaseFactorParser> childWithParserTyped = 
		    thisParserParsed.getChildWithParserTyped(NumberDefaultCaseFactorParser.class); //3
		return childWithParserTyped;
	}
  
}