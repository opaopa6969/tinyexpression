package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Name;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.parser.combinator.Chain;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.parser.posix.DotParser;

public class JavaClassNameParser extends LazyChain {

	private static final long serialVersionUID = -8875168129834784571L;

	public JavaClassNameParser() {
		super();
	}

	public JavaClassNameParser(Name name) {
		super(name);
	}
	
	@Override
	public org.unlaxer.parser.Parsers getLazyParsers() {
	  
	  return 
      new Parsers(
        Parser.get(IdentifierParser.class),
        new ZeroOrMore(
          new Chain(
            Parser.get(DotParser.class),
            Parser.get(IdentifierParser.class)
          ) 
        )
      );
	}
}