package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.NoneChildCollectingParser;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedChain;
import org.unlaxer.parser.combinator.ZeroOrMore;

public class TermParser extends NoneChildCollectingParser implements Expression {
	
	private static final long serialVersionUID = 1430560948407993197L;
	
	public TermParser() {
		super();
	}
	
	Parser parser;
	
	@Override
	public void initialize() {
		// <term>::= <factor>[('*'|'/')<factor>]*
		parser = 
			new WhiteSpaceDelimitedChain(
				Parser.get(FactorParser.class),
				new ZeroOrMore(
					new WhiteSpaceDelimitedChain(
						new Choice(
							Parser.get(MultipleParser.class),
							Parser.get(DivisionParser.class)
						),
						Parser.get(FactorParser.class)
					)
				)
			);
	}

	@Override
	public Parser createParser() {
		return parser;
	}
}
