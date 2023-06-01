package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class TernaryOperatorParser extends JavaStyleDelimitedLazyChain{

	private static final long serialVersionUID = -6559995208538992563L;

	public TernaryOperatorParser() {
		super();
	}

	@Override
	public List<Parser> getLazyParsers() {
	  return  
	      new Parsers(
	        Parser.get(BooleanExpressionParser.class),
	        Parser.get(QuestionParser.class),
	        Parser.get(ExpressionParser.class),
	        Parser.get(ColonParser.class),
	        Parser.get(ExpressionParser.class)
	      );
	  }

}