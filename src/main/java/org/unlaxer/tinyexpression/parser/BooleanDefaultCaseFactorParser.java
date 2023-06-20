package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class BooleanDefaultCaseFactorParser extends JavaStyleDelimitedLazyChain{
	
	public BooleanDefaultCaseFactorParser() {
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
            Parser.newInstance(StrictTypedBooleanExpressionParser.class),
            Parser.get(BooleanExpressionParser.class)
        ).addTag(BooleanMatchExpressionParser.choiceTag)
      );
	}
	
  @TokenExtractor
	public static Token getExpression(Token thisParserParsed) {
		return thisParserParsed.getChild(
		    TokenPredicators.parserImplements(BooleanExpression.class , VariableParser.class)); //3
	}

}