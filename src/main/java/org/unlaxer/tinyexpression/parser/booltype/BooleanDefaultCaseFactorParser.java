package org.unlaxer.tinyexpression.parser.booltype;

import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.ExpressionTags;
import org.unlaxer.tinyexpression.parser.StrictTypedBooleanExpressionParser;
import org.unlaxer.tinyexpression.parser.VariableParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class BooleanDefaultCaseFactorParser extends JavaStyleDelimitedLazyChain{
	
	public BooleanDefaultCaseFactorParser() {
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