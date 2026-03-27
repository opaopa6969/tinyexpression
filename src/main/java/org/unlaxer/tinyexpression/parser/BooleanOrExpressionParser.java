package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class BooleanOrExpressionParser extends JavaStyleDelimitedLazyChain implements BinaryOperatorParser{

	private static final long serialVersionUID = 7759882947777575321L;

	public BooleanOrExpressionParser() {
		super();
	}

	@Override
	public org.unlaxer.parser.Parsers getLazyParsers() {
	  return
      new Parsers(
        Parser.get(BooleanFactorParser.class),
        Parser.get(OrParser.class),
        Parser.get(BooleanFactorParser.class)
      );
	}
}