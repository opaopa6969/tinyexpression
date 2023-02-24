package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;

public class StringInParser extends WhiteSpaceDelimitedLazyChain implements BooleanExpression{

	private static final long serialVersionUID = -6734066553844884039L;
	
	List<Parser> parsers;
	
	public StringInParser() {
		super();
	}

	@Override
	public void initialize() {
		//  StringIn:=StringExpression'.in('StringExpression(','StringExpression)*')';
		parsers = 
			new Parsers(
				Parser.get(StringExpressionParser.class),
				Parser.get(InMethodParser.class)
			);
	}


	@Override
	public List<Parser> getLazyParsers() {
		return parsers;
	}
	
	public static Token getLeftExpression(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(0);
	}
	
	public static Token getInMethod(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(1);
	}
}