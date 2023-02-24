package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;

public class TernaryOperatorParser extends WhiteSpaceDelimitedLazyChain{

	private static final long serialVersionUID = -6559995208538992563L;

	public TernaryOperatorParser() {
		super();
	}

	List<Parser> parsers;
	
	@Override
	public void initialize() {
		parsers = 
			new Parsers(
				Parser.get(BooleanExpressionParser.class),
				Parser.get(QuestionParser.class),
				Parser.get(ExpressionParser.class),
				Parser.get(ColonParser.class),
				Parser.get(ExpressionParser.class)
			);
	}

	@Override
	public List<Parser> getLazyParsers() {
		return parsers; 
	}

}