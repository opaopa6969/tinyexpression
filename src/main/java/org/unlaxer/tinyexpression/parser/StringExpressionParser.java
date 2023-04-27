package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.combinator.ZeroOrMore;

public class StringExpressionParser extends WhiteSpaceDelimitedLazyChain implements StringExpression{

	private static final long serialVersionUID = 3057326703009847594L;
	
	
	public StringExpressionParser() {
		super();
	}

	@Override
	public List<Parser> getLazyParsers() {
	  return
	      // StringExpression:=StringTerm('+'StringTerm)*;
        new Parsers(
          Parser.get(StringTermParser.class),
          new ZeroOrMore(
            new Choice(
              Parser.get(StringPlusParser.class),
              Parser.get(StringTermParser.class)
            )
          )
        );

	}
}