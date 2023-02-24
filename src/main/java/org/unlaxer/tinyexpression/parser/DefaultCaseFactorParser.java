package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.elementary.WordParser;

public class DefaultCaseFactorParser extends WhiteSpaceDelimitedLazyChain{
	
	private static final long serialVersionUID = -955174558962757636L;


	public DefaultCaseFactorParser() {
		super();
	}
	
	List<Parser> parsers;

	
	@Override
	public void initialize() {
		parsers = 
			new Parsers(
				new WordParser(","),
				new WordParser("default"),
				new WordParser("->"),
				Parser.get(ExpressionParser.class)//3
			);
	}


	@Override
	public List<Parser> getLazyParsers() {
		return parsers;
	}
	
	public static Token getExpression(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(3);
	}

}