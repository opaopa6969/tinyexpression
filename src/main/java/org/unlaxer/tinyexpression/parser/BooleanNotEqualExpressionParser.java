package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class BooleanNotEqualExpressionParser extends JavaStyleDelimitedLazyChain implements BinaryOperatorParser{

	private static final long serialVersionUID = 4968376203603812079L;
	
	
	public BooleanNotEqualExpressionParser() {
		super();
	}
	
	@Override
	public List<Parser> getLazyParsers() {
    return 
      new Parsers(
        Parser.get(BooleanExpressionParser.class),
        Parser.get(NotEqualParser.class),
        Parser.get(BooleanExpressionParser.class)
      );
	}
}