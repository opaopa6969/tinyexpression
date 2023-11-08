package org.unlaxer.tinyexpression.parser.bool;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.BinaryOperatorParser;
import org.unlaxer.tinyexpression.parser.XorParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class BooleanXorExpressionParser extends JavaStyleDelimitedLazyChain implements BinaryOperatorParser{

	private static final long serialVersionUID = -3478050535868409168L;

	public BooleanXorExpressionParser() {
		super();
	}

	@Override
	public Parsers getLazyParsers() {
	  return 
      new Parsers(
        Parser.get(BooleanFactorParser.class),
        Parser.get(XorParser.class),
        Parser.get(BooleanFactorParser.class)
      );
	}
}