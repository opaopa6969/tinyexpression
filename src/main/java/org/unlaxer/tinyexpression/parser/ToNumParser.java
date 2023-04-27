package org.unlaxer.tinyexpression.parser;

import java.util.List;
import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.elementary.WordParser;

public class ToNumParser extends WhiteSpaceDelimitedLazyChain {

	private static final long serialVersionUID = -4619955945031421138L;

	public static Token getLeftExpression(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(2);
	}

	public static Token getRightExpression(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(4);
	}

	@Override
	public List<Parser> getLazyParsers() {
	  return new Parsers(
        Parser.get(ToNumNameParser.class), // 0
        Parser.get(LeftParenthesisParser.class), // 1
        Parser.get(StringExpressionParser.class), // 2
        Parser.<WordParser>get(()->new WordParser(",")), // 3
        Parser.get(ExpressionParser.class), // 4
        Parser.get(RightParenthesisParser.class) // 5
    );
	}
}