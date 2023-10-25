package org.unlaxer.tinyexpression.parser.javalang;
import java.util.List;

import org.unlaxer.Name;
import org.unlaxer.parser.ChainParsers;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.elementary.WildCardStringTerninatorParser;
import org.unlaxer.parser.elementary.WordParser;

public class JavaBlockComment extends LazyChain{
  
  // parser for /* */ comment

	private static final long serialVersionUID = 2143565367402566927L;

	public JavaBlockComment() {
		super();
	}
	public JavaBlockComment(Name name) {
		super(name);
	}

	@Override
	public org.unlaxer.parser.Parsers getLazyParsers() {
		return new ChainParsers(
			new WordParser("/*"),
			new WildCardStringTerninatorParser("*/"),
			new WordParser("*/")
		);
	}
}