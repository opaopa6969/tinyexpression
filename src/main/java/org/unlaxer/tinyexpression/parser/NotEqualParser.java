package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.elementary.WordParser;

public class NotEqualParser extends WordParser implements BooleanExpression{

	private static final long serialVersionUID = 6534427781820258318L;

	public NotEqualParser() {
		super("!=");
	}
}