package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Chain;
import org.unlaxer.parser.combinator.ZeroOrOne;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.number.NumberExpressionParser;
import org.unlaxer.tinyexpression.parser.string.StringExpression;

public class SliceParser extends JavaStyleDelimitedLazyChain implements StringExpression{
	
	private static final long serialVersionUID = 5398027501329177390L;


	public SliceParser() {
		super();
	}
	
  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

	@Override
	public Parsers getLazyParsers() {
	  return
	      // Slice:='['Expression?':'Expression?(':'Expression)?']';
        new Parsers(
          Parser.<WordParser>get(()->new WordParser("[")),
          new ZeroOrOne(
            Parser.get(NumberExpressionParser.class)
          ),
          Parser.<WordParser>get(()->new WordParser(":")),
          new ZeroOrOne(
            Parser.get(NumberExpressionParser.class)
          ),
          new ZeroOrOne(
            new Chain(
              Parser.<WordParser>get(()->new WordParser(":")),
              Parser.get(NumberExpressionParser.class)
            )
          ),
          Parser.<WordParser>get(()->new WordParser("]"))
        );

	}
}