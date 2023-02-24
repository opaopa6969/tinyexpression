package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Name;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;

public class ImmediatesBooleanExpressionParser extends LazyChoice{

	private static final long serialVersionUID = 7283923437053966934L;
	
	public ImmediatesBooleanExpressionParser() {
		super();
	}

	List<Parser> parsers;
	
	@Override
	public void initialize() {
		parsers = 
			new Parsers(
				Parser.get(()->new VariableParser(Name.of("booleanVariable"))),
				Parser.get(TrueTokenParser.class),
				Parser.get(FalseTokenParser.class)
			);
	}

	@Override
	public List<Parser> getLazyParsers() {
		return parsers; 
	}
}