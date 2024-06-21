package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedChain;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class BooleanCaseExpressionParser extends JavaStyleDelimitedLazyChain{
	
	public BooleanCaseExpressionParser() {
		super();
    addTag(ExpressionTags.matchCase.tag());
	}

	@Override
	public org.unlaxer.parser.Parsers getLazyParsers() {
		return
      new Parsers(
        Parser.get(BooleanCaseFactorParser.class),
        new ZeroOrMore(
          new WhiteSpaceDelimitedChain(
            new WordParser(","),
            Parser.get(BooleanCaseFactorParser.class)
          )
        )
      );

	}
}