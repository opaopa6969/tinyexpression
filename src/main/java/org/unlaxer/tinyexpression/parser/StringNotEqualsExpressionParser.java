package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class StringNotEqualsExpressionParser extends JavaStyleDelimitedLazyChain implements BinaryOperatorParser ,BooleanExpression{

private static final long serialVersionUID = -6949606984841006427L;

	public StringNotEqualsExpressionParser() {
		super();
	}

	@Override
	public org.unlaxer.parser.Parsers getLazyParsers() {
	  return 
	      new Parsers(
	        Parser.get(StringExpressionParser.class),
	        Parser.get(NotEqualParser.class),
	        Parser.get(StringExpressionParser.class)
	      );	}

	@TokenExtractor
	public static Token getLeftExpression(Token thisParserParsed) {
		return thisParserParsed.getChildrenWithParserAsList(StringExpressionParser.class).get(0); //0
	}
	
  @TokenExtractor
	public static Token getRightExpression(Token thisParserParsed) {
    return thisParserParsed.getChildrenWithParserAsList(StringExpressionParser.class).get(1); //2
	}

}