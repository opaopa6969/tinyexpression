package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.tinyexpression.parser.numbertype.NumberExpression;
import org.unlaxer.tinyexpression.parser.stringtype.StringLengthParser;

public class FactorOfStringParser extends LazyChoice implements NumberExpression{
	
	private static final long serialVersionUID = -371473916528690853L;
	
	
	public FactorOfStringParser() {
		super();
	}

	@Override
	public org.unlaxer.parser.Parsers getLazyParsers() {
	  
	  return 
	      
	    // FactorOfString:=StringLength|StringIndexOf;
      new Parsers(
        Parser.get(StringLengthParser.class)
//	          Parser.get(StringIndexOfParser.class)
      );

	}
}