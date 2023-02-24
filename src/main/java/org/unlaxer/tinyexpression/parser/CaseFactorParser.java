package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.elementary.WordParser;

public class CaseFactorParser extends WhiteSpaceDelimitedLazyChain{
	
	private static final long serialVersionUID = -475039384168549876L;


	public CaseFactorParser() {
		super();
	}
	
	List<Parser> parsers;

	
	@Override
	public void initialize() {
		parsers = 
			new Parsers(
				Parser.get(BooleanClauseParser.class),//0
				new WordParser("->"),
				Parser.get(ExpressionParser.class)//2
			);
	}


	@Override
	public List<Parser> getLazyParsers() {
		return parsers;
	}

	public static Token getBooleanClause(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(0);
	}

	public static Token getExpression(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(2);
	}

}