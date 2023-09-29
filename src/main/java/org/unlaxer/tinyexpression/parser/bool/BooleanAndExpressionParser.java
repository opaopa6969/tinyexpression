package org.unlaxer.tinyexpression.parser.bool;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.AndParser;
import org.unlaxer.tinyexpression.parser.BinaryOperatorParser;
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
          Parser.get(BooleanFactorParser.class),
          Parser.get(AndParser.class),
          Parser.get(BooleanFactorParser.class)
        );
	  }
}