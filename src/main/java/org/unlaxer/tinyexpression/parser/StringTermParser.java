package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.combinator.ZeroOrMore;

public class StringTermParser extends WhiteSpaceDelimitedLazyChain implements StringExpression{

	private static final long serialVersionUID = 1742165276514464092L;
	

	public StringTermParser() {
		super();
	}


	List<Parser> parsers;

	
	@Override
	public void initialize() {
		
//		.append("StringTerm:=StringFactor,Slice*;")
		parsers = 
			new Parsers(
				Parser.get(StringFactorParser.class),
				new ZeroOrMore(
					Parser.get(SliceParser.class)
				)
			);
	}


	@Override
	public List<Parser> getLazyParsers() {
		return parsers;
	}
}