package org.unlaxer.tinyexpression.parser.javatype;

import org.unlaxer.Name;
import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.posix.HashParser;

public class JavaClassAndHashParser extends LazyChain{

	private static final long serialVersionUID = 1784019191326765711L;

	public JavaClassAndHashParser() {
		super();
	}

	public JavaClassAndHashParser(Name name) {
		super(name);
	}
	
	@Override
	public org.unlaxer.parser.Parsers getLazyParsers() {
	  return 
      new Parsers(
        Parser.get(JavaClassNameParser.class),
        Parser.get(HashParser.class)
      );
	}
	
	public static Token getJavaClass(Token thisParserParsed) {
	  
	  return thisParserParsed.getChildWithParser(JavaClassNameParser.class);
	}
}