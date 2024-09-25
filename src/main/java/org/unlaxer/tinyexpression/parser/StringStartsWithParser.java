package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class StringStartsWithParser extends JavaStyleDelimitedLazyChain implements BooleanExpression{
	
	private static final long serialVersionUID = 4961342621488883708L;
	
	public StringStartsWithParser() {
		super();
	}

	@Override
	public org.unlaxer.parser.Parsers getLazyParsers() {
    //  StringStartsWith:=StringExpression'.startsWith('StringExpression')';
    return
      new Parsers(
        Parser.get(StringExpressionParser.class),
//        Parser.get(StringStartsWithMethodParser.class)
				Parser.get(StartsWithMethodParser.class)
      );
	}

	@TokenExtractor
	public static Token getLeftExpression(Token thisParserParsed) {
		return thisParserParsed.getChildWithParser(StringExpressionParser.class);
	}

	@TokenExtractor
	public static Token getInMethod(Token thisParserParsed) {
		return thisParserParsed.getChildWithParser(StartsWithMethodParser.class);
	}
}