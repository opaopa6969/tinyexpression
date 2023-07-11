package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class NumberDefaultCaseFactorParser extends JavaStyleDelimitedLazyChain{
	
	private static final long serialVersionUID = -955174558962757636L;


	public NumberDefaultCaseFactorParser() {
		super();
	}

	@Override
	public List<Parser> getLazyParsers() {
		return
	    new Parsers(
        new WordParser(","),
        new WordParser("default"),
        new WordParser("->"),
        new Choice(
            Parser.newInstance(StrictTypedNumberExpressionParser.class),
            Parser.get(NumberExpressionParser.class)
        ).addTag(NumberMatchExpressionParser.choiceTag)
      );
	}
}