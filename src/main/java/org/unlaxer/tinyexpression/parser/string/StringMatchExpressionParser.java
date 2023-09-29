package org.unlaxer.tinyexpression.parser.string;

import java.util.List;

import org.unlaxer.Tag;
import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.ExpressionTags;
import org.unlaxer.tinyexpression.parser.LeftCurlyBraceParser;
import org.unlaxer.tinyexpression.parser.RightCurlyBraceParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.number.NumberExpression;
import org.unlaxer.tinyexpression.parser.number.NumberMatchExpressionParser.MatchFuctionNameParser;
import org.unlaxer.util.annotation.TokenExtractor;

public class StringMatchExpressionParser extends JavaStyleDelimitedLazyChain implements NumberExpression{
	
	public static Tag choiceTag= Tag.of("stringChoice");
	
	public StringMatchExpressionParser() {
		super();
		addTag(ExpressionTags.matchExpression.tag());
	}
	
	@Override
	public List<Parser> getLazyParsers() {
	  return
      //MatchExpression:='match{'CaseExpression,DefaultCaseFactor'}';
      new Parsers(
        Parser.get(MatchFuctionNameParser.class),
        Parser.get(LeftCurlyBraceParser.class),
        Parser.get(StringCaseExpressionParser.class),//2
        Parser.get(StringDefaultCaseFactorParser.class),//3
        Parser.get(RightCurlyBraceParser.class)
      );

	}
	
	@TokenExtractor
	public static Token getCaseExpression(Token thisParserParsed) {
		return thisParserParsed.getChildWithParser(StringCaseExpressionParser.class); //2
	}
	
  @TokenExtractor
	public static Token getDefaultExpression(Token thisParserParsed) {
		return thisParserParsed.getChildWithParser(StringDefaultCaseFactorParser.class); //3
	}
  
}