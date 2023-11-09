package org.unlaxer.tinyexpression.parser.string;

import org.unlaxer.Parsed;
import org.unlaxer.Token;
import org.unlaxer.TokenKind;
import org.unlaxer.TokenPredicators;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.ExpressionTags;
import org.unlaxer.tinyexpression.parser.VariableParser;
import org.unlaxer.tinyexpression.parser.bool.BooleanExpressionParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class StringCaseFactorParser extends JavaStyleDelimitedLazyChain{
	
	public StringCaseFactorParser() {
		super();
		addTag(ExpressionTags.matchCaseFactor.tag());
  }
	
  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

	@Override
	public Parsers getLazyParsers() {
	  return 
      new Parsers(
        Parser.get(BooleanExpressionParser.class),//0
        new WordParser("->"),
        new Choice(
            Parser.newInstance(StrictTypedStringExpressionParser.class),
            Parser.get(StringExpressionParser.class)
        ).addTag(StringMatchExpressionParser.choiceTag)
      );
	}

  @TokenExtractor
	public static Token getBooleanExpression(Token thisParserParsed) {
		return thisParserParsed.getChildWithParser(BooleanExpressionParser.class); //0
	}

  @TokenExtractor
	public static Token getExpression(Token thisParserParsed) {
		return thisParserParsed.getChild(
		    TokenPredicators.parserImplements(StringExpression.class, VariableParser.class));
	}

}