package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedChain;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.parser.elementary.WordParser;

public class BooleanClauseParser extends WhiteSpaceDelimitedLazyChain implements BooleanExpression{

	private static final long serialVersionUID = 1362501275934237988L;

	public BooleanClauseParser() {
		super();
	}

	List<Parser> parsers;

	@Override
	public void initialize() {
		// <BooleanClause> ::= <BooleanExpression>[('=='|'!='|'&'|'|'|'^')<BooleanExpression>]*
		
		parsers =
			new Parsers(
				Parser.get(BooleanExpressionParser.class),
				new ZeroOrMore(
					new WhiteSpaceDelimitedChain(
						new Choice(
							Parser.<WordParser>get(()->new EqualEqualParser()),
							Parser.<WordParser>get(()->new NotEqualParser()),
							Parser.<WordParser>get(()->new AndParser()),
							Parser.<WordParser>get(()->new OrParser()),
							Parser.<WordParser>get(()->new XorParser())
						),
						Parser.get(BooleanExpressionParser.class)
					)
				)
			);
	}

	@Override
	public List<Parser> getLazyParsers() {
		return parsers;
	}
	
}