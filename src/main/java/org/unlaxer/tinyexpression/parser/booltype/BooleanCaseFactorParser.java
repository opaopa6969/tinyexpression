package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;
import org.unlaxer.util.annotation.TokenExtractor.Timing;

public class BooleanCaseFactorParser extends JavaStyleDelimitedLazyChain{
	
	public BooleanCaseFactorParser() {
		super();
		addTag(ExpressionTags.matchCaseFactor.tag());
	}

	@Override
	public org.unlaxer.parser.Parsers getLazyParsers() {
	  return 
      new Parsers(
        Parser.get(BooleanExpressionParser.class),//0
        new WordParser("->"),
        new Choice(
            Parser.newInstance(StrictTypedBooleanExpressionParser.class),
            Parser.get(BooleanExpressionParser.class)
        ).addTag(BooleanMatchExpressionParser.choiceTag)
      );
	}

  @TokenExtractor(timings = {Timing.UseOperatorOperandTree})
	public static Token getBooleanExpression(Token thisParserParsed) {
		return thisParserParsed.getChildWithParser(BooleanExpressionParser.class); //0
	}

  @TokenExtractor(timings = {Timing.UseOperatorOperandTree})
	public static Token getExpression(Token thisParserParsed) {
		return thisParserParsed.getChild(
		    TokenPredicators.parserImplements(BooleanExpression.class, VariableParser.class));
	}

}