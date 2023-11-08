package org.unlaxer.tinyexpression.parser.number;

import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.ExpressionTags;
import org.unlaxer.tinyexpression.parser.VariableParser;
import org.unlaxer.tinyexpression.parser.bool.BooleanExpressionParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class NumberCaseFactorParser extends JavaStyleDelimitedLazyChain{
	
	private static final long serialVersionUID = -475039384168549876L;

	public NumberCaseFactorParser() {
		super();
		addTag(ExpressionTags.matchCaseFactor.tag());
	}

	@Override
	public Parsers getLazyParsers() {
	  return 
      new Parsers(
        Parser.get(BooleanExpressionParser.class),//0
        new WordParser("->"),
        new Choice(
            Parser.newInstance(StrictTypedNumberExpressionParser.class),
            Parser.get(NumberExpressionParser.class)
        ).addTag(NumberMatchExpressionParser.choiceTag)
      );
	}

  @TokenExtractor
	public static Token getBooleanExpression(Token thisParserParsed) {
		return thisParserParsed.getChildWithParser(BooleanExpressionParser.class); //0
	}

  @TokenExtractor
	public static Token getExpression(Token thisParserParsed) {
		return thisParserParsed.getChild(
		    TokenPredicators.parserImplements(NumberExpression.class, VariableParser.class));
	}
}