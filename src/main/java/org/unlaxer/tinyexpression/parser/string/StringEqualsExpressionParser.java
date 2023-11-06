package org.unlaxer.tinyexpression.parser.string;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.BinaryOperatorParser;
import org.unlaxer.tinyexpression.parser.EqualEqualParser;
import org.unlaxer.tinyexpression.parser.bool.BooleanExpression;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;
public class StringEqualsExpressionParser extends JavaStyleDelimitedLazyChain implements BinaryOperatorParser ,BooleanExpression{

	
	private static final long serialVersionUID = -1451866679195094560L;
	
	
	public StringEqualsExpressionParser() {
		super();
	}

	@Override
	public Parsers getLazyParsers() {
	  return
      new Parsers(
        Parser.get(StringExpressionParser.class),
        Parser.get(EqualEqualParser.class),
        Parser.get(StringExpressionParser.class)
      );
	}
	
	@TokenExtractor
	public static Token getLeftExpression(Token thisParserParsed) {
		return thisParserParsed.getChildrenWithParserAsList(StringExpressionParser.class).get(0); //0
	}
	
  @TokenExtractor
	public static Token getRightExpression(Token thisParserParsed) {
    return thisParserParsed.getChildrenWithParserAsList(StringExpressionParser.class).get(1); //1
	}

}