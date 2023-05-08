package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;

public class FactorOfStringParser extends LazyChoice implements Expression{
	
	private static final long serialVersionUID = -371473916528690853L;
	
	
	public FactorOfStringParser() {
		super();
	}

	@Override
	public List<Parser> getLazyParsers() {
	  
	  return 
	      
	    // FactorOfString:=StringLength|StringIndexOf;
      new Parsers(
        Parser.get(StringLengthParser.class)
//	          Parser.get(StringIndexOfParser.class)
      );

	}
}