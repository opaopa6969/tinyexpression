package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class StringDefaultCaseFactorParser extends JavaStyleDelimitedLazyChain{
	
	public StringDefaultCaseFactorParser() {
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
            Parser.newInstance(StrictTypedStringExpressionParser.class),
            Parser.get(StringExpressionParser.class)
        ).addTag(StringMatchExpressionParser.choiceTag)
      );
	}
	
  @TokenExtractor
	public static Token getExpression(Token thisParserParsed) {
		return thisParserParsed.getChild(
		    TokenPredicators.parserImplements(StringExpression.class , VariableParser.class)); //3
	}

}