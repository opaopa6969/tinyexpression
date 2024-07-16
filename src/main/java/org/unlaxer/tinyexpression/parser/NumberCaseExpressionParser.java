package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedChain;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class NumberCaseExpressionParser extends JavaStyleDelimitedLazyChain{
	
	private static final long serialVersionUID = 5853356919426515297L;


	public NumberCaseExpressionParser() {
		super();
    addTag(ExpressionTags.matchCase.tag());
	}
	

	@Override
	public org.unlaxer.parser.Parsers getLazyParsers() {
		return
      new Parsers(
        Parser.get(NumberCaseFactorParser.class),
        new ZeroOrMore(
          new WhiteSpaceDelimitedChain(
            new WordParser(","),
            Parser.get(NumberCaseFactorParser.class)
          )
        )
      );

	}
}