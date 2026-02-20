package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.SuggestableParser;

public class ToNumNameParser extends SuggestableParser {

	private static final long serialVersionUID = 2741478106997736124L;

	public ToNumNameParser() {
		super(true, "toNum");
	}
	
	@Override
	public String getSuggestString(String matchedString) {
		return "(".concat(matchedString).concat(")");
	}
}