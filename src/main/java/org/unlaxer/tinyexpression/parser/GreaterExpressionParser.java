package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;

public class GreaterExpressionParser extends WhiteSpaceDelimitedLazyChain implements BinaryOperatorParser ,BooleanExpression{

	private static final long serialVersionUID = -7037973346958970512L;
	
	
	public GreaterExpressionParser() {
		super();
	}
	
	List<Parser> parsers;
	
	@Override
	public void initialize() {
		parsers = 
			new Parsers(
				Parser.get(ExpressionParser.class),
				Parser.get(GreaterParser.class),
				Parser.get(ExpressionParser.class)
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