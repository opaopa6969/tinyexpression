package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;

public class BooleanXorExpressionParser extends WhiteSpaceDelimitedLazyChain implements BinaryOperatorParser{

	private static final long serialVersionUID = -3478050535868409168L;

	public BooleanXorExpressionParser() {
		super();
	}
	
	List<Parser> parsers;
	
	@Override
	public void initialize() {
		parsers = new Parsers(
			Parser.get(BooleanExpressionParser.class),
			Parser.get(XorParser.class),
			Parser.get(BooleanExpressionParser.class)
		);
	}

	@Override
	public List<Parser> getLazyParsers() {
		return parsers; 
	}
}