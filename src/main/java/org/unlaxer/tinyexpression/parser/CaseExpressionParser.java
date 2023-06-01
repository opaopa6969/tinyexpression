package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedChain;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class CaseExpressionParser extends JavaStyleDelimitedLazyChain{
	
	private static final long serialVersionUID = 5853356919426515297L;


	public CaseExpressionParser() {
		super();
	}
	

	@Override
	public List<Parser> getLazyParsers() {
		return
      new Parsers(
        Parser.get(CaseFactorParser.class),
        new ZeroOrMore(
          new WhiteSpaceDelimitedChain(
            new WordParser(","),
            Parser.get(CaseFactorParser.class)
          )
        )
      );

	}
}