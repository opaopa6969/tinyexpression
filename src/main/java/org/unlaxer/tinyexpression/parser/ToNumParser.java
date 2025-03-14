package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.numbertype.NumberExpressionParser;
import org.unlaxer.tinyexpression.parser.stringtype.StringExpressionParser;
import org.unlaxer.util.annotation.TokenExtractor;

public class ToNumParser extends JavaStyleDelimitedLazyChain {

	private static final long serialVersionUID = -4619955945031421138L;

	@TokenExtractor
	public static Token getLeftExpression(Token thisParserParsed) {
		return thisParserParsed.getChildWithParser(StringExpressionParser.class); //2
	}
	
  @TokenExtractor
	public static Token getRightExpression(Token thisParserParsed) {
		return thisParserParsed.getChildWithParser(NumberExpressionParser.class);// 4
  }

	@Override
	public org.unlaxer.parser.Parsers getLazyParsers() {
	  return new Parsers(
        Parser.get(ToNumNameParser.class), // 0
        Parser.get(LeftParenthesisParser.class), // 1
        Parser.get(StringExpressionParser.class), // 2
        Parser.<WordParser>get(()->new WordParser(",")), // 3
        Parser.get(NumberExpressionParser.class), // 4
        Parser.get(RightParenthesisParser.class) // 5
    );
	}
}