package org.unlaxer.tinyexpression.parser.string;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.InMethodParser;
import org.unlaxer.tinyexpression.parser.bool.BooleanExpression;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class StringInParser extends JavaStyleDelimitedLazyChain implements BooleanExpression{

	private static final long serialVersionUID = -6734066553844884039L;
	
	List<Parser> parsers;
	
	public StringInParser() {
		super();
	}

	@Override
	public List<Parser> getLazyParsers() {
	  return
	      //  StringIn:=StringExpression'.in('StringExpression(','StringExpression)*')';
        new Parsers(
          Parser.get(StringExpressionParser.class),
          Parser.get(InMethodParser.class)
        );

	}

	@TokenExtractor
	public static Token getLeftExpression(Token thisParserParsed) {
		return thisParserParsed.getChildWithParser(StringExpressionParser.class);
	}
	
  @TokenExtractor
	public static Token getInMethod(Token thisParserParsed) {
    return thisParserParsed.getChildWithParser(InMethodParser.class);
	}
}