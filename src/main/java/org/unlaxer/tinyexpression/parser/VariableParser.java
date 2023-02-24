package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Name;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.combinator.OneOrMore;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedChain;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.parser.posix.AlphabetNumericUnderScoreParser;

public class VariableParser extends LazyChain implements Expression , BooleanExpression , StringExpression{

	private static final long serialVersionUID = -8533685205048474333L;

	public VariableParser() {
		super();
	}

	public VariableParser(Name name) {
		super(name);
	}
	
	List<Parser> parsers;
	
	@Override
	public void initialize() {
		parsers = 
			new Parsers(
				Parser.get(DollarParser.class),
				new OneOrMore(
					Parser.get(AlphabetNumericUnderScoreParser.class)
				),
				new Optional(
				    new WhiteSpaceDelimitedChain(
				        Parser.get(LeftCurlyBraceParser.class),
				        new org.unlaxer.parser.combinator.Choice(
				            new WordParser("string"),
                    new WordParser("boolean"),
                    new WordParser("number")
				        ),
                Parser.get(RightCurlyBraceParser.class)
				    )
				)
			);
	}

	@Override
	public List<Parser> getLazyParsers() {
		return parsers; 
	}
}
