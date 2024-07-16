package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Name;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.StaticParser;
import org.unlaxer.parser.ascii.PointParser;
import org.unlaxer.parser.combinator.Chain;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.combinator.OneOrMore;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.parser.elementary.ExponentParser;
import org.unlaxer.parser.elementary.SignParser;
import org.unlaxer.parser.posix.DigitParser;

public class NumberParser extends LazyChain implements StaticParser , NumberExpression{
	
	private static final long serialVersionUID = -7768486767795358533L;
	
	static final Parser digitParser = new DigitParser();
	static final Parser signParser = new SignParser();
	static final Parser pointParser = new PointParser();
	static final OneOrMore digitsParser = new OneOrMore(Name.of("any-digit"),digitParser);
	
	public NumberParser() {
		super();
	}

	@Override
	public org.unlaxer.parser.Parsers getLazyParsers() {
		return new Parsers(				
				// + or -
				new Optional(Name.of("optional-signParser"),signParser),
				new Choice(
					// 12.3
					new Chain(Name.of("digits-point-degits"),
						digitsParser,
						pointParser,
						digitsParser
					),
					//12.
					new Chain(Name.of("digits-point"),
						digitsParser,
						pointParser
					),
					//12
					new Chain(Name.of("digits"),
						digitsParser
					),
					//.3
					new Chain(Name.of("point-digits"),
						pointParser,
						digitsParser
					)
				),
				// e-3
				new Optional(ExponentParser.class)
		);
	}

}
