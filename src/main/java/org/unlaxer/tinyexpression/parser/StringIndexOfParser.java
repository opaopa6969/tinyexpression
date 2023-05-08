package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;

public class StringIndexOfParser extends WhiteSpaceDelimitedLazyChain{
	
	private static final long serialVersionUID = 4873171506716157516L;


	public StringIndexOfParser() {
		super();
	}

	@Override
	public List<Parser> getLazyParsers() {
	   // StringIndexOf:=StringExpression'.indexOf('StringExpression')';
    return  
      new Parsers(
        Parser.get(StringExpressionParser.class),
        Parser.get(IndexOfMethodParser.class)
      );

	}
}