package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class BooleanAndExpressionParser extends JavaStyleDelimitedLazyChain implements BinaryOperatorParser{

	private static final long serialVersionUID = -7028954465842938523L;

	public BooleanAndExpressionParser() {
		super();
	}
	
	 @Override
	  public List<Parser> getLazyParsers() {
	    return
        new Parsers(
          Parser.get(BooleanExpressionParser.class),
          Parser.get(AndParser.class),
          Parser.get(BooleanExpressionParser.class)
        );
	  }
}