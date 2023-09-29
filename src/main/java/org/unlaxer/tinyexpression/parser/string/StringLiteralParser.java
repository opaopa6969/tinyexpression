package org.unlaxer.tinyexpression.parser.string;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.parser.elementary.DoubleQuotedParser;
import org.unlaxer.parser.elementary.SingleQuotedParser;

public class StringLiteralParser extends LazyChoice implements StringExpression{

	private static final long serialVersionUID = 3503116045698010940L;

	public StringLiteralParser() {
		super();
	}
	
	@Override
	public List<Parser> getLazyParsers() {
	  return 
	      // StringLiteral:="CharactersWithoutDoubleQuote*"|'CharactersWithoutSingleQuote*';      
        new Parsers(
          new DoubleQuotedParser(),
          new SingleQuotedParser()
        );

	}
	
}