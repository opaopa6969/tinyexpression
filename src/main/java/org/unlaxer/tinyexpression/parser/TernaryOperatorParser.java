package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class TernaryOperatorParser extends JavaStyleDelimitedLazyChain{

	private static final long serialVersionUID = -6559995208538992563L;

	public TernaryOperatorParser() {
		super();
	}

	@Override
	public org.unlaxer.parser.Parsers getLazyParsers() {
	  return  
	      new Parsers(
	        Parser.get(BooleanFactorParser.class),
	        Parser.get(QuestionParser.class),
	        Parser.get(NumberExpressionParser.class),
	        Parser.get(ColonParser.class),
	        Parser.get(NumberExpressionParser.class)
	      );
	  }

}