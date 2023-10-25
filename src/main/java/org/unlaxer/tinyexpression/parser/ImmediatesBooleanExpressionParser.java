package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;

public class ImmediatesBooleanExpressionParser extends LazyChoice{

	private static final long serialVersionUID = 7283923437053966934L;
	
	public ImmediatesBooleanExpressionParser() {
		super();
	}

	@Override
	public org.unlaxer.parser.Parsers getLazyParsers() {
	  
	  return 
      new Parsers(
        Parser.get(BooleanVariableParser.class),
        Parser.get(NakedVariableParser.class),
        Parser.get(TrueTokenParser.class),
        Parser.get(FalseTokenParser.class)
      );
	}
}