package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.bool.BooleanExpression;

public class NotEqualParser extends WordParser implements BooleanExpression{

	private static final long serialVersionUID = 6534427781820258318L;

	public NotEqualParser() {
		super("!=");
	}
}