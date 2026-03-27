package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Tag;
import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.NumberMatchExpressionParser.MatchFuctionNameParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class BooleanMatchExpressionParser extends JavaStyleDelimitedLazyChain implements BooleanExpression{
	
	public static Tag choiceTag= Tag.of("booleanChoice");
	
	public BooleanMatchExpressionParser() {
		super();
		addTag(ExpressionTags.matchExpression.tag());
	}
	
	@Override
	public org.unlaxer.parser.Parsers getLazyParsers() {
	  return
      //MatchExpression:='match{'CaseExpression,DefaultCaseFactor'}';
      new Parsers(
        Parser.get(MatchFuctionNameParser.class),
        Parser.get(LeftCurlyBraceParser.class),
        Parser.get(BooleanCaseExpressionParser.class),//2
        Parser.get(BooleanDefaultCaseFactorParser.class),//3
        Parser.get(RightCurlyBraceParser.class)
      );

	}
	
	@TokenExtractor
	public static Token getCaseExpression(Token thisParserParsed) {
		return thisParserParsed.getChildWithParser(BooleanCaseExpressionParser.class); //2
	}
	
  @TokenExtractor
	public static Token getDefaultExpression(Token thisParserParsed) {
		return thisParserParsed.getChildWithParser(BooleanDefaultCaseFactorParser.class); //3
	}
  
}