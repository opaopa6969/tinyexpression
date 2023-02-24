package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;

public class BooleanEqualEqualExpressionParser extends WhiteSpaceDelimitedLazyChain implements BinaryOperatorParser{

	private static final long serialVersionUID = -7023729381549645494L;
	
	public BooleanEqualEqualExpressionParser() {
		super();
	}
	
	List<Parser> parsers;
	
	@Override
	public void initialize() {
		parsers = new Parsers(
			Parser.get(BooleanExpressionParser.class),
			Parser.get(EqualEqualParser.class),
			Parser.get(BooleanExpressionParser.class)
		);
	}

	@Override
	public List<Parser> getLazyParsers() {
		return parsers; 
	}
}