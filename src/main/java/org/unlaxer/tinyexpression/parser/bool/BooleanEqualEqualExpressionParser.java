package org.unlaxer.tinyexpression.parser.bool;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.BinaryOperatorParser;
import org.unlaxer.tinyexpression.parser.EqualEqualParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class BooleanEqualEqualExpressionParser extends JavaStyleDelimitedLazyChain implements BinaryOperatorParser{

	private static final long serialVersionUID = -7023729381549645494L;
	
	public BooleanEqualEqualExpressionParser() {
		super();
	}

	@Override
	public Parsers getLazyParsers() {
		return
	    new Parsers(
        Parser.get(BooleanFactorParser.class),
        Parser.get(EqualEqualParser.class),
        Parser.get(BooleanFactorParser.class)
      );
	}
}