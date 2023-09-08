package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class InTimeRangeParser extends JavaStyleDelimitedLazyChain {

	private static final long serialVersionUID = -4619955945031421138L;

	@Override
	public List<Parser> getLazyParsers() {
	  return
      new Parsers(
        Parser.get(InTimeRangeNameParser.class), // 0
        Parser.get(LeftParenthesisParser.class), // 1
        Parser.get(NumberExpressionParser.class), // 2
        Parser.<WordParser>get(()->new WordParser(",")), // 3
        Parser.get(NumberExpressionParser.class), // 4
        Parser.get(RightParenthesisParser.class) // 5
      );
	}

	@TokenExtractor
	public static Token getLeftExpression(Token thisParserParsed) {
		return thisParserParsed.getChildrenWithParserAsList(NumberExpressionParser.class).get(0); //2
	}

	@TokenExtractor
	public static Token getRightExpression(Token thisParserParsed) {
		return thisParserParsed.getChildrenWithParserAsList(NumberExpressionParser.class).get(1);//4
	}
}