package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.RootParserIndicator;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.NoneChildCollectingParser;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedChain;
import org.unlaxer.parser.combinator.ZeroOrMore;

public class ExpressionParser extends NoneChildCollectingParser implements RootParserIndicator , Expression{
	
	private static final long serialVersionUID = -2100891203224283395L;
	
	Parser parser;

	public ExpressionParser() {
		super();
	}

	@Override
	public Parser createParser() {
	  return
      // <expression> ::= <term>[('+'|'-')<term>]*
        new WhiteSpaceDelimitedChain(
            Parser.get(TermParser.class),
            new ZeroOrMore(
              new WhiteSpaceDelimitedChain(
                new Choice(
                  Parser.get(PlusParser.class),
                  Parser.get(MinusParser.class)
                ),
                Parser.get(TermParser.class)
              )
            )
          );

	}
}