package org.unlaxer.tinyexpression.parser.function;

import org.unlaxer.Parsed;
import org.unlaxer.Token;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.SuggestableParser;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedChain;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleNamedParenthesesParser;
import org.unlaxer.tinyexpression.parser.number.NumberExpression;
import org.unlaxer.tinyexpression.parser.number.NumberExpressionParser;
import org.unlaxer.util.annotation.TokenExtractor;



public class CosParser extends JavaStyleNamedParenthesesParser implements NumberExpression{

	private static final long serialVersionUID = -7555523412735694127L;

	public CosParser() {
		super();
	}
	
  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

	public static class CosFuctionNameParser extends SuggestableParser{

		private static final long serialVersionUID = 6594507116737484751L;

		public CosFuctionNameParser() {
			super(true, "cos");
		}

		@Override
		public String getSuggestString(String matchedString) {
			return "(".concat(matchedString).concat(")");
		}
		
	}

	@Override
	public Parser createParser() {
		return
      new WhiteSpaceDelimitedChain(
        Parser.get(CosFuctionNameParser.class),
        Parser.get(LeftParenthesisParser.class),
        Parser.get(NumberExpressionParser.class),//2
        Parser.get(RightParenthesisParser.class)
      );
	}

	@TokenExtractor
	public static Token getExpression(Token thisParserParsed) {
		return thisParserParsed.getChildWithParser(NumberExpressionParser.class); //2
	}

  @Override
  public Parser nameParser() {
    return Parser.get(CosFuctionNameParser.class);
  }

  @Override
  public Parser innerParser() {
    return Parser.get(NumberExpressionParser.class);
  }
}
