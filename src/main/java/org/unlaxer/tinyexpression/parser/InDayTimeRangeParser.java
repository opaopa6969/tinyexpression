package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;



public class InDayTimeRangeParser extends WhiteSpaceDelimitedLazyChain {

	private static final long serialVersionUID = -4619955945031421138L;

	 @Override
	  public org.unlaxer.parser.Parsers getLazyParsers() {
	    return
	      new Parsers(
	          Parser.get(InDayTimeRangeNameParser.class), // inDayTimeRange
	          Parser.get(LeftParenthesisParser.class), // (
	          Parser.get(DayOfWeekEnumParser.class), // MONDAY
	          Parser.get(CommaParser.class), // ,
	          Parser.get(NumberExpressionParser.class), // 7.0f
	          Parser.get(CommaParser.class), // ,
	          Parser.get(DayOfWeekEnumParser.class), // FRIDAY
	          Parser.get(CommaParser.class), // ,
	          Parser.get(NumberExpressionParser.class), // 16.0f
	          Parser.get(RightParenthesisParser.class) // )
	      );
	  }


  @TokenExtractor
	public static Token getFromDayOfWeek(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(2);
	}

  @TokenExtractor
	public static Token getFromHour(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(4);
	}

  @TokenExtractor
	public static Token getToDayOfWeek(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(6);
	}

  @TokenExtractor
	public static Token getToHour(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(8);
	}
}