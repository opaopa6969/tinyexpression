package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class BooleanNotEqualExpressionParser extends JavaStyleDelimitedLazyChain implements BinaryOperatorParser{

	private static final long serialVersionUID = 4968376203603812079L;
	
	
	public BooleanNotEqualExpressionParser() {
		super();
	}
	
	@Override
	public org.unlaxer.parser.Parsers getLazyParsers() {
    return 
      new Parsers(
        Parser.get(BooleanFactorParser.class),
        Parser.get(NotEqualParser.class),
        Parser.get(BooleanFactorParser.class)
      );
	}
}