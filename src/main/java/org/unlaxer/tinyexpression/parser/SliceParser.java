package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Chain;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.combinator.ZeroOrOne;
import org.unlaxer.parser.elementary.WordParser;

public class SliceParser extends WhiteSpaceDelimitedLazyChain implements StringExpression{
	
	private static final long serialVersionUID = 5398027501329177390L;


	public SliceParser() {
		super();
	}
	
	List<Parser> parsers;

	
	@Override
	public void initialize() {
		// Slice:='['Expression?':'Expression?(':'Expression)?']';
		parsers = 
			new Parsers(
				Parser.<WordParser>get(()->new WordParser("[")),
				new ZeroOrOne(
					Parser.get(ExpressionParser.class)
				),
				Parser.<WordParser>get(()->new WordParser(":")),
				new ZeroOrOne(
					Parser.get(ExpressionParser.class)
				),
				new ZeroOrOne(
					new Chain(
						Parser.<WordParser>get(()->new WordParser(":")),
						Parser.get(ExpressionParser.class)
					)
				),
				Parser.<WordParser>get(()->new WordParser("]"))
			);
	}


	@Override
	public List<Parser> getLazyParsers() {
		return parsers;
	}
}