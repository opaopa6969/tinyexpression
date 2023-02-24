package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Name;
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
	
	List<Parser> parsers;
	
	@Override
	public void initialize() {
		parsers = 
			new Parsers(
					Parser.get(JavaClassNameParser.class),
					Parser.get(HashParser.class)
				);
	}

	@Override
	public List<Parser> getLazyParsers() {
		return parsers; 
	}
	

}