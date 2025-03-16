package org.unlaxer.tinyexpression.parser.stringtype;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.IndexOfMethodParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class StringIndexOfParser extends JavaStyleDelimitedLazyChain{
	
	private static final long serialVersionUID = 4873171506716157516L;


	public StringIndexOfParser() {
		super();
	}

	@Override
	public org.unlaxer.parser.Parsers getLazyParsers() {
	   // StringIndexOf:=StringExpression'.indexOf('StringExpression')';
    return  
      new Parsers(
        Parser.get(StringExpressionParser.class),
        Parser.get(IndexOfMethodParser.class)
      );

	}
}