package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedChain;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.parser.elementary.WordParser;

public class CaseExpressionParser extends WhiteSpaceDelimitedLazyChain{
	
	private static final long serialVersionUID = 5853356919426515297L;


	public CaseExpressionParser() {
		super();
	}
	
	List<Parser> parsers;

	
	@Override
	public void initialize() {
		parsers = 
			new Parsers(
				Parser.get(CaseFactorParser.class),
				new ZeroOrMore(
					new WhiteSpaceDelimitedChain(
						new WordParser(","),
						Parser.get(CaseFactorParser.class)
					)
				)
			);
	}


	@Override
	public List<Parser> getLazyParsers() {
		return parsers;
	}
}