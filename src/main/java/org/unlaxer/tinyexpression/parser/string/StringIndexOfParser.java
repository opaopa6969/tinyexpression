package org.unlaxer.tinyexpression.parser.string;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
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
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

	@Override
	public Parsers getLazyParsers() {
	   // StringIndexOf:=StringExpression'.indexOf('StringExpression')';
    return  
      new Parsers(
        Parser.get(StringExpressionParser.class),
        Parser.get(IndexOfMethodParser.class)
      );

	}
}