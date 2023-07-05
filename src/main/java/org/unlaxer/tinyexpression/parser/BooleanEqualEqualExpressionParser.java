package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class BooleanEqualEqualExpressionParser extends JavaStyleDelimitedLazyChain implements BinaryOperatorParser{

	private static final long serialVersionUID = -7023729381549645494L;
	
	public BooleanEqualEqualExpressionParser() {
		super();
	}

	@Override
	public List<Parser> getLazyParsers() {
		return
	    new Parsers(
        Parser.get(BooleanFactorParser.class),
        Parser.get(EqualEqualParser.class),
        Parser.get(BooleanFactorParser.class)
      );
	}
}