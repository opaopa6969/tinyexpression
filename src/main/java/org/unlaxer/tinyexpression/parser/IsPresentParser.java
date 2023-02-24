package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;

public class IsPresentParser extends WhiteSpaceDelimitedLazyChain implements BooleanExpression{
	
	private static final long serialVersionUID = -4619955945031421138L;


	public IsPresentParser() {
		super();
	}
	
	List<Parser> parsers;

	
	@Override
	public void initialize() {
		// IsPresentExpression:='isPresent('Variable');
		parsers = 
			new Parsers(
				Parser.get(IsPresentNameParser.class),
				Parser.get(LeftParenthesisParser.class),
				Parser.get(VariableParser.class),//2
				Parser.get(RightParenthesisParser.class)
			);
	}


	@Override
	public List<Parser> getLazyParsers() {
		return parsers;
	}
	
	public static Token getVariable(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(2);
	}
}