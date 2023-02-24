package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedChain;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.parser.elementary.WordParser;

public class CommaSeparatedStringExpressionParser extends WhiteSpaceDelimitedLazyChain{

	private static final long serialVersionUID = -145498364741083714L;
	
	List<Parser> parsers;
	
	public CommaSeparatedStringExpressionParser() {
		super();
	}


	@Override
	public void initialize() {
		//  CommaSeparatedStringExpression:=StringExpression(','StringExpression)*')';
		parsers = 
			new Parsers(
				Parser.get(StringExpressionParser.class),
				new ZeroOrMore(
					new WhiteSpaceDelimitedChain(
						Parser.<WordParser>get(()->new WordParser(",")),
						Parser.get(StringExpressionParser.class)
					)
				)
			);
	}


	@Override
	public List<Parser> getLazyParsers() {
		return parsers;
	}
	
}