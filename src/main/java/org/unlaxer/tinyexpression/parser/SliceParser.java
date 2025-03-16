package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Chain;
import org.unlaxer.parser.combinator.ZeroOrOne;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.numbertype.NumberExpressionParser;
import org.unlaxer.tinyexpression.parser.stringtype.StringExpression;

public class SliceParser extends JavaStyleDelimitedLazyChain implements StringExpression{
	
	private static final long serialVersionUID = 5398027501329177390L;


	public SliceParser() {
		super();
	}
	
	@Override
	public org.unlaxer.parser.Parsers getLazyParsers() {
	  return
	      // Slice:='['Expression?':'Expression?(':'Expression)?']';
        new Parsers(
          Parser.<WordParser>get(()->new WordParser("[")),
          new ZeroOrOne(
            NumberExpressionParser.class
          ),
          Parser.<WordParser>get(()->new WordParser(":")),
          new ZeroOrOne(
            NumberExpressionParser.class
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