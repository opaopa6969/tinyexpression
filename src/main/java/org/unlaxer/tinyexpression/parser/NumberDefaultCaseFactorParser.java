package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class NumberDefaultCaseFactorParser extends JavaStyleDelimitedLazyChain{
	
	private static final long serialVersionUID = -955174558962757636L;


	public NumberDefaultCaseFactorParser() {
		super();
    addTag(ExpressionTags.matchDefaultFactor.tag());
	}

	@Override
	public org.unlaxer.parser.Parsers getLazyParsers() {
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