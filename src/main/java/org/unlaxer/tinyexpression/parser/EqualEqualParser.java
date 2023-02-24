package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.elementary.WordParser;

public class EqualEqualParser extends WordParser implements BooleanExpression{

	private static final long serialVersionUID = -1468277152882221234L;
	
	public EqualEqualParser() {
		super("==");
	}
}