package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Name;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.parser.posix.AlphabetNumericUnderScoreParser;
import org.unlaxer.parser.posix.AlphabetUnderScoreParser;

public class IdentifierParser extends LazyChain {

	private static final long serialVersionUID = -5555275182104345815L;

	public IdentifierParser() {
		super();
	}

	public IdentifierParser(Name name) {
		super(name);
	}
	
	@Override
	public List<Parser> getLazyParsers() {
	  return
	      new Parsers(
	        Parser.get(AlphabetUnderScoreParser.class),
	        new ZeroOrMore(
	          Parser.get(AlphabetNumericUnderScoreParser.class)
	        )
	      );
	}

}