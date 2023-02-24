package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;

public class StringNotEqualsExpressionParser extends WhiteSpaceDelimitedLazyChain implements BinaryOperatorParser ,BooleanExpression{

private static final long serialVersionUID = -6949606984841006427L;

	public StringNotEqualsExpressionParser() {
		super();
	}

	List<Parser> parsers;
	
	@Override
	public void initialize() {
		parsers = 
			new Parsers(
				Parser.get(StringExpressionParser.class),
				Parser.get(NotEqualParser.class),
				Parser.get(StringExpressionParser.class)
			);
	}

	@Override
	public List<Parser> getLazyParsers() {
		return parsers; 
	}
	
	public static Token getLeftExpression(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(0);
	}
	
	public static Token getRightExpression(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(2);
	}

}