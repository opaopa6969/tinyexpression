package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;

public class BooleanOrExpressionParser extends WhiteSpaceDelimitedLazyChain implements BinaryOperatorParser{

	private static final long serialVersionUID = 7759882947777575321L;

	public BooleanOrExpressionParser() {
		super();
	}
	
	List<Parser> parsers;
	
	@Override
	public void initialize() {
		parsers = new Parsers(
			Parser.get(BooleanExpressionParser.class),
			Parser.get(OrParser.class),
			Parser.get(BooleanExpressionParser.class)
		);
	}

	@Override
	public List<Parser> getLazyParsers() {
		return parsers; 
	}
}